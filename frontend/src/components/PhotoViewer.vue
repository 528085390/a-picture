<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { PictureVO } from '@/api/types'
import { pictureApi } from '@/api/picture'
import { useFavorites } from '@/composables/useFavorites'
import { useUiStore } from '@/stores/ui'
import { formatBytes, formatDate, fileExt, pad } from '@/utils/format'
import Icon from './Icon.vue'
import { mediaUrl, thumbUrl } from '@/utils/media'

const props = withDefaults(defineProps<{
  open: boolean
  photos: PictureVO[]
  index: number
  videoMap?: Record<string, string>
  canEdit?: boolean
  collab?: boolean
}>(), { videoMap: () => ({}), canEdit: false, collab: false })
const emit = defineEmits<{
  'update:open': [v: boolean]
  'update:index': [v: number]
  edit: [photo: PictureVO]
  delete: [photo: PictureVO]
  outpainting: [photo: PictureVO]
  collab: [photo: PictureVO]
}>()
const ui = useUiStore()
const favorites = useFavorites()
const infoOpen = ref(false)
const immersive = ref(false)
const playing = ref(false)
const mainFailed = ref(false)
const usePreview = ref(false)
const failedThumbs = ref<Set<string>>(new Set())
const photo = computed(() => props.photos[props.index] || null)
const displaySrc = computed(() => { const p = photo.value; if (!p) return ''; return mediaUrl(usePreview.value ? (p.previewUrl || p.url || '') : (p.url || p.previewUrl || '')) })
const videoUrl = computed(() => photo.value ? props.videoMap[photo.value.id] || '' : '')
const isLive = computed(() => !!videoUrl.value)
const filmEl = ref<HTMLElement | null>(null)
const videoEl = ref<HTMLVideoElement | null>(null)
const total = computed(() => props.photos.length)

function close() { emit('update:open', false) }
function prev() { if (props.index > 0) emit('update:index', props.index - 1) }
function next() { if (props.index < props.photos.length - 1) emit('update:index', props.index + 1) }
function onMainError() { const p = photo.value; if (p && !usePreview.value && p.previewUrl && p.previewUrl !== p.url) { usePreview.value = true; return } mainFailed.value = true }
function markThumbFailed(id: string) { const next = new Set(failedThumbs.value); next.add(id); failedThumbs.value = next }
function toggleFav() { if (!photo.value) return; favorites.toggle(photo.value.id); ui.toast(favorites.has(photo.value.id) ? '已加入收藏' : '已从收藏移除') }
async function download() {
  const p = photo.value; if (!p?.url) return
  try {
    const blob = await pictureApi.download(p.url)
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = (p.name || 'photo') + '.' + (p.picFormat || fileExt(p.url).toLowerCase() || 'jpg'); a.click(); URL.revokeObjectURL(a.href)
    ui.toast('原图已开始下载')
  } catch (e) { ui.toast((e as Error).message || '下载失败', 'error') }
}
function toggleImmersive() { immersive.value = !immersive.value; if (immersive.value) infoOpen.value = false }
function playLive() {
  if (!isLive.value) return
  const v = videoEl.value; if (!v) return
  if (v.paused) { v.play().then(() => { playing.value = true }).catch(() => { playing.value = false }) }
  else { v.pause(); playing.value = false }
}
function onKey(e: KeyboardEvent) {
  if (!props.open) return
  if (e.key === 'Escape') { if (immersive.value) immersive.value = false; else if (infoOpen.value) infoOpen.value = false; else close() }
  else if (e.key === 'ArrowLeft') prev(); else if (e.key === 'ArrowRight') next()
  else if (e.key.toLowerCase() === 'i') infoOpen.value = !infoOpen.value
}
watch(() => props.open, async v => {
  document.body.classList.toggle('locked', v)
  if (v) {
    infoOpen.value = false; immersive.value = false; playing.value = false; mainFailed.value = false; usePreview.value = false
    window.addEventListener('keydown', onKey)
    await nextTick(); scrollActiveThumb()
  } else {
    window.removeEventListener('keydown', onKey)
    if (videoEl.value) videoEl.value.pause()
  }
})
watch(() => props.index, async () => { playing.value = false; infoOpen.value = false; mainFailed.value = false; usePreview.value = false; await nextTick(); scrollActiveThumb() })
function scrollActiveThumb() {
  const active = filmEl.value?.querySelector('.v-thumb.active') as HTMLElement | null
  active?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' })
}
onBeforeUnmount(() => { window.removeEventListener('keydown', onKey); document.body.classList.remove('locked') })
</script>
<template>
  <Teleport to="body">
    <div class="viewer" :class="{ open, 'info-open': infoOpen, immersive }" role="dialog" aria-modal="true" :aria-hidden="!open">
      <header class="v-top">
        <button class="v-back" type="button" @click="close"><Icon name="arrow-left" :size="15" /><span>返回图库</span></button>
        <div class="v-title"><b>{{ photo?.name || '未命名影像' }}</b><span>{{ formatDate(photo?.createTime) }} · {{ photo?.category || '私人档案馆' }}</span></div>
        <div class="v-actions">
          <button class="ic" type="button" title="照片信息 (I)" aria-label="照片信息" @click="infoOpen = !infoOpen"><Icon name="info" :size="16" /></button>
          <button v-if="isLive" class="ic" type="button" title="播放 Live" aria-label="播放 Live" @click="playLive"><Icon name="play" :size="15" /></button>
          <button class="ic heart" :class="{ on: photo && favorites.has(photo.id) }" type="button" title="收藏" aria-label="收藏" @click="toggleFav"><Icon name="heart" :size="16" /></button>
          <button class="ic" type="button" title="下载原图" aria-label="下载原图" @click="download"><Icon name="download" :size="16" /></button>
          <button class="ic" type="button" title="沉浸模式" aria-label="沉浸模式" @click="toggleImmersive"><Icon name="expand" :size="16" /></button>
        </div>
      </header>
      <div class="v-stage">
        <button class="v-nav prev" type="button" :disabled="index <= 0" aria-label="上一张" @click="prev"><Icon name="chevron-left" :size="16" /></button>
        <div class="photo-wrap" :class="{ playing }" title="点击进入 / 退出沉浸模式" @click="toggleImmersive">
          <img v-if="photo && !mainFailed && open" :src="displaySrc" :alt="photo.name || ''" loading="eager" decoding="async" @error="onMainError">
          <div v-else-if="photo" class="viewer-fallback"><span>NOCTURNE</span><b>{{ photo.name || '未命名影像' }}</b><em>原始文件不可访问 · 档案记录仍然保留</em></div>
          <video v-if="isLive" ref="videoEl" :src="mediaUrl(videoUrl)" muted playsinline preload="metadata" @ended="playing = false" />
          <div v-if="isLive" class="v-live"><i></i>LIVE</div>
          <div class="live-line"><i /></div>
        </div>
        <button class="v-nav next" type="button" :disabled="index >= total - 1" aria-label="下一张" @click="next"><Icon name="chevron-right" :size="16" /></button>
      </div>
      <aside class="v-info">
        <div class="info-kicker">Collection entry / 照片档案</div>
        <h3>{{ photo?.name || '未命名影像' }}</h3>
        <div class="info-en">{{ photo?.category || 'Nocturne Archive' }}</div>
        <div class="info-rule"></div>
        <div class="meta">
          <div><span>File</span><b>{{ photo?.id }}</b></div>
          <div><span>Size</span><b>{{ formatBytes(photo?.picSize) }}</b></div>
          <div><span>Dimensions</span><b>{{ photo?.picWidth && photo?.picHeight ? photo.picWidth + ' × ' + photo.picHeight : '—' }}</b></div>
          <div><span>Format</span><b>{{ (photo?.picFormat || fileExt(photo?.url)).toUpperCase() }}</b></div>
          <div><span>Captured</span><b>{{ formatDate(photo?.createTime, true) }}</b></div>
          <div><span>Collection</span><b>{{ photo?.spaceId ? '团队空间' : '公共图库' }}</b></div>
          <div><span>Uploader</span><b>{{ photo?.user?.userName || photo?.user?.userAccount || '—' }}</b></div>
          <div><span>Ratio</span><b>{{ photo?.picScale ? Number(photo.picScale).toFixed(2) : '—' }}</b></div>
        </div>
        <p v-if="photo?.introduction" class="info-intro">{{ photo.introduction }}</p>
        <div v-if="photo?.tags?.length" class="info-tags"><span v-for="tag in photo.tags" :key="tag">{{ tag }}</span></div>
        <div v-if="collab" class="collab-box"><b>Collaborative edit</b>此照片属于团队空间。打开协同编辑器，与成员实时共享缩放与旋转动作。</div>
        <div class="info-actions">
          <button v-if="canEdit" type="button" @click="photo && emit('outpainting', photo)">AI 扩图</button>
          <button v-if="canEdit" type="button" @click="photo && emit('edit', photo)">编辑信息</button>
          <button v-if="collab && canEdit" type="button" @click="photo && emit('collab', photo)">协同编辑</button>
          <button v-if="canEdit" type="button" class="danger-text" @click="photo && emit('delete', photo)">删除照片</button>
          <a class="wide" href="javascript:void(0)" @click="download">下载原图</a>
        </div>
      </aside>
      <div class="v-bottom">
        <div ref="filmEl" class="v-thumbs">
          <button v-for="(p, i) in photos" :key="p.id" class="v-thumb" :class="{ active: i === index }" type="button" @click="emit('update:index', i)"><img v-if="open && !failedThumbs.has(p.id)" :src="thumbUrl(p.previewUrl || p.url, 240)" :alt="p.name || ''" loading="lazy" decoding="async" @error="markThumbFailed(p.id)"><span v-else class="thumb-fallback"><Icon name="image" :size="12" /></span></button>
        </div>
        <div class="v-count"><b>{{ pad(index + 1) }}</b><span>/ {{ total }}</span></div>
      </div>
      <div class="v-hint">点击画面 · 进入沉浸模式</div>
      <div class="immersive-note">Immersive / 沉浸模式 · 点击画面恢复操作</div>
    </div>
  </Teleport>
</template>
<style scoped>
.v-nav:disabled{opacity:.15;pointer-events:none}
.ic.heart.on{color:var(--gold)}
.danger-text{color:var(--danger)!important;border-color:rgba(217,140,128,.25)!important}
.viewer-fallback{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;background:radial-gradient(circle at 50% 40%,rgba(201,169,106,.13),transparent 55%),linear-gradient(145deg,#111116,#070708)}.viewer-fallback span{font-family:var(--serif);font-size:10px;letter-spacing:.36em;color:var(--gold-soft)}.viewer-fallback b{font-family:var(--cn);font-weight:300;font-size:24px;letter-spacing:.08em;color:var(--ivory)}.viewer-fallback em{font-style:normal;font-size:8px;letter-spacing:.12em;color:var(--dim)}
.v-thumb .thumb-fallback{display:grid;place-items:center;width:100%;height:100%;color:var(--gold-soft);background:linear-gradient(145deg,#111116,#08080a)}
</style>
