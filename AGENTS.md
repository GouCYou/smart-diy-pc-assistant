# Smart DIY project operations

## Architecture
- User UI: `smart-diy-pc-assistant-web` (Vue 3); admin: `smart-diy-pc-assistant-admin` (Element Plus).
- Both frontends use same-origin `/api`, `/agent`, `/media`. All requests enter Java; Java forwards `/agent/**` to Python using HTTP/1.1 with immediate SSE flushes. Never point a frontend directly at the Python port.
- Java owns users, permissions, hardware, saved builds, community/news and uploads. Python owns Agent orchestration, crawling, history, RAG and tool events. Do not restore Java AI or the removed market module.
- Python authenticates each user/admin through Java `/api/auth/me`; do not trust frontend-supplied identity.
- History is a persistent sidebar with fold controls, default expanded. Global navigation defaults collapsed, its toggle sits below the personal-center item. Keep desktop/mobile slider and route animations consistent. Respect reduced-motion preferences.

## Production server (verified 2026-10-21)
- SSH: `gouc@192.168.1.6`; never log in as root. Obtain credentials from the user/session, never write them in source/docs.
- Docker Compose project `smartdiy`, directory `/www/smartdiy-docker`, services `backend`, `agent`, `web`, `admin`; restart policy `unless-stopped`.
- Existing host MySQL `localhost:3306` / database `smart_diy_pc_assistant`, Redis `localhost:6379`, and Qiniu bucket/domain continue unchanged. Linux production uses host networking so these localhost services remain compatible. Java binds 127.0.0.1:8080; Python binds 127.0.0.1:8000. Only frontend ports 18088/18089 serve LAN HTTP.
- Existing config was copied to `/www/smartdiy-docker/secrets/application-server.yaml`, with obsolete `app.ai` removed. `secrets/backend.env` retains Qiniu/SMTP variables; `secrets/agent.env` holds model settings. Files must stay private (0600; directory 0700), never print them or `docker compose config` with resolved environment.
- Persistent data: `agent-data/` (SQLite knowledge, conversations, checkpoints/settings) and `uploads/`. Back up Agent SQLite with its backup API or with the Agent stopped; don't copy a live WAL database file alone.
- Host Cloudflare Tunnel config: `/etc/cloudflared/config.yml`; service `cloudflared`. Routes: `smartdiy.cctstudio.cn` -> localhost:18088; `admin-smartdiy.cctstudio.cn` -> localhost:18089; `api-smartdiy.cctstudio.cn` -> localhost:8080. Other hostnames are unrelated: preserve them.
- Canonical admin entry: `https://smartdiy.cctstudio.cn/admin/`; build admin with `VITE_PUBLIC_BASE=/admin/`. Vue Router uses `import.meta.env.BASE_URL`; Nginx strips `/admin/` when forwarding to the admin container. Test `/admin/assistant` and `/admin/knowledge` with direct refresh. The admin subdomain is an alias, not the main entry.
- User domain formerly pointed at Cloudflare Pages; its DNS now points at the existing tunnel. Do not redeploy the old uni-app Pages site.
- Legacy Jenkins job `smart-diy` still references the previous direct-deployment paths and has no configured triggers as of this verification. Do not run it for Docker releases; use the documented production staging/Compose procedure until that job is explicitly migrated.
- Previous direct Java systemd unit `cct-smartdiy` is stopped/disabled to prevent port collision; old JAR/config remain in `/www/smartdiy`.
- Pre-migration backup: `/www/smartdiy-backups/20261021-130426` (MySQL dump, prior JAR/env/YAML, Tunnel config, DNS records). `last-backup` records the path. This folder is private.
- Imported 78 legacy conversations and 250 messages into Agent SQLite using `scripts/import_legacy.py`; original MySQL tables remain intact. Import is idempotent and must run with Agent stopped. Never replace production data with local QA fixtures.

## Build, release and verify
1. Backend: `cd smart-diy-pc-assistant-backend && bash mvnw clean test package`. User frontend: `npm ci && npm run build`; admin: `npm ci && VITE_PUBLIC_BASE=/admin/ npm run build`. Agent: `.venv/bin/python -m pytest -q`.
2. Run `bash deployment/package-release.sh /absolute/staging-directory`; upload that staging directory into the production directory (do not use `rsync --delete` against secrets/data).
3. Use `deployment/compose.production.yaml`, not the development compose with a fresh MySQL. On server: `docker compose build` then `docker compose up -d --wait`. If the standard registry is unavailable, use a reachable trusted mirror explicitly; never change global Docker configuration or restart Docker (other workloads share it). `PIP_INDEX_URL` can select an accessible package index during Agent build.
4. Verify all four container health checks; `/api/health`, `/agent/health` via frontend origin; authenticated settings GET/PUT, upload+retrieval, actual streamed run and tool history. Check desktop/mobile visual layout and animations, then the public domains through Tunnel. Builds alone are not acceptance.
5. Never ship `application-local.yaml` inside a JAR/image. Maven resource exclusions and staging validation enforce this. Never commit secrets, test tokens, runtime databases or `.codex_tmp`.
6. Rollback: stop application Compose services, restore previous Tunnel/DNS configuration from the private backup if necessary, then `systemctl enable --now cct-smartdiy`. Preserve new Agent data. Restore MySQL only when a real schema/data rollback is necessary and after reviewing new writes; never blindly overwrite the live database.

## RAG/admin
- `/assistant`: endpoint/key/model, optional embedding model, time/step/tool/token limits, extra prompt, RAG chunk length/overlap/top-K/cosine threshold. Changes persist in Agent SQLite. Blank API key retains the current key.
- `/knowledge`: text/PDF/Markdown/TXT/URL imports, search preview, per-document reindex/delete. Reindex after changing chunk settings or embedding model. Without embeddings, retrieval uses keyword matching; do not claim vector search is enabled.
- `/runs`: execution status and events. JD search panel returns observed page prices and unknown availability; do not infer stock. Risk-control responses are reported, never bypassed.
