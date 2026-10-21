# Agent 应用重构与验收

## 已完成范围

- [x] `smart-diy-pc-assistant-agent/`：Python FastAPI、LangChain/LangGraph Agent、知识库、商品爬虫、会话、偏好、执行记录及 checkpoint。
- [x] Java 移除 AI、意图/记忆实现、AI 评审、爬虫、行情分析及相关依赖；保留账户、硬件、配置单、内容和通知等业务。
- [x] Element Plus 管理端：概览、用户、硬件、资讯、社区、反馈、系统通知、Agent 配置、知识库、运行记录和系统信息。
- [x] 纯 Vue 3 用户端：账户、聊天、配置、社区、资讯、空间、关注/粉丝、通知、反馈与偏好设置；删除 uni-app 工程与依赖。
- [x] 手机界面对照原 uni-app 截图恢复居中标题、提示卡、悬浮输入、五项导航及底部需求面板；电脑端独立工作区。
- [x] 原模型参数已迁入忽略提交的 Python `.env`；Java 私有配置中的 AI 段已移除，其他私有参数保留。
- [x] 构建、自动化测试、接口联调、逐页检查、迁移脚本与部署说明。

## 技术边界

Python 每次通过 Java `/api/auth/me` 验证身份和管理员角色，不信任请求体中的用户 ID。调用业务工具携带当前凭证，凭证不写入事件或 checkpoint。Java 访问权限以数据库当前角色/封禁状态为准。

Agent 可以规划、检索、调用工具、暂停追问、继续任务、取消和重试。工具参数采用结构化 schema，硬件类别为枚举。查询不存在的型号会返回真实候选，避免反复猜测。配置草稿中的报价只能引用该任务实际查到的商品；明确预算不能由模型静默提高。任务有步骤、调用、时间和输出额度。

业务配置仍由 Java 校验及保存。Agent 不自动写入配置单，恢复任务不会自动重复保存。用户点击保存后才写业务数据库；用户手动编辑报价会记录为配置快照。用户偏好与任务状态分别保存。

RAG 为中文分块、关键词检索、可选 Embedding 语义检索与 RRF 融合，向量及来源保存在 SQLite。更换模型后旧模型向量不参与检索，需重新索引。适用于单实例、小型知识库；当前不是分布式向量索引。

## 自动化与接口结果

| 项目 | 结果 |
|---|---|
| Python pytest | 15 项通过 |
| Java clean verify | 6 项通过；隔离 H2 数据库 |
| Java 打包 | 通过；JAR 无 AI/commerce 类、LangChain4j 或 Jsoup |
| 用户端 TypeScript + Vite 生产构建 | 通过 |
| 管理端 TypeScript + Vite 生产构建 | 通过 |
| 本地业务 API 联调 | 23 项通过：配置快照、收藏、修改、社区/评论、资讯互动、反馈处理、通知、空间等 |
| 原模型真实调用：RAG | 完成 search_knowledge，返回检索资料及引用 |
| 原模型真实调用：装机 | 完成计划、8 类硬件查询、propose_build，生成 8 件配置，无兼容性冲突；报价明确保持待询价 |
| 界面真实操作 | 配置草稿保存、查看、修改名称、兼容性检查、再次保存均成功；知识文档录入及检索成功 |
| JD 真实请求 | 未返回有效商品；按查询失败处理，不伪造报价 |

Python 测试覆盖真实 LangGraph 工具循环、追问恢复、权限隔离、配置失败重试、取消和删除、服务重启、知识重建/删除、语义融合、Embedding 模型切换、型号后缀/空价格解析，以及未知型号回退。模型服务的图测试使用可控测试模型，另有上述真实模型验证。

Java 测试覆盖业务上下文启动、完整目录 HTTP 接口、硬件尺寸保存、ATX/MATX 精确校验、配置读取保持快照，以及角色变更对旧 token 立即生效。

## 页面检查

已逐页查看管理端全部 11 个功能入口，以及用户端聊天、配置、社区、资讯、账户、通知、反馈、个人空间和内容详情。检查了登录、空状态、真实内容、表单、分页、错误提示和知识检索。

手机在 390px 宽度下检查了上述页面，文档宽度与视口均为 390px，没有横向溢出；另检查 320px 聊天布局。电脑端检查 1280px/1440px 布局。最终聊天页浏览器控制台未记录错误或警告。需求面板确认底部坐标与视口底部一致，内部内容可滚动，提交按钮固定可见。

修复了验收中发现的分类参数不一致、目录接口强制分类、/agent 页面与 API 代理路径冲突、按需样式覆盖底部面板定位、表格行类型检查，以及历史事件短暂覆盖已完成状态等问题。Element Plus 按需加载，路由拆包。

截图：

- [手机对话](qa/mobile-chat.png)、[手机需求面板](qa/mobile-requirements.png)、[320px 小屏](qa/mobile-320.png)
- [电脑工作区](qa/desktop-chat.png)、[管理端概览](qa/admin-dashboard.png)
- 其余页面截图保存在 `docs/qa/`。

## 未在真实外部环境验证的部分

- JD 当前返回无有效商品，无法据此承诺实时价格、库存或完整预算可购买性。
- SMTP 发送、七牛云上传、真实 Embedding 服务及 Docker 实际部署未作外部验收；相关配置入口、代码和说明已提供。
- 旧 AI 会话/记忆/动态设置提供显式只读 MySQL 导入脚本；本次没有连接/迁移现有业务数据库。历史表不自动 DROP，旧 Java 运行中任务和评审表保留归档。

本地预览使用独立 H2、Redis 6387 和 `/tmp/smartdiy-agent-qa`，与现有业务 MySQL 分离。用户端 5273、管理端 5274；常规启动端口为 5173、5174。

## 运维

Python 默认单 worker SQLite，正式扩容需共享 checkpoint、任务队列与事件存储。模型密钥在私有文件/数据目录中，不通过设置读取 API 返回。备份 Agent 数据时同时备份业务 SQLite 和 checkpoint。部署与导入步骤见根目录 README 及 Python 服务 README。

实现参考：[LangChain Agents](https://docs.langchain.com/oss/python/langchain/agents)、[LangGraph Persistence](https://docs.langchain.com/oss/python/langgraph/persistence)、[Element Plus 按需加载](https://element-plus.org/en-US/guide/quickstart.html)。


## 2026-10-21 生产部署

四个应用服务已在 192.168.1.6 Docker Compose 运行，业务 MySQL、Redis 和七牛继续沿用服务器原配置。前端 `/api`、`/agent`、`/media` 统一走 Java，Python 仅内部开放；Java 使用 HTTP/1.1 逐块刷新 SSE，并原样转发 multipart 上传。迁移了 78 个旧会话、250 条消息，源表保留。生产操作、备份与回滚详见根目录 AGENTS.md。

新增 RAG 分块长度、重叠、返回数量和向量相似度阈值，保存后参与实际切分和检索。更改分块或 Embedding 模型需重新索引；生产未配置 Embedding 模型，当前使用关键词检索。
