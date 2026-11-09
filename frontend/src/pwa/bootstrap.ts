import { isStandalone } from '@/composables/useIsMobile'

/**
 * 独立运行（添加到主屏）时给 <html> 打标记，用于 CSS 关闭整页回弹、文本选择与长按菜单。
 * Service Worker：HTTP 环境下浏览器不允许注册，检测到非安全上下文就跳过，
 * 等站点上了 HTTPS 会自动启用（也可以在控制台 localStorage.setItem('nocturne.sw','off') 关掉）。
 */
function markDisplayMode() {
  const apply = () => {
    const standalone = isStandalone()
    document.documentElement.classList.toggle('standalone', standalone)
    document.documentElement.dataset.displayMode = standalone ? 'standalone' : 'browser'
  }
  apply()
  window.matchMedia('(display-mode: standalone)').addEventListener?.('change', apply)
}

function registerServiceWorker() {
  if (!('serviceWorker' in navigator) || !import.meta.env.PROD) return
  if (!window.isSecureContext) {
    console.info('[nocturne] 当前为 HTTP 环境，Service Worker 已跳过；配置 HTTPS 后自动启用离线缓存。')
    return
  }
  if (localStorage.getItem('nocturne.sw') === 'off') return
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(err => {
      console.warn('[nocturne] Service Worker 注册失败：', err)
    })
  })
}

/**
 * iOS Safari 的双指缩放属于「页面缩放」，网页拿不到事件也改不了 —— 只有拦截
 * gesturestart/gesturechange 才能按住它。照片查看器自己算缩放，所以整站关掉页面缩放，
 * 图库里双指改列数、查看器里双指放大图片都只作用于内容。
 */
function lockPageZoom() {
  const block = (e: Event) => { if (e.cancelable) e.preventDefault() }
  document.addEventListener('gesturestart', block, { passive: false })
  document.addEventListener('gesturechange', block, { passive: false })
  document.addEventListener('gestureend', block, { passive: false })
  // 双击放大（iOS 15 之前的行为）也一并关掉；双击手势由组件自己处理。
  let lastTouch = 0
  document.addEventListener('touchend', (e) => {
    const now = Date.now()
    if (now - lastTouch < 320 && e.cancelable) e.preventDefault()
    lastTouch = now
  }, { passive: false })
}

export function bootstrapApp() {
  markDisplayMode()
  lockPageZoom()
  registerServiceWorker()
}
