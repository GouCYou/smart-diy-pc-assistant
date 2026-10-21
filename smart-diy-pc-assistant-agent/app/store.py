import json
import asyncio
from datetime import datetime, timezone
from uuid import uuid4
import aiosqlite


def now():
    return datetime.now(timezone.utc).isoformat()


def uid():
    return uuid4().hex


class Store:
    def __init__(self, path):
        self.path = path
        self.write_lock = asyncio.Lock()

    async def connect(self):
        self.db = await aiosqlite.connect(self.path)
        self.db.row_factory = aiosqlite.Row
        await self.db.executescript("""
        PRAGMA journal_mode=WAL;
        PRAGMA foreign_keys=ON;
        CREATE TABLE IF NOT EXISTS conversations(id TEXT PRIMARY KEY,user_id INTEGER NOT NULL,title TEXT NOT NULL,updated_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS conversation_settings(conversation_id TEXT PRIMARY KEY REFERENCES conversations(id) ON DELETE CASCADE,pinned INTEGER NOT NULL DEFAULT 0,archived INTEGER NOT NULL DEFAULT 0,title_manual INTEGER NOT NULL DEFAULT 0,title_generated INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE IF NOT EXISTS conversation_shares(token TEXT PRIMARY KEY,conversation_id TEXT UNIQUE NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,created_at TEXT NOT NULL,snapshot TEXT);
        CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY,conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,role TEXT NOT NULL,content TEXT NOT NULL,created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS runs(id TEXT PRIMARY KEY,user_id INTEGER NOT NULL,conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,status TEXT NOT NULL,payload TEXT NOT NULL,answer TEXT NOT NULL DEFAULT '',draft TEXT,references_json TEXT NOT NULL DEFAULT '[]',error TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT,run_id TEXT NOT NULL REFERENCES runs(id) ON DELETE CASCADE,type TEXT NOT NULL,data TEXT NOT NULL,created_at TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS events_run ON events(run_id,id);
        CREATE INDEX IF NOT EXISTS runs_user ON runs(user_id,created_at);
        CREATE UNIQUE INDEX IF NOT EXISTS one_active_run_per_conversation ON runs(conversation_id) WHERE status IN ('queued','running','needs_input');
        CREATE TABLE IF NOT EXISTS preferences(user_id INTEGER PRIMARY KEY,content TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS documents(id TEXT PRIMARY KEY,title TEXT NOT NULL,source TEXT NOT NULL,content TEXT NOT NULL,created_at TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS chunks(id TEXT PRIMARY KEY,document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,text TEXT NOT NULL,embedding TEXT);
        """)
        columns={row[1] for row in await (await self.db.execute("PRAGMA table_info(conversation_settings)")).fetchall()}
        if 'title_generated' not in columns:
            await self.db.execute("ALTER TABLE conversation_settings ADD COLUMN title_generated INTEGER NOT NULL DEFAULT 0")
        share_columns={row[1] for row in await (await self.db.execute("PRAGMA table_info(conversation_shares)")).fetchall()}
        if 'snapshot' not in share_columns:
            await self.db.execute("ALTER TABLE conversation_shares ADD COLUMN snapshot TEXT")
        run_columns={row[1] for row in await (await self.db.execute("PRAGMA table_info(runs)")).fetchall()}
        if 'partial_text' not in run_columns: await self.db.execute("ALTER TABLE runs ADD COLUMN partial_text TEXT NOT NULL DEFAULT ''")
        # Completed model output is durable even if a later title/checkpoint step failed.
        await self.db.execute("""UPDATE runs SET answer=COALESCE((SELECT json_extract(data,'$.answer') FROM events WHERE run_id=runs.id AND type='answer_ready' ORDER BY id DESC LIMIT 1),'') WHERE answer=''""")
        await self.db.execute("""INSERT OR IGNORE INTO messages(id,conversation_id,role,content,created_at) SELECT 'run-'||r.id,r.conversation_id,'assistant',r.answer,r.updated_at FROM runs r WHERE r.answer<>'' AND NOT EXISTS(SELECT 1 FROM messages m WHERE m.conversation_id=r.conversation_id AND m.role='assistant' AND m.content=r.answer)""")
        # Recover previously streamed text from the latest model turn without inventing a final answer.
        await self.db.execute("""UPDATE runs SET partial_text=COALESCE((SELECT group_concat(json_extract(data,'$.text'),'') FROM (SELECT data FROM events WHERE run_id=runs.id AND type='text_delta' AND id>COALESCE((SELECT max(id) FROM events WHERE run_id=runs.id AND type IN ('model_turn_started','work_summary','answer_ready')),0) ORDER BY id)),'') WHERE answer='' AND partial_text='' AND status IN ('failed','interrupted','cancelled')""")
        # Upgrade legacy five-minute limits; never lower an administrator's longer limits.
        for key,default in (("timeout_seconds",1800),("max_run_seconds",3600)):
            await self.db.execute("UPDATE settings SET value=? WHERE key=? AND CAST(value AS REAL)<1800",(json.dumps(default),key))
        await self.db.execute("UPDATE runs SET status='interrupted',error='服务重启，点击重试继续任务' WHERE status IN ('queued','running')")
        await self.db.commit()

    async def close(self):
        await self.db.close()

    async def execute(self, sql, params=()):
        async with self.write_lock:
            try:
                cursor = await self.db.execute(sql, params)
                await self.db.commit()
                return cursor
            except Exception:
                await self.db.rollback()
                raise

    async def all(self, sql, params=()):
        async with self.db.execute(sql, params) as cursor:
            return [dict(row) for row in await cursor.fetchall()]

    async def one(self, sql, params=()):
        rows = await self.all(sql, params)
        return rows[0] if rows else None

    async def event(self, run_id, kind, data):
        cursor = await self.execute("INSERT INTO events(run_id,type,data,created_at) VALUES(?,?,?,?)", (run_id, kind, json.dumps(data, ensure_ascii=False, default=str), now()))
        return cursor.lastrowid

    async def model_event(self, run_id, kind, data, partial_text="", answer=None):
        # Publish the snapshot and its event cursor together, so reconnect cannot
        # either duplicate a delta or skip text generated during the snapshot.
        async with self.write_lock:
            try:
                if kind=="model_turn_started":
                    previous=await (await self.db.execute("SELECT partial_text FROM runs WHERE id=?",(run_id,))).fetchone()
                    if previous and previous[0].strip():
                        await self.db.execute("INSERT INTO events(run_id,type,data,created_at) VALUES(?,?,?,?)",(run_id,"partial_saved",json.dumps({"text":previous[0]},ensure_ascii=False),now()))
                await self.db.execute("UPDATE runs SET partial_text=? WHERE id=?",(partial_text,run_id))
                if answer is not None:
                    await self.db.execute("UPDATE runs SET answer=? WHERE id=?",(answer,run_id))
                    await self.db.execute("INSERT INTO messages(id,conversation_id,role,content,created_at) SELECT ?,conversation_id,'assistant',?,? FROM runs WHERE id=? ON CONFLICT(id) DO UPDATE SET content=excluded.content",("run-"+run_id,answer,now(),run_id))
                await self.db.execute("INSERT INTO events(run_id,type,data,created_at) VALUES(?,?,?,?)",(run_id,kind,json.dumps(data,ensure_ascii=False),now()))
                await self.db.commit()
            except Exception:
                await self.db.rollback()
                raise

    async def configuration(self):
        return {row["key"]: json.loads(row["value"]) for row in await self.all("SELECT * FROM settings")}

    async def run(self, run_id):
        async with self.write_lock:
            row = await self.one("SELECT runs.*,(SELECT COALESCE(max(id),0) FROM events WHERE run_id=runs.id) AS event_cursor FROM runs WHERE id=?", (run_id,))
        if row:
            for field in ("payload", "draft", "references_json"):
                row[field] = json.loads(row[field]) if row[field] else None
            row["references"] = row.pop("references_json")
            if row['status']=='needs_input':
                question=await self.one("SELECT data FROM events WHERE run_id=? AND type='needs_input' ORDER BY id DESC LIMIT 1",(run_id,))
                row['question']=json.loads(question['data']) if question else None
        return row
