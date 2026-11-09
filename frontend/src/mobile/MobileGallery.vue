<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import MobilePhotoGrid from './MobilePhotoGrid.vue'
import MobilePhotoViewer from './MobilePhotoViewer.vue'
import MobileSelectionBar from './MobileSelectionBar.vue'
import MobileTabBar from './MobileTabBar.vue'
import InstallHint from './InstallHint.vue'
import EditPhotoModal from '@/components/EditPhotoModal.vue'
import OutpaintingModal from '@/components/OutpaintingModal.vue'
import { fetchAllLivePhotos, useGalleryStore } from '@/stores/gallery'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'
import { favoriteState } from '@/composables/useFavorites'
import { isIOS, isStandalone } from '@/composables/useIsMobile'
import { pictureApi } from '@/api/picture'
import type { LivePictureVO, PictureVO } from '@/api/types'
import type { LibraryMode, Rect } from './types'
import { measurePhoto } from './types'

const route = useRoute()
const router = useRouter()
const gallery = useGalleryStore()
const user = useUserStore()
const ui = useUiStore()

const mode = computed<'all' | 'favorites' | 'live'>(() => route.name === 'favorites' ? 'favorites' : route.name === 'live' ? 'live' : 'all')
const title = computed(() => mode.value === 'favorites' ? '收藏' : mode.value === 'live' ? 'Live 照片' : '图库')

/* ---------- 数据 ---------- */
const livePhotos = ref<PictureVO[]>([])
const liveVideoMap = ref<Record<string, string>>({})
const liveLoading = ref(false)
const favPhotos = ref<PictureVO[]>([])
const favLoading = ref(false)

const basePhotos = computed<PictureVO[]>(() => {
  if (mode.value === 'live') return livePhotos.value
  if (mode.value === 'favorites') return favPhotos.value
  return gallery.photos
})

const searchOpen = ref(false)
const searchText = ref('')
const searchResults = ref<PictureVO[]>([])
const searchLoading = ref(false)
let searchTimer = 0
const searching = computed(() => searchText.value.trim().length > 0)
const photos = computed<PictureVO[]>(() => searching.value ? searchResults.value : basePhotos.value)

const LIB_MODES: LibraryMode[] = ['years', 'months', 'days', 'all']
const storedLib = localStorage.getItem('nocturne.mobile.lib') as LibraryMode | null
const libraryMode = ref<LibraryMode>(storedLib && LIB_MODES.includes(storedLib) ? storedLib : 'all')
watch(libraryMode, v => localStorage.setItem('nocturne.mobile.lib', v))
const gridMode = computed<LibraryMode>(() => searching.value ? 'all' : libraryMode.value)

const gridRef = ref<{ scrollToTop?: () => void } | null>(null)

const columns = ref(clampColumns(Number(localStorage.getItem('nocturne.mobile.cols')) || 4))
watch(columns, v => localStorage.setItem('nocturne.mobile.cols', String(v)))
function clampColumns(n: number) { return Math.min(6, Math.max(2, Math.round(n) || 4)) }

const segIndex = computed(() => Math.max(0, segments.findIndex(s => s.key === libraryMode.value)))
function setLibraryMode(k: LibraryMode) {
  if (libraryMode.value === k) return
  libraryMode.value = k
  gridRef.value?.scrollToTop?.()
}

const segments: { key: LibraryMode; label: string }[] = [
  { key: 'years', label: '年' },
  { key: 'months', label: '月' },
  { key: 'days', label: '日' },
  { key: 'all', label: '全部照片' },
]

const loading = computed(() => mode.value === 'live' ? liveLoading.value : mode.value === 'favorites' ? favLoading.value : (gallery.loading && !gallery.photos.length))
const hasMore = computed(() => !searching.value && mode.value === 'all' && gallery.hasMore)

async function loadCurrent() {
  if (mode.value === 'all') { await gallery.ensureLoaded(); return }
  if (mode.value === 'live') { await loadLive(); return }
  await loadFavorites()
}
async function loadLive() {
  if (liveLoading.value || livePhotos.value.length) return
  liveLoading.value = true
  try {
    const list = await fetchAllLivePhotos()
    livePhotos.value = list.map((x: LivePictureVO) => x.pictureVO)
    liveVideoMap.value = Object.fromEntries(list.filter(x => x.videoUrl).map(x => [x.pictureVO.id, x.videoUrl as string]))
  } catch (e) { ui.toast((e as Error).message, 'error') } finally { liveLoading.value = false }
}
async function loadFavorites() {
  const ids = favoriteState.ids.map(String)
  const known = new Map(gallery.photos.map(p => [String(p.id), p]))
  const missing = ids.filter(id => !known.has(id)).slice(0, 120)
  if (missing.length) {
    favLoading.value = true
    const fetched = await Promise.all(missing.map(id => pictureApi.getVO(id).catch(() => null)))
    fetched.forEach(p => { if (p) known.set(String(p.id), p) })
    favLoading.value = false
  }
  favPhotos.value = ids.map(id => known.get(id)).filter((p): p is PictureVO => !!p)
}
async function loadMore() {
  if (searching.value || mode.value !== 'all') return
  await gallery.load(false)
}
function runSearch(force = false) {
  const q = searchText.value.trim()
  if (!q) { searchResults.value = []; return }
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(async () => {
    searchLoading.value = true
    try {
      const page = await pictureApi.listVOByPage({ current: 1, pageSize: 60, searchText: q, sortField: 'createTime', sortOrder: 'descend' })
      if (q === searchText.value.trim() || force) searchResults.value = page.records
    } catch (e) { ui.toast((e as Error).message, 'error') } finally { searchLoading.value = false }
  }, 220)
}
watch(searchText, () => { if (searching.value) runSearch(); else searchResults.value = [] })

/* ---------- 顶栏 / 滚动 ---------- */
const solid = ref(false)
const bigTitleEl = ref<HTMLElement | null>(null)
let lastScrollY = 0
let scrollRaf = 0
// 滚动联动只直写 DOM（transform/opacity），不进入 Vue 渲染队列
function applyScroll(y: number) {
  const t = Math.min(y, 72)
  const el = bigTitleEl.value
  if (el) {
    el.style.transform = 'translate3d(0,' + (-t * 0.85) + 'px,0)'
    el.style.opacity = String(Math.max(0, 1 - t / 56))
  }
  const next = y > 8
  if (next !== solid.value) solid.value = next
}
function onScroll(e: Event) {
  lastScrollY = (e.target as HTMLElement).scrollTop || 0
  if (scrollRaf) return
  scrollRaf = requestAnimationFrame(() => { scrollRaf = 0; applyScroll(lastScrollY) })
}
function resetScroll() { lastScrollY = 0; applyScroll(0); toTop() }
function toTop() { const el = document.querySelector('.m-body .m-scroll-inner') as HTMLElement | null; const scroller = document.querySelector('.m-body [data-scroll-root]') as HTMLElement | null; if (scroller) scroller.scrollTop = 0; else if (el) el.scrollTop = 0 }

/* ---------- 选择 ---------- */
const selecting = ref(false)
const selected = ref<string[]>([])
const canDelete = computed(() => selected.value.length > 0 && selected.value.every(id => { const p = photos.value.find(x => String(x.id) === String(id)); return !!p && canEdit(p) }))
const allFavorite = computed(() => selected.value.length > 0 && selected.value.every(id => favoriteState.ids.includes(id)))
function canEdit(p: PictureVO) { return !!user.user && (user.isAdmin || String(p.userId) === String(user.user.id)) }
function toggleSelect() {
  if (selecting.value) { selecting.value = false; selected.value = [] } else selecting.value = true
}
function onSelecting(v: boolean) { selecting.value = v; if (!v) selected.value = [] }
function onSelected(ids: string[]) { selected.value = ids }
async function deleteSelected() {
  const ids = selected.value.slice()
  if (!ids.length) return
  const ok = await ui.confirm({ title: '删除 ' + ids.length + ' 张照片？', message: '选中的影像会从图库中永久移除，此操作不可撤销。', confirmText: '删除', danger: true })
  if (!ok) return
  let done = 0, failed = 0
  for (const id of ids) {
    try { await pictureApi.delete(id); gallery.removeLocal(id); livePhotos.value = livePhotos.value.filter(p => String(p.id) !== String(id)); done++ }
    catch { failed++ }
  }
  selected.value = []; selecting.value = false
  await loadFavorites()
  ui.toast(failed ? done + ' 张已删除，' + failed + ' 张未能删除' : done + ' 张照片已删除', failed ? 'error' : 'success')
}
async function favoriteSelected() {
  const ids = selected.value.slice()
  if (!ids.length) return
  const on = !allFavorite.value
  ids.forEach(id => { const has = favoriteState.ids.includes(id); if (on && !has) favoriteState.ids.push(id); if (!on && has) favoriteState.ids.splice(favoriteState.ids.indexOf(id), 1) })
  ui.toast(on ? '已收藏 ' + ids.length + ' 张' : '已取消收藏')
}
async function shareSelected() {
  const list = photos.value.filter(p => selected.value.includes(String(p.id)))
  if (!list.length) return
  const urls = list.map(p => p.url).filter(Boolean)
  try {
    if (navigator.share && list.length === 1) { await navigator.share({ title: list[0].name || '照片', url: urls[0] }); return }
    await navigator.clipboard.writeText(urls.join('\n'))
    ui.toast(urls.length > 1 ? '已复制 ' + urls.length + ' 个链接' : '链接已复制')
  } catch { /* 用户取消 */ }
}

/* ---------- 查看器 ---------- */
const viewerMounted = ref(false)
const viewerOpen = ref(false)
const viewerIndex = ref(0)
const originRect = ref<Rect | null>(null)
function openViewer(i: number) {
  const p = photos.value[i]
  originRect.value = p ? measurePhoto(p.id) : null
  viewerIndex.value = i
  viewerMounted.value = true
  nextTick(() => { viewerOpen.value = true })
}
function onViewerClosed() {
  viewerMounted.value = false
  viewerOpen.value = false
  // 关闭后把对应缩略图滚回可见区域
  const p = photos.value[viewerIndex.value]
  if (p) document.querySelector('[data-photo-id="' + p.id + '"]')?.scrollIntoView({ block: 'nearest' })
}
const viewerCanEdit = computed(() => { const p = photos.value[viewerIndex.value]; return !!p && canEdit(p) })

/* ---------- 编辑 / 扩图 ---------- */
const editOpen = ref(false)
const editing = ref<PictureVO | null>(null)
const outpaintOpen = ref(false)
const outpainting = ref<PictureVO | null>(null)
function openEdit(p: PictureVO) { editing.value = p; editOpen.value = true }
function openOutpaint(p: PictureVO) { outpainting.value = p; outpaintOpen.value = true }
function patchPhoto(updated: PictureVO) {
  const apply = (list: PictureVO[]) => {
    const i = list.findIndex(x => x.id === updated.id)
    if (i >= 0) list[i] = { ...list[i], ...updated }
  }
  apply(gallery.photos); apply(livePhotos.value); apply(favPhotos.value); apply(searchResults.value)
}
async function deletePhoto(p: PictureVO) {
  const ok = await ui.confirm({ title: '删除这张照片？', message: '这张影像会从图库中永久移除，此操作不可撤销。', confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await pictureApi.delete(p.id)
    gallery.removeLocal(p.id)
    livePhotos.value = livePhotos.value.filter(x => x.id !== p.id)
    favPhotos.value = favPhotos.value.filter(x => x.id !== p.id)
    searchResults.value = searchResults.value.filter(x => x.id !== p.id)
    viewerOpen.value = false
    ui.toast('照片已删除', 'success')
  } catch (e) { ui.toast((e as Error).message, 'error') }
}

/* ---------- 其他弹层 ---------- */
const installOpen = ref(false)
function openUpload() {
  if (!user.isLoggedIn) { router.push({ name: 'login', query: { redirect: route.fullPath } }); return }
  ui.openUpload()
}
function closeSearch() { searchOpen.value = false; searchText.value = '' }

function onUploaded() { gallery.refresh(); if (mode.value === 'live') { livePhotos.value = []; loadLive() } }
onMounted(() => {
  loadCurrent()
  applyScroll(0)
  window.addEventListener('nocturne:uploaded', onUploaded)
  if (isIOS() && !isStandalone() && localStorage.getItem('nocturne.install.hint') !== 'off') {
    window.setTimeout(() => { installOpen.value = true }, 2600)
  }
})
onBeforeUnmount(() => { window.clearTimeout(searchTimer); if (scrollRaf) cancelAnimationFrame(scrollRaf); window.removeEventListener('nocturne:uploaded', onUploaded) })
watch(mode, () => { selecting.value = false; selected.value = []; searchOpen.value = false; resetScroll(); loadCurrent() })
watch(() => favoriteState.ids.length, () => { if (mode.value === 'favorites') loadFavorites() })
function onInstallClose(v: boolean) { installOpen.value = v; if (!v) localStorage.setItem('nocturne.install.hint', 'off') }
</script>

<template>
  <div class="m-app">
    <header class="m-head" :class="{ solid }">
      <div class="m-head-row">
        <span class="m-head-title">{{ title }}</span>
        <div class="m-head-actions">
          <button class="m-ic" type="button" aria-label="搜索" @click="searchOpen = true"><Icon name="search" :size="21" /></button>
          <button class="m-ic" type="button" aria-label="上传" @click="openUpload"><Icon name="plus" :size="23" /></button>
          <button class="m-text" type="button" @click="toggleSelect">{{ selecting ? '完成' : '选择' }}</button>
        </div>
      </div>
    </header>

    <div ref="bigTitleEl" class="m-bigtitle">
      <h1>{{ title }}</h1>
      <span>{{ photos.length }} 张</span>
    </div>

    <div class="m-body" :class="{ 'is-selecting': selecting }" @scroll.capture="onScroll">
      <MobilePhotoGrid
    ref="gridRef"
        :photos="photos"
        :mode="gridMode"
        v-model:columns="columns"
        v-model:selecting="selecting"
        v-model:selected="selected"
        :show-live-badge="mode === 'live'"
        :loading="loading || searchLoading"
        :has-more="hasMore"
        @open="openViewer"
        @load-more="loadMore"
        @update:selecting="onSelecting"
        @update:selected="onSelected"
      >
        <template #empty>
          <div class="m-empty">
            <Icon name="image" :size="34" />
            <b>{{ searching ? '没有找到相关照片' : mode === 'favorites' ? '还没有收藏' : mode === 'live' ? '还没有 Live 照片' : '图库还是空的' }}</b>
            <p>{{ searching ? '换一个关键词试试' : mode === 'favorites' ? '在查看照片时点收藏，它会出现在这里' : '上传第一张影像，开始建立你的档案馆' }}</p>
            <button v-if="!searching && mode === 'all'" class="m-empty-btn" type="button" @click="openUpload">上传照片</button>
          </div>
        </template>
      </MobilePhotoGrid>
    </div>

    <div v-if="!selecting" class="m-seg" role="tablist" aria-label="浏览方式">
      <span class="m-seg-pill" :style="{ transform: `translate3d(${segIndex * 100}%, 0, 0)` }" aria-hidden="true" />
      <button v-for="s in segments" :key="s.key" type="button" role="tab" :aria-selected="libraryMode === s.key" :class="{ on: libraryMode === s.key }" @click="setLibraryMode(s.key)">{{ s.label }}</button>
    </div>

    <MobileTabBar :hidden="selecting" />
    <MobileSelectionBar v-if="selecting" :count="selected.length" :can-delete="canDelete" :favorite-all="allFavorite" @delete="deleteSelected" @favorite="favoriteSelected" @share="shareSelected" @cancel="selecting = false; selected = []" />

    <Transition name="m-fade">
      <div v-if="searchOpen" class="m-search">
        <div class="m-search-bar">
          <div class="m-search-field">
            <Icon name="search" :size="16" />
            <input v-model="searchText" type="search" placeholder="搜索照片、分类或标签" autocomplete="off" autocorrect="off" spellcheck="false" enterkeyhint="search">
            <button v-if="searchText" type="button" aria-label="清除" @click="searchText = ''"><Icon name="close" :size="15" /></button>
          </div>
          <button class="m-text" type="button" @click="closeSearch">取消</button>
        </div>
        <div class="m-search-body">
          <MobilePhotoGrid v-if="searching" :photos="searchResults" mode="all" :columns="4" :selecting="false" :selected="[]" :loading="searchLoading" @open="openViewer" />
          <div v-else class="m-search-hint">
            <b>搜索你的影像</b>
            <span>试试照片名称、分类或标签</span>
          </div>
        </div>
      </div>
    </Transition>

    <MobilePhotoViewer
      v-if="viewerMounted"
      :photos="photos"
      v-model:index="viewerIndex"
      :open="viewerOpen"
      :origin-rect="originRect"
      :video-map="liveVideoMap"
      :can-edit="viewerCanEdit"
      @update:open="v => { if (!v) viewerOpen = false }"
      @close="onViewerClosed"
      @edit="openEdit"
      @delete="deletePhoto"
      @outpainting="openOutpaint"
    />

    <EditPhotoModal v-model:open="editOpen" :photo="editing" @saved="patchPhoto" />
    <OutpaintingModal v-model:open="outpaintOpen" :photo="outpainting" @saved="patchPhoto" />

    <InstallHint :open="installOpen" @update:open="onInstallClose" />
  </div>
</template>

<style scoped>
.m-head{position:absolute;top:0;left:0;right:0;z-index:30;padding-top:var(--safe-top);background:transparent;transition:background var(--dur-fade) var(--spring-fade)}
.m-head.solid{background:rgba(8,8,9,.88);box-shadow:0 .5px 0 rgba(255,255,255,.12)}
.m-head-row{height:52px;display:flex;align-items:center;gap:8px;padding:0 8px 0 16px}
.m-head-title{font-size:17px;font-weight:600;letter-spacing:.01em;opacity:0;transition:opacity var(--dur-fade) var(--spring-fade)}
.m-head.solid .m-head-title{opacity:1}
.m-head-actions{margin-left:auto;display:flex;align-items:center;gap:2px}
.m-ic{width:44px;height:44px;display:grid;place-items:center;color:#fff;border-radius:50%}
.m-ic:active{opacity:.5}
.m-text{height:44px;padding:0 10px;color:var(--ios-blue);font-size:17px;letter-spacing:.01em}
.m-text:active{opacity:.5}
.m-bigtitle{position:absolute;z-index:25;top:calc(var(--safe-top) + 52px);left:16px;right:16px;display:flex;align-items:baseline;gap:10px;pointer-events:none;will-change:transform,opacity}
.m-bigtitle h1{font-size:34px;font-weight:700;letter-spacing:.01em;line-height:1.15;margin:0}
.m-bigtitle span{font-size:13px;color:var(--m-text-3)}
.m-body{position:absolute;top:calc(var(--safe-top) + 52px + 44px);left:0;right:0;bottom:calc(var(--m-tab) + var(--safe-bottom) + 46px);overflow:hidden}
.m-body.is-selecting{bottom:calc(var(--m-tab) + var(--safe-bottom))}
.m-empty{display:flex;flex-direction:column;align-items:center;gap:10px;padding:80px 30px;color:var(--m-text-3);text-align:center}
.m-empty b{font-size:17px;font-weight:600;color:var(--m-text-2)}
.m-empty p{font-size:13px;margin:0;line-height:1.5}
.m-empty-btn{margin-top:8px;height:36px;padding:0 18px;border-radius:18px;background:var(--ios-blue);color:#fff;font-size:14px;font-weight:600}
.m-seg{position:absolute;z-index:35;left:50%;transform:translateX(-50%);bottom:calc(var(--m-tab) + var(--safe-bottom) + 8px);display:flex;width:268px;padding:2px;border-radius:9px;background:rgba(38,38,40,.92);box-shadow:0 2px 12px rgba(0,0,0,.4)}
.m-seg-pill{position:absolute;top:2px;bottom:2px;left:2px;width:calc((100% - 4px) / 4);border-radius:7px;background:rgba(99,99,102,.92);transition:transform var(--dur-bar) var(--spring-bar);will-change:transform}
.m-seg button{position:relative;z-index:1;flex:1;height:28px;padding:0;border-radius:7px;font-size:12px;font-weight:500;color:rgba(235,235,245,.72);transition:color var(--dur-fade) ease}
.m-seg button.on{color:#fff}
.m-search{position:absolute;inset:0;z-index:60;background:#000;display:flex;flex-direction:column}
.m-search-bar{display:flex;align-items:center;gap:8px;padding:calc(var(--safe-top) + 8px) 12px 8px}
.m-search-field{flex:1;display:flex;align-items:center;gap:6px;height:36px;padding:0 10px;border-radius:10px;background:rgba(118,118,128,.24);color:var(--m-text-3)}
.m-search-field input{flex:1;min-width:0;background:none;border:0;outline:0;color:#fff;font-size:16px}
.m-search-field input::placeholder{color:var(--m-text-3)}
.m-search-field button{width:20px;height:20px;display:grid;place-items:center;border-radius:50%;background:rgba(235,235,245,.3);color:#000}
.m-search-body{flex:1;min-height:0;position:relative;overflow:hidden}
.m-search-hint{display:flex;flex-direction:column;gap:6px;align-items:center;padding-top:30vh;color:var(--m-text-3)}
.m-search-hint b{font-size:17px;color:var(--m-text-2);font-weight:600}
.m-search-hint span{font-size:13px}
.m-fade-enter-active,.m-fade-leave-active{transition:opacity var(--dur-fade) var(--spring-fade)}
.m-fade-enter-from,.m-fade-leave-to{opacity:0}
</style>