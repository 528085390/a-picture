<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Icon from '@/components/Icon.vue'
import { storageKey, thumbUrl, originThumbUrl } from '@/utils/media'
import { vLazySrc } from '@/directives/lazySrc'
import type { PictureVO } from '@/api/types'

type GridMode = 'years' | 'months' | 'days' | 'all'
type GroupMode = GridMode

interface GridItem {
  photo: PictureVO
  index: number
}

interface PhotoGroup {
  key: string
  mode: GroupMode
  label: string
  secondary: string
  items: GridItem[]
}

interface DragState {
  anchor: number
  base: Set<string>
  active: boolean
  startX: number
  startY: number
  x: number
  y: number
}

interface PinchState {
  startDistance: number
  startColumns: number
  steps: number
  midX: number
  midY: number
}

interface AnchorInfo {
  id: string
  ratio: number
  pointY: number
}

const props = withDefaults(defineProps<{
  photos: PictureVO[]
  mode: GridMode
  columns: number
  selecting: boolean
  selected: string[]
  showLiveBadge?: boolean
  loading?: boolean
  hasMore?: boolean
}>(), {
  showLiveBadge: false,
  loading: false,
  hasMore: false,
})

const emit = defineEmits<{
  'update:columns': [n: number]
  'update:selecting': [v: boolean]
  'update:selected': [ids: string[]]
  open: [index: number]
  'load-more': []
}>()

const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
const LOAD_MORE_DISTANCE = 800
const LOAD_MORE_THROTTLE = 600
const LOAD_IDLE_MS = 140
const LONG_PRESS_MS = 420
const PINCH_STEP = 60
const DRAG_THRESHOLD = 6
const EDGE_ZONE = 60
const EDGE_SPEED = 16

const scroller = ref<HTMLElement | null>(null)
const localColumns = ref(clampColumns(props.columns))
const selectedSet = ref(new Set(props.selected))
// 非响应式：图片失败只做 DOM 级兜底，一旦响应式更新，一次 error 就会重渲染上百张卡片（掉帧主因）
const imageStage: Record<string, number> = {}
const pinching = ref(false)
const viewportWidth = ref(typeof window === 'undefined' ? 430 : window.innerWidth)
const pixelRatio = ref(typeof window === 'undefined' ? 1 : window.devicePixelRatio || 1)

let longPressTimer = 0
let longPressOrigin: { x: number, y: number, index: number, id: string } | null = null
let drag: DragState | null = null
let pinch: PinchState | null = null
let autoScrollFrame = 0
let loadCheckFrame = 0
let lastLoadAt = 0
let loadRetryTimer = 0
let suppressClickUntil = 0
let columnTask = 0
let flipTimer = 0
let flipCards: HTMLElement[] = []

const thumbWidth = computed(() => {
  const cols = props.mode === 'years' ? 3 : localColumns.value
  // 缩略图按 2x 取就够清晰；DPR3 全量会让每张解码内存 ×2.25（4 列 322px → 215px）
  const dpr = Math.min(2, pixelRatio.value)
  return Math.min(900, Math.max(240, Math.round(viewportWidth.value / cols * dpr)))
})

const gridColumns = computed(() => props.mode === 'years' ? 3 : localColumns.value)

const groups = computed<PhotoGroup[]>(() => {
  if (!props.photos.length) return []
  if (props.mode === 'all') {
    return [{
      key: 'all',
      mode: 'all',
      label: '',
      secondary: '',
      items: props.photos.map((photo, index) => ({ photo, index })),
    }]
  }
  const map = new Map<string, PhotoGroup>()
  props.photos.forEach((photo, index) => {
    const date = parsePhotoDate(photo.createTime)
    let key = 'unknown'
    let label = '未知日期'
    let secondary = ''
    if (date) {
      const year = date.getFullYear()
      const month = date.getMonth() + 1
      const day = date.getDate()
      if (props.mode === 'days') {
        key = String(year) + '-' + pad(month) + '-' + pad(day)
        label = month + '月' + day + '日'
        secondary = WEEKDAYS[date.getDay()] || ''
      } else if (props.mode === 'months') {
        key = String(year) + '-' + pad(month)
        label = year + '年' + month + '月'
      } else {
        key = String(year)
        label = year + '年'
      }
    } else if (props.mode === 'months') {
      label = '未知月份'
    } else if (props.mode === 'years') {
      label = '未知年份'
    }
    let group = map.get(key)
    if (!group) {
      group = { key, mode: props.mode, label, secondary, items: [] }
      map.set(key, group)
    }
    group.items.push({ photo, index })
  })
  return [...map.values()]
})

watch(() => props.columns, value => {
  const next = clampColumns(value)
  if (next !== localColumns.value) localColumns.value = next
})

watch(() => props.selected, ids => {
  const incoming = new Set(ids)
  if (incoming.size === selectedSet.value.size && [...incoming].every(id => selectedSet.value.has(id))) return
  selectedSet.value = incoming
}, { deep: true })

watch(() => props.selecting, value => {
  if (value) cancelLongPress()
  else cancelDrag()
})

watch(() => props.hasMore, value => {
  if (!value) return
  lastLoadAt = 0
  scheduleLoadCheck()
})

watch(() => props.photos.length, () => {
  scheduleLoadCheck()
})

watch(() => props.loading, value => {
  if (!value) scheduleLoadCheck()
})

function clampColumns(value: number) {
  const rounded = Math.round(Number(value))
  return Math.min(6, Math.max(2, Number.isFinite(rounded) ? rounded : 4))
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function parsePhotoDate(value?: string) {
  if (!value) return null
  let normalized = value
  if (!normalized.includes('T')) {
    normalized = /^\d{4}-\d{2}-\d{2}$/.test(normalized) ? normalized + 'T00:00:00' : normalized.replace(' ', 'T')
  }
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

function isSelected(id: string) {
  return selectedSet.value.has(id)
}

function commitSelection(next: Set<string>) {
  const same = next.size === selectedSet.value.size && [...next].every(id => selectedSet.value.has(id))
  if (same) return
  selectedSet.value = next
  emit('update:selected', [...next])
}

function togglePhoto(photo: PictureVO) {
  const next = new Set(selectedSet.value)
  if (next.has(photo.id)) next.delete(photo.id)
  else next.add(photo.id)
  commitSelection(next)
}

function onCardClick(item: GridItem) {
  if (performance.now() < suppressClickUntil) return
  if (props.selecting) togglePhoto(item.photo)
  else emit('open', item.index)
}

/** 兜底一律走同源：缩略图 → 后端文件代理 → 占位块，任何情况下都不让网格直连对象存储/CDN 域名 */ 
function fallbackSource(photo: PictureVO) {
  const raw = photo.previewUrl || photo.url || ''
  if (!raw) return ''
  const key = storageKey(raw)
  return key ? '/api/picture/file/' + key : ''
}

// 模板里每张卡会调用 2 次 imageSource；不缓存的话每次渲染都要 new URL + 正则 + encodeURIComponent。
// 只按 (id, 档位, stage) 缓存，滚动过程中命中即可，不参与响应式。
const sourceCache = new Map<string, string>()

function computeImageSource(photo: PictureVO, stage: number) {
  if (stage >= 3) return ''
  if (stage === 2) return fallbackSource(photo)
  if (stage === 1) return originThumbUrl(photo.previewUrl || photo.url, thumbWidth.value)
  const raw = photo.previewUrl || photo.url || ''
  if (!raw) return ''
  const thumb = thumbUrl(raw, thumbWidth.value)
  // thumbUrl 遇到未知域名会原样返回，这种情况直接同源预览兜底
  if (!thumb || !/^(https?:\/\/|\/)/i.test(thumb)) return fallbackSource(photo)
  return thumb
}

function imageSource(photo: PictureVO) {
  const stage = imageStage[photo.id] || 0
  const key = photo.id + '|' + thumbWidth.value + '|' + stage
  const cached = sourceCache.get(key)
  if (cached !== undefined) return cached
  const next = computeImageSource(photo, stage)
  if (sourceCache.size > 4000) sourceCache.clear()
  sourceCache.set(key, next)
  return next
}

function isImageDead(photo: PictureVO) {
  return (imageStage[photo.id] || 0) >= 3 || (!photo.url && !photo.previewUrl)
}

function onImageLoad(event: Event) {
  const img = event.target as HTMLImageElement
  img.classList.add('is-loaded')
}

function onImageError(photo: PictureVO, event?: Event) {
  imageStage[photo.id] = Math.min(3, (imageStage[photo.id] || 0) + 1)
  const img = event?.target as HTMLImageElement | undefined
  if (!img) return
  // 先把破图藏起来再换源：浏览器在换源成功前会画出「图标 + 文件名」，肉眼看到的就是一闪而过的坏图
  img.classList.remove('is-loaded')
  // 直接改 DOM：不触发重渲染，失败一次只换一次 src
  const next = imageSource(photo)
  if (next) { img.dataset.src = next; img.src = next; return }
  img.removeAttribute('src')
  img.classList.add('is-dead')
  img.closest('.photo-card')?.classList.add('is-dead')
}

/* ---------- 缩略图按需加载 ---------- */
// 共用指令：视口上下各留 900px，滚近了才真正设 src（见 src/directives/lazySrc.ts）。

let scrollFrame = 0
let lastScrollAt = 0

function onScroll() {
  lastScrollAt = performance.now()
  if (scrollFrame) return
  scrollFrame = window.requestAnimationFrame(() => {
    scrollFrame = 0
    checkLoadMore()
  })
}

function scheduleLoadCheck() {
  if (typeof window === 'undefined') return
  window.cancelAnimationFrame(loadCheckFrame)
  if (scrollFrame) window.cancelAnimationFrame(scrollFrame)
  scrollFrame = 0
  loadCheckFrame = window.requestAnimationFrame(() => checkLoadMore())
}

function checkLoadMore() {
  const el = scroller.value
  if (!el || !props.hasMore || props.loading || !props.photos.length) return
  // 手指还在滑：等停稳再插新卡片，否则插入瞬间的那一帧一定掉
  const idle = performance.now() - lastScrollAt
  if (idle < LOAD_IDLE_MS) {
    if (!loadRetryTimer) {
      loadRetryTimer = window.setTimeout(() => {
        loadRetryTimer = 0
        scheduleLoadCheck()
      }, LOAD_IDLE_MS - idle + 16)
    }
    return
  }
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight
  if (distance > LOAD_MORE_DISTANCE) return
  const now = performance.now()
  if (now - lastLoadAt < LOAD_MORE_THROTTLE) {
    if (!loadRetryTimer) {
      loadRetryTimer = window.setTimeout(() => {
        loadRetryTimer = 0
        scheduleLoadCheck()
      }, LOAD_MORE_THROTTLE - (now - lastLoadAt) + 16)
    }
    return
  }
  lastLoadAt = now
  emit('load-more')
}

function cardFromTarget(target: EventTarget | null) {
  return target instanceof Element ? target.closest<HTMLElement>('.photo-card') : null
}

function cardIndex(card: HTMLElement) {
  const index = Number(card.dataset.index)
  return Number.isInteger(index) ? index : -1
}

function cardUnderPoint(x: number, y: number) {
  if (typeof document === 'undefined') return null
  const nodes = document.elementsFromPoint(x, y)
  for (const node of nodes) {
    if (!(node instanceof Element)) continue
    const card = node.closest<HTMLElement>('.photo-card')
    if (card && scroller.value?.contains(card)) return card
  }
  return null
}

function allCards() {
  if (!scroller.value) return [] as HTMLElement[]
  return Array.from(scroller.value.querySelectorAll<HTMLElement>('.photo-card'))
}

function findCardById(id: string) {
  return allCards().find(card => card.dataset.photoId === id) || null
}

function nearestAnchor(x: number, y: number): AnchorInfo | null {
  const el = scroller.value
  if (!el) return null
  const sr = el.getBoundingClientRect()
  let best: HTMLElement | null = null
  let bestDistance = Infinity
  for (const card of allCards()) {
    const rect = card.getBoundingClientRect()
    if (rect.bottom < sr.top || rect.top > sr.bottom) continue
    const dx = Math.max(rect.left - x, 0, x - rect.right)
    const dy = Math.max(rect.top - y, 0, y - rect.bottom)
    const distance = dx * dx + dy * dy
    if (distance < bestDistance) {
      bestDistance = distance
      best = card
    }
  }
  if (!best) return null
  const rect = best.getBoundingClientRect()
  return {
    id: best.dataset.photoId || '',
    ratio: rect.height ? Math.min(1, Math.max(0, (y - rect.top) / rect.height)) : 0,
    pointY: y,
  }
}

function visibleRects() {
  const rects = new Map<string, DOMRect>()
  const el = scroller.value
  if (!el) return rects
  const sr = el.getBoundingClientRect()
  for (const card of allCards()) {
    const rect = card.getBoundingClientRect()
    if (rect.bottom < sr.top - 120 || rect.top > sr.bottom + 120) continue
    if (card.dataset.photoId) rects.set(card.dataset.photoId, rect)
  }
  return rects
}

function clearFlip() {
  window.clearTimeout(flipTimer)
  flipTimer = 0
  for (const card of flipCards) {
    card.style.transition = 'none'
    card.style.transform = ''
    card.style.transformOrigin = ''
    card.classList.remove('is-flipping')
  }
  flipCards = []
}

function playFlip(first: Map<string, DOMRect>) {
  const cards = new Map<string, HTMLElement>()
  for (const card of allCards()) {
    const id = card.dataset.photoId
    if (id) cards.set(id, card)
  }
  const moving: HTMLElement[] = []
  first.forEach((start, id) => {
    const card = cards.get(id)
    if (!card) return
    const end = card.getBoundingClientRect()
    const dx = start.left - end.left
    const dy = start.top - end.top
    const sx = end.width ? start.width / end.width : 1
    const sy = end.height ? start.height / end.height : 1
    if (Math.abs(dx) < .5 && Math.abs(dy) < .5 && Math.abs(sx - 1) < .002 && Math.abs(sy - 1) < .002) return
    card.classList.add('is-flipping')
    card.style.transition = 'none'
    card.style.transformOrigin = 'top left'
    card.style.transform = 'translate3d(' + dx.toFixed(2) + 'px,' + dy.toFixed(2) + 'px,0) scale(' + sx.toFixed(4) + ',' + sy.toFixed(4) + ')'
    moving.push(card)
  })
  if (!moving.length) return
  const el = scroller.value
  if (el) void el.offsetWidth
  flipCards = moving
  window.requestAnimationFrame(() => {
    for (const card of moving) {
      card.style.transition = 'transform var(--dur-grid) var(--spring-grid)'
      card.style.transform = 'translate3d(0,0,0) scale(1,1)'
    }
  })
  flipTimer = window.setTimeout(clearFlip, 320)
}

async function changeColumns(nextValue: number, point?: { x: number, y: number }) {
  const next = clampColumns(nextValue)
  if (next === localColumns.value) return
  const task = ++columnTask
  clearFlip()
  const first = visibleRects()
  let anchor: AnchorInfo | null = null
  if (point) {
    const card = cardUnderPoint(point.x, point.y)
    if (card) {
      const rect = card.getBoundingClientRect()
      anchor = {
        id: card.dataset.photoId || '',
        ratio: rect.height ? Math.min(1, Math.max(0, (point.y - rect.top) / rect.height)) : 0,
        pointY: point.y,
      }
    } else {
      anchor = nearestAnchor(point.x, point.y)
    }
  }
  localColumns.value = next
  emit('update:columns', next)
  await nextTick()
  if (task !== columnTask) return
  const el = scroller.value
  if (el && anchor) {
    const anchorCard = findCardById(anchor.id)
    if (anchorCard) {
      const rect = anchorCard.getBoundingClientRect()
      const currentY = rect.top + rect.height * anchor.ratio
      const delta = currentY - anchor.pointY
      if (Math.abs(delta) > .5) el.scrollTop += delta
    }
  }
  playFlip(first)
}

function startPinch(event: TouchEvent) {
  if (event.touches.length < 2) return
  cancelLongPress()
  cancelDrag()
  const first = event.touches[0]
  const second = event.touches[1]
  pinch = {
    startDistance: Math.max(1, touchDistance(first, second)),
    startColumns: localColumns.value,
    steps: 0,
    midX: (first.clientX + second.clientX) / 2,
    midY: (first.clientY + second.clientY) / 2,
  }
  pinching.value = true
  setAggressiveMove(true)
  suppressClickUntil = performance.now() + 500
  if (event.cancelable) event.preventDefault()
}

function touchDistance(a: Touch, b: Touch) {
  return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
}

function onTouchStart(event: TouchEvent) {
  if (event.touches.length >= 2) {
    startPinch(event)
    return
  }
  if (event.touches.length !== 1 || pinch) return
  const touch = event.touches[0]
  const card = cardFromTarget(event.target)
  if (!card) return
  const index = cardIndex(card)
  if (index < 0 || !props.photos[index]) return
  if (props.selecting) {
    setAggressiveMove(true)
    cancelLongPress()
    drag = {
      anchor: index,
      base: new Set(selectedSet.value),
      active: false,
      startX: touch.clientX,
      startY: touch.clientY,
      x: touch.clientX,
      y: touch.clientY,
    }
  } else {
    cancelLongPress()
    longPressOrigin = { x: touch.clientX, y: touch.clientY, index, id: props.photos[index].id }
    longPressTimer = window.setTimeout(() => {
      const origin = longPressOrigin
      longPressOrigin = null
      longPressTimer = 0
      if (!origin || pinch) return
      const photo = props.photos[origin.index]
      if (!photo || photo.id !== origin.id) return
      suppressClickUntil = performance.now() + 700
      setAggressiveMove(true)
      emit('update:selecting', true)
      commitSelection(new Set([origin.id]))
      drag = {
        anchor: origin.index,
        base: new Set([origin.id]),
        active: true,
        startX: origin.x,
        startY: origin.y,
        x: origin.x,
        y: origin.y,
      }
      startAutoScroll()
    }, LONG_PRESS_MS)
  }
}

function onTouchMove(event: TouchEvent) {
  if (pinch) {
    if (event.touches.length < 2) return
    if (event.cancelable) event.preventDefault()
    if (props.mode === 'years') return
    const first = event.touches[0]
    const second = event.touches[1]
    const distance = touchDistance(first, second)
    const diff = distance - pinch.startDistance
    const direction = diff >= 0 ? 1 : -1
    const steps = Math.trunc(Math.abs(diff) / PINCH_STEP) * direction
    if (steps === pinch.steps) return
    pinch.steps = steps
    pinch.midX = (first.clientX + second.clientX) / 2
    pinch.midY = (first.clientY + second.clientY) / 2
    const next = clampColumns(pinch.startColumns - steps)
    if (next !== localColumns.value) void changeColumns(next, { x: pinch.midX, y: pinch.midY })
    return
  }
  const touch = event.touches[0]
  if (!touch) return
  if (drag) {
    drag.x = touch.clientX
    drag.y = touch.clientY
    if (!drag.active) {
      if (Math.hypot(touch.clientX - drag.startX, touch.clientY - drag.startY) <= DRAG_THRESHOLD) return
      drag.active = true
      suppressClickUntil = performance.now() + 800
      startAutoScroll()
    }
    if (event.cancelable) event.preventDefault()
    updateDragSelection(touch.clientX, touch.clientY)
    return
  }
  if (longPressOrigin) {
    const distance = Math.hypot(touch.clientX - longPressOrigin.x, touch.clientY - longPressOrigin.y)
    if (distance > 8) cancelLongPress()
  }
}

function onTouchEnd(event: TouchEvent) {
  if (event.touches.length < 2 && pinch) {
    pinch = null
    pinching.value = false
    setAggressiveMove(false)
    suppressClickUntil = performance.now() + 500
  }
  if (event.touches.length === 0) {
    cancelLongPress()
    if (drag) {
      if (drag.active) suppressClickUntil = performance.now() + 500
      cancelDrag()
    }
    setAggressiveMove(false)
  }
}

function onTouchCancel(event: TouchEvent) {
  setAggressiveMove(false)
  if (pinch) {
    pinch = null
    pinching.value = false
  }
  cancelLongPress()
  cancelDrag()
  suppressClickUntil = performance.now() + 500
  if (event.cancelable) event.preventDefault()
}

function cancelLongPress() {
  if (longPressTimer) window.clearTimeout(longPressTimer)
  longPressTimer = 0
  longPressOrigin = null
}

function cancelDrag() {
  drag = null
  stopAutoScroll()
}

function startAutoScroll() {
  stopAutoScroll()
  const tick = () => {
    if (!drag?.active) {
      autoScrollFrame = 0
      return
    }
    const el = scroller.value
    if (el) {
      const rect = el.getBoundingClientRect()
      let delta = 0
      if (drag.y < rect.top + EDGE_ZONE) delta = -EDGE_SPEED
      else if (drag.y > rect.bottom - EDGE_ZONE) delta = EDGE_SPEED
      if (delta) {
        const before = el.scrollTop
        el.scrollTop = Math.max(0, Math.min(el.scrollHeight - el.clientHeight, before + delta))
        if (el.scrollTop !== before) updateDragSelection(drag.x, drag.y)
      }
    }
    autoScrollFrame = window.requestAnimationFrame(tick)
  }
  autoScrollFrame = window.requestAnimationFrame(tick)
}

function stopAutoScroll() {
  if (autoScrollFrame) window.cancelAnimationFrame(autoScrollFrame)
  autoScrollFrame = 0
}

function updateDragSelection(x: number, y: number) {
  if (!drag?.active) return
  const card = cardUnderPoint(x, y)
  if (!card) return
  const index = cardIndex(card)
  if (index < 0) return
  const start = Math.min(drag.anchor, index)
  const end = Math.max(drag.anchor, index)
  const next = new Set(drag.base)
  for (let i = start; i <= end; i++) {
    const photo = props.photos[i]
    if (photo) next.add(photo.id)
  }
  commitSelection(next)
}

function onWindowResize() {
  viewportWidth.value = window.innerWidth
  pixelRatio.value = window.devicePixelRatio || 1
  scheduleLoadCheck()
}

function scrollToTop() {
  const el = scroller.value
  if (el) el.scrollTo({ top: 0, behavior: 'auto' })
}

defineExpose({ scrollToTop })

/**
 * 非 passive 的 touchmove 会让 iOS 每次触摸移动都等主线程执行完 JS 才继续滚动，
 * 单指纵向滚动因此掉帧。这里默认 passive，只在双指缩放 / 拖选这类必须
 * preventDefault 的手势里临时换成非 passive。
 */
let aggressiveMove = false
function setAggressiveMove(on: boolean) {
  const el = scroller.value
  if (!el || aggressiveMove === on) return
  aggressiveMove = on
  el.removeEventListener('touchmove', onTouchMove)
  el.addEventListener('touchmove', onTouchMove, { passive: !on })
}

onMounted(() => {
  const el = scroller.value
  el?.addEventListener('touchstart', onTouchStart, { passive: false })
  el?.addEventListener('touchmove', onTouchMove, { passive: true })
  el?.addEventListener('touchend', onTouchEnd, { passive: true })
  el?.addEventListener('touchcancel', onTouchCancel, { passive: true })
  window.addEventListener('resize', onWindowResize, { passive: true })
  scheduleLoadCheck()
})

onBeforeUnmount(() => {
  const el = scroller.value
  el?.removeEventListener('touchstart', onTouchStart)
  el?.removeEventListener('touchmove', onTouchMove)
  el?.removeEventListener('touchend', onTouchEnd)
  el?.removeEventListener('touchcancel', onTouchCancel)
  window.removeEventListener('resize', onWindowResize)
  columnTask += 1
  cancelLongPress()
  cancelDrag()
  clearFlip()
  window.cancelAnimationFrame(loadCheckFrame)
  if (loadRetryTimer) window.clearTimeout(loadRetryTimer)
  loadRetryTimer = 0
})
</script>

<template>
  <div
    ref="scroller"
    class="m-photo-grid"
    :class="{ 'is-selecting': selecting, 'is-pinching': pinching }"
    data-scroll-root
    @scroll.passive="onScroll"
    @contextmenu.prevent
  >
    <div v-if="loading && !photos.length" class="skeleton-grid" :style="{ '--cols': gridColumns }" aria-hidden="true">
      <span v-for="n in 12" :key="n" class="skeleton-cell"></span>
    </div>

    <div v-else-if="!photos.length" class="empty-state">
      <slot name="empty">这里还没有照片</slot>
    </div>

    <template v-else>
      <section v-for="group in groups" :key="group.key" class="photo-section">
        <header v-if="group.mode !== 'all'" class="group-head" :class="'head-' + group.mode">
          <template v-if="group.mode === 'days'">
            <span class="group-title day-title">{{ group.label }}</span>
            <span class="group-secondary">{{ group.secondary }}</span>
          </template>
          <template v-else>
            <span class="group-title" :class="group.mode === 'years' ? 'year-title' : 'month-title'">{{ group.label }}</span>
            <span class="group-count">{{ group.items.length }} 张</span>
          </template>
        </header>

        <div class="photo-grid" :style="{ '--cols': group.mode === 'years' ? 3 : localColumns }">
          <article
            v-for="item in group.items"
            :key="item.photo.id"
            class="photo-card"
            :class="{ selected: isSelected(item.photo.id) }"
            :data-photo-id="item.photo.id"
            :data-index="item.index"
            role="button"
            :aria-pressed="isSelected(item.photo.id)"
            :aria-label="item.photo.name || '照片'"
            @click="onCardClick(item)"
          >
            <img
              v-if="!isImageDead(item.photo) && imageSource(item.photo)"
              class="photo-thumb"
              v-lazy-src="imageSource(item.photo)"
              alt=""
              :loading="item.index < 12 ? 'eager' : 'lazy'"
              decoding="async"
              draggable="false"
              @load="onImageLoad"
              @error="onImageError(item.photo, $event)"
            >
            <div v-else class="photo-fallback"><Icon name="image" :size="18" /></div>

            <span v-if="showLiveBadge" class="live-badge"><i></i>LIVE</span>
            <span class="photo-check" aria-hidden="true"><Icon name="check" :size="12" /></span>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.m-photo-grid{position:relative;width:100%;height:100%;min-height:0;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain;touch-action:pan-y;background:#000;color:var(--m-text,#fff);font-family:-apple-system,BlinkMacSystemFont,'SF Pro Text','SF Pro Display','PingFang SC','Helvetica Neue',sans-serif;scrollbar-width:none;-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;overflow-anchor:none}
.m-photo-grid::-webkit-scrollbar{display:none}
.photo-card.is-dead{background:#1c1c1e}
.photo-card.is-dead .photo-thumb{display:none}
.m-photo-grid.is-pinching{touch-action:none}
.photo-section{position:relative}
.group-head{position:sticky;top:0;z-index:2;display:flex;align-items:baseline;gap:8px;padding:8px 16px;background:rgba(0,0,0,.9)}
.head-days{padding-top:7px;padding-bottom:6px}
.head-months{padding-top:12px;padding-bottom:8px}
.head-years{padding-top:18px;padding-bottom:10px}
.group-title{min-width:0;color:#fff;font-weight:700;line-height:1.15;letter-spacing:-.01em}
.day-title{font-size:15px}
.month-title{font-size:20px}
.year-title{font-size:28px;letter-spacing:-.02em}
.group-secondary,.group-count{margin-left:auto;font-weight:400;white-space:nowrap}
.group-secondary{color:var(--m-text-2,rgba(235,235,245,.6));font-size:13px}
.group-count{color:var(--m-text-3,rgba(235,235,245,.32));font-size:13px}
.photo-grid,.skeleton-grid{display:grid;grid-template-columns:repeat(var(--cols),1fr);gap:2px;padding:0;margin:0}
.photo-card{position:relative;z-index:0;min-width:0;aspect-ratio:1 / 1;overflow:hidden;contain:layout paint style;border-radius:0;background:#1c1c1e;cursor:pointer;-webkit-touch-callout:none;transition:transform var(--dur-press) var(--spring-press),opacity var(--dur-press) ease}
/* 离屏卡片交给浏览器跳过布局/绘制：滚到哪只算哪几行 */
@supports (content-visibility: auto){
  .photo-card{content-visibility:auto}
}
.photo-card:active{transform:scale(.945);opacity:.9;transition-duration:110ms,110ms;transition-timing-function:ease-out,ease-out}
.photo-card.is-flipping{will-change:transform}
.photo-thumb{display:block;width:100%;height:100%;object-fit:cover;border-radius:0;background:#1c1c1e;pointer-events:none;-webkit-user-drag:none;opacity:0}
.photo-thumb.is-loaded{opacity:1}
.photo-fallback{display:grid;place-items:center;width:100%;height:100%;background:#1c1c1e;color:rgba(235,235,245,.2);pointer-events:none}
.photo-card.selected::after{content:'';position:absolute;inset:0;z-index:3;border:2px solid var(--ios-blue,#0a84ff);pointer-events:none}
.photo-check{position:absolute;top:6px;right:6px;z-index:4;display:grid;place-items:center;width:20px;height:20px;box-sizing:border-box;border:1.5px solid rgba(255,255,255,.58);border-radius:50%;background:rgba(0,0,0,.16);color:#fff;opacity:0;pointer-events:none}
.photo-check svg{opacity:0}
.is-selecting .photo-check,.photo-card.selected .photo-check{opacity:1}
.photo-card.selected .photo-check{border:2px solid var(--ios-blue,#0a84ff);background:var(--ios-blue,#0a84ff)}
.photo-card.selected .photo-check svg{opacity:1}
.live-badge{position:absolute;top:6px;left:6px;z-index:4;display:inline-flex;align-items:center;gap:4px;height:18px;padding:0 6px;border-radius:3px;background:rgba(0,0,0,.62);color:#fff;font-size:9px;font-weight:600;letter-spacing:.04em;line-height:1;pointer-events:none}
.live-badge i{width:5px;height:5px;border-radius:50%;background:#fff}
.skeleton-cell{position:relative;aspect-ratio:1 / 1;overflow:hidden;background:#141416}
.skeleton-cell::after{content:'';position:absolute;inset:0;background:linear-gradient(100deg,transparent 20%,rgba(255,255,255,.07) 50%,transparent 80%);transform:translateX(-100%);animation:grid-shimmer 1.25s ease-in-out infinite}
@keyframes grid-shimmer{to{transform:translateX(100%)}}
.empty-state{display:flex;align-items:center;justify-content:center;min-height:58vh;padding:40px 24px;color:var(--m-text-2,rgba(235,235,245,.6));font-size:15px;line-height:1.5;text-align:center}
@media (prefers-reduced-motion:reduce){.skeleton-cell::after{animation:none}}
</style>
