<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { pictureApi } from '@/api/picture'
import { spacePictureApi } from '@/api/space'
import { useUiStore } from '@/stores/ui'
import { useGalleryStore } from '@/stores/gallery'
import { useSpaceStore } from '@/stores/spaces'
import Icon from './Icon.vue'

const ui = useUiStore()
const gallery = useGalleryStore()
const spaces = useSpaceStore()
const open = computed(() => ui.uploadOpen)
const spaceId = computed(() => ui.uploadSpaceId)
const isSpace = computed(() => !!spaceId.value)
const files = ref<File[]>([])
const drag = ref(false)
const uploading = ref(false)
const progress = reactive<Record<string, 'waiting' | 'uploading' | 'done' | 'error'>>({})
const errors = reactive<Record<string, string>>({})
const inputEl = ref<HTMLInputElement | null>(null)
const MAX = 10 * 1024 * 1024

watch(open, v => { if (v) { files.value = []; Object.keys(progress).forEach(k => delete progress[k]); Object.keys(errors).forEach(k => delete errors[k]) } })
function addFiles(list: FileList | File[]) {
  const incoming = [...list].filter(f => f.type.startsWith('image/'))
  const known = new Set(files.value.map(f => f.name + f.size))
  files.value = [...files.value, ...incoming.filter(f => !known.has(f.name + f.size))].slice(0, 30)
}
function onDrop(e: DragEvent) { drag.value = false; if (e.dataTransfer?.files) addFiles(e.dataTransfer.files) }
function removeFile(i: number) { files.value.splice(i, 1) }
async function uploadAll() {
  if (!files.value.length || uploading.value) return
  uploading.value = true
  for (const file of files.value) {
    const key = file.name + file.size
    if (file.size > MAX) { progress[key] = 'error'; errors[key] = '超过 10MB 限制'; continue }
    progress[key] = 'uploading'
    try {
      if (isSpace.value && spaceId.value) await spacePictureApi.upload(file, spaceId.value, undefined, 'static')
      else await pictureApi.upload(file)
      progress[key] = 'done'
    } catch (e) { progress[key] = 'error'; errors[key] = (e as Error).message }
  }
  uploading.value = false
  const ok = files.value.filter(f => progress[f.name + f.size] === 'done').length
  if (ok) {
    ui.toast(ok + ' 张影像已进入档案馆', 'success')
    window.dispatchEvent(new CustomEvent('nocturne:uploaded', { detail: { spaceId: spaceId.value } }))
    if (!isSpace.value) gallery.refresh()
    if (isSpace.value) spaces.ensureLoaded(true)
    setTimeout(() => { if (!uploading.value) ui.closeUpload() }, 650)
  }
}
const allDone = computed(() => files.value.length > 0 && files.value.every(f => progress[f.name + f.size] === 'done'))
</script>
<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="!uploading && ui.closeUpload()">
      <div class="modal wide">
        <header class="modal-head"><div><h2>{{ isSpace ? '上传到空间' : '上传影像' }}</h2><p>{{ isSpace ? (spaces.nameFor(spaceId) || '团队空间') + ' · 原图将存入 R2' : 'Public archive / 公共图库' }}</p></div><button class="icon-btn" :disabled="uploading" @click="ui.closeUpload()"><Icon name="close" :size="16" /></button></header>
        <div class="modal-body">
          <div class="dropzone" :class="{ drag }" @click="inputEl?.click()" @dragover.prevent="drag = true" @dragleave.prevent="drag = false" @drop.prevent="onDrop">
            <Icon name="upload" :size="26" /><b>拖入照片，或点击选择文件</b><span>JPG / PNG / WEBP · 单张不超过 10 MB · 可一次选择多张</span>
            <input ref="inputEl" type="file" accept="image/*" multiple hidden @change="addFiles(($event.target as HTMLInputElement).files || [])">
          </div>
          <div v-if="files.length" class="upload-list">
            <div v-for="(f, i) in files" :key="f.name + f.size" class="upload-item">
              <Icon name="image" :size="14" /><span class="grow">{{ f.name }}</span><span class="dim">{{ (f.size / 1024 / 1024).toFixed(1) }} MB</span>
              <span v-if="progress[f.name + f.size] === 'uploading'" class="spinner"></span>
              <span v-else-if="progress[f.name + f.size] === 'done'" class="ok">已上传</span>
              <span v-else-if="progress[f.name + f.size] === 'error'" class="err" :title="errors[f.name + f.size]">失败</span>
              <button v-else class="icon-btn" type="button" @click="removeFile(i)"><Icon name="close" :size="12" /></button>
            </div>
          </div>
          <p class="hint" style="margin-top:12px">公共图库上传后，照片会保存在你的个人目录下；团队空间照片仅成员可见。</p>
        </div>
        <footer class="modal-foot"><span class="dim" style="margin-right:auto;font-size:8px;letter-spacing:.1em">{{ files.length }} 个文件</span><button class="btn" :disabled="uploading" @click="ui.closeUpload()">关闭</button><button class="btn gold" :disabled="!files.length || uploading || allDone" @click="uploadAll">{{ uploading ? '上传中…' : '开始上传' }}</button></footer>
      </div>
    </div>
  </Teleport>
</template>
