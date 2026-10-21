"""Read-only MySQL -> Agent SQLite import. Run with the Agent stopped; reruns are idempotent.
Connection: LEGACY_MYSQL_HOST/PORT/USER/PASSWORD/DATABASE env vars. No source rows are modified.
"""
import asyncio
import json
import os
from datetime import datetime
from zoneinfo import ZoneInfo
import pymysql
from app.config import settings
from app.store import Store


def timestamp(value):
    if isinstance(value, datetime):
        return value.replace(tzinfo=ZoneInfo('Asia/Shanghai')).isoformat()
    return str(value)


async def main():
    connection=pymysql.connect(host=os.environ.get('LEGACY_MYSQL_HOST','127.0.0.1'),port=int(os.environ.get('LEGACY_MYSQL_PORT','3306')),user=os.environ['LEGACY_MYSQL_USER'],password=os.environ['LEGACY_MYSQL_PASSWORD'],database=os.environ.get('LEGACY_MYSQL_DATABASE','smart_diy_pc_assistant'),cursorclass=pymysql.cursors.DictCursor)
    settings.data_dir.mkdir(parents=True,exist_ok=True)
    store=Store(settings.data_dir/'agent.sqlite');await store.connect()
    with connection.cursor() as cursor:
        cursor.execute('SELECT * FROM ai_conversation')
        conversations=cursor.fetchall()
        for row in conversations:
            await store.execute('INSERT OR IGNORE INTO conversations VALUES(?,?,?,?)',(f"legacy-{row['id']}",row['user_id'],row['title'],timestamp(row['updated_at'])))
        cursor.execute('SELECT * FROM ai_chat_message ORDER BY created_at,id')
        messages=cursor.fetchall()
        for row in messages:
            content=row['content']
            if row.get('build_config_id'):
                content+=f"\n\n关联配置：{row.get('build_config_title','')}（ID {row['build_config_id']}）"
            if row.get('news_references_json'):
                content+='\n\n历史新闻引用：'+row['news_references_json']
            if row['role'] in ('user','assistant'):
                await store.execute('INSERT OR IGNORE INTO messages VALUES(?,?,?,?,?)',(f"legacy-{row['id']}",f"legacy-{row['conversation_id']}",row['role'],content,timestamp(row['created_at'])))
        cursor.execute('SHOW TABLES LIKE %s',('user_memory',))
        if cursor.fetchone():
            cursor.execute('SELECT * FROM user_memory')
            for row in cursor.fetchall():
                content='\n'.join(f'{key}: {value}' for key,value in row.items() if value and (key.endswith('_preference') or key in ('preferred_brands','rejected_brands','upgrade_plan')))
                await store.execute('INSERT OR IGNORE INTO preferences VALUES(?,?)',(row['user_id'],content[:2000]))
        cursor.execute('SHOW TABLES LIKE %s',('ai_runtime_setting',))
        if cursor.fetchone():
            cursor.execute('SELECT * FROM ai_runtime_setting LIMIT 1');row=cursor.fetchone()
            if row:
                for old,new in [('base_url','base_url'),('model_name','model'),('api_key','api_key'),('timeout_seconds','timeout_seconds'),('extra_system_prompt','extra_prompt')]:
                    if row.get(old):
                        await store.execute('INSERT OR IGNORE INTO settings VALUES(?,?)',(new,json.dumps(row[old])))
    await store.close();connection.close()
    print(f'Imported {len(conversations)} conversations and {len(messages)} historical messages. Source unchanged.')


if __name__=='__main__':
    asyncio.run(main())
