<script setup lang="ts">
import Icon from '@/components/Icon.vue'

const props = withDefaults(defineProps<{
  count: number
  canDelete?: boolean
  favoriteAll?: boolean
}>(), {
  canDelete: true,
  favoriteAll: false,
})

const emit = defineEmits<{
  share: []
  favorite: []
  delete: []
  cancel: []
}>()
</script>

<template>
  <div class="m-selection-bar" role="toolbar" aria-label="多选操作">
    <span class="m-selection-count">已选择 {{ props.count }} 张</span>
    <div class="m-selection-actions">
      <button type="button" class="m-selection-btn" @click="emit('share')">
        <svg class="m-selection-svg" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 3v12" />
          <path d="m8 7 4-4 4 4" />
          <path d="M6 10H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2h-1" />
        </svg>
        <span>分享</span>
      </button>
      <button type="button" class="m-selection-btn" :class="{ 'is-on': favoriteAll }" @click="emit('favorite')">
        <Icon name="heart" :size="20" />
        <span>收藏</span>
      </button>
      <button type="button" class="m-selection-btn danger" :disabled="!canDelete" @click="emit('delete')">
        <Icon name="trash" :size="20" />
        <span>删除</span>
      </button>
      <button type="button" class="m-selection-btn done" @click="emit('cancel')">
        <Icon name="check" :size="20" />
        <span>完成</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.m-selection-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  width: 100%;
  max-width: 430px;
  height: calc(49px + var(--safe-bottom, env(safe-area-inset-bottom, 0px)));
  margin: 0 auto;
  padding: 0 16px var(--safe-bottom, env(safe-area-inset-bottom, 0px));
  background: rgba(28, 28, 30, .98);
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display", "Helvetica Neue", Arial, sans-serif;
  color: var(--m-text, #fff);
  user-select: none;
  -webkit-user-select: none;
  -webkit-tap-highlight-color: transparent;
  animation: m-selection-in var(--dur-bar) var(--spring-bar) both;
  will-change: transform;
}
.m-selection-bar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: .5px;
  background: var(--m-line, rgba(255, 255, 255, .14));
}
.m-selection-count {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 18px;
  letter-spacing: -.08px;
  color: var(--m-text-2, rgba(235, 235, 245, .6));
  text-overflow: ellipsis;
  white-space: nowrap;
}
.m-selection-actions {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
}
.m-selection-btn {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--m-text, #fff);
  font: inherit;
  font-size: 10px;
  line-height: 12px;
  letter-spacing: .1px;
  cursor: pointer;
  transition: opacity var(--dur-press) ease, transform var(--dur-press) var(--spring-press);
  -webkit-tap-highlight-color: transparent;
}
.m-selection-btn:active {
  opacity: .55;
  transform: scale(.94);
  transition-duration: 100ms, 100ms;
  transition-timing-function: ease-out, ease-out;
}
.m-selection-btn:disabled {
  opacity: .4;
  pointer-events: none;
}
.m-selection-btn.danger {
  color: #FF453A;
}
.m-selection-btn.done {
  color: var(--ios-blue, #0A84FF);
  font-weight: 600;
}
.m-selection-btn.is-on :deep(svg) {
  fill: currentColor;
}
.m-selection-svg {
  display: block;
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
@keyframes m-selection-in {
  from { transform: translate3d(0, 100%, 0); }
  to { transform: translate3d(0, 0, 0); }
}
</style>
