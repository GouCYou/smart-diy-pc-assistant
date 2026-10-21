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

    async def configuration(self):
        return {row["key"]: json.loads(row["value"]) for row in await self.all("SELECT * FROM settings")}

    async def run(self, run_id):
        row = await self.one("SELECT * FROM runs WHERE id=?", (run_id,))
        if row:
            for field in ("payload", "draft", "references_json"):
                row[field] = json.loads(row[field]) if row[field] else None
            row["references"] = row.pop("references_json")
        return row
