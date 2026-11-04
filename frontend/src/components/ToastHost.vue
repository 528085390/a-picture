<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
const ui = useUiStore()
</script>
<template>
  <div class="toast-stack" aria-live="polite">
    <div v-for="t in ui.toasts" :key="t.id" class="toast-item" :class="t.tone">{{ t.message }}</div>
  </div>
</template>
