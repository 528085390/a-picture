# NOCTURNE · 夜之馆

一个完整的图片平台：Spring Boot 后端 + Vue 3 前端。视觉方向是暗色私人图库、金色细线、衬线大字，让照片本身成为页面主角。

## 目录

```text
a-picture-backend/
├─ src/                 Spring Boot 后端（Java 11）
├─ frontend/            Vue 3 + Vite + TypeScript 前端
├─ deploy/              Docker Compose 部署配置
├─ pom.xml
└─ README.md
```

## 功能

- 公开图库：月份分组、搜索、分类筛选、收藏、无限加载、缩略图 4 档缩放
- 图片查看器：大图、胶片条、下载、编辑、AI 扩图
- 图片管理：上传、编辑、删除、批量管理、Live 图片
- 团队空间：空间/成员/角色权限、空间图片、空间分析
- 协同编辑：WebSocket 实时同步、单编辑者锁
- 管理后台：用户、图片、空间管理
- 认证：Session Cookie；公开图库允许访客浏览

## 本地开发

### 1. 后端

要求 Java 11、Maven、MySQL 8、Redis。默认端口 `8000`，context path `/api`。

```bash
# 在仓库根目录
mvn spring-boot:run
```

数据库连接、Redis、R2/COS 等本地配置放在 `src/main/resources/application-local.yml`（该文件已被忽略，不要提交密钥）。

### 2. 前端

要求 Node.js 18+。

```bash
cd frontend
npm install
npm run dev
```

打开 <http://127.0.0.1:5173>。Vite 已把 `/api`（含 WebSocket）代理到 `http://127.0.0.1:8000`。

生产构建：

```bash
cd frontend
npm run build
```

## Docker 部署

部署配置在 `deploy/`，包含 Nginx、Spring Boot、MySQL、Redis 四个容器。

```bash
cd deploy
cp .env.example .env
# 编辑 .env：数据库密码、管理员密码、R2/COS 凭证
```

准备构建产物（仓库不提交这两个大文件）：

```bash
# 后端 fat jar -> deploy/app.jar
mvn clean package -DskipTests
cp target/a-picture-backend-0.0.1-SNAPSHOT.jar deploy/app.jar

# 前端 dist -> deploy/web/
cd frontend && npm run build
rm -rf ../deploy/web && cp -r dist ../deploy/web
```

启动：

```bash
cd deploy
docker compose up -d --build
```

默认对外端口 `8090`。更详细的服务器、数据卷、R2 和运维说明见 [`deploy/README.md`](deploy/README.md)。

## 当前演示

- 前端入口：<https://pic.raylx.xyz>
- 公开图库：<https://pic.raylx.xyz/gallery>
- 生产管理员账号见部署目录 `.env` 的 `ADMIN_PASSWORD`。

## 开发约定

- 前端 API 统一走 `/api`，使用 Session Cookie；请求需要 `credentials: 'include'`。
- 图片存储以 R2 为主，COS 配置保留；前端通过 `/api/picture/file/` 代理读取对象，不直接暴露对象存储域名。
- `frontend/design-samples/`、`shots/`、`scratch/` 是本地设计和验收材料，已被 `.gitignore` 忽略。
