import asyncio
import json
import sqlite3
from contextlib import asynccontextmanager
from io import BytesIO
from typing import Annotated
import httpx
from fastapi import Depends, FastAPI, File, Header, HTTPException, Query, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel, Field
from pypdf import PdfReader
from .business import Business
from .config import settings
from .crawler import Crawler
from .knowledge import Knowledge
from .runtime import Runtime
from .store import Store, now, uid


@asynccontextmanager
async def lifespan(app):
    settings.data_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    store = Store(settings.data_dir / "agent.sqlite")
    await store.connect()
    async with httpx.AsyncClient(base_url=settings.java_url.rstrip("/"), timeout=20) as client:
        crawler = Crawler()
        knowledge = Knowledge(store, settings)
        runtime = Runtime(store, knowledge, crawler, client, settings)
        await runtime.start()
        app.state.store, app.state.client = store, client
        app.state.runtime, app.state.knowledge, app.state.crawler = runtime, knowledge, crawler
        try:
            yield
        finally:
            await runtime.close()
            await crawler.close()
            await store.close()


app = FastAPI(title="Smart DIY Agent", version="1.0.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins.split(","), allow_methods=["GET", "POST", "PUT", "DELETE"], allow_headers=["Authorization", "Content-Type"])


@app.exception_handler(sqlite3.IntegrityError)
async def concurrent_write(request, exc):
    return JSONResponse(status_code=409, content={"detail": "当前会话已有任务或数据发生变化，请刷新后重试"})


async def identity(request: Request, authorization: Annotated[str | None, Header()] = None):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "请先登录")
    user = await Business(request.app.state.client, authorization).user()
    return {**user, "authorization": authorization}


async def administrator(user=Depends(identity)):
    if user.get("role") != "ADMIN":
        raise HTTPException(403, "需要管理员权限")
    return user


async def owned_run(request, run_id, user):
    run = await request.app.state.store.run(run_id)
    if not run or run["user_id"] != user["userId"]:
        raise HTTPException(404, "任务不存在")
    return run


class RunRequest(BaseModel):
    message: str = Field(min_length=1, max_length=8000)
    conversation_id: str | None = None
    build_id: int | None = None


class ResumeRequest(BaseModel):
    answer: str = Field(min_length=1, max_length=4000)


class Preferences(BaseModel):
    content: str = Field(max_length=2000)


class Configuration(BaseModel):
    base_url: str = Field(min_length=1, max_length=500)
    model: str = Field(min_length=1, max_length=120)
    api_key: str = Field(default="", max_length=512)
    clear_api_key: bool = False
    embedding_model: str = Field(default="", max_length=120)
    timeout_seconds: int = Field(default=60, ge=10, le=180)
    max_steps: int = Field(default=30, ge=4, le=100)
    max_tool_calls: int = Field(default=20, ge=2, le=60)
    max_output_tokens: int = Field(default=4096, ge=256, le=16384)
    max_run_seconds: int = Field(default=180, ge=30, le=600)
    rag_chunk_size: int = Field(default=900, ge=200, le=4000)
    rag_chunk_overlap: int = Field(default=150, ge=0, le=1000)
    rag_top_k: int = Field(default=6, ge=1, le=20)
    rag_similarity_threshold: float = Field(default=0.35, ge=0, le=1)
    extra_prompt: str = Field(default="", max_length=8000)


class Document(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    content: str = Field(min_length=1, max_length=300000)
    source: str = Field(default="", max_length=2000)


class UrlDocument(BaseModel):
    url: str = Field(max_length=2000)


@app.get("/agent/health")
async def health():
    return {"status": "ok", "service": "smart-diy-agent"}


@app.get("/agent/conversations")
async def conversations(request: Request, user=Depends(identity)):
    return await request.app.state.store.all("SELECT * FROM conversations WHERE user_id=? ORDER BY updated_at DESC", (user["userId"],))


@app.get("/agent/conversations/{conversation_id}")
async def conversation(conversation_id: str, request: Request, user=Depends(identity)):
    store = request.app.state.store
    row = await store.one("SELECT * FROM conversations WHERE id=? AND user_id=?", (conversation_id, user["userId"]))
    if not row:
        raise HTTPException(404, "会话不存在")
    row["messages"] = await store.all("SELECT * FROM messages WHERE conversation_id=? ORDER BY created_at,id", (conversation_id,))
    rows = await store.all("SELECT id FROM runs WHERE conversation_id=? ORDER BY created_at", (conversation_id,))
    row["runs"] = [await store.run(r["id"]) for r in rows]
    return row


@app.delete("/agent/conversations/{conversation_id}")
async def delete_conversation(conversation_id: str, request: Request, user=Depends(identity)):
    store = request.app.state.store
    running = await store.one("SELECT id FROM runs WHERE conversation_id=? AND user_id=? AND status IN ('queued','running')", (conversation_id, user["userId"]))
    if running:
        raise HTTPException(409, "请先取消运行中的任务")
    runs = await store.all("SELECT r.id FROM runs r JOIN conversations c ON c.id=r.conversation_id WHERE c.id=? AND c.user_id=?", (conversation_id, user["userId"]))
    for row in runs:
        await request.app.state.runtime.checkpointer.adelete_thread(row["id"])
    await store.execute("DELETE FROM conversations WHERE id=? AND user_id=?", (conversation_id, user["userId"]))
    return {"deleted": True}


@app.post("/agent/runs", status_code=201)
async def create_run(body: RunRequest, request: Request, user=Depends(identity)):
    if not body.message.strip():
        raise HTTPException(422, "消息不能为空")
    store = request.app.state.store
    active_count = await store.one("SELECT COUNT(*) AS n FROM runs WHERE user_id=? AND status IN ('queued','running','needs_input')", (user["userId"],))
    if active_count["n"] >= 3:
        raise HTTPException(429, "已有三个进行中的任务，请先完成或取消后再创建")
    conversation_id = body.conversation_id or uid()
    if body.conversation_id:
        row = await store.one("SELECT * FROM conversations WHERE id=? AND user_id=?", (conversation_id, user["userId"]))
        if not row:
            raise HTTPException(404, "会话不存在")
        active = await store.one("SELECT id FROM runs WHERE conversation_id=? AND status IN ('queued','running','needs_input')", (conversation_id,))
        if active:
            raise HTTPException(409, "请先完成或取消当前任务")
    else:
        await store.execute("INSERT INTO conversations VALUES(?,?,?,?)", (conversation_id, user["userId"], body.message.strip()[:40], now()))
    message = body.message.strip()
    if body.build_id:
        await Business(request.app.state.client, user["authorization"]).get(f"/api/build-configs/public/{body.build_id}")
        message += f"\n关联配置单 ID：{body.build_id}，请用 get_build 读取。"
    run_id = uid()
    await store.execute("INSERT INTO messages VALUES(?,?,?,?,?)", (uid(), conversation_id, "user", message, now()))
    await store.execute("INSERT INTO runs(id,user_id,conversation_id,status,payload,created_at,updated_at) VALUES(?,?,?,?,?,?,?)", (run_id, user["userId"], conversation_id, "queued", body.model_dump_json(), now(), now()))
    await store.execute("UPDATE conversations SET updated_at=? WHERE id=?", (now(), conversation_id))
    request.app.state.runtime.launch(run_id, user["authorization"])
    return await store.run(run_id)


@app.get("/agent/runs/{run_id}")
async def get_run(run_id: str, request: Request, user=Depends(identity)):
    return await owned_run(request, run_id, user)


@app.get("/agent/runs/{run_id}/activity")
async def run_activity(run_id: str, request: Request, user=Depends(identity)):
    await owned_run(request, run_id, user)
    rows = await request.app.state.store.all("SELECT * FROM events WHERE run_id=? AND type NOT IN ('text_delta','usage') ORDER BY id", (run_id,))
    return [{**row, "data": json.loads(row["data"])} for row in rows]


@app.get("/agent/runs/{run_id}/events")
async def events(run_id: str, request: Request, after: int = 0, user=Depends(identity)):
    await owned_run(request, run_id, user)
    async def stream():
        cursor, idle = after, 0
        while not await request.is_disconnected():
            rows = await request.app.state.store.all("SELECT * FROM events WHERE run_id=? AND id>? ORDER BY id LIMIT 100", (run_id, cursor))
            for row in rows:
                cursor = row["id"]
                payload = json.loads(row["data"])
                if isinstance(payload, dict):
                    payload["_event_time"] = row["created_at"]
                yield f"id: {cursor}\nevent: {row['type']}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"
            run = await request.app.state.store.run(run_id)
            if not run or (run["status"] not in ("queued", "running") and not rows):
                break
            idle += 1
            if idle % 20 == 0:
                yield ": keepalive\n\n"
            await asyncio.sleep(0.25)
    return StreamingResponse(stream(), media_type="text/event-stream", headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})


@app.post("/agent/runs/{run_id}/cancel")
async def cancel(run_id: str, request: Request, user=Depends(identity)):
    run = await owned_run(request, run_id, user)
    task = request.app.state.runtime.tasks.get(run_id)
    if task:
        task.cancel()
        await asyncio.gather(task, return_exceptions=True)
    if run["status"] in ("queued", "running", "needs_input", "interrupted"):
        await request.app.state.store.execute("UPDATE runs SET status='cancelled',updated_at=? WHERE id=?", (now(), run_id))
    return await request.app.state.store.run(run_id)


@app.post("/agent/runs/{run_id}/resume")
async def resume(run_id: str, body: ResumeRequest, request: Request, user=Depends(identity)):
    run = await owned_run(request, run_id, user)
    if run["status"] != "needs_input":
        raise HTTPException(409, "当前任务没有等待补充信息")
    await request.app.state.store.execute("INSERT INTO messages VALUES(?,?,?,?,?)", (uid(), run["conversation_id"], "user", body.answer, now()))
    await request.app.state.store.execute("UPDATE runs SET status='queued' WHERE id=?", (run_id,))
    request.app.state.runtime.launch(run_id, user["authorization"], body.answer)
    return await request.app.state.store.run(run_id)


@app.post("/agent/runs/{run_id}/retry")
async def retry(run_id: str, request: Request, user=Depends(identity)):
    run = await owned_run(request, run_id, user)
    if run["status"] not in ("failed", "interrupted", "cancelled"):
        raise HTTPException(409, "当前任务不需要重试")
    other = await request.app.state.store.one("SELECT id FROM runs WHERE conversation_id=? AND id<>? AND status IN ('queued','running','needs_input')", (run["conversation_id"], run_id))
    if other:
        raise HTTPException(409, "请先完成当前会话的其他任务")
    await request.app.state.store.execute("UPDATE runs SET status='queued',error=NULL WHERE id=?", (run_id,))
    request.app.state.runtime.launch(run_id, user["authorization"])
    return await request.app.state.store.run(run_id)


@app.get("/agent/preferences")
async def preferences(request: Request, user=Depends(identity)):
    return await request.app.state.store.one("SELECT content FROM preferences WHERE user_id=?", (user["userId"],)) or {"content": ""}


@app.put("/agent/preferences")
async def save_preferences(body: Preferences, request: Request, user=Depends(identity)):
    await request.app.state.store.execute("INSERT INTO preferences VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET content=excluded.content", (user["userId"], body.content))
    return body


@app.get("/agent/admin/products/search", dependencies=[Depends(administrator)])
async def search_products(request: Request, keyword: str = Query(min_length=2, max_length=120), part_type: str = Query(default="", pattern="^(|CPU|GPU|MOTHERBOARD|MEMORY|STORAGE|PSU|CASE|COOLER)$")):
    return await request.app.state.crawler.search(keyword, part_type)


@app.get("/agent/admin/settings", dependencies=[Depends(administrator)])
async def configuration(request: Request):
    cfg = {**settings.model_dump(mode="json"), **await request.app.state.store.configuration()}
    cfg["api_key_configured"] = bool(cfg.pop("api_key", ""))
    return cfg


@app.put("/agent/admin/settings", dependencies=[Depends(administrator)])
async def save_configuration(body: Configuration, request: Request):
    from urllib.parse import urlparse
    if body.rag_chunk_overlap >= body.rag_chunk_size:
        raise HTTPException(422, "分块重叠必须小于分块长度")
    parsed = urlparse(body.base_url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password:
        raise HTTPException(422, "模型地址无效")
    for key, value in body.model_dump(exclude={"clear_api_key"}).items():
        if key == "api_key" and not value and not body.clear_api_key:
            continue
        await request.app.state.store.execute("INSERT INTO settings VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (key, json.dumps(value)))
    return await configuration(request)


@app.get("/agent/admin/runs", dependencies=[Depends(administrator)])
async def admin_runs(request: Request):
    return await request.app.state.store.all("SELECT id,user_id,conversation_id,status,error,created_at,updated_at FROM runs ORDER BY created_at DESC LIMIT 200")


@app.get("/agent/admin/runs/{run_id}/events", dependencies=[Depends(administrator)])
async def admin_events(run_id: str, request: Request):
    rows = await request.app.state.store.all("SELECT id,type,data,created_at FROM events WHERE run_id=? ORDER BY id LIMIT 2000", (run_id,))
    return [{**row, "data": json.loads(row["data"])} for row in rows]


@app.get("/agent/admin/documents", dependencies=[Depends(administrator)])
async def documents(request: Request):
    return await request.app.state.store.all("SELECT d.id,d.title,d.source,d.created_at,COUNT(c.id) chunk_count FROM documents d LEFT JOIN chunks c ON c.document_id=d.id GROUP BY d.id ORDER BY d.created_at DESC")


@app.post("/agent/admin/documents", dependencies=[Depends(administrator)])
async def add_document(body: Document, request: Request):
    try:
        return await request.app.state.knowledge.ingest(body.title, body.content, body.source)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc


@app.post("/agent/admin/documents/url", dependencies=[Depends(administrator)])
async def add_url(body: UrlDocument, request: Request):
    try:
        title, content = await request.app.state.crawler.fetch_document(body.url)
        return await request.app.state.knowledge.ingest(title, content, body.url)
    except (ValueError, httpx.HTTPError) as exc:
        raise HTTPException(422, "网址导入失败，请检查网址、公开访问权限或改为上传文件") from exc


@app.post("/agent/admin/documents/upload", dependencies=[Depends(administrator)])
async def upload_document(request: Request, file: UploadFile = File(...)):
    raw = await file.read(5_000_001)
    if len(raw) > 5_000_000:
        raise HTTPException(413, "文档不能超过 5MB")
    name = file.filename or "文档"
    try:
        if name.lower().endswith(".pdf"):
            content = await asyncio.to_thread(lambda: "\n".join(p.extract_text() or "" for p in PdfReader(BytesIO(raw)).pages))
        elif name.lower().endswith((".md", ".txt")):
            content = raw.decode("utf-8-sig")
        else:
            raise ValueError("只支持 PDF、Markdown 和 UTF-8 文本")
        return await request.app.state.knowledge.ingest(name, content, name)
    except Exception as exc:
        raise HTTPException(422, "无法解析文档，请使用可提取文字的 PDF 或 UTF-8 文本") from exc


@app.delete("/agent/admin/documents/{document_id}", dependencies=[Depends(administrator)])
async def delete_document(document_id: str, request: Request):
    await request.app.state.store.execute("DELETE FROM documents WHERE id=?", (document_id,))
    return {"deleted": True}


@app.post("/agent/admin/documents/{document_id}/reindex", dependencies=[Depends(administrator)])
async def reindex(document_id: str, request: Request):
    doc = await request.app.state.store.one("SELECT * FROM documents WHERE id=?", (document_id,))
    if not doc:
        raise HTTPException(404, "文档不存在")
    return await request.app.state.knowledge.ingest(doc["title"], doc["content"], doc["source"], document_id)


@app.get("/agent/admin/search", dependencies=[Depends(administrator)])
async def preview_search(request: Request, q: str = ""):
    return await request.app.state.knowledge.search(q[:1000])
