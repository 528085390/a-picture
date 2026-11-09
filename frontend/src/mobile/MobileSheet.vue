<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  open: boolean
  title?: string
  subtitle?: string
  height?: 'auto' | 'half' | 'tall'
  closable?: boolean
}>(), {
  title: '',
  subtitle: '',
  height: 'auto',
  closable: true,
})

const emit = defineEmits<{
  'update:open': [v: boolean]
}>()

const rendered = ref(false)
const shown = ref(false)
const dragging = ref(false)
const dragY = ref(0)
const bodyRef = ref<HTMLElement | null>(null)

const heightClass = computed(() => `is-${props.height}`)
const sheetStyle = computed(() => (
  dragging.value || dragY.value > 0
    ? { transform: `translate3d(0, ${dragY.value}px, 0)` }
    : undefined
))

let hideTimer = 0
let startY = 0
let lastY = 0
let lastT = 0
let lastMoveAt = 0
let velocity = 0
let active = false
let source: 'head' | 'body' = 'head'
let touchId: number | null = null
let pointerId: number | null = null

watch(() => props.open, (open) => {
  window.clearTimeout(hideTimer)
  if (open) {
    dragY.value = 0
    dragging.value = false
    rendered.value = true
    nextTick(() => {
      window.requestAnimationFrame(() => {
        if (props.open) shown.value = true
      })
    })
  } else if (rendered.value) {
    shown.value = false
    hideTimer = window.setTimeout(() => {
      rendered.value = false
      dragY.value = 0
    }, 260)
  }
}, { immediate: true })

function requestClose() {
  if (!props.closable) return
  emit('update:open', false)
}

function onMaskClick() {
  requestClose()
}

function resolveSource(target: EventTarget | null): 'head' | 'body' | null {
  if (!props.open || !props.closable) return null
  const el = target as HTMLElement | null
  if (!el || typeof el.closest !== 'function') return null
  if (el.closest('.m-sheet-grab, .m-sheet-head')) return 'head'
  if (bodyRef.value && bodyRef.value.scrollTop <= 0) return 'body'
  return null
}

function beginDrag(y: number, from: 'head' | 'body') {
  startY = y
  lastY = y
  lastT = performance.now()
  lastMoveAt = lastT
  velocity = 0
  active = false
  source = from
  dragY.value = 0
}

function moveDrag(y: number, event: Event) {
  const dy = y - startY
  if (!active) {
    if (dy <= 0) {
      if (source === 'body' && (bodyRef.value?.scrollTop ?? 0) > 0) resetDrag()
      return
    }
    if (source === 'body' && dy < 10) return
    if (source === 'body' && (bodyRef.value?.scrollTop ?? 0) > 0) {
      resetDrag()
      return
    }
    active = true
    dragging.value = true
  }
  if (event.cancelable) event.preventDefault()
  const now = performance.now()
  const dt = now - lastT
  if (dt > 4) velocity = (y - lastY) / dt
  lastY = y
  lastT = now
  lastMoveAt = now
  dragY.value = Math.max(0, Math.min(dy, window.innerHeight))
}

function endDrag(y: number) {
  const wasActive = active
  const dy = Math.max(dragY.value, y - startY)
  const now = performance.now()
  if (now - lastMoveAt > 80) velocity = 0
  active = false
  if (wasActive && props.closable && (dy > 90 || velocity > 0.5)) {
    dragging.value = false
    requestClose()
  } else {
    dragging.value = false
    dragY.value = 0
  }
}

function resetDrag() {
  active = false
  dragging.value = false
  dragY.value = 0
}

function cancelDrag() {
  resetDrag()
  touchId = null
  pointerId = null
}

function onTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1) return
  const touch = e.touches[0]
  const from = resolveSource(e.target)
  if (!from) return
  touchId = touch.identifier
  beginDrag(touch.clientY, from)
}

function onTouchMove(e: TouchEvent) {
  if (touchId === null) return
  const touch = Array.from(e.touches).find(item => item.identifier === touchId)
  if (!touch) return
  moveDrag(touch.clientY, e)
}

function onTouchEnd(e: TouchEvent) {
  if (touchId === null) return
  const touch = Array.from(e.changedTouches).find(item => item.identifier === touchId)
  if (!touch) return
  endDrag(touch.clientY)
  touchId = null
}

function onTouchCancel() {
  cancelDrag()
}

function onPointerDown(e: PointerEvent) {
  if (e.pointerType !== 'mouse' || e.button !== 0) return
  const from = resolveSource(e.target)
  if (from !== 'head') return
  const el = e.currentTarget as HTMLElement
  if (el.setPointerCapture) {
    try {
      el.setPointerCapture(e.pointerId)
    } catch {
      // pointer capture can fail for stale pointer ids
    }
  }
  pointerId = e.pointerId
  beginDrag(e.clientY, 'head')
}

function onPointerMove(e: PointerEvent) {
  if (e.pointerType !== 'mouse' || e.pointerId !== pointerId) return
  moveDrag(e.clientY, e)
}

function onPointerUp(e: PointerEvent) {
  if (e.pointerType !== 'mouse' || e.pointerId !== pointerId) return
  endDrag(e.clientY)
  pointerId = null
}

function onPointerCancel() {
  if (pointerId === null) return
  resetDrag()
  pointerId = null
}

function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Escape' || !props.open) return
  e.preventDefault()
  requestClose()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.clearTimeout(hideTimer)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="rendered" class="m-sheet-layer">
      <div class="m-sheet-mask" :class="{ show: shown }" @click="onMaskClick" />
      <div class="m-sheet-view" :class="[heightClass, { show: shown, leaving: !shown }]">
        <section
          class="m-sheet"
          :class="{ dragging }"
          :style="sheetStyle"
          role="dialog"
          aria-modal="true"
          :aria-label="title || undefined"
          @touchstart="onTouchStart"
          @touchmove="onTouchMove"
          @touchend="onTouchEnd"
          @touchcancel="onTouchCancel"
          @pointerdown="onPointerDown"
          @pointermove="onPointerMove"
          @pointerup="onPointerUp"
          @pointercancel="onPointerCancel"
        >
          <div class="m-sheet-grab"><span /></div>
          <header v-if="title || subtitle || $slots.header" class="m-sheet-head">
            <slot name="header">
              <h2 v-if="title" class="m-sheet-title">{{ title }}</h2>
              <p v-if="subtitle" class="m-sheet-sub">{{ subtitle }}</p>
            </slot>
          </header>
          <div ref="bodyRef" class="m-sheet-body">
            <slot />
          </div>
        </section>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.m-sheet-layer {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", "Helvetica Neue", Arial, sans-serif;
  color: var(--m-text, #fff);
  -webkit-tap-highlight-color: transparent;
}
.m-sheet-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, .45);
  opacity: 0;
  transition: opacity var(--dur-fade) var(--spring-fade);
}
.m-sheet-mask.show {
  opacity: 1;
  transition-duration: var(--dur-sheet);
}
.m-sheet-view {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 430px;
  max-height: 70vh;
  transform: translate3d(0, 100%, 0);
  transition: transform var(--dur-sheet) var(--spring-sheet);
  will-change: transform;
}
.m-sheet-view.show {
  transform: translate3d(0, 0, 0);
}
.m-sheet-view.leaving {
  transition-duration: var(--dur-fade);
}
.m-sheet-view.is-auto {
  max-height: 70vh;
}
.m-sheet-view.is-half {
  max-height: 52vh;
}
.m-sheet-view.is-tall {
  max-height: 84vh;
}
.m-sheet {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  max-height: inherit;
  padding-bottom: calc(12px + var(--safe-bottom, env(safe-area-inset-bottom, 0px)));
  overflow: hidden;
  background: #1c1c1e;
  border-radius: 14px 14px 0 0;
  box-shadow: 0 -8px 32px rgba(0, 0, 0, .4);
  transition: transform var(--dur-sheet) var(--spring-sheet);
  will-change: transform;
}
.m-sheet.dragging {
  transition: none;
}
.m-sheet-grab {
  flex: 0 0 auto;
  user-select: none;
  -webkit-user-select: none;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 8px;
  cursor: grab;
  touch-action: none;
}
.m-sheet-grab:active {
  cursor: grabbing;
}
.m-sheet-grab span {
  display: block;
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: rgba(235, 235, 245, .3);
}
.m-sheet-head {
  flex: 0 0 auto;
  user-select: none;
  -webkit-user-select: none;
  padding: 4px 16px 12px;
  text-align: center;
  touch-action: none;
}
.m-sheet-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  line-height: 22px;
  letter-spacing: -.24px;
}
.m-sheet-sub {
  margin: 2px 0 0;
  font-size: 13px;
  line-height: 18px;
  color: var(--m-text-2, rgba(235, 235, 245, .6));
}
.m-sheet-body {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0 16px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.m-sheet-body::-webkit-scrollbar {
  display: none;
}
</style>
