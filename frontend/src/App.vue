<script setup lang="ts">
import { RouterView } from 'vue-router'
import ToastHost from '@/components/ToastHost.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import UploadModal from '@/components/UploadModal.vue'
</script>
<template>
  <RouterView v-slot="{ Component }">
    <Transition name="page" mode="out-in"><component :is="Component" /></Transition>
  </RouterView>
  <ToastHost />
  <ConfirmDialog />
  <UploadModal />
</template>
<style>
.page-enter-active,.page-leave-active{transition:opacity .3s var(--ease-soft)}.page-enter-from,.page-leave-to{opacity:0}
</style>
