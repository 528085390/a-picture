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
- 响应式：`useIsMobile()`（`(max-width: 860px), (pointer: coarse)`）决定渲染移动端还是桌面端。桌面端 = `DesktopGalleryView` + `PhotoGrid` + `PhotoViewer`（原有交互保持不变），移动端 = `src/mobile/*` 下的 iPhone 相册式实现（见下节）。

## 移动端 / PWA（iPhone App 形态）

手机（iPhone 15 Pro Max，430×932 @3x）走独立的移动端实现，目标是「添加到主屏后像一个原生相册 App」。

**分流**：`src/views/GalleryView.vue` 只做分流，`GalleryView` → `MobileGallery` 或 `DesktopGalleryView`。桌面端组件从原 `GalleryView` 平移而来，行为未改。空间详情页（`SpaceDetailView.vue`）同样分流：移动端用 `MobileSpacePanel` + `MobilePhotoViewer`，桌面端保持原样。

**结构**

| 文件 | 作用 |
| --- | --- |
| `src/mobile/MobileGallery.vue` | 移动端总壳：顶栏（图库/收藏/Live 标题 + 大标题）、年/月/日/全部照片段控件（滑块 pill）、搜索、选择模式、数据源（公开图库 / 收藏 / Live / 搜索结果）、上传与安装提示、编辑/扩图/删除接线 |
| `src/mobile/MobilePhotoGrid.vue` | 原生滚动网格：2–6 列、双指捏合改列数（FLIP + 锚点保持）、长按 420ms 进选择、拖选跨卡区间、距边自动滚动、组头 sticky、距底 800px 自动加载 |
| `src/mobile/MobilePhotoViewer.vue` | 查看器：左滑/右滑翻页（橡皮筋 + 阈值）、双击 1↔2.5×、双指 0.7–4× 锚点缩放、1× 下滑关闭（背景渐隐 + 缩小）、点按隐藏控件、缩略图↔大图位置衔接、Live 长按播放、信息面板、操作面板、键盘 Esc/←/→/i |
| `src/mobile/MobileSpacePanel.vue` | 空间详情的移动外壳：返回 + 标题 + 副标题 + 上传/设置、`MobilePhotoGrid` 网格、底部 tab |
| `src/mobile/MobileTabBar.vue` | 底部五项 tab：图库 / 收藏 / Live / 空间 / 我的；`hidden` 属性让它弹簧下滑（选择模式用） |
| `src/mobile/MobileSelectionBar.vue` | 多选工具条：已选数量 + 分享 / 收藏 / 删除 / 完成 |
| `src/mobile/InstallHint.vue` | iOS「添加到主屏」引导（只在 Safari、非独立运行时出现一次，关闭后写 `nocturne.install.hint=off`） |
| `src/mobile/spring.ts` | 弹簧动效内核：`SPRING` 预设（response/damping）+ `springCss()` 生成 CSS `linear()` 缓动 + `runSpring()` 手势收尾 |
| `src/mobile/types.ts` | `Rect` / `measurePhoto(id)`（取卡片位置，做缩略图↔大图衔接） |
| `src/pwa/bootstrap.ts` | 标记 `html.standalone` / `data-display-mode`；按条件注册 Service Worker；`lockPageZoom()` 拦 iOS 页面级双指缩放 |
| `public/manifest.webmanifest` | standalone、start_url `/gallery`、主题纯黑、192/512 + maskable 图标 |
| `public/icons/`、`public/splash/` | 应用图标与 10 张 iOS 启动图（1290×2796 等），由 `scripts/build-pwa-assets.mjs` 用本机 Chrome 生成 |
| `public/sw.js` | 手工写的 Service Worker：缩略图/原图 cache-first、其它 API network-first、导航回退 index.html、静态资源 stale-while-revalidate |

**手势实现要点**：全部用 Pointer Events，网格容器 `touch-action: pan-y`（1 指交给原生滚动，2 指自己接管缩放）；查看器里 1 指 >7px 才判定方向（横向翻页 / 纵向下滑关闭 / 放大后平移），双击判定 280ms 内、位移 <40px。

**只缩放图片、不缩放页面**：三处配合 —— ① `index.html` 的 viewport 写死 `maximum-scale=1, user-scalable=no`；② `mobile.css` 里 `html, body, .m-app, [data-scroll-root]` 全部 `touch-action: pan-y`，`.m-viewer` 内 `touch-action: none`；③ `bootstrap.lockPageZoom()` 拦掉 iOS Safari 独有的 `gesturestart / gesturechange / gestureend`。实测（CDP 触摸模拟）：双指捏合时 `visualViewport.scale` 恒为 1、`innerWidth` 不变，只有网格列数 4→2 / 查看器 `canvas` 的 `scale()` 变化。

**动效（Apple 弹簧）**：按 WWDC18「Designing Fluid Interfaces」只保留 `response`（秒）与 `damping`（阻尼比）两个参数，`spring.ts` 解析阻尼振子 `x(t)=1-e^{-ζω₀t}(cos ω_d t + ζω₀/ω_d·sin ω_d t)`，采样成 CSS `linear(0, …)` 交给浏览器插值（可中断、无 JS 逐帧）。当前 8 组预设（response / damping）：hero 0.36/0.78、page 0.30/0.82、zoom 0.30/0.72、sheet 0.34/0.80、bar 0.28/0.74、press 0.18/0.52、grid 0.32/0.84、fade 0.24/1.0 —— 阻尼比压到 0.72–0.84 才有肉眼可见的回弹（0.88–0.9 读起来就是普通 ease，会被评价为"死板"）。`springCss()` 采样点从 48 提到 **120**：504ms 的曲线原来每段 12.5ms，在 120Hz ProMotion 上每段只跨约 1.5 帧，折线感明显；120 点后 4.2ms/段。`mobile.css` 的 `:root` 由 `scratch/gen-vars.cjs` 生成并粘贴（`--spring-*` / `--dur-*`：hero 504ms、page 429ms、zoom 483ms、sheet 483ms、bar 392ms、press 396ms、grid 454ms、fade 358ms）。**按压反馈单独走快通道**：所有 `:active` 规则写死 `transition-duration:90–110ms; ease-out`，松手才回到弹簧（否则按下去也要 396ms 才到位，等于输入延迟）。

**滚动流畅度**：移动端**不使用任何 `backdrop-filter`**（此前 8 层毛玻璃：顶栏、段控件、sticky 组头、tab、查看器上下栏、信息面板、操作面板、选择条）。滚动时相邻每帧都要重算模糊 + 重新光栅化整块区域，是掉帧主因；同一构建内做 A/B（注入 vs 移除模糊层，6x CPU 节流）：fps 51.7 → 70.8、帧间隔 p50 12.7ms → 6.2ms。现在改用高不透明度纯色（`rgba(8,8,9,.88)` / `rgba(18,18,20,.94)` / `rgba(28,28,30,.98)`）+ 1px 发丝线，视觉上接近 iOS 的深色工具栏但零模糊成本；桌面端玻璃拟态保持原样。另外：① 缩放标题随滚动的位移/透明度改为 `rAF` 内直接写 `el.style`（不再进 Vue 渲染队列）；② 选择模式下 `.m-body` 不再动画 `bottom`（布局属性动画会每帧重排整块网格），只保留 tabbar 自身的弹簧；③ `.photo-card` 加 `contain: layout paint style` 隔离重排。

**白屏 / 首屏速度**：之前 `index.html` 引了 `fonts.googleapis.com`（render-blocking），国内网络拉不动会一直等 —— 实测首绘 **57.9 秒**。已彻底移除 Google Fonts，改系统字体栈（`-apple-system / SF Pro / Didot / Songti SC`）；`index.html` 内联极简首屏骨架（纯 CSS 金色圆环 + NOCTURNE，无外链），`main.ts` 挂载后淡出移除；`stores/gallery.ts` 的首屏改为「第 1 页立刻渲染，2–6 页后台预取后追加」，不再等 6 页全部返回。改后生产实测（4 Mbps / 120 ms RTT 节流）：首绘 1.2 s、首屏 20 张卡片 3.5 s 内。

**Service Worker 何时开**：`bootstrap.registerServiceWorker()` 要求 `import.meta.env.PROD && window.isSecureContext`（即 HTTPS 或 localhost），并且 `localStorage['nocturne.sw'] !== 'off'`。当前生产站是纯 HTTP，所以 SW 不会注册、页面不会缓存——这是刻意设计，等挂上域名 + TLS 后自动生效；要临时关闭可在控制台写 `localStorage.setItem('nocturne.sw','off')`。

**iPhone 验收步骤**：Safari 打开 <https://pic.raylx.xyz/gallery> → 点底部「分享」→「添加到主屏幕」→ 从主屏图标冷启动，应看到纯黑启动图后直接进图库，没有浏览器地址栏。

## 目录

```text
src/
  api/         请求封装、类型、图片/空间/用户接口
  components/  AppShell、PhotoGrid、PhotoViewer、上传/编辑/批量/AI/协同组件（桌面端）
  mobile/      MobileGallery / MobilePhotoGrid / MobilePhotoViewer / MobileSpacePanel / MobileTabBar / MobileSelectionBar / InstallHint / spring.ts（弹簧）/ types.ts
  pwa/         bootstrap.ts（独立运行标记 + SW 注册）
  composables/ 收藏、是否移动端、快捷键等组合式逻辑
  router/      路由与登录/管理员守卫
  stores/      Pinia：用户、图库、空间、UI
  styles/      app.css 基础控件 + nocturne.css 视觉系统 + mobile.css 移动端变量与弹簧变量
  utils/       媒体 URL、格式化、存储键
  views/       页面
design-samples/  b-library.html 设计样例（60 张，独立静态页）
shots/           验收截图
scratch/         验收脚本、数据清理与调试工具（不参与构建）
```

## 关键实现说明

- **雪崩 ID**：后端 Jackson 把 19 位 Long 输出为 JSON 数字，JS 会丢精度。`src/api/request.ts` 在 `JSON.parse` 前把 `id/userId/spaceId/pictureId/taskId` 等字段的 16 位以上整数转成字符串；新增接口字段时记得同步 `LONG_ID_KEYS`。
- **自动加载**：底部不再有「加载更多」按钮。首屏第 1 页回来立刻渲染，第 2–6 页在后台并行预取后一次性追加（不阻塞首屏）；本机 69 张图库会直接完整出现，生产 1000 张图库首屏约渲染 138 张。**滚动中不插新卡片**：`scroll` 事件只记 `lastScrollAt`，`checkLoadMore` 用 rAF 合并，手指停下 140ms（`LOAD_IDLE_MS`）后才触发下一页，整页一次插入——分帧插入会让每一帧都重排整块网格，比一次插入更卡。
- **网格缩略图**：网格不直接加载 1280px 预览图，而是走 `/api/picture/thumb?key=...&w=<档位>`，由原生 `<img>` 配合前 12 张 `loading="eager"`、其余 `loading="lazy"` 与 `decoding="async"` 渲染。档位按「列宽 × DPR」算但 **DPR 上限 2**（430px 屏 4 列取 240px 档）：DPR3 会让每张解码内存涨到 2.25 倍，滚动中更容易被反复丢弃重解码。图片加载失败只做 DOM 级兜底（`imageStage` 刻意不是响应式），一次 error 不会触发上百张卡片重渲染。滚轮直接按 4 档缩放缩略图（不再使用 `Ctrl/⌘`，因此不会触发浏览器页面缩放；每次缩放带 FLIP 过渡）；网格列宽按容器自动等分铺满，右侧不留黑边；`Shift + 滚轮` 在网格内翻页。
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
node scratch/check-paging-images.mjs <base> mobile|desktop  # 每次加载增量、图片失败数、破图/alt 检查
node scratch/verify-prod-pan.mjs    # 生产站拖拽惯性验收

# 移动端 / PWA（Chrome headless + iPhone 430×932 @3x 触摸模拟）
node scratch/verify-mobile.mjs   http://127.0.0.1:5173   # 启动度量、PWA 资源、sticky、双指列数、多选、tab 切换
node scratch/verify-viewer.mjs   http://127.0.0.1:5173   # 查看器：打开、双击、点按隐藏控件、信息面板、收藏、下滑关闭
node scratch/verify-paging.mjs   http://127.0.0.1:5173   # 左右滑翻页、双击 2.5×、双指 4×/回 1×、下滑关闭
node scratch/verify-drag.mjs     http://127.0.0.1:5173   # 长按后拖选 12 张、完成退出、双指 2 列
node scratch/verify-desktop.mjs  http://127.0.0.1:5173   # 桌面端回归（118 卡片 / dock / 查看器 / 缩窄切移动端）
node scratch/verify-prod.mjs     https://pic.raylx.xyz  # 生产站移动端验收
node scratch/verify-prod-desktop.mjs https://pic.raylx.xyz  # 生产站桌面端回归
node scratch/shots-mobile.mjs    http://127.0.0.1:5173   # 出图（deviceScaleFactor=1 才能截图）
node scratch/verify-mobile2.mjs  http://127.0.0.1:5173   # 页面级捏合不缩放 + 网格列数变化
node scratch/verify-shell2.mjs   <base> <账号> <密码>     # 选择模式 tabbar 下滑、空间列表/设置页 headless 壳、空间详情
node scratch/verify-hero.mjs     <base> <账号> <密码>     # 缩略图→大图 hero 衔接（图库 + 空间）
node scratch/perf2.mjs           <base>                  # 4Mbps/120ms 节流下的 FCP 与首屏卡片时间（写 shots/perf2.json）
node scratch/frames.mjs          <base>                  # 帧间隔探针：6x CPU 节流下纵滚/横滑的 fps、p50/p95、超 50ms 帧数
node scratch/frames2.mjs         <base>                  # 同上 + Long Animation Frame（loaf）拆分
node scratch/frames3.mjs         <base>                  # 同一构建内做「有模糊 / 无模糊」A/B 对比
node scratch/frames4.mjs         <base>                  # 同 frames3，但先关安装提示再测（遮罩会吃掉触摸，是方差来源）
node scratch/scroll-cost.mjs     <base>                  # Performance.getMetrics + LoAF：滚动期间的 task/script/layout 耗时与长帧明细
node scratch/probe-thumbs.mjs    <base>                  # 网格缩略图 ok/failed/pending 与请求 host 分布
node scratch/probe-profile.mjs   <base>                  # CDP Profiler 200µs 采样，滚动时 JS 自身耗时排行
node scratch/check-loadmore.mjs  <base>                  # 滚到底后「立刻」与「停稳后」的卡片数，验证停稳才分页
node scratch/check-dismiss.mjs   <base> <账号> <密码> <空间id>
node scratch/probe-space-media.mjs <base> <账号> <密码> <空间id>   # 空间详情页图片的 currentSrc/naturalWidth 与网络状态（验证私密图走同源）  # 画廊与空间详情下滑关闭（滑完等 900ms 再判定）
node scratch/frames5.mjs          <base>                  # 分别测「首屏滚动 / 仍在加载 / 加载完」三段的 fps
node scratch/frames6.mjs          <base>                  # 同上，带 2Mbps/150ms 网络节流（本轮主度量）
node scratch/trace-layout.mjs     <base>                  # CDP Tracing 抓滚动期间的 layout/paint/layerize 耗时
node scratch/trace-layout2.mjs    <base>                  # 同上，对比「block 掉所有缩略图请求」前后的 layout/paint
node scratch/ab-cv.mjs            <base>                  # 运行时注入 content-visibility / -webkit-overflow-scrolling 做滚动 A/B
node scratch/ab-cv2.mjs           <base>                  # 只注入 content-visibility，并核对 scrollHeight 不塌
node scratch/check-cv-prod.mjs    <base>                  # 生产站核对 cv 生效、scrollHeight、已出图数量
node scratch/loaf-load.mjs        <base>                  # 4x 节流下采集加载期 long-animation-frame 归因
node scratch/vis-lazy.mjs         <base>                  # 懒加载覆盖：可见卡片数 vs 已出图数（并截图）
node scratch/frames-desktop.mjs     <base>                  # 桌面 1440×900 帧间隔探针（DPR1、不节流）
node scratch/frames-desktop2.mjs    <base> [cpu] [w] [h] [dpr]  # 桌面「加载期滚动」度量（默认 4x CPU、1920×1080@2、10Mbps/100ms）
node scratch/desk-interact.mjs      <base>                  # 有头 Chrome（真实 GPU）：滚轮缩放 / Shift+滚轮 / 拖滚动条 / hover 下滚动
node scratch/probe-scroll-layout.mjs <base>                 # 包装 scrollHeight/clientHeight/scrollTop 的 getter，量每次 scroll 的强制布局耗时
node scratch/verify-desktop-lazy.mjs <base>                 # 桌面懒加载：首屏与滚到底的 withSrc、视口出图、滚轮缩放、查看器
node scratch/diag-lazy-scroll.mjs   <base> [nocv]           # 慢滚后「可见 / 出图」覆盖；nocv 用 content-visibility:visible 做 A/B
node scratch/diag-lazy-desktop.mjs  <base>                  # 逐卡 src / currentSrc / complete 诊断
node scratch/verify-desktop-search-select.mjs <base>        # 桌面搜索分页（59→258 卡）与 Shift 拖框选
node scratch/net-waterfall.mjs    <base>                  # 首屏图片请求的 host 分布、bytes、耗时分位（桌面 1440×900）
node scratch/net-proto.mjs        <base>                  # CDP Network 事件：图片请求的协议（h2/http1.1）、状态码与起止时间
node scratch/load-timeline.mjs    <base> [cpu] [mbps] [lat] # 加载期：图片到达分位 + LoAF 长帧数与总时长（默认 6x CPU / 2Mbps / 150ms）
node scratch/cdn-concurrency.mjs  <base>                  # CDN 单张/并发图片加载探针
node scratch/thumb-sizes.mjs      <base>                  # 列表实际取到的缩略图 URL、尺寸与像素宽
node scratch/check-prefetch.mjs   <base>                  # 首屏预取 link 的 media 与档位（手机 w240 / 桌面 w360）
```

移动端验收（2026-11-06，iPhone 430×932 DPR3 触摸模拟）：
启动后 118 张卡片、4 列 106×106、顶栏 52px、tab 五项「图库/收藏/Live/空间/我的」、段控件「年/月/日/全部照片」、无横向溢出；manifest 200 且 `Page.getAppManifest` 零错误，icon/splash 全部 200；滚动 1500px 组头吸顶在 96px 且大标题淡出；双指张开 100→340px 变 2 列（214px），捏回 340→100px 变 6 列（70px）；长按 0.7s 进选择并拖选到第 12 张后工具条显示「已选择 12 张」，完成后 tabbar 恢复；查看器单击打开（日期 2026年11月3日 / 22:54 / 五项工具），左滑连续两次换图、右滑回退，双击 2.5×、双指到 4×、双击复位 1×，下滑关闭后网格与 tabbar 复原；收藏写入 localStorage 后「收藏」tab 显示 1 张。生产站同样项目全部通过，控制台 0 error，桌面端 1440×900 仍是 118 卡片 + dock + 查看器，`/spaces` 未登录仍跳 `/login`。

移动端动效与空间页验收（2026-11-08，生产站 430×932 DPR3 触摸模拟）：长按 0.7s 进选择 → `.m-tabbar` 变 `is-hidden` 且 `translateY(50px)`（弹簧下滑），点「完成」恢复；`/gallery` 与 `/spaces/2119096355873230850` 点卡片后 90ms 都能抓到 `img.hero`，其 `transition` 为 `transform 637ms linear(0, …)`（弹簧曲线而非 ease）；查看器 430×932 满屏、点返回按钮关闭并复原 body 滚动；`/spaces`、`/settings` 在 430px 下顶栏隐藏、`.library` 贴顶 13px、单列 394px 卡片、底部 tab 49px「图库/收藏/Live/空间/我的」、原生 tab 高亮 `rgb(10,132,255)`、无横向溢出；空间详情 `.m-app` + 3 张卡片 + tabbar。性能（4Mbps/120ms 节流）：FCP 1208ms、首屏 20 张卡片 3464ms（改造前同条件首绘 57.9s）。下滑关闭复验（`check-dismiss.mjs`，滑完等 900ms）：画廊与空间详情都从 `viewer true` 变 `false`、`body.overflow` 复原（`verify-prod-mobile.mjs` 的 `spaceDismiss`/`dismissAfterPinch` 是滑完立刻查、没有等待，显示 open=true 属脚本时序假象）。

移动端流畅度验收（2026-11-08，生产站，Chrome headless 430×932 DPR2 + 触摸模拟 + **6x CPU 节流** + 软件光栅，绝对值不代表真机，只用于相对比较）：改造前滚动 fps 49.7 / p50 14ms / p95 46.2ms / 最坏帧 317.7ms / 超 50ms 帧 8 个；改造后同一 harness 两轮为 fps 72.2（p50 9ms、p95 37.5、最坏 77.3、超 50ms 6）与 fps 43.9（p50 17、p95 50.9、最坏 213.9、超 50ms 10），横滑 fps 103–107 / p95 24–28ms。**同一构建多次运行方差很大（第二轮是离群），所以只声明「中位数明显下降、最坏帧从 318ms 降到 77–214ms、页面内 `backdrop-filter` 层数清零」**；同构建 A/B 才是干净证据：模糊 ON → fps 51.7 / p50 12.7ms，模糊 OFF → fps 70.8 / p50 6.2ms。真机手感仍需在 iPhone 上确认。

最近一次本机结果：首页 hero 1600px 图完整加载、图库 69 张 / 桌面 176px 7 列、搜索“山谷”2 张、查看器 69 张胶片条、移动端 390px 3 列且无横向溢出、滚轮 176→238→300px（4 档）、长按框选 4 张、空间与设置页保留原外壳、控制台 0 error。

生产站 1000 张示例验收：公开图库 total=1000、首屏 118 张卡片、缩略图 7 列 / 176px、搜索「随机示例-0500」1 张、查看器主图 1600px、移动端 390px 3 列且无横向溢出、控制台 0 error。

生产站 4 档 + 铺满验收（2026-11-04）：`/assets/index-DAR-H9AY.js`；滚轮 2 次 176→238→300px，画面单调过渡、无 console error；300px 档 4 列、列宽 340.75px、rightGap=0，网格宽 1384px；拖拽 scrollTop 300→982，惯性收尾约 2s，帧间隔 p50 5.6ms / p95 5.9ms / max 44.5ms，松手后继续滑行到 982。

## 滚动手感专项（2026-11-09）

用户反馈「上下滑动帧数低」。按影响从大到小查到三层原因，全部已修并上线：

1. **缩略图 401 风暴（真凶）**：R2 的 S3 令牌在 Cloudflare 侧失效（服务器上直连 S3 打同一对象返回 `401 Unauthorized`，公网域名同 key 却是 200）。后端 `R2Manager.download` 抛错后被全局异常处理器包成 **HTTP 200 + 57 字节 JSON**（`读取图片失败`），nginx `proxy_cache_valid 200 7d/30d` 又把这份错误体当图片缓存下来 → 一张都读不出来。前端每次 `IMG onerror` 还会改响应式的 `imageStage`，**一次失败重渲染整块网格**，LoAF 里全是 `IMG[…].onerror` 单条 106–329ms。
   修法：`R2Manager` 读失败回退 Cloudflare 公网域名（`downloadFromPublicUrl`，5s/30s 超时、非 200 抛 IOException、流关闭时断开连接）；`/picture/thumb` 与 `/picture/file/**` 失败改返 **500 + `Cache-Control: no-store`**（`writeImageError`），不再污染 nginx 缓存；清掉 `/var/cache/nginx/img` 里已中毒的条目；前端 `imageStage` 改成非响应式 + 只改 DOM，兜底一律同源。
2. **非 passive 的 touchmove**：网格原本全局 `{ passive: false }` 监听 touchmove（只给双指缩放/拖选做 preventDefault），iOS 每次触摸都要等主线程跑完才滚动。改成默认 passive，仅在 `is-pinching`/拖选/长按后临时 `setAggressiveMove(true)`，结束时切回。
3. **滚动帧里的额外工作**：`scroll` 里同步读 `scrollHeight - scrollTop - clientHeight`（强制布局）→ 改 rAF 合并 + 记 `lastScrollAt`；分页按 4 张一帧插入 → 改停稳 140ms 后整页一次插入；缩略图按满 DPR 取（4 列 322px）→ DPR 上限 2（240px 档）；`thumbUrl` 每次渲染都 `new URL()` → 按 (id, 档位, stage) 记忆化。

度量（生产站，Chrome headless 430×932 DPR2 + 触摸模拟 + 6x CPU 节流 + 软件光栅；绝对值不代表真机，只看相对）：

| 指标 | 修复前（坏图） | 修坏图后 | 再加 p6 优化 |
| --- | --- | --- | --- |
| `scroll-cost` taskSec | 7.308 | 3.598 | 3.33 / 3.82 / 4.05 |
| LoAF 条数 / 总时长 | 27 / 4028ms | 7 / 955ms | 4 / 279ms、3 / 326ms、9 / 598ms |
| LoAF 内容 | 全是 `IMG.onerror` 106–329ms | 分页 JSON + 插入卡片 | 同上，已无 image error |
| `probe-thumbs` | ok 8 / failed 32 / pending 98 | ok 76 / failed 0 | failed 0（138/138 同源） |

`probe-profile`（200µs 采样、6 次纵向滚动）在记忆化之后 `URL` 已掉出前 15，图片 `178/178 complete`，JS 自身占用最高的一项从 2.4% 降到 1.2%，其余是 Chromium 的 native（布局/绘制/解码）。

部署与回滚：前端 `web.prev-memo-20261109`，后端 `app.jar.prev-r2fallback-20261109`（均在 `/opt/a-picture`）。

### 第二轮：图片加载期（2026-11-09 晚）

用户复测仍卡，且「特别是还有缩略图在加载的时候」。上一轮修的是坏图风暴，这一轮是**一次把 138 张缩略图塞进网络/解码队列**本身的成本。

- 先试 `content-visibility:auto`（`@supports` 包住；卡片本身有 `aspect-ratio:1/1`，被跳过的卡片不塌高，`scrollHeight` 恒为 3778）。运行时注入 A/B 数据很好看（帧率 51/34 → 123/152），**但上线后生产数据毫无变化**（frames6 p1 48.5/31.4、p2 41.1/40.9，与上线前一致）。原因是 `content-visibility` 只跳过布局与绘制，**不会阻止被跳过子树里 `loading="lazy"` 的图片继续下载/解码**，138 张照样全进队列。这一条保留（对渲染无害、省离屏布局），但不是解药。
- 真正的修法：`v-lazy-src` 指令 + `IntersectionObserver`（root = 滚动容器，`rootMargin: '900px 0px'`），只有滚到视口上下各约一屏的图才设 `src`；档位变化时已出图的立即换源，未出图的继续排队。首屏一次性设 `src` 的图片从 138 张降到 64 张。
- 度量（frames6，6x CPU 节流 + 2Mbps/150ms，两轮一致）：首屏滚动 fps 48.5/31.4 → **108/104**，最坏帧 393.9/200.8ms → **96.8/96.4ms**，超 50ms 帧 15/27 → **3/3**；「仍在加载」阶段 41.1/40.9 → **152.5/163.5**，超 50ms 帧清零。
- 出图覆盖（`vis-lazy.mjs`）：顶部可见 40 张全部出图、无空白格；快滑两屏后 1.2s 内 49/52 可见卡片出图。
- 部署与回滚：前端 `web.prev-lazy-20261109`（上线前含 cv 的版本是 `web.prev-cv-20261109`），入口 `assets/index-DrWf8Tj6.js`。

### 第三轮：桌面端（2026-11-09 晚）

用户反馈「电脑上手感还是很差，帧数低」。桌面端 `PhotoGrid` 当时还没吃到移动端第一轮的优化，用 `frames-desktop2.mjs`（1920×1080 DPR2 + 4x CPU + 10Mbps/100ms + 禁缓存）复现：首屏滚动 **15.2fps**、p95 386ms、最坏帧 946.7ms、`taskSec` 16.5s；「仍在加载」14.7fps。

- 根因（`probe-scroll-layout.mjs` 给 `scrollHeight/clientHeight/scrollTop` 的 getter 包计时）：`onScroll → maybeAutoLoad` **每条 scroll 事件同步读 `scrollHeight`**，触发强制布局，单次要 70–141ms；卡片越多越慢（218/398 张时单条 scroll 任务 200–470ms）。次要项是每条 scroll 事件都切 `body.scrolling` 类，造成全树 style 重算（styleCount 142–198）。
- 修法与移动端同款：①删掉 `maybeAutoLoad`，改列表末尾哨兵 `<div ref="loadSentinel">` + `IntersectionObserver`（root = 滚动容器，`rootMargin: '0px 0px 700px 0px'`），保留 hasMore/loadingMore/框选/冷却判断，并加「滚动未停稳 140ms 内不加载，90ms 后重试」；②`onScroll` 只记 `lastScrollAt` + 首次加 `body.scrolling`（150ms 后移除）；③懒加载抽成共用指令 `src/directives/lazySrc.ts`（`v-lazy-src`，滚动根由 `[data-scroll-root]` 查找），移动端与桌面共用一份；④`.card` 加 `@supports (content-visibility:auto){content-visibility:auto}`（**不加** paint containment，否则 hover 的 box-shadow 会被裁掉）。
- 度量（frames-desktop2，生产站同一 harness）：首屏滚动 fps 15.2 → **138.5**（p95 386→16ms、最坏 946.7→64ms、超 50ms 帧 57→2、`taskSec` 16.512→1.734）；「仍在加载」14.7 → **170.5**（p95 330→7.6ms、`taskSec` 14.7→1.27）；加载完 121.5。残留长帧只有首屏 `Response.text.then` 194/77ms（模块与接口解析，与滚动无关）。
- 桌面懒加载与交互（`verify-desktop-lazy.mjs`，1440×900）：首屏 138 卡中只给 63 张设 `src`（42 张已出图），滚到底 298 卡；视口出图 35/35；滚轮缩放 176→238px；点卡片开查看器、Esc 关闭；0 error。慢滚 `diag-lazy-scroll.mjs`：松手瞬间 26 可见/2 出图，2s 内 35/35 全部出图（Vite 代理下要 10–12s，慢在代理链路而非懒加载）。搜索分页与框选（`verify-desktop-search-select.mjs`）：搜索「示例」59 卡 → 继续下滑 258 卡（总数 1000），Shift 拖框选 3 张、进入选择模式、完成退出。
- 移动端回归（共用指令后）：`frames6` p1 110.9 / p2 160.5 / p3 129.6 fps（与上一轮 108/104、152/163、138 持平或更好），`verify-prod`、`verify-drag`、`check-loadmore`、`check-dismiss`、`verify-prod-desktop` 全过。
- 部署与回滚：入口 `assets/index-D4N60Sos.js`，`/opt/a-picture/web.prev-desktoplazy-20261109`（上一版 `web.prev-lazy-20261109`）。

### 第四轮：每批更多 + 去掉破图闪现（2026-11-09 夜）

用户反馈「往下翻的时候每一次加载的图片太少，而且会有一瞬间的图片报错，显示图片名称和坏掉的图片提示」。

- 「每次太少」：后端 `listVOByPage` 把 `pageSize` 卡死在 20（防爬），一屏 4 列能显示 35 张，所以一次只来半屏。改法不动后端限制：`stores/gallery.ts` 加 `LOAD_BATCH = 3`，一次触发并发拉 3 页（20×3）再一次性追加。实测每次 `+60`：178 → 238 → 298 → 358（`check-paging-images.mjs`，移动端与桌面端一致）。
- 「破图一闪」：库里 62 条记录（`public/2117476515809546242/` 下）只有 preview，**三档缩略图一个都没有**，CDN 直接 404，浏览器在换源成功前会画出「破图图标 + 文件名（alt）」。两步修：①把这 186 个变体（62×240/360/640）用 `/api/picture/thumb?key=…&w=…` 现场渲染并回写 R2（后端 `healAsync`），再 purge 对应 CDN URL；②前端两个网格改成「加载完成前 `opacity:0`、成功后加 `.is-loaded`」，并把 `alt` 置空（卡片本身有 `aria-label`），失败时先摘掉 `.is-loaded` 再换下一档，破图在视觉上完全不可见。
- 踩坑：`purge_cache` 用错 zone（`<credentials-file>` 里的 `CF_ZONE_ID` 是 raylx.xyz，图片站是 raylxpj.us.ci，zone id `<redacted-zone-id>`），purge 返回 success 但 CDN 仍 HIT 404；换对 zone 后立刻 200。
- 度量（frames6，6x CPU + 2Mbps/150ms，三轮中位数）：p1 93.3 / p2 116.4 / p3 133.8 fps，p95 17–28ms。比上一轮（108 / 152 / 138）略低是因为首屏 DOM 从 138 张涨到 178 张（多了一次 60 张的批量追加）；先给 `opacity` 配 `.18s` 过渡时 p2 只有 92.7fps，去掉过渡后回到 116.4，说明几百张同时做透明度过渡本身也要钱。
- 验收：`check-paging-images.mjs` 移动/桌面均 `failedImg 0 / loadingFailed 0 / broken 0 / badAlt 0`；`verify-prod`（178 卡、查看器、收藏/Live）、`verify-prod-desktop`（178 卡、dock）、`verify-drag`（拖选 12 张、双指 2 列 214px）、`check-dismiss`（画廊与空间都能下滑关闭）全过。
- 部署与回滚：入口 `assets/index-Cy5dJu2E.js`；`/opt/a-picture/web.prev-paging2-20261109`（第一版带过渡的是 `web.prev-paging-20261109`）。

### 第四轮：缩略图主通道切 CDN（2026-11-09 傍晚）

用户先问「这类图库站的最佳实践是什么，是不是一开始呈现方式就选错了」。调研结论：Google Photos 的公开做法是「布局与渲染分离 + 只渲染视口内的行（虚拟化）+ 多档尺寸」，Flickr 的 justified 布局在**服务端预生成**（HTML 一到就知道该请求哪一档），Immich 每个资源预生成三档变体、并明确「缩略图调大会降低响应速度」。也就是说呈现方式没错，瓶颈在**缩略图交付链路**。

- 实测（生产站，桌面 1440×900，真实网络不节流）：首屏 63 张缩略图全部走同源 `/api/picture/thumb`，`nextHopProtocol: "http/1.1"`，**mean 955ms / p50 918 / p95 1777 / max 1899**；单张命中 nginx 缓存也要 191ms（≈RTT 190ms）。合成 A/B（138 张、430×932 DPR2、4x CPU、2Mbps/100ms、冷缓存）：同源 w240 11,013ms / 4,894ms，CDN w240 **1,672ms / 1,527ms**。
- 对象核对：R2 里缩略图**早就预生成好了**，只是键名是 `<stem>_preview_w240/w360/w640.jpg`（后端 `uploadThumbnails` 拿的是 preview 键），CDN 上 `cf-cache-status: HIT`、`cache-control: max-age=31536000, immutable`；`space/` 前缀的对象没有缩略图。
- 改动：`src/utils/media.ts` 的 `thumbUrl()` 主通道改成 CDN（`https://img.raylxpj.us.ci/<key>_w<n>.jpg`），原同源接口保留为 `originThumbUrl()` 作兜底；`PhotoGrid` 与 `MobilePhotoGrid` 的降级链变成「CDN 缩略图 → 同源 /api/picture/thumb → 同源 /api/picture/file 预览图 → 空白」；`index.html` 加 `preconnect`（crossorigin），首屏预取按视口出两档（`(max-width: 820px)` → w240，桌面 → w360），不再预取另一档再重下；`SpacesView`/`SpaceDetailView` 封面改 `originThumbUrl`（space 对象在 CDN 上没有缩略图，会 404）。
- 上线后（`net-waterfall.mjs`）：首屏图片请求 **100% 来自 `img.raylxpj.us.ci`，`/api/picture/thumb` 请求数 0**，**avg 232ms**（两次采样 231/232ms，改前 mean 955ms）；`net-proto.mjs` 抓 CDP Network 事件：70 个图片请求全是 **h2**、全 200，首个 451ms 发出、末个 1770ms 收完（40Mbps/30ms 模拟）。
- 试过又回退的：在 `lazySrc.ts` 里加「最多 8 张并发」的闸门排队设 `src`。合成测里反而更差（长帧 7–13 条、总 4.1–4.9s，且首屏图数从 70 掉到 34–41），因为 h2 下并行本就该放开、排队只是把到达时间串行化，已回退为 IO 直发。
- 回归（生产站）：`verify-prod` 138 卡 + errors []；`check-loadmore` 138→218→258；`check-dismiss` 画廊 viewer true→false 且 overflow 复原；`verify-drag` 长按拖选 12 张 + 双指 2 列 214px；`verify-prod-desktop` 138 卡 + dock + errors []；`vis-lazy` 首屏 40 可见 40 出图、滚动后 52/52。
- 度量口径提醒：本轮之后合成帧率（`frames6` 等）在本机波动极大（同一构建 26.5–135.9fps，机器上同时跑着用户自己的 Chrome），**不作为结论**；能站住脚的证据是真实网络下的单张时延、host 分布与协议。
- 部署与回滚：入口 `assets/index-CDtQ2MHy.js`，`/opt/a-picture/web.prev-cdnthumb-20261109` 为切 CDN 前的版本（再往前的 `web.prev-queue-20261109`、`web.prev-prefetch-20261109` 是中间实验版本，可删）。

### 第五轮：私密空间改走私密桶（2026-11-09 夜）

用户给了新的 R2 S3 令牌，并提了第二条要求：**私密空间的图片不能进公共桶**。此前 `space/` 前缀的对象和公开图库共用 `apic` 桶，而 `apic` 挂了自定义域名 `img.raylxpj.us.ci`，等于任何拿到 URL 的人都能匿名读私密图。

- 凭据：截图 OCR 出来的 Secret 不可信——它是 64 位十六进制串，OCR 把其中 3 处认出大写 `C`，导致签名一直 403 `SignatureDoesNotMatch`；**全部转小写**后 `ListObjectsV2` 直接 200。旧的 `<redacted-ak>…` 那组已彻底失效（401）。
- 新桶：`apic-space`，不挂 custom domain、r2.dev managed 也关闭，因此**只能 S3 签名读**，没有公网入口。原 `apic` 里 38 个 `space/` 对象（21.4MB）逐个 CopyObject 迁入并 HeadObject 校验大小一致，随后从 `apic` 删除，再用 CF API `purge_cache` 把 38 个 CDN URL 全部清掉——现在 `https://img.raylxpj.us.ci/space/...` 一律 404。
- 后端：`R2Manager` 按 key 前缀选桶（`space/` → 私密桶，其余公开桶），`FileStorageManager` 新增 `getUrl(key)`（私密对象生成同源 `/api/picture/file/<key>`，公开对象仍给 CDN 域名），私密对象**绝不回退公网地址**；新增 `SpaceMediaGuard`（`userService.getLoginUser` + `spacePermissionHelper.checkSpaceAccess`，异常一律按无权限处理）。`/api/picture/file/**` 与 `/api/picture/thumb` 对 `space/` 前缀先鉴权：无权限统一 **404**（不泄露对象是否存在）+ `Cache-Control: no-store` + `Vary: Cookie`，私密缩略图不设 ETag、不走 304；公开路径行为完全不变。
- 前端：`media.ts` 识别 `space/` 前缀（`isPrivateUrl`），私密图片与缩略图一律改走同源，旧库里存量的绝对 CDN URL 也会被改写，所以**没有改数据库**；`MobilePhotoGrid` 放行 `/` 开头的相对地址。
- nginx：新增 `map $arg_key $private_thumb`（`~*^/?space(%2f|/)` → 1）与 `location ^~ /api/picture/file/space/`（`proxy_no_cache`/`proxy_cache_bypass`，响应头 `X-File-Cache: PRIVATE`），缩略图接口对私密 key 也 bypass 缓存；公开的 `/api/picture/file/`（30d）与 `thumb`（7d）缓存策略保持原样。
- 踩过的坑：浏览器请求缩略图时 key 是 URL 编码形态（`space%2F…`），最初的正则只匹配未编码的 `^/?space/`，实测 `X-Thumb-Cache` 仍是 `MISS`（等于缓存开着，管理员加载过的私密缩略图有被公共缓存命中的风险）。正则改为 `~*^/?space(%2f|/)` 后，两种形态都返回 `BYPASS`；公开缩略图仍 `MISS → HIT`。
- 验证（生产站）：匿名 `file` 404 / 管理员 200（422,517B）；匿名 `thumb` 404 / 管理员 200；`img.raylxpj.us.ci/space/...` 三条样例全 404；空间详情页两张卡缩略图全部同源 `/api/picture/thumb?key=space/…` 200（`naturalWidth` 240）。上传回归：公共上传 → CDN 200（163,731B）并可删除；空间上传 → 5 个对象（原图 + 预览 + w240/w360/w640）**全部落在 `apic-space`，`apic` 里没有副本**，匿名 404 / 管理员 200。
- 部署与回滚（`/opt/a-picture`）：`web.prev-spacemedia-20261109`、`app.jar.prev-spacemedia-20261109`、`nginx.conf.prev-spacemedia-20261109`、`application-prod.yml.prev-spacemedia-20261109`、`.env.prev-spacemedia-20261109`；前端入口 `assets/index-JXM4QNgA.js`。
- 新增脚本：`probe-space-media.mjs`（登录 → 打开空间详情 → 抓 `img` 的 `currentSrc`/`naturalWidth` 与同类资源的网络状态码）。

## 已知边界

- **R2 上传已恢复（2026-11-09 夜）**：新 S3 令牌（AK `<redacted-ak>…`）写入 `.env`，公共上传与空间上传都实测通过。旧令牌 `<redacted-ak>…` 已彻底失效，别再回滚到它。
- **私密空间图片没有 CDN 加速**：`space/` 前缀的对象只在 `apic-space` 私有桶里，前端一律走同源 `/api/picture/file/space/…` 与 `/api/picture/thumb?key=space/…`，既受服务器出口带宽约束，也不进 nginx 缓存（`X-File-Cache: PRIVATE`）。这是「私密不进公共桶」的必要代价。

- 收藏是本地偏好，不跨设备同步。
- 管理后台依赖后端 `/user`、`/picture`、`/space` 的管理员接口；普通用户访问 `/admin` 会被路由守卫送回图库。
- 协同编辑只对团队空间图片开放，且受后端单编辑锁限制；非团队空间不会建立 WebSocket。
- AI 扩图结果不会自动入库，需要在结果面板里手动保存。
- **移动端只在「窄屏或触摸设备」生效**：桌面浏览器把窗口拖窄到 860px 以下也会进移动端布局（`pointer: coarse` 也命中）。
- **Service Worker 目前不会生效**：生产站是纯 HTTP，`isSecureContext` 为 false。等挂域名 + TLS 后再验证离线缓存与二次启动速度。
- **无法做到的原生能力**：触感震动（iOS 无 Web API）、系统分享面板（`navigator.share` 在 iOS Safari 只在 HTTPS 可用，HTTP 下走「复制链接」兜底）、3D Touch 压感、照片编辑。
- **移动端刻意不用毛玻璃**：iOS 那种 `backdrop-filter` 模糊每帧都要重算，是滚动掉帧的主因（同构建 A/B：p50 12.7ms → 6.2ms）。移动端顶栏/tab/组头/面板一律用高不透明度纯色模拟，观感接近但不是真模糊；桌面端的玻璃拟态保持不变。
- **`/spaces`、`/settings` 是简化移动壳**：430px 下隐藏桌面顶栏与侧栏、内容改为单列 + 底部五项 tab，但卡片内部排版沿用桌面组件，不是逐像素的 iOS 复刻。
- **年/月/日分组在生产上只有一组**：示例图 1000 张的时间戳全为 2026-11-03 22:54，所以三档视图都能验证但没有多组样本。
- **网格仍是全量 DOM（2026-11-09）**：懒加载只控制图片下载/解码，卡片节点本身仍按分页全部保留（生产图库 1000 张时会累积到上千个节点）。桌面 138–298 卡实测已到 138–170fps；如果更弱的机器在几百张后仍掉帧，下一步是行虚拟化（DOM 只留视口 ±2 屏）。
- **Live 长按播放未在真实数据上验证**：生产库没有 Live 照片（`/live` 为 0 张），代码路径已写好但没有可复现样本。
