<script setup lang="ts">
import Icon from '@/components/Icon.vue'
import MobilePhotoGrid from './MobilePhotoGrid.vue'
import MobileTabBar from './MobileTabBar.vue'
import type { PictureVO } from '@/api/types'

const props = defineProps<{
  title: string
  subtitle?: string
  photos: PictureVO[]
  columns: number
  loading?: boolean
  error?: string
  photoLoading?: boolean
  hasMore?: boolean
  canEdit?: boolean
  canManage?: boolean
}>()

const emit = defineEmits<{
  back: []
  upload: []
  settings: []
  open: [index: number]
  loadMore: []
  'update:columns': [value: number]
}>()
</script>

<template>
  <header class="ms-head">
    <div class="ms-row">
      <button class="ms-btn" type="button" aria-label="返回" @click="emit('back')"><Icon name="chevron-left" :size="26" /></button>
      <span class="ms-title">{{ title }}</span>
      <button v-if="canEdit" class="ms-btn" type="button" aria-label="上传" @click="emit('upload')"><Icon name="plus" :size="24" /></button>
      <button v-else-if="canManage" class="ms-btn" type="button" aria-label="设置" @click="emit('settings')"><Icon name="more" :size="22" /></button>
      <span v-else class="ms-btn" />
    </div>
    <div v-if="subtitle" class="ms-sub">{{ subtitle }}</div>
  </header>

  <div class="ms-body">
    <div v-if="loading" class="ms-center"><span class="ms-spin" />正在进入空间…</div>
    <div v-else-if="error" class="ms-center"><b>无法打开这个空间</b><span>{{ error }}</span><button class="ms-link" type="button" @click="emit('back')">返回空间</button></div>
    <MobilePhotoGrid
      v-else
      :photos="photos"
      mode="all"
      :columns="columns"
      :selecting="false"
      :selected="[]"
      :loading="photoLoading && !photos.length"
      :has-more="hasMore"
      @update:columns="v => emit('update:columns', v)"
      @open="i => emit('open', i)"
      @load-more="emit('loadMore')"
    >
      <template #empty>
        <div class="ms-center">
          <Icon name="image" :size="30" />
          <b>这个空间还没有照片</b>
          <span>把第一批影像放进来，空间才算真正开始。</span>
          <button v-if="canEdit" class="ms-link" type="button" @click="emit('upload')">上传照片</button>
        </div>
      </template>
    </MobilePhotoGrid>
  </div>

  <MobileTabBar />
</template>

<style scoped>
.ms-head{position:relative;z-index:20;flex:none;padding-top:var(--safe-top);background:rgba(18,18,20,.94)}
.ms-row{display:flex;align-items:center;gap:2px;height:var(--m-nav);padding:0 4px}
.ms-title{flex:1;min-width:0;font-size:17px;font-weight:600;letter-spacing:-.01em;text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ms-btn{display:grid;place-items:center;width:44px;height:44px;color:var(--ios-blue);flex:none;transition:transform var(--dur-press) var(--spring-press),opacity var(--dur-press) ease}
.ms-btn:active{transform:scale(.86);opacity:.6}
.ms-sub{padding:0 16px 8px;font-size:12px;color:var(--m-text-2);text-align:center;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ms-body{position:relative;flex:1;min-height:0;display:flex;flex-direction:column;padding-bottom:calc(var(--m-tab) + var(--safe-bottom))}
.ms-center{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;min-height:56vh;padding:32px 24px;text-align:center;color:var(--m-text-2);font-size:14px}
.ms-center b{color:#fff;font-size:16px;font-weight:600}
.ms-center span{line-height:1.5}
.ms-link{color:var(--ios-blue);font-size:15px;font-weight:500}
.ms-spin{width:20px;height:20px;border-radius:50%;border:2px solid rgba(255,255,255,.18);border-top-color:#fff;animation:ms-spin .8s linear infinite}
@keyframes ms-spin{to{transform:rotate(360deg)}}
</style>
