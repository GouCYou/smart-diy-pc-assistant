# Smart DIY 装机助手

🇨🇳 简体中文|
<a href="README.zh-TW.md">🇹🇼 繁體中文</a>|
<a href="README.en-US.md">🇺🇸 English</a>

面向 DIY PC 装机场景的智能化助手，通过录入标准硬件型号库，自动获取京东商品作为实时价格、图片和购买链接来源。
项目包含用户端、管理端和 Spring Boot 后端，覆盖 AI 装机对话、配置单生成、京东商品搜索、硬件兼容性检查、社区动态、热点新闻、个人空间、用户管理和账号安全等功能。

## 目录

- [演示截图](#演示截图)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [开发检查](#开发检查)
- [安全说明](#安全说明)
- [贡献](#贡献)
- [许可证](#许可证)

## 演示截图

<img width="369" height="803" alt="image" src="https://github.com/user-attachments/assets/27c2a9a8-d787-4a1c-a494-59a4f8042727" />
<img width="366" height="802" alt="image" src="https://github.com/user-attachments/assets/ed12b66d-abfd-4bed-9052-a9a944842eae" />
![配置详情](docs/screenshots/build-detail.png)
![AI 对话](docs/screenshots/ai-chat.png)
![管理面板](docs/screenshots/admin-dashboard.png)

## 核心功能

- AI 装机对话：根据预算、用途、外观偏好和性能需求生成配置单。
- 标准硬件型号库：数据库只存 CPU、主板、显卡、内存、硬盘、电源、机箱、散热器等标准型号。
- 京东商品搜索：后端使用 Spring Boot + Jsoup 抓取京东搜索结果，返回标题、价格、图片、链接和 SKU。
- 配置单系统：支持手动创建、AI 生成、收藏、预览、详情查看和京东商品跳转。
- 兼容性检查：检查 CPU 插槽、主板内存类型、电源功率、机箱版型和散热等级等基础规则。
- 社区动态：支持发布动态、上传图片、关联配置单、点赞、收藏和评论。
- 个人空间：展示用户资料、背景图、动态、收藏内容、关注和粉丝数据。
- 热点新闻：支持新闻列表、排序、详情、点赞、收藏和评论。
- 账号安全：支持邮箱验证码、图形验证码、密码强度校验、JWT 鉴权、密码哈希存储和账号封禁。
- 管理后台：管理用户、标准硬件型号、新闻热点、社区内容、AI 设置和系统配置。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA, MySQL, Redis |
| AI | LangChain4j OpenAI adapter |
| 商品抓取 | Jsoup |
| 用户端 | uni-app, Vue |
| 管理端 | Vue 3, TypeScript, Vite |
| 对象存储 | 七牛云对象存储 |
| 邮件服务 | SMTP |
| 鉴权 | JWT |

## 项目结构

```text
smart-diy-pc-assistant/
├── smart-diy-pc-assistant-backend/   # Spring Boot 后端
├── smart-diy-pc-assistant-admin/     # Vue 管理端
├── smart-diy-pc-assistant-uniapp/    # uni-app 用户端
├── LICENSE
└── README.md
```

## 环境要求

- JDK 17+
- Maven 3.9+，或使用后端模块内置 Maven Wrapper
- MySQL 8+
- Redis 6+
- Node.js 20+，用于管理端
- HBuilderX，用于运行 uni-app 用户端
- 可选：七牛云对象存储、SMTP 邮箱、OpenAI 兼容模型服务

## 快速开始

### 1. 创建数据库

```sql
CREATE DATABASE smart_diy_pc_assistant
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 2. 配置后端私有参数

在 `smart-diy-pc-assistant-backend/src/main/resources/application-local.yaml` 中放置本地私有配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/smart_diy_pc_assistant?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: your_mysql_user
    password: your_mysql_password
  data:
    redis:
      host: localhost
      port: 6379
  mail:
    username: your_mail_account
    password: your_mail_authorization_code

app:
  security:
    jwt-secret: replace-with-a-long-random-secret
  qiniu:
    access-key: your_qiniu_access_key
    secret-key: your_qiniu_secret_key
    bucket: your_bucket
    domain: https://your-cdn-domain
  ai:
    base-url: https://your-openai-compatible-endpoint
    api-key: your_api_key
    model-name: your_model
```

### 3. 启动后端

```bash
cd smart-diy-pc-assistant-backend
./mvnw spring-boot:run
```

Windows：

```powershell
cd smart-diy-pc-assistant-backend
.\mvnw.cmd spring-boot:run
```

默认后端地址：`http://localhost:8080`

### 4. 启动管理端

```bash
cd smart-diy-pc-assistant-admin
npm install
npm run dev
```

默认 Vite 地址通常为 `http://localhost:5173` 或 `http://localhost:5174`。

### 5. 启动用户端

1. 使用 HBuilderX 打开 `smart-diy-pc-assistant-uniapp`。
2. 确认 `smart-diy-pc-assistant-uniapp/common/api.ts` 中的 `API_BASE_URL` 指向后端地址。
3. 使用 HBuilderX 运行到 Web 浏览器、App 或小程序目标平台。

## 配置说明

- `application.yaml` 只保留默认开发配置。
- `application-local.yaml` 用于本地私有配置，不应提交到仓库。
- 生产环境建议使用环境变量或独立的服务器配置文件管理密钥。
- 京东商品搜索仅作为价格和购买链接参考，请遵守目标站点规则并避免高频请求。

## 开发检查

后端编译：

```bash
cd smart-diy-pc-assistant-backend
./mvnw -DskipTests compile
```

管理端构建：

```bash
cd smart-diy-pc-assistant-admin
npm run build
```

## 安全说明

- 不要提交邮箱授权码、JWT 密钥、对象存储密钥、AI API Key 或小程序 AppID。
- 如果发现密钥泄露，应立即撤销并重新生成。
- 上传文件建议使用对象存储和 CDN 域名，不建议生产环境长期依赖本地文件服务。

## 贡献

欢迎提交 Issue 和 Pull Request。建议在提交前说明：

- 问题背景或功能目标
- 影响范围
- 主要实现思路
- 已完成的测试或验证步骤

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
