<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
const ui = useUiStore()
</script>
<template>
  <Teleport to="body">
    <div v-if="ui.confirmState.resolve" class="modal-backdrop confirm-backdrop" @click.self="ui.answerConfirm(false)">
      <div class="confirm-panel">
        <h3>{{ ui.confirmState.title }}</h3>
        <p v-if="ui.confirmState.message">{{ ui.confirmState.message }}</p>
        <div class="form-actions">
          <button class="btn sm" @click="ui.answerConfirm(false)">取消</button>
          <button class="btn sm" :class="ui.confirmState.danger ? 'danger' : 'gold'" @click="ui.answerConfirm(true)">{{ ui.confirmState.confirmText }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
