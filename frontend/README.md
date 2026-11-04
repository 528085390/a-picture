# NOCTURNE · 夜之馆

面向 `a-picture-backend` 的图片平台前端。视觉方向为 **暗色私人图库 / 金色细线 / 衬线大字**，把照片本身放在第一位，而不是常见的后台管理卡片墙。

- 技术栈：Vue 3 + Vite + TypeScript + Pinia + Vue Router
- 后端：Spring Boot `http://127.0.0.1:8000`，统一前缀 `/api`，Session Cookie 鉴权
- 本机开发数据：60 张本地策展图片已导入演示账号，公开图库合计 69 张；生产站已预置 1000 张随机示例图片

## 生产部署（os）

已用 Docker Compose 部署到 os 服务器，公网入口：<https://pic.raylx.xyz>。管理员账号 `admin`，初始密码保存在 `<repo>\deploy\.env` 的 `ADMIN_PASSWORD`。完整启动、更新、数据与网络说明见 [`../deploy/README.md`](../deploy/README.md)。

生产站已预置 1000 张随机示例图片（`随机示例-0001` … `随机示例-1000`，1600×1200，分类「示例」）；旧 R2 对象没有删除，本机开发库也没有改动。下面的演示账号只适用于本机开发环境。

## 启动

要求 Node.js 18+（本机实测 Node 24）。先确认后端 8000 已启动，再执行：

```powershell
cd <repo>\frontend
npm install
npm run dev
```

打开 <http://127.0.0.1:5173>。

Vite 已配置开发代理，把 `/api`（含 WebSocket）转发到 `http://127.0.0.1:8000`，因此开发环境不需要处理跨域。不要直接双击 `dist/index.html` 打开构建产物，浏览器 `file://` 下无法使用 History 路由和 Cookie。

生产构建：

```powershell
npm run build
npm run preview
```

`dist/` 可交给 Nginx / Caddy 托管；需要把 `/api` 反向代理到后端，并为 WebSocket 保留 Upgrade 头。示例：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8000;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
location / {
    root /path/to/a-picture-backend/frontend/dist;
    try_files $uri $uri/ /index.html;
}
```

## 演示账号

| 账号 | 密码 | 说明 |
| --- | --- | --- |
| `nocturne_demo` | `********` | 普通用户；Night Voyage 团队空间管理员 |
| `nocturne_viewer` | `********` | Night Voyage 观看者，用来验证只读权限 |

管理控制台 `/admin` 只对 `userRole=admin` 的账号开放；后台已有管理员账号 `youuuu`，密码由原系统维护，前端不内置管理员密码。

## 页面与功能

- `/` 首页：NOCTURNE 夜之馆的完整品牌首页，包含全屏 hero、聚光灯、序言、正在展出、馆藏精选与私人入口；照片全部来自后端公开图库。
- `/gallery` 图库：去掉站点顶栏与左侧栏，只保留悬浮控件和照片墙；公开图片按月份分组、吸顶月份标题；桌面 176px 档位多列缩略图（列宽自动铺满图册区，右侧不留黑边），滚轮按 iPhone 相册式 4 档平滑缩放（116 / 176 / 238 / 300px）（Shift + 滚轮翻页），按住拖动平移（高跟手软跟随），松手带 2–2.6s 线性减速惯性（上限 650px/s，逐步收尾），Shift 框选，长按拖拽框选；搜索、分类筛选、下滑自动加载。
- `/favorites` 收藏：复用纯图册布局；收藏 ID 保存在浏览器 `localStorage`，不写入后端，换浏览器不同步。
- `/live` Live 照片：复用纯图册布局，读取后端 Live Picture 列表。
- `/login` 登录 / 注册：Session Cookie 登录，支持注册后自动登录。
- `/spaces` 空间列表：我的空间、团队空间封面、容量与数量。
- `/spaces/:id` 空间详情：照片、成员、数据分析；团队空间支持上传、批量编辑、角色管理、WebSocket 协同编辑和 AI 扩图。
- `/settings` 账号设置：身份、存储概览、收藏/空间快捷入口、退出登录。
- `/admin` 管理控制台：用户、图片、空间的管理列表与编辑入口。
- 查看器：大图、胶片条、键盘方向键切换、下载、图片信息、编辑、删除、AI 扩图。
- 响应式：桌面、平板、390px 手机均有独立布局；移动端为 3 列缩略图。

## 目录

```text
src/
  api/         请求封装、类型、图片/空间/用户接口
  components/  AppShell、PhotoGrid、PhotoViewer、上传/编辑/批量/AI/协同组件
  composables/ 收藏、快捷键等组合式逻辑
  router/      路由与登录/管理员守卫
  stores/      Pinia：用户、图库、空间、UI
  styles/      app.css 基础控件 + nocturne.css 视觉系统
  utils/       媒体 URL、格式化、存储键
  views/       页面
design-samples/  b-library.html 设计样例（60 张，独立静态页）
shots/           验收截图
scratch/         验收脚本、数据清理与调试工具（不参与构建）
```

## 关键实现说明

- **雪崩 ID**：后端 Jackson 把 19 位 Long 输出为 JSON 数字，JS 会丢精度。`src/api/request.ts` 在 `JSON.parse` 前把 `id/userId/spaceId/pictureId/taskId` 等字段的 16 位以上整数转成字符串；新增接口字段时记得同步 `LONG_ID_KEYS`。
- **自动加载**：底部不再有「加载更多」按钮。首屏先请求第 1 页，再并行预取前 6 页（最多 120 张）后一次性渲染；本机 69 张图库会直接完整出现，生产 1000 张图库首屏约渲染 118 张，继续下滑时自动拉取后续页，并按每 4 张一帧追加，避免一次插入整页。
- **网格缩略图**：网格不直接加载 1280px 预览图，而是走 `/api/picture/thumb?key=...&w=360`，由原生 `<img>` 配合前 12 张 `loading="eager"`、其余 `loading="lazy"` 与 `decoding="async"` 渲染。滚轮直接按 4 档缩放缩略图（不再使用 `Ctrl/⌘`，因此不会触发浏览器页面缩放；每次缩放带 FLIP 过渡）；网格列宽按容器自动等分铺满，右侧不留黑边；`Shift + 滚轮` 在网格内翻页。
- **R2 图片地址**：当前 R2 公网域名 `gdufbbs.dpdns.org` 在本机不可直连，`src/utils/media.ts` 的 `PROXY_HOSTS` 会把匹配的图片 URL 改写为同源 `/api/picture/file/...`，由后端代理读取。换存储域名时改这里，并同步检查下载用的 `storageKey`。
- **上传**：`FormData` 直接走 `request` 的 `rawBody`，不要手动设置 `Content-Type`，否则 boundary 会丢。
- **收藏**：只存在 `localStorage` 的 `nocturne.favorites.v1`，属于本机偏好，不是后端数据。
- **动效**：统一使用 `--ease-out/--ease-soft`、`--dur-*` 变量；`prefers-reduced-motion` 与 `html.static` 会关闭或缩短动画。

## 验收脚本

本机 `frontend/scratch/` 下有 Chrome CDP 验收脚本，需要先启动 Vite（5173）。这些脚本属于本地验收材料，已被 `.gitignore` 忽略，不随仓库提交：

```powershell
node scratch/verify-home-gallery.mjs   # 首页、纯图册、搜索、查看器、移动端
node scratch/verify-interactions.mjs    # 滚轮缩放、拖动、长按框选
node scratch/verify-routes.mjs          # 空间、设置、收藏、访客与首页跳转
node scratch/verify-roles.mjs   # 空间 owner / viewer 权限差异
node scratch/verify-prod-1000.mjs # 生产站 1000 张示例图验收（需公网可访问）
node scratch/verify-prod-smooth.mjs # 生产站滚轮 4 档缩放验收
node scratch/verify-prod-pan.mjs    # 生产站拖拽惯性验收
```

最近一次本机结果：首页 hero 1600px 图完整加载、图库 69 张 / 桌面 176px 7 列、搜索“山谷”2 张、查看器 69 张胶片条、移动端 390px 3 列且无横向溢出、滚轮 176→238→300px（4 档）、长按框选 4 张、空间与设置页保留原外壳、控制台 0 error。

生产站 1000 张示例验收：公开图库 total=1000、首屏 118 张卡片、缩略图 7 列 / 176px、搜索「随机示例-0500」1 张、查看器主图 1600px、移动端 390px 3 列且无横向溢出、控制台 0 error。

生产站 4 档 + 铺满验收（2026-11-04）：`/assets/index-DAR-H9AY.js`；滚轮 2 次 176→238→300px，画面单调过渡、无 console error；300px 档 4 列、列宽 340.75px、rightGap=0，网格宽 1384px；拖拽 scrollTop 300→982，惯性收尾约 2s，帧间隔 p50 5.6ms / p95 5.9ms / max 44.5ms，松手后继续滑行到 982。

## 已知边界

- 收藏是本地偏好，不跨设备同步。
- 管理后台依赖后端 `/user`、`/picture`、`/space` 的管理员接口；普通用户访问 `/admin` 会被路由守卫送回图库。
- 协同编辑只对团队空间图片开放，且受后端单编辑锁限制；非团队空间不会建立 WebSocket。
- AI 扩图结果不会自动入库，需要在结果面板里手动保存。
