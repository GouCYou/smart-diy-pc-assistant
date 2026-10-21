# Smart DIY 管理控制台

Vue 3 + TypeScript + Vite + Element Plus。用户、硬件、资讯、社区、反馈、通知、Agent 配置、知识库、运行记录分别拆页。默认字号 15–16px，表格、筛选、分页、确认框、编辑弹窗和错误状态统一。

```bash
npm ci
npm run dev
npm run build
```

默认端口 5174。同域 `/api`、`/media`、`/agent` 统一代理 Java，Agent 请求由 Java 内部转发到 Python；Agent 配置页使用 `/assistant` 路由，避免与 API 前缀冲突。开发代理与部署说明见根目录 README。模型配置需要 Python 服务运行；其他业务页面使用 Java 服务。
