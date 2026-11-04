# NOCTURNE · 夜之馆 — Docker 部署

## 公网入口

- 地址：<https://pic.raylx.xyz>
- 服务器：os（demo-host，aarch64，Ubuntu）
- 部署目录：`/opt/a-picture`
- Compose 项目：`a-picture`

生产站已预置 1000 张随机示例图片（Picsum Seeds，1600×1200）；管理员登录后可以继续上传、编辑和管理。公开图库无需登录。

## 管理员

- 账号：`admin`
- 初始密码：见部署目录 `.env` 的 `ADMIN_PASSWORD`
- 角色：`admin`

建议登录后在「账号设置」里改密码，或在服务器上改 `.env` 后重建 admin 种子（仅首次初始化生效）。

## 架构

```text
浏览器
  → https://pic.raylx.xyz
  → a-picture-web (Nginx 1.27, 静态 SPA + /api 反代)
  → a-picture-backend (Spring Boot 2.7.6, Java 11, 内部 8000, context /api)
  → a-picture-mysql (MySQL 8.0)
  → a-picture-redis (Redis 8, Spring Session)
  → Cloudflare R2 (图片对象；bucket apic)
```

四个容器均设置 `restart: unless-stopped`，MySQL/Redis 使用 named volumes 持久化。

## 文件

| 文件 | 用途 |
| --- | --- |
| `app.jar` | Spring Boot fat jar |
| `backend.Dockerfile` | 后端镜像，eclipse-temurin:11-jre-jammy |
| `web/` | 前端 `dist/` 构建产物 |
| `web.Dockerfile` | Nginx 镜像 |
| `nginx.conf` | SPA fallback + `/api` / WebSocket 反代 |
| `docker-compose.yml` | 四个服务编排 |
| `application-prod.yml` | prod profile 配置 |
| `initdb/` | 首次启动建表 + admin 种子 |
| `.env` | 密码、R2/COS 凭证，权限 600 |

## 运维命令

在服务器上：

```bash
cd /opt/a-picture
docker compose ps
docker compose logs -f backend
docker compose up -d --build backend web
docker compose restart backend
```

更新前端：本地 `npm run build`，把新的 `dist/` 覆盖到部署目录 `web/`，再执行 `docker compose up -d --build web`。

更新后端：替换 `app.jar` 或 `application-prod.yml`，再执行 `docker compose up -d --build backend`。

## 数据说明

- 当前 MySQL `picture` 表有 1000 行示例记录：`随机示例-0001` … `随机示例-1000`，分类统一为 `示例`，尺寸 1600×1200。
- 生产站公开图库 total=1000；示例图来自 `https://picsum.photos/seed/nocturne-sample-NNNN/1600/1200.jpg`。
- 示例图片的原图与 preview 都存在 R2 bucket `apic`。R2 当前为 2138 个对象 / 430,796,890 bytes：原先 138 个旧对象（46,502,770 bytes，未删除）+ 1000 张示例原图 + 1000 张 preview。
- 加示例前的旧对象清单：`r2-manifest-20261103.json`（key / size / lastModified / etag）；该清单只覆盖加示例前的 138 个对象。
- **没有删除 R2 里的旧原图**；本机开发库也没有改动。如果确认要连 R2 原图一起删除，这是不可恢复操作，需要另行确认后执行。
- MySQL/Redis 数据在 Docker named volumes：`a-picture_mysql-data`、`a-picture_redis-data`。

## 网络与安全

- 服务器本机 iptables 已新增 `8090/tcp` 放行，并通过 `netfilter-persistent save` 持久化；改动前备份在 `/etc/iptables/rules.v4.bak-apicture-*`。
- OCI VCN Default Security List 已新增 `0.0.0.0/0 → 8090/tcp` ingress；改动前快照在 `oci-security-list-before-8090.json`。
- 没有改动机器上已有的 80/443/8080/8088/8443 端口、Nginx 站点、sing-box 或证书配置。
- 当前是 HTTP；登录态会经明文传输，正式使用建议加域名 + TLS。

## 首次初始化

MySQL 只在数据卷为空时执行 `initdb/`。如果删掉 `mysql-data` 卷再启动，会重新建表并写入 admin 种子。
