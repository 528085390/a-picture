<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { PictureVO } from '@/api/types'
import { monthKey, monthRange, parseDate, pad, formatBytes } from '@/utils/format'
import { useThumbZoom, ZOOM_STEPS } from '@/composables/useThumbZoom'
import Icon from './Icon.vue'
import { mediaUrl, thumbUrl, originThumbUrl } from '@/utils/media'
import { vLazySrc } from '@/directives/lazySrc'

const props = withDefaults(defineProps<{
  photos: PictureVO[]
  loading?: boolean
  loadingMore?: boolean
  hasMore?: boolean
  selectMode?: boolean
  selectable?: boolean
  canBatchDelete?: boolean
  canAddToSpace?: boolean
  showLiveBadge?: boolean
  emptyTitle?: string
  emptyText?: string
  moreLabel?: string
  flat?: boolean
}>(), { loading: false, loadingMore: false, hasMore: false, selectMode: false, selectable: true, canBatchDelete: false, canAddToSpace: false, showLiveBadge: false, emptyTitle: '暂无照片', emptyText: '这里还没有影像。上传第一张照片，开始建立你的私人档案馆。', moreLabel: '加载更多', flat: false })
const emit = defineEmits<{
  open: [index: number]
  'load-more': []
  'update:selectMode': [v: boolean]
  'batch-delete': [ids: string[]]
  'batch-add': [ids: string[]]
  'selection-change': [ids: string[]]
}>()
const { thumb, stepZoom, setZoom } = useThumbZoom()
const scrollEl = ref<HTMLElement | null>(null)
const rootEl = ref<HTMLElement | null>(null)
const selected = ref<Set<string>>(new Set())
const failed = ref<Set<string>>(new Set())
const marquee = ref<HTMLElement | null>(null)
const showMarquee = ref(false)
let suppressClickUntil = 0
let marqueeBox: { startX: number; startY: number; curX: number; curY: number; base: Set<string>; startCard: Element | null } | null = null
let marqueeRAF = 0
let zoomFlipTimer = 0
let zoomFlipCards: HTMLElement[] = []
let wheelAccum = 0
let panRAF = 0
let wheelLockedUntil = 0
let zoomPointer: { x: number; y: number } | null = null
let scrollIdleTimer = 0
let autoLoadArmed = true
let autoLoadCooldownUntil = 0

const groups = computed(() => {
  if (props.flat) return props.photos.length ? [{ key: 'flat', items: props.photos, start: 0, dates: [] }] : []
  const map = new Map<string, { key: string; items: PictureVO[]; start: number; dates: Date[] }>()
  props.photos.forEach((p, i) => {
    const key = monthKey(p.createTime)
    if (!map.has(key)) map.set(key, { key, items: [], start: i, dates: [] })
    map.get(key)!.items.push(p)
    const d = parseDate(p.createTime); if (d) map.get(key)!.dates.push(d)
  })
  return [...map.values()]
})
const selectedCount = computed(() => selected.value.size)
const allSelected = computed(() => props.photos.length > 0 && selected.value.size >= props.photos.length)

watch(() => props.selectMode, v => {
  document.body.classList.toggle('select-mode', v)
  if (!v) selected.value = new Set()
  emit('selection-change', [])
})
watch(selected, v => { emit('selection-change', [...v]) }, { deep: true })
function clearZoomFlip() {
  window.clearTimeout(zoomFlipTimer)
  zoomFlipCards.forEach(card => {
    card.style.transition = 'none'
    card.classList.remove('zoom-flip')
    card.style.transform = ''
    card.style.transformOrigin = ''
    card.style.willChange = ''
    requestAnimationFrame(() => {
      if (!card.classList.contains('zoom-flip')) card.style.transition = ''
    })
  })
  zoomFlipCards = []
}

/* 滚轮缩放时给当前视口附近的卡片做 FLIP。
   细档位 + 指针锚定 + 稍长的缓出，缩放时保持鼠标下的照片不漂移。 */
watch(thumb, (next, prev) => {
  if (next === prev) return
  const root = rootEl.value, scroller = scrollEl.value
  if (!root || !scroller) { zoomPointer = null; return }
  const reduced = document.documentElement.classList.contains('static') || matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduced) {
    clearZoomFlip(); scroller.style.setProperty('--thumb', next + 'px'); zoomPointer = null; return
  }
  const sr = scroller.getBoundingClientRect()
  const pointer = zoomPointer || { x: sr.left + sr.width * .5, y: sr.top + sr.height * .35 }
  zoomPointer = null
  const firstRects = new Map<HTMLElement, DOMRect>()
  root.querySelectorAll<HTMLElement>('.card').forEach(card => {
    const r = card.getBoundingClientRect()
    if (r.bottom > sr.top - 160 && r.top < sr.bottom + 160 && r.right > sr.left - 160 && r.left < sr.right + 160) firstRects.set(card, r)
  })
  if (!firstRects.size) { scroller.style.setProperty('--thumb', next + 'px'); return }
  // 找到鼠标下的卡片作为锚点，缩放后补偿滚动，避免视线里的照片整体上跳/下沉。
  let anchorEl: HTMLElement | null = null
  const hit = document.elementFromPoint(pointer.x, pointer.y)
  const hitCard = hit && (hit as Element).closest ? (hit as Element).closest('.card') as HTMLElement | null : null
  if (hitCard && firstRects.has(hitCard)) anchorEl = hitCard
  if (!anchorEl) {
    let bestD = Infinity
    firstRects.forEach((r, card) => {
      const dx = Math.max(r.left - pointer.x, 0, pointer.x - r.right)
      const dy = Math.max(r.top - pointer.y, 0, pointer.y - r.bottom)
      const d = dx * dx + dy * dy
      if (d < bestD) { bestD = d; anchorEl = card }
    })
  }
  const anchorTop = anchorEl ? firstRects.get(anchorEl)?.top : undefined
  // 先把上一档动画定格，再立即应用新布局；这里用 sync watch，避免等 Vue patch 带来的一帧停顿。
  clearZoomFlip()
  scroller.style.setProperty('--thumb', next + 'px')
  if (anchorEl && anchorTop != null) {
    const delta = anchorEl.getBoundingClientRect().top - anchorTop
    if (Math.abs(delta) > .5) scroller.scrollTop += delta
  }
  const moving: HTMLElement[] = []
  firstRects.forEach((first, card) => {
    const last = card.getBoundingClientRect()
    const dx = first.left - last.left, dy = first.top - last.top
    const sx = first.width / last.width, sy = first.height / last.height
    if (Math.abs(dx) < .5 && Math.abs(dy) < .5 && Math.abs(sx - 1) < .002 && Math.abs(sy - 1) < .002) return
    card.classList.add('zoom-flip')
    card.style.transition = 'none'
    card.style.transformOrigin = 'top left'
    card.style.transform = 'translate3d(' + dx.toFixed(2) + 'px,' + dy.toFixed(2) + 'px,0) scale(' + sx.toFixed(4) + ',' + sy.toFixed(4) + ')'
    moving.push(card)
  })
  zoomFlipCards = moving
  if (!moving.length) return
  void root.offsetWidth
  requestAnimationFrame(() => {
    moving.forEach(card => {
      card.style.transition = 'transform 520ms var(--ease-out)'
      card.style.transform = 'translate3d(0,0,0) scale(1,1)'
    })
  })
  zoomFlipTimer = window.setTimeout(clearZoomFlip, 600)
}, { flush: 'sync' })
watch(scrollEl, (el, old) => {
  old?.removeEventListener('wheel', onWheel)
  el?.addEventListener('wheel', onWheel, { passive: false })
}, { flush: 'post' })
onBeforeUnmount(() => {
  document.body.classList.remove('select-mode', 'panning', 'box-selecting')
  cancelAnimationFrame(panRAF); cancelAnimationFrame(marqueeRAF); clearZoomFlip(); window.clearTimeout(scrollIdleTimer); window.clearTimeout(autoLoadTimer); autoObserver?.disconnect(); scrollEl.value?.removeEventListener('wheel', onWheel); document.body.classList.remove('scrolling')
  window.removeEventListener('pointermove', onPanMove); window.removeEventListener('pointerup', onPanUp); window.removeEventListener('pointercancel', onPanUp)
})

// 三级回退：同源缩略图 → Cloudflare 缩略图 → 1280 预览图，全失败才显示占位块
const fallbackStage = ref<Map<string, number>>(new Map())
function imageSrc(p: PictureVO) {
  const source = p.previewUrl || p.url || ''
  const stage = fallbackStage.value.get(p.id) || 0
  if (stage === 0) return thumbUrl(source, 360)
  if (stage === 1) return originThumbUrl(source, 360)
  return mediaUrl(source)
}
function onThumbLoad(e: Event) { (e.target as HTMLImageElement).classList.add('is-loaded') }
function onThumbError(p: PictureVO, ev?: Event) {
  ;(ev?.target as HTMLImageElement | undefined)?.classList.remove('is-loaded')
  const stage = fallbackStage.value.get(p.id) || 0
  if (stage < 2) {
    const next = new Map(fallbackStage.value)
    next.set(p.id, stage + 1)
    fallbackStage.value = next
    return
  }
  markFailed(p.id)
}
function markFailed(id: string) { const next = new Set(failed.value); next.add(id); failed.value = next }
function dims(p: PictureVO) { return p.picWidth && p.picHeight ? p.picWidth + '×' + p.picHeight : (p.picFormat || '原始影像').toUpperCase() }
function size(p: PictureVO) { return p.picSize ? formatBytes(p.picSize) : '' }
function toggleSelect(id: string, force?: boolean) {
  if (!props.selectable) return
  if (!props.selectMode && !force) emit('update:selectMode', true)
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id); else next.add(id)
  selected.value = next
}
function onCardClick(index: number, p: PictureVO, ev: MouseEvent) {
  if (performance.now() < suppressClickUntil) return
  if (props.selectMode || ev.shiftKey) { toggleSelect(p.id, true); return }
  emit('open', index)
}
function toggleMode() { emit('update:selectMode', !props.selectMode) }
function selectAll() { selected.value = allSelected.value ? new Set() : new Set(props.photos.map(p => p.id)) }
function done() { emit('update:selectMode', false) }
async function downloadSelected() {
  const { pictureApi } = await import('@/api/picture')
  const ids = [...selected.value]
  for (const id of ids) {
    const p = props.photos.find(x => x.id === id); if (!p?.url) continue
    try {
      const blob = await pictureApi.download(p.url)
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = (p.name || 'photo') + '.' + (p.picFormat || 'jpg'); a.click(); URL.revokeObjectURL(a.href)
    } catch { /* silent individual failure */ }
  }
}
function emitDelete() { if (props.canBatchDelete) emit('batch-delete', [...selected.value]) }
function emitAdd() { if (props.canAddToSpace) emit('batch-add', [...selected.value]) }

/* wheel zoom over the grid: no modifier, so it never conflicts with browser page zoom. */
function onWheel(e: WheelEvent) {
  const target = e.target as Element
  if (!target.closest('.grid')) return
  const el = scrollEl.value
  if (!el) return
  // Shift + wheel is the escape hatch for browsing without leaving the grid.
  if (e.shiftKey && !e.ctrlKey && !e.metaKey) {
    e.preventDefault()
    stopMomentum()
    el.scrollTop += e.deltaY
    return
  }
  e.preventDefault()
  const now = performance.now()
  const cur = thumb.value
  const dir = e.deltaY < 0 ? 1 : -1
  if ((dir > 0 && cur >= ZOOM_STEPS[ZOOM_STEPS.length - 1]) || (dir < 0 && cur <= ZOOM_STEPS[0])) { wheelAccum = 0; return }
  wheelAccum += e.deltaY
  if (now < wheelLockedUntil) return
  if (Math.abs(wheelAccum) < 16) return
  wheelAccum = 0
  wheelLockedUntil = now + 105
  // 一档一档推进；档位更细，滚轮的每个 notch 不会一次跳过大半屏。
  let idx = 0
  ZOOM_STEPS.forEach((s, i) => { if (Math.abs(s - cur) < Math.abs(ZOOM_STEPS[idx] - cur)) idx = i })
  const next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, idx + dir))
  if (ZOOM_STEPS[next] !== cur) { zoomPointer = { x: e.clientX, y: e.clientY }; setZoom(ZOOM_STEPS[next]) }
}

/* pan: soft follow + inertia (mouse only) */
function maxPanTop() { return Math.max(0, (scrollEl.value?.scrollHeight || 0) - (scrollEl.value?.clientHeight || 0)) }
function clampPan(v: number) { return Math.max(0, Math.min(maxPanTop(), v)) }
function beginGlide(speed: number, remaining = 0) {
  const el = scrollEl.value; if (!el) return
  if (document.documentElement.classList.contains('static') || matchMedia('(prefers-reduced-motion: reduce)').matches) {
    el.scrollTop = clampPan(el.scrollTop + remaining); return
  }
  // Keep the travel, but spread it over a longer, linear deceleration.
  // At least 2s of tail: 10-9-8-7-6-5-4-3-2-1-0 instead of a short 10-8-5-3-0 drop.
  const v0 = Math.max(-650, Math.min(650, speed))
  const direction = Math.sign(v0)
  const duration = Math.abs(v0) > 1 ? Math.min(2.6, Math.max(2, Math.abs(v0) / 250)) : 1.2
  const startTop = el.scrollTop
  const coast = Math.abs(v0) * duration / 2
  let elapsed = 0, last = performance.now()
  const glide = (now: number) => {
    const dt = Math.min(.05, Math.max(.001, (now - last) / 1000)); last = now; elapsed += dt
    const u = Math.min(1, elapsed / duration); const ease = 1 - Math.pow(1 - u, 2)
    const before = el.scrollTop
    el.scrollTop = clampPan(startTop + remaining * ease + direction * coast * ease)
    if (u >= 1) { panRAF = 0; return }
    if (el.scrollTop === before && Math.abs(startTop + remaining * ease + direction * coast * ease - before) > .5) { panRAF = 0; return }
    panRAF = requestAnimationFrame(glide)
  }
  panRAF = requestAnimationFrame(glide)
}
let panState: { sx: number; sy: number; startTop: number; targetTop: number; currentTop: number; engaged: boolean; lastFrame: number; samples: { y: number; t: number }[] } | null = null
function onPointerDown(e: PointerEvent) {
  onBoxDown(e)
  onPanDown(e)
}
function onPanDown(e: PointerEvent) {
  if ((e.pointerType && e.pointerType !== 'mouse') || e.button !== 0) return
  if ((e.target as Element).closest('button,a,input,.check')) return
  if (props.selectMode || e.shiftKey) return
  cancelAnimationFrame(panRAF); panRAF = 0
  const el = scrollEl.value; if (!el) return
  panState = { sx: e.clientX, sy: e.clientY, startTop: el.scrollTop, targetTop: el.scrollTop, currentTop: el.scrollTop, engaged: false, lastFrame: 0, samples: [] }
  window.addEventListener('pointermove', onPanMove, { passive: false })
  window.addEventListener('pointerup', onPanUp)
  window.addEventListener('pointercancel', onPanUp)
}
function record(y: number) {
  if (!panState) return
  const now = performance.now(); panState.samples.push({ y, t: now })
  while (panState.samples.length > 12 || now - panState.samples[0].t > 130) panState.samples.shift()
}
function onPanMove(e: PointerEvent) {
  const el = scrollEl.value
  if (!panState || !el) return
  if (!panState.engaged) {
    if (Math.hypot(e.clientX - panState.sx, e.clientY - panState.sy) <= 3) return
    panState.engaged = true; panState.currentTop = el.scrollTop; panState.targetTop = el.scrollTop
    panState.lastFrame = performance.now()
    document.body.classList.add('panning'); suppressClickUntil = performance.now() + 700
    const frame = (now: number) => {
      if (!panState || !panState.engaged) { panRAF = 0; return }
      const dt = Math.min(.04, Math.max(.001, (now - (panState.lastFrame || now)) / 1000)); panState.lastFrame = now
      panState.currentTop += (panState.targetTop - panState.currentTop) * (1 - Math.exp(-55 * dt))
      if (Math.abs(panState.targetTop - panState.currentTop) < .25) panState.currentTop = panState.targetTop
      el.scrollTop = panState.currentTop
      panRAF = requestAnimationFrame(frame)
    }
    panRAF = requestAnimationFrame(frame)
  }
  e.preventDefault()
  panState.targetTop = clampPan(panState.startTop - (e.clientY - panState.sy))
  record(e.clientY)
}
function onPanUp() {
  window.removeEventListener('pointermove', onPanMove); window.removeEventListener('pointerup', onPanUp); window.removeEventListener('pointercancel', onPanUp)
  if (!panState || !panState.engaged) { panState = null; return }
  cancelAnimationFrame(panRAF); panRAF = 0
  document.body.classList.remove('panning')
  const now = performance.now(); const recent = panState.samples.filter(s => now - s.t < 110)
  let v = 0
  if (recent.length >= 2) { const a = recent[0], b = recent[recent.length - 1]; const dt = b.t - a.t; if (dt >= 12) v = Math.max(-2.4, Math.min(2.4, (b.y - a.y) / dt)) }
  const remaining = panState.targetTop - panState.currentTop
  panState = null; suppressClickUntil = performance.now() + 380
  if (Math.abs(v) > .03 || Math.abs(remaining) > .5) beginGlide(-v * 1000, remaining)
}
function stopMomentum() { cancelAnimationFrame(panRAF); panRAF = 0 }

/* marquee: long press in select mode / shift */
function cancelPanForBox() {
  panState = null
  window.removeEventListener('pointermove', onPanMove)
  window.removeEventListener('pointerup', onPanUp)
  window.removeEventListener('pointercancel', onPanUp)
  cancelAnimationFrame(panRAF); panRAF = 0
  document.body.classList.remove('panning')
}
function onBoxDown(e: PointerEvent) {
  if ((e.pointerType && e.pointerType !== 'mouse') || e.button !== 0) return
  if ((e.target as Element).closest('button,a,input')) return
  if (!(e.target as Element).closest('.grid')) return
  const startCard = (e.target as Element).closest('.card')
  const sx = e.clientX, sy = e.clientY, additive = e.shiftKey
  let started = false
  const timer = window.setTimeout(() => {
    started = true
    cancelPanForBox()
    suppressClickUntil = performance.now() + 700
    if (!props.selectMode) emit('update:selectMode', true)
    const base = additive ? new Set(selected.value) : new Set<string>()
    if (!additive) selected.value = new Set()
    marqueeBox = { startX: sx, startY: sy, curX: sx, curY: sy, base, startCard }
    document.body.classList.add('box-selecting'); showMarquee.value = true
    if (marquee.value) marquee.value.hidden = false
    drawMarquee(); marqueeRAF = requestAnimationFrame(tickBox)
  }, 260)
  const cleanup = () => { window.clearTimeout(timer); window.removeEventListener('pointermove', onMove); window.removeEventListener('pointerup', onUp); window.removeEventListener('pointercancel', onUp) }
  const onMove = (ev: PointerEvent) => {
    if (!started) { if (Math.hypot(ev.clientX - sx, ev.clientY - sy) > 6) { suppressClickUntil = performance.now() + 240; cleanup() } return }
    ev.preventDefault(); if (!marqueeBox) return
    marqueeBox.curX = ev.clientX; marqueeBox.curY = ev.clientY
    drawMarquee(); updateMarqueeSelection()
  }
  const onUp = () => { cleanup(); if (started) finishBox() }
  window.addEventListener('pointermove', onMove, { passive: false }); window.addEventListener('pointerup', onUp); window.addEventListener('pointercancel', onUp)
}
function drawMarquee() {
  const el = marquee.value; if (!el || !marqueeBox) return
  const x = Math.min(marqueeBox.startX, marqueeBox.curX), y = Math.min(marqueeBox.startY, marqueeBox.curY)
  el.style.transform = 'translate(' + x + 'px,' + y + 'px)'
  el.style.width = Math.abs(marqueeBox.curX - marqueeBox.startX) + 'px'
  el.style.height = Math.abs(marqueeBox.curY - marqueeBox.startY) + 'px'
}
function updateMarqueeSelection() {
  const el = marquee.value; if (!el || !marqueeBox || !rootEl.value) return
  const mr = el.getBoundingClientRect()
  const next = new Set(marqueeBox.base)
  rootEl.value.querySelectorAll('.card').forEach(card => {
    const cr = card.getBoundingClientRect()
    const hit = cr.left < mr.right && cr.right > mr.left && cr.top < mr.bottom && cr.bottom > mr.top
    const id = (card as HTMLElement).dataset.id || ''
    if (hit) next.add(id); else if (!marqueeBox!.base.has(id)) next.delete(id)
    card.classList.toggle('selected', next.has(id))
  })
  selected.value = next
}
function tickBox() {
  const el = scrollEl.value; if (!marqueeBox || !el) { marqueeRAF = 0; return }
  let dy = 0
  if (marqueeBox.curY < 64) dy = -16; else if (window.innerHeight - marqueeBox.curY < 64) dy = 16
  if (dy) { el.scrollBy(0, dy); updateMarqueeSelection() }
  marqueeRAF = requestAnimationFrame(tickBox)
}
function finishBox() {
  if (!marqueeBox) return
  const noMove = Math.abs(marqueeBox.curX - marqueeBox.startX) < 4 && Math.abs(marqueeBox.curY - marqueeBox.startY) < 4
  if (noMove && marqueeBox.startCard) {
    const id = (marqueeBox.startCard as HTMLElement).dataset.id || ''
    if (id) selected.value = new Set([...selected.value, id])
  }
  marqueeBox = null; showMarquee.value = false
  if (marquee.value) marquee.value.hidden = true
  document.body.classList.remove('box-selecting'); cancelAnimationFrame(marqueeRAF); marqueeRAF = 0
  suppressClickUntil = performance.now() + 360
}
/* 自动加载：用挂在列表末尾的哨兵 + IntersectionObserver 判断「快到底了」。
   旧写法在每条 scroll 事件里同步读 scrollHeight/scrollTop/clientHeight，卡片上百张时
   每次都触发一次同步布局（实测单次 70–140ms），是桌面滚动掉帧的主因。 */
const loadSentinel = ref<HTMLElement | null>(null)
let autoObserver: IntersectionObserver | null = null
let autoLoadTimer = 0
let lastScrollAt = 0
let scrolling = false

function armAutoLoad() {
  autoObserver?.disconnect()
  const root = scrollEl.value, sentinel = loadSentinel.value
  if (!root || !sentinel) return
  autoObserver = new IntersectionObserver((entries) => {
    if (entries.some(e => e.isIntersecting)) queueAutoLoad()
  }, { root, rootMargin: '0px 0px 700px 0px' })
  autoObserver.observe(sentinel)
}

function queueAutoLoad() {
  if (!props.hasMore || props.loadingMore || !autoLoadArmed || marqueeBox) return
  if (performance.now() < autoLoadCooldownUntil) return
  // 滚轮/拖拽还没停稳就等一等，别在滑动中插进一整批卡片
  if (performance.now() - lastScrollAt < 140) {
    window.clearTimeout(autoLoadTimer)
    autoLoadTimer = window.setTimeout(queueAutoLoad, 90)
    return
  }
  autoLoadArmed = false
  autoLoadCooldownUntil = performance.now() + 450
  emit('load-more')
}
watch(() => props.loadingMore, v => {
  if (v) return
  autoLoadArmed = true
  autoLoadCooldownUntil = 0
  nextTick(armAutoLoad)
})
watch(() => props.hasMore, v => { if (v) autoLoadArmed = true })
watch(() => props.photos.length, () => {
  autoLoadArmed = true
  autoLoadCooldownUntil = 0
  nextTick(armAutoLoad)
})
watch([scrollEl, loadSentinel], () => nextTick(armAutoLoad), { flush: 'post' })
watch(thumb, () => { window.clearTimeout(autoLoadTimer); autoLoadArmed = true; nextTick(armAutoLoad) })
function onScroll() {
  lastScrollAt = performance.now()
  if (!scrolling) { scrolling = true; document.body.classList.add('scrolling') }
  window.clearTimeout(scrollIdleTimer)
  scrollIdleTimer = window.setTimeout(() => { scrolling = false; document.body.classList.remove('scrolling') }, 150)
}
function focusFirst() { nextTick(() => scrollEl.value?.scrollTo({ top: 0, behavior: 'smooth' })) }
defineExpose({ focusFirst, selectAll, toggleMode })
</script>
<template>
  <div ref="rootEl" class="photo-grid-root">
    <div v-if="loading" class="page-loading"><span class="spinner"></span>正在冲洗影像</div>
    <div v-else-if="!photos.length" class="empty-rich">
      <div class="glyph">N</div><h3>{{ emptyTitle }}</h3><p>{{ emptyText }}</p>
      <slot name="empty-action" />
    </div>
    <div v-else ref="scrollEl" class="library-scroll groups-anim" data-scroll-root :style="{ '--thumb': thumb + 'px' }" @pointerdown="onPointerDown" @scroll.passive="onScroll">
      <section v-for="(group, gi) in groups" :key="group.key" class="month-group" :style="{ '--gi': gi }">
        <div v-if="!flat" class="month"><h2>{{ group.key }}</h2><span class="range">{{ monthRange(group.dates) }}</span><span class="n">{{ pad(group.items.length) }} 张</span></div>
        <div class="grid">
          <article v-for="(p, ci) in group.items" :key="p.id" class="card" :class="{ selected: selected.has(p.id) }" :style="{ '--i': ci }" :data-id="p.id" @click="onCardClick(group.start + ci, p, $event)">
            <img v-if="imageSrc(p) && !failed.has(p.id)" v-lazy-src="imageSrc(p)" class="thumb-img" alt="" :loading="(group.start + ci) < 12 ? 'eager' : 'lazy'" :fetchpriority="(group.start + ci) < 6 ? 'high' : 'auto'" decoding="async" draggable="false" @load="onThumbLoad" @error="onThumbError(p, $event)" />
            <div v-else class="img-fallback"><span>N</span><em>{{ (p.picFormat || 'IMAGE').toUpperCase() }}</em></div>
            <span v-if="showLiveBadge && p.type === 'live'" class="badge"><i></i>LIVE</span>
            <button v-if="selectable" class="check" type="button" :aria-label="'选择 ' + (p.name || '照片')" @click.stop="toggleSelect(p.id, true)"><Icon name="check" :size="10" /></button>
            <div class="cap"><div class="t">{{ p.name || '未命名影像' }}</div><div class="m"><span>{{ dims(p) }}</span><em>{{ p.category || size(p) || 'R2' }}</em></div></div>
          </article>
        </div>
      </section>
      <div ref="loadSentinel" class="load-sentinel" aria-hidden="true"></div>
      <div v-if="loadingMore" class="load-more"><span class="spinner"></span>正在加载</div>
      <div v-else-if="hasMore" class="load-more auto-load-hint">继续下滑，自动加载更多</div>
      <div v-else class="load-more">已显示全部 {{ photos.length }} 张</div>
    </div>
    <div ref="marquee" class="marquee" :class="{ show: showMarquee }" hidden></div>
    <div class="selbar" aria-live="polite">
      <div class="n"><b>{{ selectedCount }}</b> 已选择</div><div class="sep"></div>
      <button type="button" @click="downloadSelected">下载</button>
      <button v-if="canAddToSpace" type="button" @click="emitAdd">加入空间</button>
      <button v-if="canBatchDelete" type="button" class="danger-text" @click="emitDelete">删除</button>
      <button type="button" @click="selectAll">{{ allSelected ? '取消全选' : '全选' }}</button>
      <button type="button" class="done" @click="done">完成</button>
    </div>
  </div>
</template>
<style scoped>
.photo-grid-root{display:flex;flex-direction:column;min-height:0;flex:1}
.photo-grid-root>.library-scroll{flex:1;min-height:0}
.month-group{display:contents}
.card img{background:#0f0f12}
.card img.thumb-img{opacity:0}
.card img.thumb-img.is-loaded{opacity:1}
.card .img-fallback{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;background:radial-gradient(circle at 50% 35%,rgba(201,169,106,.12),transparent 55%),linear-gradient(145deg,#111116,#08080a);color:rgba(201,169,106,.55)}.card .img-fallback span{font-family:var(--serif);font-size:38px;line-height:1;letter-spacing:.08em}.card .img-fallback em{font-style:normal;font-size:6px;letter-spacing:.22em;color:var(--dim)}
.card .check{opacity:0}
:global(body.select-mode) .card .check{opacity:1}
.card.selected .check{opacity:1}
.selbar .danger-text:hover{color:var(--danger)}
.grid{scroll-margin-top:20px}
.load-sentinel{height:1px;width:100%}
@media(max-width:820px){.photo-grid-root{min-height:0}.selbar{bottom:70px}}
</style>
