const PROXY_HOSTS = ['gdufbbs.dpdns.org']
/** R2 的 Cloudflare 公网域名：网格缩略图与预览图的主通道（HTTP/2 + 边缘缓存） */
const CDN_HOSTS = ['img.raylxpj.us.ci']
/** 后端上传时预生成的缩略图档位，必须与 ThumbnailManager.WIDTHS 保持一致 */
const THUMB_WIDTHS = [240, 360, 640]
const IMAGE_EXT = /^(jpe?g|png|webp|gif|bmp)$/i

/** 私密空间对象键前缀：空间图片/视频在私密桶里，只能走同源后端按登录态读取 */
const PRIVATE_KEY = /^space\//

/** 后端同源文件代理前缀 */
const FILE_PROXY = '/api/picture/file/'

/** 该地址是否指向私密空间对象（space/<spaceId>/…） */
export function isPrivateUrl(url?: string | null): boolean {
  if (!url) return false
  try { return PRIVATE_KEY.test(new URL(url, window.location.origin).pathname.replace(/^\/+/, '')) } catch { return false }
}

/** 预览/原图地址：私密对象走同源后端；R2 公网域名不可用时也改走同源。 */
export function mediaUrl(url?: string | null): string {
  if (!url) return ''
  try {
    const parsed = new URL(url, window.location.origin)
    const key = parsed.pathname.replace(/^\/+/, '')
    if (PRIVATE_KEY.test(key)) {
      return FILE_PROXY + key + parsed.search
    }
    if (PROXY_HOSTS.includes(parsed.hostname)) {
      return FILE_PROXY + key + parsed.search
    }
    return url
  } catch { return url }
}

/** 下载接口需要的是对象键，不是完整的公网 URL。 */
export function storageKey(url: string): string {
  try {
    const parsed = new URL(url, window.location.origin)
    const path = parsed.pathname.replace(/^\/+/, '')
    if (path.startsWith('api/picture/file/')) return path.slice('api/picture/file/'.length)
    return path
  } catch { return url.replace(/^\/+/, '') }
}

/** 取最接近且不小于目标宽度的预生成档位；超过最大档按最大档算。 */
function snapWidth(width: number): number {
  const target = Number.isFinite(width) && width > 0 ? width : 480
  for (const w of THUMB_WIDTHS) { if (target <= w) return w }
  return THUMB_WIDTHS[THUMB_WIDTHS.length - 1]
}

/**
 * 把任意图片地址归一到「预览图对象键」：缩略图始终以 _preview 为底生成，
 * 所以原图键也要先补成 <stem>_preview.jpg，否则后端会白跑一次现场生成。
 */
function previewKey(pathname: string): string {
  const key = pathname.replace(/^\/+/, '')
  const dot = key.lastIndexOf('.')
  if (dot <= 0) return key
  const ext = key.slice(dot + 1)
  if (!IMAGE_EXT.test(ext)) return key
  const stem = key.slice(0, dot)
  return (stem.endsWith('_preview') ? stem : stem + '_preview') + '.' + ext
}

/** 缩略图对象键：<stem>_preview.<ext> → <stem>_preview_w360.jpg */
function thumbKey(pathname: string, width: number): string {
  const key = previewKey(pathname)
  const dot = key.lastIndexOf('.')
  return (dot > 0 ? key.slice(0, dot) : key) + '_w' + snapWidth(width) + '.jpg'
}

/**
 * 网格/胶片条缩略图主通道：直连 Cloudflare 上的预生成对象。
 * 优势：HTTP/2 多路复用（同源是 HTTP/1.1，浏览器每域 6 条连接）、边缘缓存 immutable 1 年。
 * 实测（138 张冷缓存、10Mbps/100ms、4x CPU）：CDN 1.5s，同源后端 5–11s。
 */
export function thumbUrl(url?: string | null, width = 480): string {
  if (!url) return ''
  try {
    const parsed = new URL(url, window.location.origin)
    // 私密对象没有 CDN 副本，只能走同源缩略图接口（后端会校验空间权限）
    if (PRIVATE_KEY.test(parsed.pathname.replace(/^\/+/, ''))) {
      return '/api/picture/thumb?key=' + encodeURIComponent(previewKey(parsed.pathname)) + '&w=' + snapWidth(width)
    }
    if (PROXY_HOSTS.includes(parsed.hostname) || CDN_HOSTS.includes(parsed.hostname)) {
      const origin = CDN_HOSTS.includes(parsed.hostname) ? parsed.origin : 'https://img.raylxpj.us.ci'
      return origin + '/' + thumbKey(parsed.pathname, width)
    }
    return url
  } catch { return url }
}

/** 兜底通道：同源后端 /api/picture/thumb（Cloudflare 不可达或对象缺失时用）。 */
export function originThumbUrl(url?: string | null, width = 480): string {
  if (!url) return ''
  try {
    const parsed = new URL(url, window.location.origin)
    const key = parsed.pathname.replace(/^\/+/, '')
    if (PRIVATE_KEY.test(key) || PROXY_HOSTS.includes(parsed.hostname) || CDN_HOSTS.includes(parsed.hostname)) {
      return '/api/picture/thumb?key=' + encodeURIComponent(previewKey(parsed.pathname)) + '&w=' + snapWidth(width)
    }
    return url
  } catch { return url }
}

/** 兼容旧名：主通道就是 CDN。 */
export const cdnThumbUrl = thumbUrl;

/** 兼容旧调用：同源缩略图地址。 */
export function proxyThumbUrl(url?: string | null, width = 480): string {
  return originThumbUrl(url, width)
}
