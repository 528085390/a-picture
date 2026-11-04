const PROXY_HOSTS = ['gdufbbs.dpdns.org']

/** 预览/原图地址：R2 公网域名在当前环境不可用时，改走同源后端代理。 */
export function mediaUrl(url?: string | null): string {
  if (!url) return ''
  try {
    const parsed = new URL(url, window.location.origin)
    if (PROXY_HOSTS.includes(parsed.hostname)) {
      return '/api/picture/file/' + parsed.pathname.replace(/^\/+/, '') + parsed.search
    }
    return url
  } catch { return url }
}

/** 下载接口需要的是对象键，不是完整的公网 URL。 */
export function storageKey(url: string): string {
  try {
    const parsed = new URL(url, window.location.origin)
    return parsed.pathname.replace(/^\/+/, '')
  } catch { return url.replace(/^\/+/, '') }
}

/** 网格/胶片条专用缩略图：服务端按需缩放，避免列表加载 1280px 预览图。 */
export function thumbUrl(url?: string | null, width = 480): string {
  if (!url) return ''
  try {
    const parsed = new URL(url, window.location.origin)
    if (PROXY_HOSTS.includes(parsed.hostname)) {
      return '/api/picture/thumb?key=' + encodeURIComponent(parsed.pathname.replace(/^\/+/, '')) + '&w=' + width
    }
    return url
  } catch { return url }
}
