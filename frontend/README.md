# NOCTURNE 前端

`a-picture-backend` 的配套前端：**Vue 3 + Vite + TypeScript + Pinia + Vue Router**，零 UI 框架依赖（组件、设计系统、动效全部手写）。

视觉方向是「暗色私人档案馆」：金色细线、衬线大字、照片当主角，而不是常见的后台卡片墙。桌面端是一套键盘与鼠标高度优化的画廊，移动端是独立的 iPhone 相册式实现，可作为 PWA 添加到主屏。

## 启动

要求 Node.js 18+，并先启动后端（`http://127.0.0.1:8000`，统一前缀 `/api`，Session Cookie 鉴权）。

```bash
npm install
npm run dev        # http://127.0.0.1:5173
```

Vite 已把 `/api`（含 WebSocket）代理到后端，开发环境不需要处理跨域；目标地址可用 `VITE_API_TARGET` 覆盖。不要直接双击 `dist/index.html`——`file://` 下 History 路由与 Cookie 都不可用。

```bash
npm run build      # vue-tsc 类型检查 + 生产构建，产物在 dist/
npm run preview    # 本地预览构建产物
```

生产部署（Nginx 托管 + `/api` 反代 + WebSocket Upgrade + 图片磁盘缓存）见 [`../deploy/README.md`](../deploy/README.md)。

## 目录

```text
src/
  api/         请求封装（含大整数 ID 保护）、类型定义、用户/图片/空间/分析接口
  components/  AppShell、PhotoGrid、PhotoViewer、上传/编辑/批量/AI/协同组件（桌面端）
  mobile/      MobileGallery / MobilePhotoGrid / MobilePhotoViewer / MobileSpacePanel /
               MobileTabBar / MobileSelectionBar / InstallHint / spring.ts / types.ts
  pwa/         bootstrap.ts（独立运行标记、Service Worker 注册、拦截页面级缩放）
  composables/ 收藏、是否移动端、缩略图缩放等组合式逻辑
  directives/  v-lazy-src（IntersectionObserver 驱动的图片懒加载）
  router/      路由与登录 / 管理员守卫
  stores/      Pinia：用户、图库、空间、UI
  styles/      app.css 基础控件 · nocturne.css 视觉系统 · mobile.css 移动端与弹簧变量
  utils/       媒体 URL（档位吸附、私密地址改写）、格式化
  views/       页面
public/        manifest.webmanifest、sw.js、图标与 iOS 启动图
```

## 页面

| 路由 | 说明 |
| --- | --- |
| `/` | 品牌首页：全屏 hero、聚光灯、序言、正在展出、馆藏精选、私人入口；照片全部来自后端公开图库 |
| `/gallery` | 图库：纯图册布局，按月分组、月份标题吸顶、搜索、分类筛选、下滑自动加载、批量选择 |
| `/favorites` | 收藏：复用图册布局，读取 `localStorage` 里的收藏 ID（本地偏好，不写后端） |
| `/live` | Live 图片列表 |
| `/login` | 登录 / 注册（Session Cookie），注册后自动登录 |
| `/spaces` | 空间列表：我的空间、团队空间封面、容量与数量 |
| `/spaces/:id` | 空间详情：照片、成员、数据分析；团队空间支持上传、批量编辑、角色管理、协同编辑、AI 扩图 |
| `/settings` | 账号设置：身份、存储概览、收藏 / 空间快捷入口、退出登录 |
| `/admin` | 管理控制台：用户、图片、空间（仅 `userRole=admin`，守卫会拦下普通用户） |

查看器贯穿图库与空间：大图、胶片条、方向键切换、缩放、旋转、下载、信息、编辑、删除、AI 扩图。未登录访客可以浏览公开图库。

## 双端分流

`useIsMobile()`（`(max-width: 860px), (pointer: coarse)`）决定渲染哪套实现，两个页面级组件都只做分流：

- 桌面：`DesktopGalleryView` + `PhotoGrid` + `PhotoViewer`
- 移动：`src/mobile/*`（相册式交互）
- 空间详情同理：移动端 `MobileSpacePanel` + `MobilePhotoViewer`，桌面端保持原样

## 移动端 / PWA

目标是「添加到主屏后像一个原生相册 App」。

| 文件 | 作用 |
| --- | --- |
| `mobile/MobileGallery.vue` | 移动端总壳：顶栏与大标题、年/月/日/全部照片段控件、搜索、选择模式、数据源（公开图库 / 收藏 / Live / 搜索结果）、上传与安装提示 |
| `mobile/MobilePhotoGrid.vue` | 原生滚动网格：2–6 列、双指捏合改列数（FLIP + 锚点保持）、长按 420 ms 进选择、拖选跨卡区间、距边自动滚动、组头 sticky、距底 800 px 自动加载 |
| `mobile/MobilePhotoViewer.vue` | 查看器：左右滑翻页（橡皮筋 + 阈值）、双击 1↔2.5×、双指 0.7–4× 锚点缩放、1× 下滑关闭、点按隐藏控件、缩略图↔大图位置衔接、Live 长按播放 |
| `mobile/MobileTabBar.vue` | 底部五项 tab（图库 / 收藏 / Live / 空间 / 我的），选择模式时弹簧下滑隐藏 |
| `mobile/MobileSelectionBar.vue` | 多选工具条：已选数量 + 分享 / 收藏 / 删除 / 完成 |
| `mobile/InstallHint.vue` | iOS「添加到主屏」引导（仅 Safari、非独立运行时出现一次） |
| `mobile/spring.ts` | 弹簧动效内核：只留 `response` / `damping` 两个参数，采样成 CSS `linear()` 曲线 |
| `pwa/bootstrap.ts` | 标记 `html.standalone`、按条件注册 Service Worker、拦截 iOS 页面级双指缩放 |
| `public/sw.js` | 手写 Service Worker：图片 cache-first、其它 API network-first、导航回退 index.html、静态资源 stale-while-revalidate |

**手势**全部用 Pointer Events：网格容器 `touch-action: pan-y`（单指交给原生滚动，双指自己接管缩放）；查看器单指位移超过 7 px 才判定方向（横向翻页 / 纵向下滑关闭 / 放大后平移），双击判定 280 ms 内且位移 < 40 px。

**只缩放图片、不缩放页面**：`index.html` viewport 固定 `maximum-scale=1, user-scalable=no`；`mobile.css` 里 `html, body, [data-scroll-root]` 统一 `pan-y`、查看器内 `none`；`bootstrap.lockPageZoom()` 再拦掉 iOS Safari 独有的 `gesturestart / gesturechange / gestureend`。

**动效**：按 WWDC18「Designing Fluid Interfaces」只保留 response 与 damping，`spring.ts` 解析阻尼振子 `x(t)=1-e^{-ζω₀t}(cos ω_d t + ζω₀/ω_d·sin ω_d t)`，采样 120 点（4.2 ms/段，120 Hz 屏也不露折线感）交给浏览器插值——可中断、无 JS 逐帧。按压反馈单独走 90–110 ms 的快通道，松手才回弹簧。

**滚动流畅度**：移动端不使用任何 `backdrop-filter`（同构建 A/B：移除毛玻璃后滚动 fps 51.7 → 70.8、帧间隔 p50 12.7 ms → 6.2 ms），改用高不透明度纯色 + 1 px 发丝线；标题随滚动的位移/透明度在 rAF 内直接写 `el.style`；卡片加 `contain: layout paint style` 隔离重排。

**首屏速度**：移除 render-blocking 的 Google Fonts，改系统字体栈；`index.html` 内联纯 CSS 首屏骨架；首屏渲染第 1 页、第 2–6 页后台并行预取后追加。生产实测（4 Mbps / 120 ms 节流）首绘 1.2 s、首屏 20 张卡片 3.5 s 内（改前同条件首绘 57.9 s）。

**Service Worker 的开启条件**：`import.meta.env.PROD && window.isSecureContext`（HTTPS 或 localhost），可用 `localStorage.setItem('nocturne.sw','off')` 临时关闭。

## 关键实现说明

- **大整数 ID**：后端 Jackson 把 19 位 Long 输出为 JSON 数字，JS 会丢精度。`api/request.ts` 在 `JSON.parse` 前把 `id / userId / spaceId / pictureId / taskId` 等 16 位以上整数转成字符串；新增接口字段要同步 `LONG_ID_KEYS`。
- **自动加载**：没有「加载更多」按钮。首屏第 1 页回来立刻渲染，其余分页在后台并行预取；滚动中不插新卡片——`scroll` 只记时间戳，停稳 140 ms 后整页一次插入（分帧插入会让每帧都重排整块网格）。
- **网格缩略图**：不直接加载 1280 px 预览图，而是按「列宽 × DPR（上限 2）」吸附 240 / 360 / 640 三档；前若干张 `loading="eager"`，其余交给 `v-lazy-src`（IntersectionObserver，`rootMargin: 900px`）。降级链：CDN 缩略图 → 同源 `/api/picture/thumb` → 同源 `/api/picture/file` 预览图 → 空白。加载完成前卡片 `opacity: 0`，失败只做 DOM 级处理，绝不触发上百张卡片重渲染。
- **私密空间图片**：`utils/media.ts` 按对象键前缀识别私密对象，一律改写为同源地址（`/api/picture/file/`、`/api/picture/thumb`），库里存量的绝对 URL 也会被改写，因此无需改数据。
- **上传**：`FormData` 走 `request` 的 `rawBody`，不要手动设置 `Content-Type`，否则 boundary 会丢。
- **收藏**：只在 `localStorage` 的 `nocturne.favorites.v1`，属于本机偏好。
- **动效变量**：统一使用 `--ease-out / --ease-soft / --dur-*`；`prefers-reduced-motion` 会关闭或缩短动画。

## 性能优化记录

四轮改造，都是先用探针量化再动手（桌面 1440×900 / 1920×1080，移动 430×932 DPR2-3，CDP 触摸模拟 + CPU 节流）：

| 问题 | 根因 | 修法 | 效果 |
| --- | --- | --- | --- |
| 滚动掉帧（移动） | 8 层 `backdrop-filter`；非 passive 的 `touchmove`；`scroll` 里同步读 `scrollHeight` 强制布局；分页分帧插入 | 去毛玻璃、改 passive、rAF 合并、停稳后整页插入 | 同构建 A/B fps 51.7 → 70.8 |
| 一次塞入上百张缩略图 | `loading="lazy"` 不阻止被跳过子树的下载与解码 | `v-lazy-src` + IntersectionObserver，只给视口附近设 `src` | 首屏设 `src` 从 138 张降到 63 张；加载期 fps 41 → 152 |
| 桌面端极低帧率 | 每条 scroll 事件同步读 `scrollHeight`（单次 70–141 ms，卡片越多越慢） | 列表末尾哨兵 + IntersectionObserver；`onScroll` 只记时间戳 | 首屏滚动 fps 15.2 → 138.5，最坏帧 946 ms → 64 ms |
| 首屏图片慢 | 全部走同源接口、`nextHopProtocol: http/1.1`，mean 955 ms | 主通道切 CDN（h2 + `immutable`），同源接口保留为兜底 | 首屏图片平均 232 ms |
| 破图闪现 | 部分历史对象缺三档缩略图，CDN 404 时浏览器画出破图图标与 alt 文本 | 后端现场渲染回填 + 前端「加载完成前不显示、`alt` 置空」 | 破图在视觉上不可见 |

## 已知边界

- 收藏是本地偏好，不跨设备同步。
- 移动端布局在桌面浏览器把窗口拖窄到 860 px 以下也会生效（`pointer: coarse` 同样命中）。
- 网格仍是全量 DOM：懒加载只控制图片下载与解码，卡片节点随分页累积；再上一档优化是行虚拟化。
- 触感震动、系统分享面板（HTTP 下不可用）、3D Touch 压感、照片编辑属浏览器能力之外。
