export function parseDate(value?: string): Date | null {
  if (!value) return null
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const d = new Date(normalized)
  return Number.isNaN(d.getTime()) ? null : d
}
export function formatDate(value?: string, withTime = false): string {
  const d = parseDate(value); if (!d) return '—'
  const date = d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\//g, '.')
  return withTime ? date + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : date
}
export function monthKey(value?: string): string {
  const d = parseDate(value); if (!d) return '未归档'
  return d.getFullYear() + '年' + (d.getMonth() + 1) + '月'
}
export function monthRange(dates: Date[]): string {
  if (!dates.length) return ''
  const sorted = [...dates].sort((a, b) => a.getTime() - b.getTime())
  const fmt = (d: Date) => (d.getMonth() + 1) + '.' + String(d.getDate()).padStart(2, '0')
  return fmt(sorted[0]) + ' — ' + fmt(sorted[sorted.length - 1])
}
export function formatBytes(bytes?: number): string {
  if (!bytes || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']; let i = 0; let n = bytes
  while (n >= 1024 && i < units.length - 1) { n /= 1024; i++ }
  return n.toFixed(n >= 100 || i === 0 ? 0 : 1) + ' ' + units[i]
}
export function pad(n: number): string { return String(n).padStart(2, '0') }
export function relativeTime(value?: string): string {
  const d = parseDate(value); if (!d) return '—'
  const diff = Date.now() - d.getTime(); const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return min + ' 分钟前'
  const hour = Math.floor(min / 60); if (hour < 24) return hour + ' 小时前'
  const day = Math.floor(hour / 24); if (day < 30) return day + ' 天前'
  return formatDate(value)
}
export function fileExt(url?: string): string { const m = (url || '').split('?')[0].match(/\.([a-zA-Z0-9]+)$/); return m ? m[1].toUpperCase() : '—' }
