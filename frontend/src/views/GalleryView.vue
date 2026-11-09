<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useIsMobile } from '@/composables/useIsMobile'

const mobile = useIsMobile()
const MobileGallery = defineAsyncComponent(() => import('@/mobile/MobileGallery.vue'))
const DesktopGalleryView = defineAsyncComponent(() => import('./DesktopGalleryView.vue'))
</script>

<template>
  <MobileGallery v-if="mobile" />
  <DesktopGalleryView v-else />
</template>
