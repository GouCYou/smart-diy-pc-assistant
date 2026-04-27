# Smart DIY PC Assistant

Smart DIY PC Assistant is a full-stack PC building assistant. It combines an AI chat workflow, a standard hardware model library, JD product search, build compatibility checks, community posts, tech news, and an admin panel.

<p align="center">
  <a href="#zh-cn">🇨🇳 简体中文</a> ·
  <a href="#zh-tw">🇹🇼 繁體中文</a> ·
  <a href="#en-us">🇺🇸 English</a>
</p>

---

<a id="zh-cn"></a>

## 🇨🇳 简体中文

### 项目简介

Smart DIY 装机助手是一个面向 DIY PC 装机场景的全栈项目。项目不把电商商品当作硬件数据库，而是维护 CPU、主板、显卡、内存、硬盘、电源、机箱、散热器等标准硬件型号，再通过京东搜索获取可购买商品、价格、图片和链接。

系统提供用户端、管理端和后端服务，支持 AI 装机对话、配置单生成、京东商品选择、硬件兼容性检查、社区动态、热点新闻、个人空间、用户管理和封禁控制。

### 演示截图

> 截图占位：请在这里添加用户端、配置单、AI 对话、个人空间、管理端等截图。

<!--
示例：
![用户端首页](docs/screenshots/user-home.png)
![配置详情](docs/screenshots/build-detail.png)
![管理面板](docs/screenshots/admin-dashboard.png)
-->

### 核心功能

- AI 装机对话：根据预算、用途、外观偏好和性能需求生成配置单。
- 标准硬件型号库：数据库只维护标准型号，不直接存储带品牌的电商商品。
- 京东商品搜索：后端使用 Spring Boot + Jsoup 抓取京东搜索结果，返回标题、价格、图片、链接和 SKU。
- 配置单系统：支持手动创建、AI 生成、收藏、预览、详情查看和京东商品跳转。
- 兼容性检查：检查 CPU 插槽、主板内存类型、电源功率、机箱版型、散热等级等基础规则。
- 社区动态：发布动态、上传图片、关联配置单、点赞、收藏和评论。
- 个人空间：展示用户资料、背景图、动态、收藏内容、关注和粉丝数据。
- 热点新闻：支持新闻列表、排序、详情、点赞、收藏和评论。
- 邮箱验证：注册和忘记密码流程支持邮箱验证码和人机验证码。
- 管理后台：管理用户、硬件标准型号、新闻热点、社区内容、AI 设置和系统设置。
- 账户安全：JWT 鉴权、密码哈希存储、密码强度校验、账号封禁和管理员权限控制。

### 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA, Redis, MySQL |
| AI | LangChain4j OpenAI adapter |
| 商品抓取 | Jsoup |
| 用户端 | uni-app / Vue |
| 管理端 | Vue 3, TypeScript, Vite |
| 对象存储 | 七牛云对象存储 |
| 邮件 | SMTP 邮件服务 |
| 鉴权 | JWT |

### 项目结构

```text
smart-diy-pc-assistant/
├── smart-diy-pc-assistant-backend/   # Spring Boot 后端
├── smart-diy-pc-assistant-admin/     # Vue 管理端
├── smart-diy-pc-assistant-uniapp/    # uni-app 用户端
├── LICENSE
└── README.md
```

### 环境要求

- JDK 17+
- Maven 3.9+，或使用项目内 Maven Wrapper
- MySQL 8+
- Redis 6+
- Node.js 20+，用于管理端
- HBuilderX，用于 uni-app 用户端
- 可选：七牛云对象存储、SMTP 邮箱、OpenAI 兼容模型服务

### 后端启动

1. 创建数据库：

```sql
CREATE DATABASE smart_diy_pc_assistant
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

2. 在 `smart-diy-pc-assistant-backend/src/main/resources/application-local.yaml` 中放置本地私密配置：

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

3. 启动后端：

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

### 管理端启动

```bash
cd smart-diy-pc-assistant-admin
npm install
npm run dev
```

默认 Vite 地址通常为 `http://localhost:5173` 或 `http://localhost:5174`。

### 用户端启动

1. 使用 HBuilderX 打开 `smart-diy-pc-assistant-uniapp`。
2. 确认 `smart-diy-pc-assistant-uniapp/common/api.ts` 中的 `API_BASE_URL` 指向后端地址。
3. 使用 HBuilderX 运行到 Web 浏览器、App 或小程序目标平台。

### 配置说明

- `application.yaml` 只放默认开发配置。
- `application-local.yaml` 用于本地私密配置，不应提交到仓库。
- 生产环境建议使用环境变量或独立的服务器配置文件管理密钥。
- 京东商品搜索用于价格和商品链接参考，请遵守目标站点规则，避免高频请求。

### 安全说明

- 不要提交邮箱授权码、JWT 密钥、对象存储密钥、AI API Key 或小程序 AppID。
- 发现密钥泄露后应立即撤销并重新生成。
- 上传文件建议使用对象存储和 CDN 域名，不建议长期依赖本地文件服务。

### 开发检查

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

### 贡献

欢迎提交 Issue 和 Pull Request。建议在提交前说明：

- 问题背景或功能目标
- 影响范围
- 主要实现思路
- 已完成的测试或验证步骤

### 许可证

本项目基于 [MIT License](LICENSE) 开源。

[回到顶部](#smart-diy-pc-assistant)

---

<a id="zh-tw"></a>

## 🇹🇼 繁體中文

### 專案簡介

Smart DIY 裝機助手是一個面向 DIY PC 裝機場景的全端專案。專案不把電商商品當作硬體資料庫，而是維護 CPU、主機板、顯示卡、記憶體、硬碟、電源、機殼、散熱器等標準硬體型號，再透過京東搜尋取得可購買商品、價格、圖片與連結。

系統提供使用者端、管理端與後端服務，支援 AI 裝機對話、配置單生成、京東商品選擇、硬體相容性檢查、社群動態、熱點新聞、個人空間、使用者管理與封禁控制。

### 展示截圖

> 截圖佔位：請在這裡加入使用者端、配置單、AI 對話、個人空間、管理端等截圖。

<!--
範例：
![使用者端首頁](docs/screenshots/user-home.png)
![配置詳情](docs/screenshots/build-detail.png)
![管理面板](docs/screenshots/admin-dashboard.png)
-->

### 核心功能

- AI 裝機對話：根據預算、用途、外觀偏好與效能需求生成配置單。
- 標準硬體型號庫：資料庫只維護標準型號，不直接儲存帶品牌的電商商品。
- 京東商品搜尋：後端使用 Spring Boot + Jsoup 抓取京東搜尋結果，回傳標題、價格、圖片、連結與 SKU。
- 配置單系統：支援手動建立、AI 生成、收藏、預覽、詳情查看與京東商品跳轉。
- 相容性檢查：檢查 CPU 插槽、主機板記憶體類型、電源功率、機殼版型、散熱等級等基礎規則。
- 社群動態：發布動態、上傳圖片、關聯配置單、按讚、收藏與評論。
- 個人空間：展示使用者資料、背景圖、動態、收藏內容、關注與粉絲資料。
- 熱點新聞：支援新聞列表、排序、詳情、按讚、收藏與評論。
- 郵箱驗證：註冊與忘記密碼流程支援郵箱驗證碼與人機驗證碼。
- 管理後台：管理使用者、硬體標準型號、新聞熱點、社群內容、AI 設定與系統設定。
- 帳戶安全：JWT 鑑權、密碼雜湊儲存、密碼強度校驗、帳號封禁與管理員權限控制。

### 技術棧

| 模組 | 技術 |
| --- | --- |
| 後端 | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA, Redis, MySQL |
| AI | LangChain4j OpenAI adapter |
| 商品抓取 | Jsoup |
| 使用者端 | uni-app / Vue |
| 管理端 | Vue 3, TypeScript, Vite |
| 物件儲存 | 七牛雲物件儲存 |
| 郵件 | SMTP 郵件服務 |
| 鑑權 | JWT |

### 專案結構

```text
smart-diy-pc-assistant/
├── smart-diy-pc-assistant-backend/   # Spring Boot 後端
├── smart-diy-pc-assistant-admin/     # Vue 管理端
├── smart-diy-pc-assistant-uniapp/    # uni-app 使用者端
├── LICENSE
└── README.md
```

### 環境需求

- JDK 17+
- Maven 3.9+，或使用專案內 Maven Wrapper
- MySQL 8+
- Redis 6+
- Node.js 20+，用於管理端
- HBuilderX，用於 uni-app 使用者端
- 可選：七牛雲物件儲存、SMTP 郵箱、OpenAI 相容模型服務

### 後端啟動

1. 建立資料庫：

```sql
CREATE DATABASE smart_diy_pc_assistant
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

2. 在 `smart-diy-pc-assistant-backend/src/main/resources/application-local.yaml` 中放置本地私密設定：

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

3. 啟動後端：

```bash
cd smart-diy-pc-assistant-backend
./mvnw spring-boot:run
```

Windows：

```powershell
cd smart-diy-pc-assistant-backend
.\mvnw.cmd spring-boot:run
```

預設後端位址：`http://localhost:8080`

### 管理端啟動

```bash
cd smart-diy-pc-assistant-admin
npm install
npm run dev
```

預設 Vite 位址通常為 `http://localhost:5173` 或 `http://localhost:5174`。

### 使用者端啟動

1. 使用 HBuilderX 開啟 `smart-diy-pc-assistant-uniapp`。
2. 確認 `smart-diy-pc-assistant-uniapp/common/api.ts` 中的 `API_BASE_URL` 指向後端位址。
3. 使用 HBuilderX 執行到 Web 瀏覽器、App 或小程式目標平台。

### 設定說明

- `application.yaml` 只放預設開發設定。
- `application-local.yaml` 用於本地私密設定，不應提交到倉庫。
- 生產環境建議使用環境變數或獨立的伺服器設定檔管理密鑰。
- 京東商品搜尋用於價格與商品連結參考，請遵守目標網站規則，避免高頻請求。

### 安全說明

- 不要提交郵箱授權碼、JWT 密鑰、物件儲存密鑰、AI API Key 或小程式 AppID。
- 發現密鑰洩露後應立即撤銷並重新生成。
- 上傳檔案建議使用物件儲存與 CDN 網域，不建議長期依賴本地檔案服務。

### 開發檢查

後端編譯：

```bash
cd smart-diy-pc-assistant-backend
./mvnw -DskipTests compile
```

管理端建置：

```bash
cd smart-diy-pc-assistant-admin
npm run build
```

### 貢獻

歡迎提交 Issue 與 Pull Request。建議在提交前說明：

- 問題背景或功能目標
- 影響範圍
- 主要實作思路
- 已完成的測試或驗證步驟

### 授權

本專案基於 [MIT License](LICENSE) 開源。

[回到頂部](#smart-diy-pc-assistant)

---

<a id="en-us"></a>

## 🇺🇸 English

### Overview

Smart DIY PC Assistant is a full-stack project for DIY PC building. Instead of storing e-commerce products as hardware data, it keeps a standard hardware model library for CPUs, motherboards, GPUs, memory, storage, PSUs, cases, and coolers. JD product search is used as a price and purchase source, returning purchasable product titles, prices, images, links, and SKUs.

The system includes a user app, an admin panel, and a backend service. It supports AI-powered PC build conversations, build generation, JD product selection, hardware compatibility checks, community posts, tech news, user profiles, user management, and account banning.

### Screenshots

> Screenshot placeholder: add user app, build detail, AI chat, profile space, and admin panel screenshots here.

<!--
Examples:
![User Home](docs/screenshots/user-home.png)
![Build Detail](docs/screenshots/build-detail.png)
![Admin Dashboard](docs/screenshots/admin-dashboard.png)
-->

### Features

- AI build chat: generate PC builds from budget, use case, appearance preference, and performance needs.
- Standard hardware model library: store normalized hardware models instead of branded e-commerce listings.
- JD product search: Spring Boot + Jsoup crawler parses JD search results and returns title, price, image, link, and SKU.
- Build configuration system: manual builds, AI builds, favorites, previews, detail pages, and JD product links.
- Compatibility checks: CPU socket, motherboard memory type, PSU wattage, case form factor, and cooler class.
- Community posts: publish posts, upload images, attach builds, like, favorite, and comment.
- User spaces: profile data, background image, posts, favorites, following, and followers.
- Tech news: news list, sorting, detail page, likes, favorites, and comments.
- Email verification: registration and password reset with email verification and captcha.
- Admin panel: manage users, standard hardware models, news, community content, AI settings, and system settings.
- Account security: JWT authentication, password hashing, password strength checks, account bans, and admin-only access.

### Tech Stack

| Module | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA, Redis, MySQL |
| AI | LangChain4j OpenAI adapter |
| Product crawling | Jsoup |
| User app | uni-app / Vue |
| Admin panel | Vue 3, TypeScript, Vite |
| Object storage | Qiniu Cloud Object Storage |
| Email | SMTP mail service |
| Authentication | JWT |

### Repository Layout

```text
smart-diy-pc-assistant/
├── smart-diy-pc-assistant-backend/   # Spring Boot backend
├── smart-diy-pc-assistant-admin/     # Vue admin panel
├── smart-diy-pc-assistant-uniapp/    # uni-app user app
├── LICENSE
└── README.md
```

### Requirements

- JDK 17+
- Maven 3.9+, or the Maven Wrapper included in the backend module
- MySQL 8+
- Redis 6+
- Node.js 20+ for the admin panel
- HBuilderX for the uni-app user app
- Optional: Qiniu Cloud Object Storage, SMTP mail account, OpenAI-compatible model service

### Backend Setup

1. Create the database:

```sql
CREATE DATABASE smart_diy_pc_assistant
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

2. Put local secrets in `smart-diy-pc-assistant-backend/src/main/resources/application-local.yaml`:

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

3. Start the backend:

```bash
cd smart-diy-pc-assistant-backend
./mvnw spring-boot:run
```

Windows:

```powershell
cd smart-diy-pc-assistant-backend
.\mvnw.cmd spring-boot:run
```

Default backend URL: `http://localhost:8080`

### Admin Panel Setup

```bash
cd smart-diy-pc-assistant-admin
npm install
npm run dev
```

The default Vite URL is usually `http://localhost:5173` or `http://localhost:5174`.

### User App Setup

1. Open `smart-diy-pc-assistant-uniapp` in HBuilderX.
2. Check `API_BASE_URL` in `smart-diy-pc-assistant-uniapp/common/api.ts`.
3. Run the project to a web browser, app target, or mini-program target through HBuilderX.

### Configuration Notes

- `application.yaml` contains default development configuration only.
- `application-local.yaml` is for private local secrets and should not be committed.
- For production, use environment variables or a separate server configuration file.
- JD product search is used for price and product link references. Respect the target site's rules and avoid high-frequency requests.

### Security Notes

- Do not commit mail authorization codes, JWT secrets, object storage keys, AI API keys, or mini-program AppIDs.
- Rotate leaked secrets immediately.
- Use object storage and CDN domains for uploaded files instead of relying on local file serving in production.

### Development Checks

Backend compile:

```bash
cd smart-diy-pc-assistant-backend
./mvnw -DskipTests compile
```

Admin build:

```bash
cd smart-diy-pc-assistant-admin
npm run build
```

### Contributing

Issues and pull requests are welcome. Please include:

- Problem background or feature goal
- Impact scope
- Main implementation approach
- Tests or validation steps

### License

This project is open-sourced under the [MIT License](LICENSE).

[Back to top](#smart-diy-pc-assistant)
