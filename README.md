# Smart DIY 装机助手

面向装机、升级和故障排查的 Agent 应用。用户端为纯 Vue 3 Web，按视口自动切换电脑工作区和手机竖屏布局；管理端使用 Element Plus。Python 独立负责全部 AI 与爬虫，Java 保留业务系统。

| 目录 | 职责 |
|---|---|
| `smart-diy-pc-assistant-agent/` | Python、FastAPI、LangChain/LangGraph、RAG、JD 爬虫、任务/会话/偏好 |
| `smart-diy-pc-assistant-backend/` | Java 17、Spring Boot、MySQL、Redis；账户、硬件、配置单、社区、资讯、通知、反馈、上传 |
| `smart-diy-pc-assistant-web/` | Vue 3、TypeScript、Vite；响应式用户端 |
| `smart-diy-pc-assistant-admin/` | Vue 3、Element Plus；完整管理控制台 |
| `shared/` | 两个 Web 应用共享的 API 客户端和类型 |
| `deployment/` | 容器构建与 Nginx / SSE 代理 |

## Agent 工作流

用户描述目标 → 整理计划 → 必要时追问并暂停 → 查询知识/硬件/商品 → 校验与调整 → 生成配置草稿 → 用户保存。

具备工具调用、LangGraph checkpoint、跨会话偏好、流式任务事件、取消/重试、来源引用、模型用量记录和调用额度。价格必须来自实际抓取结果；失败时显示待询价。兼容性由确定性规则验证，资料缺失项明确标注。Java 不再承担 AI、爬虫、行情分析或自动行情调整。

## 本地运行

环境：JDK 17+、Node.js 22.12+（推荐 24）、Python 3.11+、MySQL 8+、Redis 6+。

1. 创建 `smart_diy_pc_assistant` MySQL 数据库。在 Java 的 `src/main/resources/application-local.yaml` 或环境变量中设置数据库、Redis、JWT、SMTP、七牛云参数。原业务数据库可以继续使用；不自动删除旧 AI/行情历史表。
2. 运行 Java：

```bash
cd smart-diy-pc-assistant-backend
SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./src/main/resources/application-local.yaml bash mvnw spring-boot:run
```

3. 运行 Python：

```bash
cd smart-diy-pc-assistant-agent
python3 -m venv .venv
source .venv/bin/activate
pip install -e '.[test,migration]'
cp -n .env.example .env
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

4. 分别在用户端和管理端目录运行：

```bash
npm ci
npm run dev
```

默认用户端 `http://localhost:5173`，管理端 `http://localhost:5174`。Vite 将 `/api`、`/media`、`/agent` 统一代理到 Java 8080，再由 Java 转发 Agent 请求到 Python 8000。使用其他开发端口时需要更新 Java CORS 配置。可以使用 `VITE_BACKEND_ORIGIN` 修改统一开发代理目标，使用 `VITE_API_BASE_URL` 设置统一 Java API 地址。

管理端的 Agent 配置页面填写支持工具调用的模型名称、OpenAI 兼容接口及 Key；知识库页面支持文档、PDF、网址导入和检索预览。可选配置 Embedding 模型启用语义+关键词融合检索，切换后重新索引。

## 容器部署

**现有服务器请使用 [生产操作说明](AGENTS.md) 和 `deployment/compose.production.yaml`，复用原 MySQL、Redis、七牛配置。** `deployment/package-release.sh` 打包已构建产物；Java/Python 仅监听服务器回环地址，Tunnel 对外提供前端同源接口。当前正式入口：[用户端](https://smartdiy.cctstudio.cn)、[管理端](https://smartdiy.cctstudio.cn/admin/)。

下面的根目录 Compose 仅用于新建独立环境：

```bash
cp -n .env.example .env
# 填写数据库密码、JWT 密钥、模型及必要的邮件/存储配置
docker compose up --build -d
```

用户端端口 8088，管理端端口 8089。Nginx 已配置 SPA 回退和 SSE 禁用缓冲。首次使用空数据库时，现有 Java 初始化逻辑仍会提供 `admin / 123456` 管理员；请在接入公网前通过账号密码重置流程修改它。邮件注册、密码找回、图片上传需要真实的 SMTP/存储配置。

Python 当前采用单 worker SQLite 持久化，适用于单实例部署。横向扩容需共享 checkpoint、任务队列与事件存储；大规模 RAG 需替换本地向量扫描。详细说明见 [Python 服务文档](smart-diy-pc-assistant-agent/README.md)。

## 迁移与验证

旧 uni-app 代码已由纯 Web 用户端替代，保留原界面截图作为视觉参考。手机端恢复原有对话、悬浮输入与五项底部导航；电脑端独立布局。旧 AI 对话、记忆及设置通过 Python 的 `scripts/import_legacy.py` 显式导入，源 MySQL 只读。未完成的旧 Java 装机任务不迁移为新图任务，旧评审记录保留在数据库归档。

```bash
# 根目录
bash smart-diy-pc-assistant-backend/mvnw -f smart-diy-pc-assistant-backend/pom.xml test
smart-diy-pc-assistant-agent/.venv/bin/python -m pytest smart-diy-pc-assistant-agent/tests -q
npm run build --prefix smart-diy-pc-assistant-web
npm run build --prefix smart-diy-pc-assistant-admin
```

Java 测试使用隔离的 H2 数据库，不要求连接业务 MySQL。模型调用、邮件发送、对象存储和 JD 实际可用性需在部署环境中用真实配置验证。界面与功能验收记录见 [迁移记录](docs/agent-migration.md)。

MIT License。
