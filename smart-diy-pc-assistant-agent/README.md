# Smart DIY Python Agent

Python 3.11+，FastAPI + LangChain `create_agent` / LangGraph。AI、知识库、会话、长期偏好、配置评审和 JD 爬虫由本服务独立负责。Java 中不再包含模型调用或爬虫。

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -e '.[test,migration]'
cp -n .env.example .env
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Java 需先运行。登录继续使用 Java 账号；Python 每次通过 `/api/auth/me` 验证真实身份，管理员配置通过 `/agent/admin/*` 管理。API 文档为 `/docs`。不要使用多个 Uvicorn worker 启动这个 SQLite 版本。

## 能力与执行语义

- 模型自主调用工具：计划、追问、型号查询、知识检索、商品查询、读取配置、生成并校验结构化配置草稿。
- LangGraph SQLite checkpoint：追问后继续执行，错误后重试，服务重启后将运行中任务标为 interrupted，由用户携带新登录凭证恢复。
- 独立任务和事件表：SSE 支持 after 游标续接；前端刷新后可查看既有任务；取消不会自动撤销已生成草稿。
- 配置草稿无业务写入：用户点击保存后由 Java 再校验并持久化，避免恢复执行重复保存。
- 调用次数、步骤、输出 token、并发及时间上限；事件包含工具步骤、知识来源、草稿和模型 token 用量。
- 用户主动编辑跨会话偏好；历史偏好不会覆盖当前明确需求。前一份草稿可作为后续修改上下文。
- JD 热搜配置和桌面页面两种解析，准确区分型号后缀，排除定金、租赁、套装、整机和附件。结果有获取时间和短缓存。遇到登录/反爬明确返回查询失败，不伪造报价，也不绕过验证码。

## RAG

支持 PDF、UTF-8 TXT、Markdown 和公开 HTTPS HTML。按中文段落分块，保留来源，支持删除与原子重建索引。默认中文二元词/型号关键词检索；配置兼容接口的 Embedding 模型后，将向量持久化在 SQLite，使用余弦相似度和 RRF 融合关键词排名。模型签名不一致的向量不参与检索，切换模型后需重新索引。当前实现适合个人/小型知识库，语义检索会扫描分块；大规模部署应接入 Qdrant 等索引，不应将此实现描述为已有分布式向量库。

## 运维边界

- 同时备份 `data/agent.sqlite` 和 `data/checkpoints.sqlite`，停止服务后备份/恢复。模型密钥存储在私有数据目录，不通过设置读取 API 返回；限制该目录权限。
- 生产代理关闭 SSE buffering，延长 read timeout；不要把 Bearer token 放在 URL 中。
- 横向扩展需替换本地 checkpoint、任务调度和事件存储，不能只增加 worker。
- 检查 BIOS、QVL、接头或资料缺失的规格时返回待核实，不宣称完整认证兼容。报价是采集快照，不保证库存或结算价。
- 爬取网址只允许公开 HTTPS，检查解析地址与每次重定向；生产环境另设出站网络限制。

## 导入旧 Java AI 数据

先备份数据库并停止新 Agent。安装 migration extra，通过环境变量设置 `LEGACY_MYSQL_HOST/PORT/USER/PASSWORD/DATABASE`，然后执行：

```bash
python scripts/import_legacy.py
```

只读取旧 MySQL 表，按 `legacy-*` ID 幂等导入会话、消息、记忆和模型设置。保留历史配置单引用文字；旧运行中的任务不恢复为新图任务，旧评审表留作归档。原数据库不删除任何表。可在导入后通过聊天请新 Agent 重新评审配置。

```bash
pytest -q
```
