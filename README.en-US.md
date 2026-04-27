# Smart DIY PC Assistant

<p align="center">
  <a href="README.md">🇨🇳 简体中文</a> ·
  <a href="README.zh-TW.md">🇹🇼 繁體中文</a> ·
  🇺🇸 English
</p>

Smart DIY PC Assistant is a full-stack project for DIY PC building. The system keeps a standard hardware model library and uses JD products only as a real-time source for prices, images, and purchase links. This avoids mixing branded e-commerce listings and store-specific product data into the hardware database.

The project includes a user app, an admin panel, and a Spring Boot backend. It supports AI-powered PC build conversations, build generation, JD product search, hardware compatibility checks, community posts, tech news, user spaces, user management, and account security.

## Table of Contents

- [Screenshots](#screenshots)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Repository Layout](#repository-layout)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Configuration Notes](#configuration-notes)
- [Development Checks](#development-checks)
- [Security Notes](#security-notes)
- [Contributing](#contributing)
- [License](#license)

## Screenshots

<!--
Place screenshots under docs/screenshots/ and reference them here.

![User Home](docs/screenshots/user-home.png)
![Build Detail](docs/screenshots/build-detail.png)
![AI Chat](docs/screenshots/ai-chat.png)
![Admin Dashboard](docs/screenshots/admin-dashboard.png)
-->

## Features

- AI build chat: generate PC builds from budget, use case, appearance preferences, and performance needs.
- Standard hardware model library: store normalized CPU, motherboard, GPU, memory, storage, PSU, case, and cooler models.
- JD product search: the backend uses Spring Boot + Jsoup to parse JD search results and return title, price, image, link, and SKU.
- Build system: supports manual builds, AI-generated builds, favorites, previews, detail pages, and JD product links.
- Compatibility checks: validates CPU socket, motherboard memory type, PSU wattage, case form factor, and cooler class.
- Community posts: publish posts, upload images, attach builds, like, favorite, and comment.
- User spaces: show profile data, background image, posts, favorites, following, and followers.
- Tech news: news list, sorting, detail pages, likes, favorites, and comments.
- Account security: email verification, captcha, password strength validation, JWT authentication, password hashing, and account banning.
- Admin panel: manage users, standard hardware models, news, community content, AI settings, and system settings.

## Tech Stack

| Module | Technology |
| --- | --- |
| Backend | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA, MySQL, Redis |
| AI | LangChain4j OpenAI adapter |
| Product crawling | Jsoup |
| User app | uni-app, Vue |
| Admin panel | Vue 3, TypeScript, Vite |
| Object storage | Qiniu Cloud Object Storage |
| Email | SMTP |
| Authentication | JWT |

## Repository Layout

```text
smart-diy-pc-assistant/
├── smart-diy-pc-assistant-backend/   # Spring Boot backend
├── smart-diy-pc-assistant-admin/     # Vue admin panel
├── smart-diy-pc-assistant-uniapp/    # uni-app user app
├── LICENSE
└── README.md
```

## Requirements

- JDK 17+
- Maven 3.9+, or the Maven Wrapper included in the backend module
- MySQL 8+
- Redis 6+
- Node.js 20+ for the admin panel
- HBuilderX for the uni-app user app
- Optional: Qiniu Cloud Object Storage, SMTP mail account, OpenAI-compatible model service

## Quick Start

### 1. Create the Database

```sql
CREATE DATABASE smart_diy_pc_assistant
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 2. Configure Backend Secrets

Put private local configuration in `smart-diy-pc-assistant-backend/src/main/resources/application-local.yaml`:

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

### 3. Start the Backend

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

### 4. Start the Admin Panel

```bash
cd smart-diy-pc-assistant-admin
npm install
npm run dev
```

The default Vite URL is usually `http://localhost:5173` or `http://localhost:5174`.

### 5. Start the User App

1. Open `smart-diy-pc-assistant-uniapp` in HBuilderX.
2. Check `API_BASE_URL` in `smart-diy-pc-assistant-uniapp/common/api.ts`.
3. Run the project to a web browser, app target, or mini-program target through HBuilderX.

## Configuration Notes

- `application.yaml` contains default development configuration only.
- `application-local.yaml` is for private local secrets and should not be committed.
- For production, use environment variables or a separate server configuration file.
- JD product search is used for price and purchase link references. Respect the target site's rules and avoid high-frequency requests.

## Development Checks

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

## Security Notes

- Do not commit mail authorization codes, JWT secrets, object storage keys, AI API keys, or mini-program AppIDs.
- Rotate leaked secrets immediately.
- Use object storage and CDN domains for uploaded files instead of relying on local file serving in production.

## Contributing

Issues and pull requests are welcome. Please include:

- Problem background or feature goal
- Impact scope
- Main implementation approach
- Tests or validation steps

## License

This project is open-sourced under the [MIT License](LICENSE).
