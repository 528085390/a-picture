# Docker 部署

`deploy/` 是一套完整的单机部署：四个容器 + 两个数据卷，一条 `docker compose up -d --build` 起来。

```text
浏览器
  → Nginx（web 容器，对外 8090 · 静态 SPA + /api 反代 + 图片磁盘缓存）
  → Spring Boot（backend 容器，内部 8000，context-path /api）
  → MySQL 8（mysql 容器，数据卷 mysql-data）
  → Redis 8（redis 容器，数据卷 redis-data，存 Session）
  → 对象存储（Cloudflare R2 的公开桶 + 私密桶；也保留腾讯云 COS 实现）
```

四个服务都设了 `restart: unless-stopped`，MySQL / Redis 用 named volume 持久化。

## 文件

| 文件 | 用途 |
| --- | --- |
| `backend.Dockerfile` | 后端镜像（`eclipse-temurin:11-jre`） |
| `app.jar` | 后端 fat jar（构建产物，**不提交**） |
| `web.Dockerfile` | 前端镜像（Nginx） |
| `web/` | 前端 `dist/` 构建产物（含 `manifest.webmanifest`、`sw.js`、`icons/`、`splash/`，**不提交**） |
| `nginx.conf` | SPA fallback、`/api` 与 WebSocket 反代、图片磁盘缓存、PWA 响应头 |
| `docker-compose.yml` | 四服务编排 |
| `application-prod.yml` | `prod` profile 配置（全部走环境变量） |
| `initdb/01-schema.sql` | 首次启动建表 |
| `initdb/02-admin.sql` | 首次启动写入 admin 种子账号 |
| `.env.example` | 环境变量模板，复制成 `.env` 后填写 |

## 准备产物

仓库不提交 jar 与前端产物：

```bash
# 后端
mvn clean package -DskipTests
cp target/a-picture-backend-0.0.1-SNAPSHOT.jar deploy/app.jar

# 前端
cd frontend && npm run build && cd ..
rm -rf deploy/web && cp -r frontend/dist deploy/web
```

## 配置 .env

```bash
cd deploy
cp .env.example .env
```

| 变量 | 说明 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | MySQL root 与业务账号（`apicture`）口令 |
| `R2_ENDPOINT` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_BUCKET_NAME` | R2 的 S3 兼容连接信息 |
| `R2_PUBLIC_URL` | 公开桶绑定的自定义域名（图片 CDN 前缀） |
| `COS_*` | 腾讯云 COS（可选，`file.storage.type` 切换后使用） |
| `ALIYUN_AI_API_KEY` | 阿里云百炼 API-KEY，仅 AI 扩图需要 |

## 管理员种子账号

`initdb/02-admin.sql` 会在**数据卷为空时**写入一个 admin 账号：

- 账号：`admin`
- 口令：`admin123456`（文件里存的是它对盐后的哈希，**部署前请替换成自己的**）
- 注意后端存的是 `md5("apicture" + 明文)`，盐值写死在 `UserServiceImpl#getEncryptPassword`，所以哈希要这样算：

```bash
printf 'apicture%s' '你的新密码' | md5sum   # 把结果填进 02-admin.sql 的 userPassword
```

> MySQL 只在数据卷为空时执行 `initdb/`。已经初始化过的话，改种子文件不会生效——请直接在「账号设置」里改密码，或删掉 `mysql-data` 卷重新初始化（会丢数据）。

## 启动 / 运维

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
docker compose restart backend
```

默认对外端口是 **8090**。

**更新前端**：产物是打进镜像的（`web.Dockerfile` 里 `COPY web/`，容器没有 bind mount），所以必须重新构建：

```bash
docker compose up -d --build web
```

**更新后端**：替换 `app.jar` 或 `application-prod.yml`，然后 `docker compose up -d --build backend`。

## 加域名 / TLS

前端容器自己只监听 HTTP 8090。要给站点挂域名和证书，标准做法是在宿主机上再放一层 Nginx（或用 Caddy / Cloudflare Tunnel）：

```nginx
server {
    listen 443 ssl http2;
    server_name example.com;

    ssl_certificate     /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;

    client_max_body_size 12m;

    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header Upgrade $http_upgrade;      # 协同编辑的 WebSocket
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
```

> 反代这一层要放开 `client_max_body_size`（后端 upload 限制 10 MB），并保留 Upgrade 头，否则 `/api/ws/picture/edit` 会握手失败。

## 容器内 Nginx 做了什么

- SPA fallback：非文件请求回 `index.html`
- `/api/` 反代到 `backend:8000`；`/api/ws/` 升级为 WebSocket（读写超时 3600 s）
- 图片磁盘缓存 `/var/cache/nginx/img`（`keys_zone=img_cache:200m`、上限 6 GB）：`= /api/picture/thumb` 缓存 7 天，`^~ /api/picture/file/` 缓存 30 天；私密空间对象（`space/` 前缀）**不缓存**，响应头带 `no-store` / `Vary: Cookie`
- gzip 文本资源；`manifest.webmanifest` 强制 `application/manifest+json`，`sw.js` 不缓存

## 数据

- 业务数据在 MySQL 卷 `a-picture_mysql-data`，Session 在 Redis 卷 `a-picture_redis-data`；图片本体在对象存储。
- 备份建议：容器内 `mysqldump` 一份 + 对象存储桶按需同步；恢复时删卷重建再导回。


## 演示实例（在线 demo）

公开演示站 <https://demo.raylxpj.us.ci/> 是把同一套镜像再跑一份：容器名、数据卷、库、Redis、对象存储桶、域名全部独立，
和生产实例共用一台机器即可。完整脚本在 [demo/](demo/README.md)：

| 文件 | 用途 |
| --- | --- |
| `demo/docker-compose.demo.yml` | 演示实例编排（web 端口默认 8880） |
| `demo/.env.demo.example` | 演示实例环境变量模板（独立库口令 + 独立桶） |
| `demo/nginx.conf` | 容器内 Nginx：在生产版基础上加注册 / 上传限流 |
| `demo/seed-demo.sh` | 从 Picsum 拉 1600×1200 示例图并走上传接口灌进演示库 |
| `demo/make-snapshot.sh` | 打一份「干净状态」的数据库快照 |
| `demo/reset-demo.sh` | 每日重置：恢复快照 → 清 Redis 会话 → 清孤儿对象 → 重启后端 |
| `demo/r2-orphan-clean.py` | 删除数据库已无引用的对象（访客上传的图不会长年堆在桶里） |

部署顺序：`up -d` → `seed-demo.sh` 灌示例图 → `make-snapshot.sh` → 把 `reset-demo.sh` 加进 crontab（每天 04:00）。

## 安全建议

- `.env` 权限收到 `600`，不要提交仓库；`deploy/.gitignore` 已忽略 `.env`、`app.jar`、`web/`。
- 首次部署后立刻改掉 admin 的示例口令。
- 只对需要的端口开放安全组 / 防火墙规则；8090 若不需要直接暴露，可只允许本机反代访问。
