<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { PictureVO } from '@/api/types'
import { pictureApi } from '@/api/picture'
import { useFavorites } from '@/composables/useFavorites'
import { useUiStore } from '@/stores/ui'
import { formatBytes, formatDate, fileExt, pad, parseDate } from '@/utils/format'
import { mediaUrl } from '@/utils/media'
import { SPRING, springCss, springDuration } from './spring'
import Icon from '@/components/Icon.vue'

interface OriginRect { x: number; y: number; width: number; height: number }
interface Rect { x: number; y: number; width: number; height: number }
interface Point { x: number; y: number }
interface Bounds { minX: number; maxX: number; minY: number; maxY: number }

type Gesture = 'idle' | 'pending' | 'page' | 'dismiss' | 'pan' | 'pinch'
type Phase = 'closed' | 'opening' | 'open' | 'closing'

const props = withDefaults(defineProps<{
  photos: PictureVO[]
  index: number
  open: boolean
  originRect?: OriginRect | null
  videoMap?: Record<string, string>
  canEdit?: boolean
}>(), {
  originRect: null,
  videoMap: () => ({}),
  canEdit: false
})

const emit = defineEmits<{
  'update:index': [n: number]
  'update:open': [v: boolean]
  close: []
  edit: [p: PictureVO]
  delete: [p: PictureVO]
  outpainting: [p: PictureVO]
}>()

const ui = useUiStore()

/* 弹簧动效：时长由 response / damping 推导，避免「要么很快、要么没有」的硬切。 */
const HERO_SPRING = springCss(SPRING.hero)
const PAGE_SPRING = springCss(SPRING.page)
const ZOOM_SPRING = springCss(SPRING.zoom)
const FADE_SPRING = springCss(SPRING.fade)
const HERO_MS = HERO_SPRING.duration
const PAGE_MS = PAGE_SPRING.duration
const HERO_TRANSITION = 'transform ' + HERO_MS + 'ms ' + HERO_SPRING.easing
const PAGE_TRANSITION = 'transform ' + PAGE_MS + 'ms ' + PAGE_SPRING.easing
const BG_TRANSITION = 'background-color ' + HERO_MS + 'ms cubic-bezier(.32,.72,0,1)'
const BG_DISMISS_TRANSITION = 'background-color ' + PAGE_MS + 'ms cubic-bezier(.32,.72,0,1)'
const FADE_TRANSITION = 'opacity ' + FADE_SPRING.duration + 'ms ' + FADE_SPRING.easing
const FADE_OUT_TRANSITION = 'opacity ' + FADE_SPRING.duration + 'ms cubic-bezier(.32,.72,0,1)'
const favorites = useFavorites()

const rootEl = ref<HTMLElement | null>(null)
const stageEl = ref<HTMLElement | null>(null)
const vw = ref(window.innerWidth)
const vh = ref(window.innerHeight)
const phase = ref<Phase>('closed')
const displayIndex = ref(clampIndex(props.index))
const chromeHidden = ref(false)
const infoOpen = ref(false)
const actionsOpen = ref(false)

const scale = ref(1)
const tx = ref(0)
const ty = ref(0)
const zoomAnimating = ref(false)

const dragX = ref(0)
const trackAnimating = ref(false)

const dismissActive = ref(false)
const dismissY = ref(0)
const dismissScale = ref(1)
const dismissAnimating = ref(false)

const bgAlpha = ref(0)
const bgTransition = ref('none')

const heroRect = ref<Rect | null>(null)
const heroSrc = ref('')
const heroTransform = ref('none')
const heroOpacity = ref(1)
const heroTransition = ref('none')

const openPhotoId = ref('')
const originSnapshot = ref<OriginRect | null>(null)

const sheetDragging = ref(false)
const sheetDragY = ref(0)

const previewFallback = ref<Set<string>>(new Set())
const brokenImages = ref<Set<string>>(new Set())
const livePlaying = ref(false)

const pointerMap = new Map<number, Point>()
const videoEls = new Map<number, HTMLVideoElement>()
let gesture: Gesture = 'idle'
const startPoint = { x: 0, y: 0, t: 0 }
let lastPoint = { x: 0, y: 0, t: 0 }
let velocity = { x: 0, y: 0 }
let basePan = { tx: 0, ty: 0 }
let pinchState = { dist: 1, scale: 1, tx: 0, ty: 0, midX: 0, midY: 0 }
let moved = false
let longPressFired = false
let longPressTimer: number | null = null
let tapTimer: number | null = null
let lastTapAt = 0
let lastTapX = 0
let lastTapY = 0

let openTimer: number | null = null
let closeTimer: number | null = null
let trackTimer: number | null = null
let zoomTimer: number | null = null
let dismissTimer: number | null = null

let sheetStartY = 0
let sheetStartT = 0
let sheetLastY = 0

let previousFocus: HTMLElement | null = null
let previousOverflow = ''
let bodyLocked = false

const photo = computed(() => props.photos[displayIndex.value] || null)
const isLive = computed(() => !!photo.value && !!props.videoMap[photo.value.id])
const favOn = computed(() => !!photo.value && favorites.has(photo.value.id))
const dragging = computed(() => Math.abs(dragX.value) > 0.5)

const dateLine = computed(() => {
  const d = parseDate(photo.value?.createTime)
  if (!d) return '未知日期'
  return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日'
})
const timeLine = computed(() => {
  const d = parseDate(photo.value?.createTime)
  return d ? pad(d.getHours()) + ':' + pad(d.getMinutes()) : ''
})

const windowPages = computed(() => {
  const pages: Array<{ offset: number; index: number; photo: PictureVO | null }> = []
  for (let offset = -1; offset <= 1; offset++) {
    const i = displayIndex.value + offset
    pages.push({ offset, index: i, photo: props.photos[i] || null })
  }
  return pages
})

const trackStyle = computed(() => ({
  transform: 'translate3d(' + (-vw.value + dragX.value) + 'px, 0, 0)',
  transition: trackAnimating.value ? PAGE_TRANSITION : 'none'
}))

const canvasStyle = computed(() => ({
  transform: 'translate3d(' + tx.value + 'px, ' + ty.value + 'px, 0) scale(' + scale.value + ')',
  transformOrigin: '0 0',
  transition: zoomAnimating.value ? PAGE_TRANSITION : 'none'
}))

const activePageStyle = computed(() => {
  if (!dismissActive.value) return undefined
  return {
    transform: 'translate3d(0, ' + dismissY.value + 'px, 0) scale(' + dismissScale.value + ')',
    transformOrigin: '50% 50%',
    transition: dismissAnimating.value ? PAGE_TRANSITION : 'none'
  }
})

const rootStyle = computed(() => ({
  backgroundColor: 'rgba(0,0,0,' + bgAlpha.value + ')',
  transition: bgTransition.value
}))

const heroStyle = computed(() => {
  const r = heroRect.value
  if (!r) return {}
  return {
    left: r.x + 'px',
    top: r.y + 'px',
    width: r.width + 'px',
    height: r.height + 'px',
    transform: heroTransform.value,
    opacity: heroOpacity.value,
    transition: heroTransition.value
  }
})

const infoSheetStyle = computed(() => {
  if (!sheetDragging.value) return {}
  return {
    transform: 'translateY(' + sheetDragY.value + 'px)',
    transition: 'none'
  }
})

const photoTitle = computed(() => photo.value?.name || '未命名照片')
const dialogLabel = computed(() => '照片查看器，' + photoTitle.value)

function clampIndex(n: number) {
  const max = Math.max(0, props.photos.length - 1)
  return Math.min(Math.max(Math.round(n) || 0, 0), max)
}

function clamp(v: number, min: number, max: number) {
  return Math.min(Math.max(v, min), max)
}

function aspectOf(p: PictureVO | null) {
  if (!p) return vw.value / Math.max(vh.value, 1)
  const w = Number(p.picWidth || 0)
  const h = Number(p.picHeight || 0)
  if (Number.isFinite(w) && Number.isFinite(h) && w > 0 && h > 0) return w / h
  return vw.value / Math.max(vh.value, 1)
}

function fitRectFor(p: PictureVO | null): Rect {
  const ratio = aspectOf(p)
  let width = vw.value
  let height = width / ratio
  if (height > vh.value) {
    height = vh.value
    width = height * ratio
  }
  return { x: (vw.value - width) / 2, y: (vh.value - height) / 2, width, height }
}

function mapTransform(from: Rect, to: Rect) {
  const sx = to.width / Math.max(from.width, 1)
  const sy = to.height / Math.max(from.height, 1)
  return 'translate3d(' + (to.x - from.x) + 'px, ' + (to.y - from.y) + 'px, 0) scale(' + sx + ', ' + sy + ')'
}

function visibleOrigin(rect: OriginRect | null | undefined) {
  if (!rect) return false
  return rect.width > 2 && rect.height > 2 && rect.x < vw.value && rect.y < vh.value && rect.x + rect.width > 0 && rect.y + rect.height > 0
}

function currentContentRect(): Rect | null {
  const p = photo.value
  if (!p) return null
  const base = fitRectFor(p)
  let x = base.x * scale.value + tx.value + dragX.value
  let y = base.y * scale.value + ty.value
  let width = base.width * scale.value
  let height = base.height * scale.value
  if (dismissActive.value) {
    const s = dismissScale.value
    x = (x - vw.value / 2) * s + vw.value / 2
    y = (y - vh.value / 2) * s + vh.value / 2
    width *= s
    height *= s
    y += dismissY.value
  }
  return { x, y, width, height }
}

function srcFor(p: PictureVO | null) {
  if (!p || brokenImages.value.has(p.id)) return ''
  const usePreview = previewFallback.value.has(p.id)
  return mediaUrl(usePreview ? (p.previewUrl || p.url) : (p.url || p.previewUrl))
}

function videoUrlOf(p: PictureVO | null) {
  if (!p) return ''
  return props.videoMap[p.id] || ''
}

function setVideoRef(el: unknown, offset: number) {
  if (el instanceof HTMLVideoElement) videoEls.set(offset, el)
  else videoEls.delete(offset)
}

function markFailed(p: PictureVO | null) {
  if (!p) return
  const hasPreview = !!p.previewUrl && p.previewUrl !== p.url
  if (hasPreview && !previewFallback.value.has(p.id)) {
    const next = new Set(previewFallback.value)
    next.add(p.id)
    previewFallback.value = next
    return
  }
  const broken = new Set(brokenImages.value)
  broken.add(p.id)
  brokenImages.value = broken
}

function onHeroError() {
  const p = photo.value
  if (!p) return
  markFailed(p)
  heroSrc.value = srcFor(p)
}

function pauseLive() {
  livePlaying.value = false
  videoEls.forEach(v => { try { v.pause() } catch {} })
}

function startLongPress() {
  clearLongPress()
  longPressTimer = window.setTimeout(() => {
    longPressTimer = null
    if (gesture !== 'pending' || moved || scale.value > 1.01) return
    const v = videoEls.get(0)
    if (!v) return
    livePlaying.value = true
    longPressFired = true
    try { v.currentTime = 0 } catch {}
    const playing = v.play()
    if (playing && typeof playing.catch === 'function') playing.catch(() => { livePlaying.value = false })
  }, 350)
}

function clearLongPress() {
  if (longPressTimer) {
    window.clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function clearTapTimer() {
  if (tapTimer) {
    window.clearTimeout(tapTimer)
    tapTimer = null
  }
}

function clearTimers() {
  if (openTimer) window.clearTimeout(openTimer)
  if (closeTimer) window.clearTimeout(closeTimer)
  if (trackTimer) window.clearTimeout(trackTimer)
  if (zoomTimer) window.clearTimeout(zoomTimer)
  if (dismissTimer) window.clearTimeout(dismissTimer)
  openTimer = null
  closeTimer = null
  trackTimer = null
  zoomTimer = null
  dismissTimer = null
  clearLongPress()
  clearTapTimer()
}

function lockBody() {
  if (bodyLocked) return
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  bodyLocked = true
}

function unlockBody() {
  if (!bodyLocked) return
  document.body.style.overflow = previousOverflow
  bodyLocked = false
}

function resetZoom(animate = false) {
  if (zoomTimer) {
    window.clearTimeout(zoomTimer)
    zoomTimer = null
  }
  scale.value = 1
  tx.value = 0
  ty.value = 0
  zoomAnimating.value = false
  if (animate) {
    zoomAnimating.value = true
    zoomTimer = window.setTimeout(() => {
      zoomAnimating.value = false
      zoomTimer = null
    }, PAGE_MS + 10)
  }
}

function boundsFor(s: number, f: Rect): Bounds {
  const cw = f.width * s
  const ch = f.height * s
  let minX = 0
  let maxX = 0
  let minY = 0
  let maxY = 0
  if (cw <= vw.value) {
    minX = (vw.value - cw) / 2 - f.x * s
    maxX = minX
  } else {
    maxX = -f.x * s
    minX = vw.value - (f.x + f.width) * s
  }
  if (ch <= vh.value) {
    minY = (vh.value - ch) / 2 - f.y * s
    maxY = minY
  } else {
    maxY = -f.y * s
    minY = vh.value - (f.y + f.height) * s
  }
  return { minX, maxX, minY, maxY }
}

function panBounds(s: number) {
  return boundsFor(s, fitRectFor(photo.value))
}

function rubber(v: number, min: number, max: number) {
  if (v < min) return min - Math.min((min - v) * 0.3, 90)
  if (v > max) return max + Math.min((v - max) * 0.3, 90)
  return v
}

function animatePanTo(nx: number, ny: number) {
  if (zoomTimer) {
    window.clearTimeout(zoomTimer)
    zoomTimer = null
  }
  zoomAnimating.value = true
  tx.value = nx
  ty.value = ny
  zoomTimer = window.setTimeout(() => {
    zoomAnimating.value = false
    zoomTimer = null
  }, PAGE_MS + 10)
}

function settlePan() {
  if (scale.value <= 1.01) {
    resetZoom(true)
    return
  }
  if (scale.value > 4) scale.value = 4
  const b = panBounds(scale.value)
  const nx = clamp(tx.value, b.minX, b.maxX)
  const ny = clamp(ty.value, b.minY, b.maxY)
  const movedX = Math.abs(nx - tx.value) > 0.5
  const movedY = Math.abs(ny - ty.value) > 0.5
  if (movedX || movedY) animatePanTo(nx, ny)
  else {
    tx.value = nx
    ty.value = ny
  }
}

function toggleZoomAt(fx: number, fy: number) {
  clearTapTimer()
  if (zoomTimer) {
    window.clearTimeout(zoomTimer)
    zoomTimer = null
  }
  if (scale.value > 1.01) {
    zoomAnimating.value = true
    scale.value = 1
    tx.value = 0
    ty.value = 0
    zoomTimer = window.setTimeout(() => {
      zoomAnimating.value = false
      zoomTimer = null
    }, PAGE_MS + 10)
    return
  }
  const target = 2.5
  const k = target / scale.value
  const b = boundsFor(target, fitRectFor(photo.value))
  const nx = clamp(fx - (fx - tx.value) * k, b.minX, b.maxX)
  const ny = clamp(fy - (fy - ty.value) * k, b.minY, b.maxY)
  zoomAnimating.value = true
  scale.value = target
  tx.value = nx
  ty.value = ny
  zoomTimer = window.setTimeout(() => {
    zoomAnimating.value = false
    zoomTimer = null
  }, PAGE_MS + 10)
}

function beginOpen() {
  clearTimers()
  pointerMap.clear()
  gesture = 'idle'
  moved = false
  longPressFired = false
  lastTapAt = 0
  lastTapX = 0
  lastTapY = 0
  displayIndex.value = clampIndex(props.index)
  phase.value = 'opening'
  chromeHidden.value = false
  infoOpen.value = false
  actionsOpen.value = false
  dismissActive.value = false
  dismissAnimating.value = false
  dismissY.value = 0
  dismissScale.value = 1
  dragX.value = 0
  trackAnimating.value = false
  resetZoom()
  pauseLive()
  previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  lockBody()
  const p = photo.value
  openPhotoId.value = p ? p.id : ''
  originSnapshot.value = props.originRect
    ? { x: props.originRect.x, y: props.originRect.y, width: props.originRect.width, height: props.originRect.height }
    : null
  const rect = p ? fitRectFor(p) : { x: 0, y: 0, width: vw.value, height: vh.value }
  const origin = visibleOrigin(originSnapshot.value) ? originSnapshot.value : null
  heroRect.value = rect
  heroSrc.value = p ? srcFor(p) : ''
  heroTransform.value = origin ? mapTransform(rect, origin) : 'none'
  heroOpacity.value = origin ? 1 : 0
  heroTransition.value = 'none'
  bgAlpha.value = 0
  bgTransition.value = 'none'
  if (!p) {
    phase.value = 'open'
    bgAlpha.value = 1
    return
  }
  nextTick(() => {
    if (phase.value !== 'opening') return
    rootEl.value?.focus({ preventScroll: true })
    void rootEl.value?.offsetWidth
    window.requestAnimationFrame(() => {
      if (phase.value !== 'opening') return
      bgTransition.value = BG_TRANSITION
      bgAlpha.value = 1
      if (origin) {
        heroTransition.value = HERO_TRANSITION
        heroTransform.value = 'none'
      } else {
        heroTransition.value = FADE_TRANSITION
        heroOpacity.value = 1
      }
    })
  })
  openTimer = window.setTimeout(finishOpen, HERO_MS + 20)
}

function finishOpen() {
  openTimer = null
  if (phase.value !== 'opening') return
  phase.value = 'open'
  heroRect.value = null
  heroSrc.value = ''
  heroTransform.value = 'none'
  heroTransition.value = 'none'
  heroOpacity.value = 1
  bgAlpha.value = 1
  bgTransition.value = 'none'
}

function finishClose() {
  clearTimers()
  closeTimer = null
  if (phase.value === 'closed') return
  clearLongPress()
  clearTapTimer()
  pointerMap.clear()
  gesture = 'idle'
  infoOpen.value = false
  actionsOpen.value = false
  sheetDragging.value = false
  sheetDragY.value = 0
  phase.value = 'closed'
  heroRect.value = null
  heroSrc.value = ''
  heroTransform.value = 'none'
  heroTransition.value = 'none'
  heroOpacity.value = 1
  bgAlpha.value = 0
  bgTransition.value = 'none'
  dismissActive.value = false
  dismissAnimating.value = false
  dismissY.value = 0
  dismissScale.value = 1
  dragX.value = 0
  trackAnimating.value = false
  resetZoom()
  pauseLive()
  unlockBody()
  if (previousFocus) {
    try { previousFocus.focus({ preventScroll: true }) } catch {}
    previousFocus = null
  }
  emit('update:open', false)
  emit('close')
}

function closeViewer() {
  if (phase.value === 'closing' || phase.value === 'closed') return
  pointerMap.clear()
  gesture = 'idle'
  clearLongPress()
  clearTapTimer()
  if (phase.value === 'opening') {
    if (openTimer) {
      window.clearTimeout(openTimer)
      openTimer = null
    }
    phase.value = 'open'
  }
  if (trackTimer) {
    window.clearTimeout(trackTimer)
    trackTimer = null
  }
  if (trackAnimating.value) {
    trackAnimating.value = false
    dragX.value = 0
  }
  const p = photo.value
  if (!p) {
    finishClose()
    return
  }
  infoOpen.value = false
  actionsOpen.value = false
  sheetDragging.value = false
  sheetDragY.value = 0
  pauseLive()
  const rect = currentContentRect() || fitRectFor(p)
  const origin = openPhotoId.value === p.id && visibleOrigin(originSnapshot.value) ? originSnapshot.value : null
  heroSrc.value = srcFor(p)
  heroRect.value = rect
  heroTransform.value = 'none'
  heroTransition.value = 'none'
  heroOpacity.value = 1
  phase.value = 'closing'
  bgTransition.value = 'none'
  nextTick(() => {
    if (phase.value !== 'closing') return
    void rootEl.value?.offsetWidth
    window.requestAnimationFrame(() => {
      if (phase.value !== 'closing') return
      if (origin) {
        heroTransition.value = HERO_TRANSITION
        heroTransform.value = mapTransform(rect, origin)
        heroOpacity.value = 1
        bgTransition.value = BG_TRANSITION
      } else {
        heroTransition.value = FADE_OUT_TRANSITION
        heroOpacity.value = 0
        bgTransition.value = 'background-color 220ms ease'
      }
      bgAlpha.value = 0
    })
  })
  closeTimer = window.setTimeout(finishClose, HERO_MS + 20)
}

function springTrack() {
  if (trackTimer) {
    window.clearTimeout(trackTimer)
    trackTimer = null
  }
  trackAnimating.value = true
  dragX.value = 0
  trackTimer = window.setTimeout(() => {
    trackAnimating.value = false
    trackTimer = null
  }, PAGE_MS + 10)
}

function startStep(dir: 1 | -1) {
  if (trackTimer) {
    window.clearTimeout(trackTimer)
    trackTimer = null
  }
  resetZoom()
  trackAnimating.value = true
  dragX.value = -dir * vw.value
  trackTimer = window.setTimeout(() => {
    displayIndex.value = clampIndex(displayIndex.value + dir)
    dragX.value = 0
    trackAnimating.value = false
    trackTimer = null
    resetZoom()
    pauseLive()
    emit('update:index', displayIndex.value)
  }, PAGE_MS + 10)
}

function step(dir: 1 | -1) {
  if (phase.value !== 'open' || trackAnimating.value) return
  const target = displayIndex.value + dir
  if (target < 0 || target >= props.photos.length) return
  startStep(dir)
}

function releasePage() {
  const dx = dragX.value
  const vx = performance.now() - lastPoint.t < 90 ? velocity.x : 0
  let dir: 0 | 1 | -1 = 0
  if (dx < -vw.value * 0.25 || (vx < -0.35 && dx < -8)) dir = 1
  else if (dx > vw.value * 0.25 || (vx > 0.35 && dx > 8)) dir = -1
  if (dir === 1 && displayIndex.value >= props.photos.length - 1) dir = 0
  if (dir === -1 && displayIndex.value <= 0) dir = 0
  if (dir === 0) springTrack()
  else startStep(dir)
}

function springDismiss() {
  if (dismissTimer) {
    window.clearTimeout(dismissTimer)
    dismissTimer = null
  }
  dismissAnimating.value = true
  bgTransition.value = BG_DISMISS_TRANSITION
  dismissY.value = 0
  dismissScale.value = 1
  bgAlpha.value = 1
  dismissTimer = window.setTimeout(() => {
    dismissActive.value = false
    dismissAnimating.value = false
    bgTransition.value = 'none'
    dismissTimer = null
  }, PAGE_MS + 10)
}

function releaseDismiss() {
  const dy = dismissY.value
  const vy = performance.now() - lastPoint.t < 90 ? velocity.y : 0
  if (dy > 110 || vy > 0.6) {
    closeViewer()
    return
  }
  springDismiss()
}

function handleTap(e: PointerEvent) {
  if (longPressFired) return
  const now = performance.now()
  if (now - startPoint.t > 250) return
  const isDouble = lastTapAt > 0 && now - lastTapAt < 280 && Math.hypot(e.clientX - lastTapX, e.clientY - lastTapY) < 40
  if (isDouble) {
    clearTapTimer()
    lastTapAt = 0
    toggleZoomAt(e.clientX, e.clientY)
    return
  }
  lastTapAt = now
  lastTapX = e.clientX
  lastTapY = e.clientY
  clearTapTimer()
  tapTimer = window.setTimeout(() => {
    chromeHidden.value = !chromeHidden.value
    tapTimer = null
  }, 280)
}

function onPointerDown(e: PointerEvent) {
  if (phase.value !== 'open' || !photo.value || trackAnimating.value) return
  if (e.pointerType === 'mouse' && e.button !== 0) return
  e.preventDefault()
  pointerMap.set(e.pointerId, { x: e.clientX, y: e.clientY })
  try { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId) } catch {}
  if (pointerMap.size === 1) {
    gesture = 'pending'
    startPoint.x = e.clientX
    startPoint.y = e.clientY
    startPoint.t = performance.now()
    lastPoint = { x: e.clientX, y: e.clientY, t: startPoint.t }
    velocity = { x: 0, y: 0 }
    moved = false
    longPressFired = false
    basePan.tx = tx.value
    basePan.ty = ty.value
    if (scale.value === 1 && isLive.value) startLongPress()
  } else if (pointerMap.size === 2) {
    clearLongPress()
    clearTapTimer()
    const pts = Array.from(pointerMap.values())
    const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
    pinchState = {
      dist: Math.max(dist, 1),
      scale: scale.value,
      tx: tx.value,
      ty: ty.value,
      midX: (pts[0].x + pts[1].x) / 2,
      midY: (pts[0].y + pts[1].y) / 2
    }
    gesture = 'pinch'
    if (dragX.value !== 0) {
      dragX.value = 0
      trackAnimating.value = false
    }
    if (dismissActive.value) {
      dismissActive.value = false
      dismissAnimating.value = false
      dismissY.value = 0
      dismissScale.value = 1
      bgAlpha.value = 1
      bgTransition.value = 'none'
    }
    zoomAnimating.value = false
  }
}

function onPointerMove(e: PointerEvent) {
  const point = pointerMap.get(e.pointerId)
  if (!point || livePlaying.value) return
  const now = performance.now()
  const dt = Math.max(now - lastPoint.t, 1)
  if (gesture === 'pinch') {
    point.x = e.clientX
    point.y = e.clientY
    const pts = Array.from(pointerMap.values())
    if (pts.length < 2) return
    const dist = Math.max(Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y), 1)
    const midX = (pts[0].x + pts[1].x) / 2
    const midY = (pts[0].y + pts[1].y) / 2
    const nextScale = Math.min(4, Math.max(1, pinchState.scale * (dist / pinchState.dist)))
    const k = nextScale / pinchState.scale
    scale.value = nextScale
    tx.value = midX - (pinchState.midX - pinchState.tx) * k
    ty.value = midY - (pinchState.midY - pinchState.ty) * k
    moved = true
    return
  }
  if (gesture === 'idle') return
  const dx = e.clientX - startPoint.x
  const dy = e.clientY - startPoint.y
  if (gesture === 'pending' && !moved) {
    if (Math.hypot(dx, dy) > 7) {
      moved = true
      clearLongPress()
      clearTapTimer()
      if (scale.value > 1.01) {
        gesture = 'pan'
        basePan.tx = tx.value
        basePan.ty = ty.value
        startPoint.x = e.clientX
        startPoint.y = e.clientY
      } else if (Math.abs(dx) > Math.abs(dy)) {
        gesture = 'page'
      } else if (dy > 0) {
        gesture = 'dismiss'
        dismissActive.value = true
        dismissAnimating.value = false
        dismissY.value = 0
        dismissScale.value = 1
        bgAlpha.value = 1
        bgTransition.value = 'none'
      } else {
        gesture = 'idle'
      }
    }
  }
  if (gesture === 'page') {
    const atStart = displayIndex.value <= 0 && dx > 0
    const atEnd = displayIndex.value >= props.photos.length - 1 && dx < 0
    dragX.value = (atStart || atEnd) ? dx * 0.35 : dx
  } else if (gesture === 'dismiss') {
    const d = Math.max(0, dy)
    dismissY.value = d
    dismissScale.value = 1 - Math.min(d, 600) / 1600
    bgAlpha.value = 1 - Math.min(d, 500) / 500 * 0.85
  } else if (gesture === 'pan') {
    const b = panBounds(scale.value)
    tx.value = rubber(basePan.tx + (e.clientX - startPoint.x), b.minX, b.maxX)
    ty.value = rubber(basePan.ty + (e.clientY - startPoint.y), b.minY, b.maxY)
  }
  if (dt > 0) velocity = { x: (e.clientX - lastPoint.x) / dt, y: (e.clientY - lastPoint.y) / dt }
  lastPoint = { x: e.clientX, y: e.clientY, t: now }
}

function onPointerUp(e: PointerEvent) {
  const active = gesture
  clearLongPress()
  pointerMap.delete(e.pointerId)
  try { (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId) } catch {}
  if (active === 'pinch') {
    if (pointerMap.size >= 2) return
    if (pointerMap.size === 1) {
      const only = Array.from(pointerMap.values())[0]
      startPoint.x = only.x
      startPoint.y = only.y
      startPoint.t = performance.now()
      lastPoint = { x: only.x, y: only.y, t: startPoint.t }
      basePan.tx = tx.value
      basePan.ty = ty.value
      gesture = 'pan'
      return
    }
    settlePan()
    gesture = 'idle'
    if (livePlaying.value) pauseLive()
    return
  }
  if (gesture === 'page') releasePage()
  else if (gesture === 'dismiss') releaseDismiss()
  else if (gesture === 'pan') settlePan()
  else if (gesture === 'pending' && !moved) handleTap(e)
  if (livePlaying.value) pauseLive()
  gesture = 'idle'
}

function onPointerCancel(e: PointerEvent) {
  pointerMap.clear()
  clearLongPress()
  if (gesture === 'page') springTrack()
  else if (gesture === 'dismiss') springDismiss()
  else if (gesture === 'pan' || gesture === 'pinch') settlePan()
  gesture = 'idle'
  if (livePlaying.value) pauseLive()
}

function toggleFav() {
  const p = photo.value
  if (!p) return
  favorites.toggle(p.id)
  ui.toast(favorites.has(p.id) ? '已收藏' : '已取消收藏')
}

async function sharePhoto() {
  const p = photo.value
  if (!p) return
  const nav = navigator as Navigator & { share?: (data: { title?: string; text?: string; url?: string }) => Promise<void> }
  if (typeof nav.share !== 'function') {
    ui.toast('当前环境不支持分享，长按图片可保存')
    return
  }
  const shareUrl = mediaUrl(p.url || p.previewUrl || '')
  try {
    await nav.share({ title: p.name || 'NOCTURNE', text: p.introduction || '', url: shareUrl ? new URL(shareUrl, window.location.href).href : '' })
  } catch {
    // user dismissed the share sheet
  }
}

function closeInfo() {
  infoOpen.value = false
  sheetDragging.value = false
  sheetDragY.value = 0
}

function toggleInfo() {
  if (phase.value !== 'open') return
  if (infoOpen.value) closeInfo()
  else {
    actionsOpen.value = false
    infoOpen.value = true
  }
}

function openActions() {
  if (phase.value !== 'open') return
  infoOpen.value = false
  actionsOpen.value = true
}

function openInfoFromActions() {
  actionsOpen.value = false
  infoOpen.value = true
}

function editPhoto() {
  const p = photo.value
  if (!p || !props.canEdit) return
  emit('edit', p)
}

function deletePhoto() {
  const p = photo.value
  if (!p || !props.canEdit) return
  actionsOpen.value = false
  infoOpen.value = false
  emit('delete', p)
}

function outpaintingPhoto() {
  const p = photo.value
  if (!p || !props.canEdit) return
  infoOpen.value = false
  emit('outpainting', p)
}

async function downloadOriginal() {
  const p = photo.value
  actionsOpen.value = false
  if (!p?.url) {
    ui.toast('原图暂不可用', 'error')
    return
  }
  try {
    const blob = await pictureApi.download(p.url)
    const href = URL.createObjectURL(blob)
    const rawExt = p.picFormat || fileExt(p.url)
    const ext = /^[a-zA-Z0-9]+$/.test(rawExt) ? rawExt.toLowerCase() : 'jpg'
    const base = p.name || 'photo'
    const name = base.lastIndexOf('.') > 0 ? base : base + '.' + ext
    const a = document.createElement('a')
    a.href = href
    a.download = name
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.setTimeout(() => URL.revokeObjectURL(href), 1500)
    ui.toast('已存储到文件')
  } catch (error) {
    ui.toast((error as Error).message || '存储失败', 'error')
  }
}

async function copyLink() {
  const p = photo.value
  if (!p?.url && !p?.previewUrl) {
    ui.toast('链接不可用', 'error')
    return
  }
  const url = p.url || p.previewUrl || ''
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(url)
    } else {
      const ta = document.createElement('textarea')
      ta.value = url
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      ta.remove()
    }
    ui.toast('链接已复制')
  } catch {
    ui.toast('复制失败', 'error')
  }
  actionsOpen.value = false
}

function onSheetDown(e: PointerEvent) {
  if (!infoOpen.value) return
  sheetDragging.value = true
  sheetDragY.value = 0
  sheetStartY = e.clientY
  sheetStartT = performance.now()
  sheetLastY = e.clientY
  try { (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId) } catch {}
}

function onSheetMove(e: PointerEvent) {
  if (!sheetDragging.value) return
  const dy = Math.max(0, e.clientY - sheetStartY)
  sheetDragY.value = dy
  sheetLastY = e.clientY
}

function onSheetUp() {
  if (!sheetDragging.value) return
  const dy = sheetDragY.value
  const vy = (sheetLastY - sheetStartY) / Math.max(performance.now() - sheetStartT, 1)
  sheetDragging.value = false
  sheetDragY.value = 0
  if (dy > 90 || vy > 0.6) closeInfo()
}

function onKey(e: KeyboardEvent) {
  if (phase.value === 'closed') return
  const target = e.target as HTMLElement | null
  if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) return
  if (e.key === 'Escape') {
    e.preventDefault()
    if (actionsOpen.value) actionsOpen.value = false
    else if (infoOpen.value) closeInfo()
    else closeViewer()
    return
  }
  if (actionsOpen.value || infoOpen.value) return
  if (e.metaKey || e.ctrlKey || e.altKey) return
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    step(-1)
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    step(1)
  } else if (e.key === 'i' || e.key === 'I') {
    e.preventDefault()
    toggleInfo()
  }
}

function onResize() {
  vw.value = window.innerWidth
  vh.value = window.innerHeight
  dragX.value = 0
  resetZoom()
  if (phase.value === 'open') bgAlpha.value = 1
}

watch(() => props.open, value => {
  if (value) {
    if (phase.value === 'closed' || phase.value === 'closing') beginOpen()
    return
  }
  if (phase.value === 'open') closeViewer()
  else if (phase.value === 'opening') finishClose()
})

watch(() => props.index, value => {
  const next = clampIndex(value)
  if (phase.value === 'closed') {
    displayIndex.value = next
    return
  }
  if (phase.value !== 'open' && phase.value !== 'opening') return
  if (next === displayIndex.value) return
  displayIndex.value = next
  dragX.value = 0
  trackAnimating.value = false
  resetZoom()
  pauseLive()
})

watch(() => props.photos.length, () => {
  displayIndex.value = clampIndex(displayIndex.value)
})

watch(() => photo.value?.id, () => {
  if (phase.value === 'open') {
    resetZoom()
    pauseLive()
  }
})

onMounted(() => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('resize', onResize)
  if (props.open) beginOpen()
})

onBeforeUnmount(() => {
  clearTimers()
  pauseLive()
  unlockBody()
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-show="phase !== 'closed'"
      ref="rootEl"
      class="m-viewer"
      :class="{ 'chrome-hidden': chromeHidden, 'is-dismissing': dismissActive, 'is-transitioning': phase === 'opening' || phase === 'closing' }"
      role="dialog"
      aria-modal="true"
      :aria-label="dialogLabel"
      :style="rootStyle"
      tabindex="-1"
    >
      <div
        ref="stageEl"
        class="stage"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerCancel"
      >
        <div class="track" :style="trackStyle">
          <div
            v-for="p in windowPages"
            :key="p.offset"
            class="page"
            :class="{ 'is-neighbor': p.offset !== 0, show: p.offset === 0 || dragging || trackAnimating }"
            :style="p.offset === 0 ? activePageStyle : undefined"
          >
            <template v-if="p.photo">
              <div class="canvas" :style="p.offset === 0 ? canvasStyle : undefined">
                <img
                  v-if="!brokenImages.has(p.photo.id) && srcFor(p.photo)"
                  class="page-img"
                  :src="srcFor(p.photo)"
                  :alt="p.photo.name || '照片'"
                  loading="eager"
                  decoding="async"
                  draggable="false"
                  @error="markFailed(p.photo)"
                >
                <div v-else class="page-fallback">
                  <Icon name="image" :size="42" />
                  <span>{{ p.photo.name || '预览暂不可用' }}</span>
                </div>
              </div>
              <video
                v-if="videoUrlOf(p.photo)"
                :ref="el => setVideoRef(el, p.offset)"
                class="page-video"
                :class="{ playing: livePlaying && p.offset === 0 }"
                :src="mediaUrl(videoUrlOf(p.photo))"
                muted
                playsinline
                preload="metadata"
                @ended="livePlaying = false"
              />
            </template>
          </div>
        </div>
      </div>

      <img v-if="heroRect && heroSrc" class="hero" :style="heroStyle" :src="heroSrc" alt="" draggable="false" @error="onHeroError">

      <div v-if="isLive" class="live-pill" :class="{ playing: livePlaying }" aria-hidden="true"><i />LIVE</div>

      <header class="top-bar">
        <button class="bar-btn" type="button" aria-label="返回" @click="closeViewer">
          <Icon name="chevron-left" :size="24" />
        </button>
        <div class="top-title" aria-hidden="true">
          <b>{{ dateLine }}</b>
          <span>{{ timeLine }}</span>
        </div>
        <button class="bar-btn" type="button" aria-label="更多操作" @click="openActions">
          <Icon name="more" :size="22" />
        </button>
      </header>

      <footer class="bottom-bar">
        <div class="toolbar">
          <button class="tool" type="button" aria-label="分享" @click="sharePhoto">
            <Icon name="share" :size="22" /><span>分享</span>
          </button>
          <button class="tool fav" :class="{ on: favOn }" type="button" :aria-label="favOn ? '取消收藏' : '收藏'" @click="toggleFav">
            <Icon name="heart" :size="22" /><span>收藏</span>
          </button>
          <button class="tool" type="button" aria-label="照片信息" @click="toggleInfo">
            <Icon name="info" :size="22" /><span>信息</span>
          </button>
          <button class="tool" type="button" aria-label="编辑照片" :disabled="!canEdit" @click="editPhoto">
            <Icon name="edit" :size="22" /><span>编辑</span>
          </button>
          <button class="tool" type="button" aria-label="删除照片" :disabled="!canEdit" @click="deletePhoto">
            <Icon name="trash" :size="22" /><span>删除</span>
          </button>
        </div>
      </footer>

      <div class="overlay info-overlay" :class="{ show: infoOpen }" :aria-hidden="!infoOpen" :inert="!infoOpen">
        <div class="overlay-mask" @click="closeInfo" />
        <section class="info-sheet" :class="{ show: infoOpen }" :style="infoSheetStyle" role="dialog" aria-modal="true" aria-label="照片信息">
          <div class="sheet-grip" @pointerdown="onSheetDown" @pointermove="onSheetMove" @pointerup="onSheetUp" @pointercancel="onSheetUp"><span /></div>
          <div class="info-head">
            <h2>信息</h2>
            <button type="button" aria-label="关闭信息" @click="closeInfo"><Icon name="close" :size="18" /></button>
          </div>
          <div class="info-body">
            <div class="info-row"><span>文件名</span><b>{{ photo?.name || '未命名照片' }}</b></div>
            <div class="info-row"><span>大小</span><b>{{ formatBytes(photo?.picSize) }}</b></div>
            <div class="info-row"><span>尺寸</span><b>{{ photo?.picWidth && photo?.picHeight ? photo.picWidth + ' × ' + photo.picHeight : '—' }}</b></div>
            <div class="info-row"><span>格式</span><b>{{ (photo?.picFormat || fileExt(photo?.url)).toUpperCase() }}</b></div>
            <div class="info-row"><span>拍摄时间</span><b>{{ formatDate(photo?.createTime, true) }}</b></div>
            <div class="info-row"><span>上传者</span><b>{{ photo?.user?.userName || photo?.user?.userAccount || '—' }}</b></div>
            <div class="info-row"><span>分类</span><b>{{ photo?.category || '—' }}</b></div>
            <div v-if="photo?.tags?.length" class="info-tags">
              <span v-for="tag in (photo?.tags || [])" :key="tag">{{ tag }}</span>
            </div>
            <p v-if="photo?.introduction" class="info-intro">{{ photo.introduction }}</p>
            <button v-if="canEdit" class="info-outpaint" type="button" aria-label="AI 扩图" @click="outpaintingPhoto">
              <Icon name="sparkles" :size="16" />AI 扩图
            </button>
          </div>
        </section>
      </div>

      <div class="overlay action-overlay" :class="{ show: actionsOpen }" :aria-hidden="!actionsOpen" :inert="!actionsOpen">
        <div class="overlay-mask" @click="actionsOpen = false" />
        <div class="action-wrap" role="dialog" aria-modal="true" aria-label="更多操作">
          <div class="action-group">
            <button type="button" class="action-item" aria-label="存储到文件" @click="downloadOriginal">存储到文件</button>
            <button type="button" class="action-item" aria-label="复制链接" @click="copyLink">复制链接</button>
            <button type="button" class="action-item" aria-label="显示信息" @click="openInfoFromActions">显示信息</button>
            <button v-if="canEdit" type="button" class="action-item danger" aria-label="删除照片" @click="deletePhoto">删除</button>
          </div>
          <button type="button" class="action-cancel" aria-label="取消" @click="actionsOpen = false">取消</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.m-viewer{position:fixed;inset:0;z-index:900;overflow:hidden;color:#fff;font-family:-apple-system,BlinkMacSystemFont,'SF Pro Text','Helvetica Neue','PingFang SC',sans-serif;background:#000;overscroll-behavior:none;-webkit-tap-highlight-color:transparent;--vb-blue:var(--ios-blue,#0A84FF);--vb-blue-soft:var(--ios-blue-soft,rgba(10,132,255,.16));--vb-line:var(--m-line,rgba(255,255,255,.14));--vb-line-soft:var(--m-line-soft,rgba(255,255,255,.08));--vb-text2:var(--m-text-2,rgba(235,235,245,.6));--vb-text3:var(--m-text-3,rgba(235,235,245,.32));--vb-fill:var(--m-fill,rgba(118,118,128,.24));--vb-safe-top:var(--safe-top,max(env(safe-area-inset-top,0px),59px));--vb-safe-bottom:var(--safe-bottom,max(env(safe-area-inset-bottom,0px),34px))}
.m-viewer:focus{outline:none}
.m-viewer,.m-viewer *,.m-viewer *::before,.m-viewer *::after{box-sizing:border-box}
.m-viewer button{font:inherit;color:inherit;-webkit-tap-highlight-color:transparent}
.stage{position:absolute;inset:0;z-index:1;overflow:hidden;touch-action:none;user-select:none;-webkit-user-select:none;}
.is-transitioning .stage{opacity:0}
.is-transitioning .top-bar,.is-transitioning .bottom-bar,.is-transitioning .live-pill{opacity:0;pointer-events:none}
.track{position:absolute;top:0;left:0;width:300%;height:100%;display:flex;will-change:transform}
.page{position:relative;flex:0 0 33.333333%;height:100%;overflow:hidden;transition:opacity 100ms linear}
.page.is-neighbor{opacity:0}
.page.is-neighbor.show{opacity:1}
.canvas{position:absolute;inset:0;transform-origin:0 0;will-change:transform}
.page-img{display:block;width:100%;height:100%;object-fit:contain;pointer-events:none;user-select:none;-webkit-user-drag:none;will-change:transform}
.page-video{position:absolute;inset:0;width:100%;height:100%;object-fit:contain;background:#000;opacity:0;pointer-events:none;transition:opacity 160ms ease;will-change:transform}
.page-video.playing{opacity:1}
.page-fallback{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:var(--vb-text3);font-size:13px}
.hero{position:absolute;z-index:3;display:block;object-fit:cover;transform-origin:0 0;pointer-events:none;user-select:none;-webkit-user-drag:none;will-change:transform,opacity}
.live-pill{position:absolute;z-index:12;left:16px;top:calc(var(--vb-safe-top) + 56px);display:flex;align-items:center;gap:5px;height:22px;padding:0 8px;border-radius:11px;background:rgba(0,0,0,.6);font-size:9px;font-weight:700;letter-spacing:.08em;color:#fff;transition:opacity var(--dur-fade) var(--spring-fade)}
.live-pill i{width:6px;height:6px;border-radius:50%;background:#FF453A;box-shadow:0 0 6px rgba(255,69,58,.8)}
.live-pill.playing i{animation:livepulse 1.2s ease-in-out infinite}
.chrome-hidden .live-pill:not(.playing),.is-dismissing .live-pill{opacity:0}
@keyframes livepulse{0%,100%{opacity:1}50%{opacity:.35}}
.top-bar{position:absolute;z-index:10;top:0;left:0;right:0;height:calc(44px + var(--vb-safe-top));padding-top:var(--vb-safe-top);display:flex;align-items:center;justify-content:space-between;background:linear-gradient(180deg,rgba(0,0,0,.82) 0%,rgba(0,0,0,.46) 58%,rgba(0,0,0,0) 100%);transition:opacity var(--dur-fade) var(--spring-fade)}
.bottom-bar{position:absolute;z-index:10;left:0;right:0;bottom:0;padding-bottom:var(--vb-safe-bottom);background:linear-gradient(0deg,rgba(0,0,0,.88) 0%,rgba(0,0,0,.52) 58%,rgba(0,0,0,0) 100%);transition:opacity var(--dur-fade) var(--spring-fade)}
.chrome-hidden .top-bar,.chrome-hidden .bottom-bar,.is-dismissing .top-bar,.is-dismissing .bottom-bar{opacity:0;pointer-events:none}
.bar-btn:active{transform:scale(.86);background:rgba(255,255,255,.14)}
.bar-btn{width:44px;height:44px;flex:none;display:grid;place-items:center;padding:0;border:0;border-radius:50%;background:transparent;color:#fff;transition:transform var(--dur-press) var(--spring-press),background var(--dur-press) ease}
.bar-btn:active{transform:scale(.9);background:rgba(255,255,255,.08);transition-duration:100ms,100ms;transition-timing-function:ease-out,ease-out}
.top-title{position:absolute;left:50%;top:calc(var(--vb-safe-top) + 5px);transform:translateX(-50%);display:flex;flex-direction:column;align-items:center;gap:1px;line-height:1.15;pointer-events:none;white-space:nowrap}
.top-title b{font-size:13px;font-weight:600;letter-spacing:.01em;color:#fff}
.top-title span{font-size:11px;color:var(--vb-text2);font-variant-numeric:tabular-nums}
.toolbar{height:49px;max-width:520px;margin:0 auto;display:grid;grid-template-columns:repeat(5,1fr);padding:0 4px}
.tool:active{transform:scale(.88);opacity:.62}
.tool{height:49px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;padding:0;border:0;background:transparent;color:#fff;font-family:inherit;transition:opacity var(--dur-press) ease,transform var(--dur-press) var(--spring-press)}
.tool span{font-size:10px;line-height:1;letter-spacing:.02em;color:rgba(255,255,255,.92)}
.tool:active{transform:scale(.94);opacity:.7;transition-duration:100ms,100ms;transition-timing-function:ease-out,ease-out}
.tool:disabled{opacity:.28;pointer-events:none}
.tool.fav.on{color:var(--vb-blue)}
.tool.fav.on :deep(svg){fill:currentColor}
.overlay{position:absolute;inset:0;pointer-events:none}
.overlay.show{pointer-events:auto}
.overlay-mask{position:absolute;inset:0;background:rgba(0,0,0,.45);opacity:0;transition:opacity var(--dur-fade) var(--spring-fade)}
.overlay.show .overlay-mask{opacity:1}
.info-overlay{z-index:32}
.info-sheet{position:absolute;left:0;right:0;bottom:0;height:52vh;min-height:300px;max-height:80vh;display:flex;flex-direction:column;padding-bottom:var(--vb-safe-bottom);border-radius:14px 14px 0 0;background:#1b1b1d;box-shadow:0 -12px 40px rgba(0,0,0,.45);transform:translateY(105%);transition:transform var(--dur-sheet) var(--spring-sheet);will-change:transform}
.info-sheet.show{transform:translateY(0)}
.sheet-grip{height:24px;flex:none;display:flex;align-items:center;justify-content:center;cursor:grab;touch-action:none}
.sheet-grip span{width:36px;height:5px;border-radius:3px;background:rgba(235,235,245,.28)}
.info-head{height:38px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 16px 0 20px}
.info-head h2{margin:0;font-size:17px;font-weight:600}
.info-head button{width:30px;height:30px;display:grid;place-items:center;padding:0;border:0;border-radius:50%;background:var(--vb-fill);color:rgba(255,255,255,.82)}
.info-body{flex:1;overflow-y:auto;-webkit-overflow-scrolling:touch;overscroll-behavior:contain;padding:0 20px 18px}
.info-row{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:11px 0;border-bottom:.5px solid var(--vb-line-soft);font-size:14px}
.info-row span{flex:none;color:var(--vb-text2)}
.info-row b{font-weight:400;line-height:1.4;text-align:right;word-break:break-word}
.info-tags{display:flex;flex-wrap:wrap;gap:6px;padding:12px 0;border-bottom:.5px solid var(--vb-line-soft)}
.info-tags span{padding:3px 9px;border-radius:9px;background:var(--vb-fill);font-size:12px;color:rgba(235,235,245,.8)}
.info-intro{margin:14px 0 0;font-size:13px;line-height:1.65;color:var(--vb-text2);white-space:pre-wrap}
.info-outpaint{display:inline-flex;align-items:center;gap:6px;margin-top:16px;padding:9px 14px;border:0;border-radius:10px;background:var(--vb-blue-soft);color:var(--vb-blue);font-family:inherit;font-size:13px}
.action-overlay{z-index:40}
.action-wrap{position:absolute;left:8px;right:8px;bottom:calc(8px + var(--vb-safe-bottom));transform:translateY(calc(100% + 20px));transition:transform var(--dur-sheet) var(--spring-sheet);will-change:transform}
.action-overlay.show .action-wrap{transform:translateY(0)}
.action-group{overflow:hidden;border-radius:14px;background:rgba(28,28,30,.98);box-shadow:0 10px 40px rgba(0,0,0,.45)}
.action-item{display:block;width:100%;height:57px;padding:0 20px;border:0;border-top:.5px solid rgba(255,255,255,.08);background:transparent;color:#fff;font-family:inherit;font-size:17px;text-align:left}
.action-item:first-child{border-top:0}
.action-item:active{background:rgba(255,255,255,.08)}
.action-item.danger{color:#FF453A}
.action-cancel{display:block;width:100%;height:57px;margin-top:8px;border:0;border-radius:14px;background:rgba(28,28,30,.98);color:#fff;font-family:inherit;font-size:17px;font-weight:600}
.action-cancel:active{background:rgba(44,44,46,.95)}
</style>