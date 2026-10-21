import asyncio
import json
import math
import re
import aiosqlite
from collections import Counter
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_openai import OpenAIEmbeddings
from .store import now, uid


def tokens(text):
    lower = text.lower()
    words = re.findall(r"[a-z0-9]+(?:[-.][a-z0-9]+)*", lower)
    chinese = "".join(re.findall(r"[\u4e00-\u9fff]", lower))
    return words + [chinese[i:i + 2] for i in range(len(chinese) - 1)]


class Knowledge:
    def __init__(self, store, config):
        self.store, self.config = store, config
        self.lock = asyncio.Lock()

    async def embeddings(self):
        cfg = {**self.config.model_dump(), **await self.store.configuration()}
        if not cfg.get("embedding_model") or not cfg.get("api_key"):
            return None
        return OpenAIEmbeddings(model=cfg["embedding_model"], api_key=cfg["api_key"], base_url=cfg["base_url"], request_timeout=cfg["timeout_seconds"], max_retries=1, check_embedding_ctx_length=False)

    async def ingest(self, title, content, source="", document_id=None):
        cfg = {**self.config.model_dump(), **await self.store.configuration()}
        splitter = RecursiveCharacterTextSplitter(chunk_size=cfg["rag_chunk_size"], chunk_overlap=cfg["rag_chunk_overlap"], separators=["\n\n", "\n", "。", "！", "？", " ", ""])
        chunks = splitter.split_text(content)
        if not chunks:
            raise ValueError("文档没有可检索内容")
        if len(chunks) > 500:
            raise ValueError("文档过大，请按章节拆分后导入")
        embedder = await self.embeddings()
        vectors = await embedder.aembed_documents(chunks) if embedder else [None] * len(chunks)
        doc_id = document_id or uid()
        # Replace atomically: a failed embedding request must not erase the old index.
        async with self.lock, aiosqlite.connect(self.store.path) as db:
            await db.execute("INSERT INTO documents VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET title=excluded.title,source=excluded.source,content=excluded.content,created_at=excluded.created_at", (doc_id, title, source, content, now()))
            await db.execute("DELETE FROM chunks WHERE document_id=?", (doc_id,))
            await db.executemany("INSERT INTO chunks VALUES(?,?,?,?)", [(uid(), doc_id, text, json.dumps({"model": embedder.model, "vector": vector}) if vector else None) for text, vector in zip(chunks, vectors)])
            await db.commit()
        return {"id": doc_id, "chunks": len(chunks), "mode": "hybrid" if embedder else "keyword"}

    async def search(self, query, limit=None):
        cfg = {**self.config.model_dump(), **await self.store.configuration()}
        limit = limit or cfg["rag_top_k"]
        rows = await self.store.all("SELECT c.*,d.title,d.source,d.created_at FROM chunks c JOIN documents d ON d.id=c.document_id")
        if not rows:
            return []
        q = Counter(tokens(query))
        query_vector = None
        embedder = await self.embeddings()
        if embedder:
            query_vector = await embedder.aembed_query(query)
        lexical, semantic = [], []
        for row in rows:
            words = Counter(tokens(row["title"] + " " + row["text"]))
            score = sum(min(count, words[word]) for word, count in q.items()) / (1 + math.log(1 + sum(words.values())))
            if score > 0:
                lexical.append((score, row))
            if query_vector and row["embedding"]:
                stored = json.loads(row["embedding"])
                if not isinstance(stored, dict) or stored.get("model") != embedder.model:
                    continue
                vector = stored["vector"]
                if len(vector) == len(query_vector):
                    norm = math.sqrt(sum(v * v for v in vector)) * math.sqrt(sum(v * v for v in query_vector))
                    similarity = sum(a * b for a, b in zip(vector, query_vector)) / norm if norm else 0
                    if similarity > cfg["rag_similarity_threshold"]:
                        semantic.append((similarity, row))
        ranks, records = {}, {}
        for ranking in (lexical, semantic):
            for rank, (_, row) in enumerate(sorted(ranking, key=lambda x: x[0], reverse=True)):
                ranks[row["id"]] = ranks.get(row["id"], 0) + 1 / (60 + rank + 1)
                records[row["id"]] = row
        return [{"id": key, "documentId": records[key]["document_id"], "title": records[key]["title"], "source": records[key]["source"], "excerpt": records[key]["text"], "updatedAt": records[key]["created_at"], "score": ranks[key]} for key in sorted(ranks, key=ranks.get, reverse=True)[:limit]]
