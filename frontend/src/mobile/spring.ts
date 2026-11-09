/**
 * Apple 风味弹簧动效工具。
 *
 * 取值思路来自 WWDC18「Designing Fluid Interfaces」：动画由 response（一次往返的
 * 响应时长，秒）与 damping（阻尼比，1 = 临界阻尼不超调）两个参数描述，而不是靠
 * cubic-bezier 硬凑。这里把弹簧方程采样成 CSS `linear()` 缓动串 —— 浏览器原生
 * 插值，跑在合成线程上，比 rAF 逐帧改 transform 更稳，也更接近 iOS 的收尾。
 *
 * 用法：
 *   const t = springCss(SPRING.page)        // { duration: 420, easing: 'linear(...)' }
 *   style.transition = `transform ${t.duration}ms ${t.easing}`
 */
export interface SpringPreset {
  /** 响应时长（秒），越小越快。iOS 常用 0.3–0.5。 */
  response: number
  /** 阻尼比：1 = 不过冲；0.8 左右有一次轻微回弹。 */
  damping: number
}

export const SPRING = {
  /** 大块转场：查看器开合、全屏覆盖 */
  hero: { response: 0.36, damping: 0.78 },
  /** 翻页吸附 */
  page: { response: 0.3, damping: 0.82 },
  /** 图片缩放回弹 */
  zoom: { response: 0.3, damping: 0.72 },
  /** 底部面板 */
  sheet: { response: 0.34, damping: 0.8 },
  /** 工具条 / 选择栏 / 标签栏 */
  bar: { response: 0.28, damping: 0.74 },
  /** 点按反馈：带一点回弹 */
  press: { response: 0.18, damping: 0.52 },
  /** 网格布局变化（列数、FLIP） */
  grid: { response: 0.32, damping: 0.84 },
  /** 纯淡入淡出（不超调，避免颜色插值越界） */
  fade: { response: 0.24, damping: 1 }
} as const

interface Curve {
  duration: number
  easing: string
}

const cache = new Map<string, Curve>()

/** 把 response / damping 采样成 CSS linear() 缓动串（带缓存）。 */
export function springCss(preset: SpringPreset, samples = 120): Curve {
  const key = preset.response + ':' + preset.damping + ':' + samples
  const hit = cache.get(key)
  if (hit) return hit

  const w0 = (2 * Math.PI) / Math.max(preset.response, 0.05)
  const zeta = Math.max(preset.damping, 0.05)
  const wd = w0 * Math.sqrt(Math.max(1 - zeta * zeta, 0.0001))
  const at = (t: number) => {
    if (zeta >= 1) {
      const e = Math.exp(-w0 * t)
      return 1 - e * (1 + w0 * t)
    }
    const e = Math.exp(-zeta * w0 * t)
    return 1 - e * (Math.cos(wd * t) + ((zeta * w0) / wd) * Math.sin(wd * t))
  }

  // 找到稳定点：位移误差 < 0.1% 且速度足够小
  let settle = 0.6
  const dt = 1 / 240
  for (let t = 0; t <= 2; t += dt) {
    const x = at(t)
    const v = (at(t + dt) - x) / dt
    settle = t
    if (Math.abs(1 - x) < 0.001 && Math.abs(v) < 0.02) break
  }

  const values: string[] = []
  for (let i = 0; i < samples; i++) {
    const t = (settle * i) / (samples - 1)
    values.push(String(Math.round(at(t) * 10000) / 10000))
  }
  values[values.length - 1] = '1'
  const curve = { duration: Math.max(120, Math.round(settle * 1000)), easing: 'linear(' + values.join(', ') + ')' }
  cache.set(key, curve)
  return curve
}

/** 生成 transition 值：springTransition('transform', SPRING.hero) */
export function springTransition(prop: string, preset: SpringPreset): string {
  const c = springCss(preset)
  return prop + ' ' + c.duration + 'ms ' + c.easing
}

export function springDuration(preset: SpringPreset): number {
  return springCss(preset).duration
}

export interface SpringRun {
  from: number
  to: number
  /** 初始速度（单位/秒），手势松手时传入，衔接才自然 */
  velocity?: number
  preset: SpringPreset
  onUpdate: (value: number) => void
  onComplete?: () => void
}

/**
 * JS 侧弹簧（用于手势松手后的收尾、需要中断重定向的场景）。
 * 返回一个 cancel 函数。
 */
export function runSpring(run: SpringRun): () => void {
  const { from, to, preset } = run
  const v0 = run.velocity ?? 0
  const w0 = (2 * Math.PI) / Math.max(preset.response, 0.05)
  const zeta = Math.max(preset.damping, 0.05)
  const x0 = from - to
  const start = performance.now()
  let frame = 0
  let cancelled = false

  const value = (t: number) => {
    if (zeta >= 1) {
      const e = Math.exp(-w0 * t)
      return to + (x0 + (v0 + w0 * x0) * t) * e
    }
    const wd = w0 * Math.sqrt(1 - zeta * zeta)
    const e = Math.exp(-zeta * w0 * t)
    const a = x0
    const b = (v0 + zeta * w0 * x0) / wd
    return to + e * (a * Math.cos(wd * t) + b * Math.sin(wd * t))
  }

  const tick = () => {
    if (cancelled) return
    const t = (performance.now() - start) / 1000
    const v = value(t)
    run.onUpdate(v)
    const speed = Math.abs(value(t + 0.008) - v) / 0.008
    if (t > 1.6 || (Math.abs(v - to) < 0.002 * Math.max(1, Math.abs(to)) && speed < 0.5)) {
      run.onUpdate(to)
      run.onComplete?.()
      return
    }
    frame = requestAnimationFrame(tick)
  }
  frame = requestAnimationFrame(tick)

  return () => {
    cancelled = true
    cancelAnimationFrame(frame)
  }
}
