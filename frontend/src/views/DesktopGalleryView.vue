<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PhotoGrid from '@/components/PhotoGrid.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import EditPhotoModal from '@/components/EditPhotoModal.vue'
import OutpaintingModal from '@/components/OutpaintingModal.vue'
import Icon from '@/components/Icon.vue'
import { useGalleryStore, fetchAllLivePhotos } from '@/stores/gallery'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'
import { useFavorites } from '@/composables/useFavorites'
import { useThumbZoom, ZOOM_STEPS } from '@/composables/useThumbZoom'
import { pictureApi } from '@/api/picture'
import type { LivePictureVO, PictureVO } from '@/api/types'

const route = useRoute()
const router = useRouter()
const gallery = useGalleryStore()
const user = useUserStore()
const ui = useUiStore()
const favorites = useFavorites()
const { thumb, label, setZoom, stepZoom } = useThumbZoom()
const gridRef = ref<InstanceType<typeof PhotoGrid> | null>(null)
const viewerOpen = ref(false)
const viewerIndex = ref(0)
const editOpen = ref(false)
const editPhoto = ref<PictureVO | null>(null)
const outpaintOpen = ref(false)
const outpaintPhoto = ref<PictureVO | null>(null)
const selectMode = ref(false)
const selection = ref<string[]>([])
const livePhotos = ref<PictureVO[]>([])
const favoritePhotos = ref<PictureVO[]>([])
const favoritesLoadedFor = ref('')
const videoMap = ref<Record<string, string>>({})
const liveLoading = ref(false)
const liveLoaded = ref(false)
const searchLoading = ref(false)
const searchLoadedFor = ref('')
const searchResults = ref<PictureVO[]>([])
const searchTotal = ref(0)
const searchPage = ref(1)
const searchHasMore = ref(false)
const searchMoreLoading = ref(false)
const activeCategory = ref('')
const searching = computed(() => typeof route.query.q === 'string' && route.query.q.trim().length > 0)
const rawQuery = computed(() => typeof route.query.q === 'string' ? route.query.q.trim() : '')
const q = computed(() => rawQuery.value.toLowerCase())
const searchOpen = ref(!!rawQuery.value)
const searchText = ref(rawQuery.value)
const filtersOpen = ref(false)
const searchInput = ref<HTMLInputElement | null>(null)
const mode = computed(() => route.name === 'favorites' ? 'favorites' : route.name === 'live' ? 'live' : route.query.view === 'recent' ? 'recent' : 'all')
const modeTitle = computed(() => mode.value === 'favorites' ? '收藏' : mode.value === 'live' ? 'Live 照片' : mode.value === 'recent' ? '最近上传' : '全部照片')
const kicker = computed(() => mode.value === 'favorites' ? 'Collected / 私人收藏' : mode.value === 'live' ? 'Living moments / 会呼吸的照片' : mode.value === 'recent' ? 'Recent arrivals / 新近入库' : 'Private archive / 私人图库')
const basePhotos = computed<PictureVO[]>(() => {
  if (mode.value === 'live') return livePhotos.value
  if (mode.value === 'favorites') {
    const map = new Map<string, PictureVO>()
    favoritePhotos.value.forEach(p => map.set(String(p.id), p))
    gallery.photos.forEach(p => { if (favorites.has(p.id)) map.set(String(p.id), p) })
    return favorites.favorites.ids.map(id => map.get(String(id))).filter((p): p is PictureVO => !!p)
  }
  if (mode.value === 'recent') return gallery.photos.slice(0, 24)
  return gallery.photos
})
const categories = computed(() => {
  const map = new Map<string, number>()
  basePhotos.value.forEach(p => { if (p.category) map.set(p.category, (map.get(p.category) || 0) + 1) })
  return [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, 7).map(([name, count]) => ({ name, count }))
})
const photos = computed(() => {
  let list = basePhotos.value
  if (activeCategory.value) list = list.filter(p => p.category === activeCategory.value)
  if (q.value) {
    if (mode.value === 'all' || mode.value === 'recent') return searchResults.value
    const needle = q.value
    list = list.filter(p => [p.name, p.category, p.introduction, p.user?.userName, ...(p.tags || [])].filter(Boolean).join(' ').toLowerCase().includes(needle))
  }
  return list
})
const displayCount = computed(() => {
  if (q.value) return mode.value === 'all' || mode.value === 'recent' ? searchTotal.value : photos.value.length
  if (activeCategory.value || mode.value === 'favorites') return photos.value.length
  if (mode.value === 'recent') return Math.min(24, gallery.total)
  return gallery.total
})
const loading = computed(() => mode.value === 'live' ? liveLoading.value : (gallery.loading && !gallery.photos.length) || searchLoading.value)
const loadingMore = computed(() => mode.value !== 'live' && (q.value ? searchMoreLoading.value : gallery.loadingMore))
const canLoadMore = computed(() => q.value ? (mode.value === 'all' || mode.value === 'recent') && searchHasMore.value : mode.value === 'all' && !activeCategory.value && gallery.hasMore)
const canEdit = (p: PictureVO) => !!user.user && (user.isAdmin || String(p.userId) === String(user.user.id))
const viewerPhoto = computed(() => photos.value[viewerIndex.value] || null)
const viewerCanEdit = computed(() => !!viewerPhoto.value && canEdit(viewerPhoto.value))
const canBatchDelete = computed(() => user.isLoggedIn && selection.value.every(id => { const p = photos.value.find(x => x.id === id); return p ? canEdit(p) : false }))
const emptyTitle = computed(() => q.value ? '没有找到这张照片' : mode.value === 'favorites' ? '收藏夹还是空的' : mode.value === 'live' ? '还没有 Live 照片' : '图库还是空的')
const emptyText = computed(() => q.value ? '换一个关键词，或试试照片的分类与标签。' : mode.value === 'favorites' ? '在查看照片时点亮心形，它会出现在这里。' : '上传第一张影像，开始建立你的私人档案馆。')

async function loadInitial() {
  if (mode.value === 'live') { await loadLive(); return }
  await gallery.ensureLoaded()
  if (mode.value === 'favorites') await loadFavoritePhotos()
  if (searching.value) await ensureSearchLoaded()
  openPhotoFromQuery()
}
async function loadFavoritePhotos() {
  const ids = favorites.favorites.ids.slice()
  const key = ids.slice().sort().join(',')
  if (favoritesLoadedFor.value === key) return
  const known = new Map(gallery.photos.map(p => [String(p.id), p]))
  const missing = ids.filter(id => !known.has(String(id))).slice(0, 80)
  const fetched = await Promise.all(missing.map(async id => { try { return await pictureApi.getVO(id) } catch { return null } }))
  const all = new Map(known); fetched.forEach(p => { if (p) all.set(String(p.id), p) })
  favoritePhotos.value = ids.map(id => all.get(String(id))).filter((p): p is PictureVO => !!p)
  favoritesLoadedFor.value = key
}
async function loadLive() {
  if (liveLoading.value) return
  liveLoading.value = true
  try {
    const list = await fetchAllLivePhotos()
    livePhotos.value = list.map((x: LivePictureVO) => x.pictureVO)
    videoMap.value = Object.fromEntries(list.filter(x => x.videoUrl).map(x => [x.pictureVO.id, x.videoUrl as string]))
    liveLoaded.value = true
  } catch (e) { ui.toast((e as Error).message, 'error') } finally { liveLoading.value = false }
}
async function ensureSearchLoaded(force = false) {
  if (!searching.value) return
  if (!force && searchLoadedFor.value === q.value) return
  searchLoading.value = true
  searchPage.value = 1
  try {
    const page = await pictureApi.listVOByPage({ current: 1, pageSize: 20, searchText: rawQuery.value, sortField: 'createTime', sortOrder: 'descend' })
    searchResults.value = page.records
    searchTotal.value = page.total
    searchHasMore.value = page.records.length < page.total
    searchLoadedFor.value = q.value
  } catch (e) { ui.toast((e as Error).message, 'error') } finally { searchLoading.value = false }
}
async function loadSearchMore() {
  if (!q.value || searchMoreLoading.value || !searchHasMore.value) return
  searchMoreLoading.value = true
  try {
    const next = searchPage.value + 1
    const page = await pictureApi.listVOByPage({ current: next, pageSize: 20, searchText: rawQuery.value, sortField: 'createTime', sortOrder: 'descend' })
    const known = new Set(searchResults.value.map(p => p.id))
    const fresh = page.records.filter(p => !known.has(p.id))
    searchTotal.value = page.total
    for (let i = 0; i < fresh.length; i += 4) {
      searchResults.value = [...searchResults.value, ...fresh.slice(i, i + 4)]
      if (i + 4 < fresh.length) await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
    }
    searchPage.value = next
    searchHasMore.value = searchResults.value.length < page.total
  } catch (e) { ui.toast((e as Error).message, 'error') } finally { searchMoreLoading.value = false }
}
function openViewer(i: number) { viewerIndex.value = i; viewerOpen.value = true }
function openEdit(p: PictureVO) { editPhoto.value = p; editOpen.value = true }
function onSaved(p: PictureVO) {
  const idx = gallery.photos.findIndex(x => x.id === p.id); if (idx >= 0) Object.assign(gallery.photos[idx], p)
  const li = livePhotos.value.findIndex(x => x.id === p.id); if (li >= 0) Object.assign(livePhotos.value[li], p)
}
async function deleteOne(p: PictureVO) {
  const ok = await ui.confirm({ title: '删除这张影像？', message: '照片将从图库中移除，此操作不可撤销。', confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await pictureApi.delete(p.id)
    gallery.removeLocal(p.id)
    livePhotos.value = livePhotos.value.filter(x => x.id !== p.id)
    if (viewerOpen.value && viewerIndex.value >= photos.value.length) viewerIndex.value = Math.max(0, photos.value.length - 1)
    if (!photos.value.length) viewerOpen.value = false
    ui.toast('照片已删除')
  } catch (e) { ui.toast((e as Error).message, 'error') }
}
async function batchDelete(ids: string[]) {
  if (!ids.length) return
  const ok = await ui.confirm({ title: '删除 ' + ids.length + ' 张照片？', message: '选中的影像会从图库中永久移除。', confirmText: '全部删除', danger: true })
  if (!ok) return
  let done = 0, failed = 0
  for (const id of ids) {
    try { await pictureApi.delete(id); gallery.removeLocal(id); livePhotos.value = livePhotos.value.filter(x => x.id !== id); done++ }
    catch { failed++ }
  }
  selection.value = []; selectMode.value = false
  if (failed) ui.toast(done + ' 张已删除，' + failed + ' 张因权限或状态未能删除', 'error')
  else ui.toast(done + ' 张照片已删除', 'success')
}
function onSelection(ids: string[]) { selection.value = ids }
function openUpload() { if (!user.isLoggedIn) { router.push({ name: 'login', query: { redirect: route.fullPath } }); return } ui.openUpload() }
function queryWithout(...keys: string[]) {
  const query: Record<string, string | string[]> = {}
  Object.entries(route.query).forEach(([k, v]) => {
    if (!keys.includes(k) && v !== null && v !== undefined) query[k] = Array.isArray(v) ? v.map(String) : String(v)
  })
  return query
}
function resetFilters() {
  activeCategory.value = ''
  if (q.value || route.query.photo) router.replace({ query: queryWithout('q', 'photo') })
}
function toggleSearch() {
  searchOpen.value = !searchOpen.value
  if (searchOpen.value) nextTick(() => { searchInput.value?.focus(); searchInput.value?.select() })
}
function closeSearch() {
  searchOpen.value = false
  searchText.value = ''
  window.clearTimeout(searchInputTimer)
  if (q.value) router.replace({ query: queryWithout('q', 'photo') })
}
function submitSearch() {
  window.clearTimeout(searchInputTimer)
  const value = searchText.value.trim()
  const query = queryWithout('q', 'photo')
  if (value) query.q = value
  router.replace({ query })
}
function onSearchInput() {
  window.clearTimeout(searchInputTimer)
  searchInputTimer = window.setTimeout(() => {
    const value = searchText.value.trim()
    if (value === rawQuery.value) return
    const query = queryWithout('q', 'photo')
    if (value) query.q = value
    router.replace({ query })
  }, 160)
}
function clearSearchInput() {
  searchText.value = ''
  window.clearTimeout(searchInputTimer)
  router.replace({ query: queryWithout('q', 'photo') })
  nextTick(() => searchInput.value?.focus())
}
function chooseCategory(name: string) { activeCategory.value = name; filtersOpen.value = false }
function openPhotoFromQuery() {
  const id = typeof route.query.photo === 'string' ? route.query.photo : ''
  if (!id || id === consumedPhotoQuery || !photos.value.length) return
  const idx = photos.value.findIndex(p => String(p.id) === id)
  if (idx < 0) return
  consumedPhotoQuery = id
  viewerIndex.value = idx
  viewerOpen.value = true
  router.replace({ query: queryWithout('photo') })
}
let searchTimer = 0
let searchInputTimer = 0
let consumedPhotoQuery = ''
watch(q, () => { activeCategory.value = ''; window.clearTimeout(searchTimer); if (q.value) searchTimer = window.setTimeout(() => ensureSearchLoaded(true), 120); else { searchResults.value = []; searchTotal.value = 0; searchHasMore.value = false; searchLoadedFor.value = '' } })
watch(rawQuery, v => { searchText.value = v; if (v) searchOpen.value = true })
watch(() => [photos.value.length, route.query.photo], openPhotoFromQuery)
watch(() => route.query.photo, v => { if (!v) consumedPhotoQuery = '' })
watch(() => route.name, () => { loadInitial(); activeCategory.value = ''; selectMode.value = false; filtersOpen.value = false })
function onUploaded() { gallery.refresh(); if (q.value) ensureSearchLoaded(true); if (mode.value === 'live') loadLive() }
onMounted(() => { loadInitial(); window.addEventListener('nocturne:uploaded', onUploaded) })
onBeforeUnmount(() => { window.clearTimeout(searchTimer); window.clearTimeout(searchInputTimer); window.removeEventListener('nocturne:uploaded', onUploaded) })
</script>
<template>
  <div class="gallery-page" :class="{ 'is-selecting': selectMode, 'search-open': searchOpen }">
    <div class="noise"></div>

    <Transition name="g-fade">
      <div v-if="searchOpen" class="g-searchbar">
        <button class="g-search-back" type="button" aria-label="关闭搜索" @click="closeSearch"><Icon name="arrow-left" :size="14" /></button>
        <Icon name="search" :size="13" />
        <input ref="searchInput" v-model="searchText" type="search" placeholder="搜索照片、地点或标签" autocomplete="off" @input="onSearchInput" @keydown.enter.prevent="submitSearch" @keydown.esc.prevent="closeSearch">
        <span v-if="q" class="g-search-count">{{ displayCount }} 张</span>
        <button v-if="searchText" class="g-search-clear" type="button" aria-label="清除搜索" @click="clearSearchInput"><Icon name="close" :size="11" /></button>
        <button v-else class="g-search-cancel" type="button" @click="closeSearch">取消</button>
      </div>
    </Transition>

    <header v-if="!searchOpen" class="g-top">
      <RouterLink class="g-brand" to="/" aria-label="NOCTURNE 夜之馆首页"><b>NOCTURNE</b><span>夜之馆</span></RouterLink>
      <div class="g-context"><span>{{ modeTitle }}</span><b>{{ displayCount }}</b><em>张</em></div>
      <div class="g-actions">
        <RouterLink class="g-icon" to="/" title="回到首页" aria-label="回到首页"><Icon name="home" :size="13" /></RouterLink>
        <button class="g-icon" type="button" title="搜索" aria-label="搜索" @click="toggleSearch"><Icon name="search" :size="13" /></button>
        <button v-if="categories.length" class="g-icon" :class="{ on: filtersOpen || activeCategory }" type="button" title="分类" aria-label="分类" @click="filtersOpen = !filtersOpen">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"><path d="M4 7h16M7 12h10M10 17h4" /></svg>
        </button>
        <RouterLink class="g-icon" to="/favorites" title="收藏" aria-label="收藏"><Icon name="heart" :size="13" /></RouterLink>
        <RouterLink class="g-icon g-optional" to="/spaces" title="团队空间" aria-label="团队空间"><Icon name="team" :size="13" /></RouterLink>
        <button class="g-icon upload" type="button" title="上传" aria-label="上传" @click="openUpload"><Icon name="plus" :size="14" /></button>
        <RouterLink class="g-avatar" :to="user.isLoggedIn ? '/settings' : '/login'" :title="user.isLoggedIn ? '账号设置' : '登录'">{{ user.isLoggedIn ? user.initial : 'N' }}</RouterLink>
      </div>
    </header>

    <Transition name="g-pop">
      <div v-if="filtersOpen" class="g-filter-wrap">
        <div class="g-filter-scrim" @click="filtersOpen = false"></div>
        <div class="g-filter-pop">
          <div class="g-filter-label">Categories / 分类</div>
          <button type="button" :class="{ on: !activeCategory }" @click="chooseCategory('')">全部照片 <em>{{ basePhotos.length }}</em></button>
          <button v-for="c in categories" :key="c.name" type="button" :class="{ on: activeCategory === c.name }" @click="chooseCategory(activeCategory === c.name ? '' : c.name)">{{ c.name }} <em>{{ c.count }}</em></button>
        </div>
      </div>
    </Transition>

    <PhotoGrid ref="gridRef" class="g-grid" :photos="photos" :flat="!!q" :loading="loading" :loading-more="loadingMore" :has-more="canLoadMore" :select-mode="selectMode" :selectable="true" :can-batch-delete="canBatchDelete" :show-live-badge="mode === 'live'" :empty-title="emptyTitle" :empty-text="emptyText" @open="openViewer" @load-more="q ? loadSearchMore() : gallery.load(false)" @update:select-mode="selectMode = $event" @batch-delete="batchDelete" @selection-change="onSelection">
      <template #empty-action><button v-if="!q" class="btn gold" type="button" @click="openUpload"><Icon name="upload" :size="13" />上传第一张</button><button v-else class="btn" type="button" @click="resetFilters">清除搜索</button></template>
    </PhotoGrid>

    <div class="g-dock">
      <div class="zoom" title="调整缩略图大小">
        <button type="button" aria-label="缩小缩略图" @click="stepZoom(-1)"><Icon name="minus" :size="10" /></button>
        <input :value="thumb" type="range" :min="ZOOM_STEPS[0]" :max="ZOOM_STEPS[ZOOM_STEPS.length - 1]" step="1" aria-label="缩略图大小" @input="setZoom(Number(($event.target as HTMLInputElement).value))">
        <button type="button" aria-label="放大缩略图" @click="stepZoom(1)"><Icon name="plus" :size="10" /></button>
        <span class="zl">{{ label }}</span>
      </div>
      <button class="tool" type="button" @click="gridRef?.focusFirst()">聚焦</button>
      <button class="tool select-tool" :class="{ active: selectMode }" type="button" @click="gridRef?.toggleMode()">{{ selectMode ? '完成' : '选择' }}</button>
    </div>

    <PhotoViewer v-model:open="viewerOpen" v-model:index="viewerIndex" :photos="photos" :video-map="videoMap" :can-edit="viewerCanEdit" @edit="openEdit" @delete="deleteOne" @outpainting="p => { outpaintPhoto = p; outpaintOpen = true }" />
    <EditPhotoModal v-model:open="editOpen" :photo="editPhoto" @saved="onSaved" />
    <OutpaintingModal v-model:open="outpaintOpen" :photo="outpaintPhoto" />
  </div>
</template>

<style scoped>
.gallery-page{height:100dvh;display:flex;flex-direction:column;overflow:hidden;position:relative;padding:calc(var(--top) + 12px) 26px 0;background:var(--bg)}
.gallery-page>:deep(.photo-grid-root){flex:1;min-height:0}
.gallery-page :deep(.library-scroll){padding-bottom:96px}
.g-top{position:fixed;top:0;left:0;right:0;height:var(--top);z-index:60;display:flex;align-items:center;gap:20px;padding:0 26px;background:linear-gradient(180deg,rgba(6,6,7,.94),rgba(6,6,7,.55) 64%,transparent)}
.g-brand{display:flex;align-items:baseline;gap:10px;flex:none}.g-brand b{font-family:var(--serif);font-size:16px;font-weight:500;letter-spacing:.28em;color:var(--ivory)}.g-brand span{font-family:var(--cn);font-size:8px;letter-spacing:.2em;color:var(--gold-soft)}
.g-context{display:flex;align-items:baseline;gap:7px;font-size:7px;letter-spacing:.18em;text-transform:uppercase;color:var(--dim);white-space:nowrap}.g-context b{font-family:var(--serif);font-size:14px;font-weight:500;color:var(--gold);letter-spacing:.04em}.g-context em{font-style:normal;font-size:7px;color:var(--dim)}
.g-actions{margin-left:auto;display:flex;align-items:center;gap:4px}
.g-icon{width:30px;height:30px;display:grid;place-items:center;border:1px solid transparent;color:var(--muted);transition:color .22s,border-color .22s,background .22s;position:relative}.g-icon:hover,.g-icon.on{color:var(--gold);border-color:var(--line-soft);background:rgba(201,169,106,.045)}.g-icon.upload{color:var(--gold);border-color:var(--line)}.g-icon.upload:hover{background:var(--gold);color:#08080a}
.g-avatar{width:30px;height:30px;display:grid;place-items:center;border:1px solid var(--line);border-radius:50%;font-family:var(--serif);font-size:13px;color:var(--gold);transition:.22s;margin-left:3px}.g-avatar:hover{background:var(--gold);color:#08080a}
.g-searchbar{position:fixed;top:0;left:0;right:0;height:var(--top);z-index:70;display:flex;align-items:center;gap:12px;padding:0 26px;background:rgba(7,7,8,.99);border-bottom:1px solid var(--line-soft)}
.g-search-back,.g-search-clear{width:28px;height:28px;display:grid;place-items:center;color:var(--muted);flex:none;transition:.2s}.g-search-back:hover,.g-search-clear:hover{color:var(--gold)}
.g-searchbar>svg{color:var(--gold);flex:none}
.g-searchbar input{flex:1;min-width:0;height:34px;background:none;border:0;border-bottom:1px solid rgba(201,169,106,.35);outline:0;color:var(--ivory);font-family:var(--cn);font-size:13px;letter-spacing:.08em}.g-searchbar input::placeholder{color:var(--dim);font-size:11px}.g-searchbar input:focus{border-color:var(--gold)}
.g-search-count{font-size:8px;letter-spacing:.12em;color:var(--gold-soft);white-space:nowrap}.g-search-cancel{font-size:8px;letter-spacing:.14em;color:var(--muted);white-space:nowrap}.g-search-cancel:hover{color:var(--gold)}
.g-filter-wrap{position:fixed;inset:0;z-index:55}.g-filter-scrim{position:absolute;inset:0;background:rgba(0,0,0,.28)}.g-filter-pop{position:absolute;right:26px;top:calc(var(--top) - 2px);width:262px;padding:14px 7px 8px;background:rgba(10,10,12,.99);border:1px solid var(--line);box-shadow:0 28px 80px rgba(0,0,0,.7)}.g-filter-label{font-size:7px;letter-spacing:.24em;text-transform:uppercase;color:var(--gold-soft);padding:0 10px 10px;border-bottom:1px solid var(--line-soft);margin-bottom:5px}.g-filter-pop button{display:flex;align-items:center;justify-content:space-between;width:100%;padding:9px 10px;font-size:9px;letter-spacing:.08em;color:var(--muted);text-align:left;transition:.2s}.g-filter-pop button:hover{color:var(--ivory);background:rgba(201,169,106,.05)}.g-filter-pop button.on{color:var(--gold)}.g-filter-pop button em{font-style:normal;font-size:7px;color:var(--dim)}
.g-dock{position:fixed;z-index:80;left:50%;bottom:22px;transform:translateX(-50%);display:flex;align-items:center;gap:6px;padding:6px 8px;border:1px solid rgba(201,169,106,.24);background:rgba(9,9,11,.96);box-shadow:0 22px 60px rgba(0,0,0,.62);transition:opacity .28s var(--ease-soft),transform .3s var(--ease-out),visibility .28s}
.g-dock .zoom{border:0;background:none;height:32px;padding:0 7px 0 4px}.g-dock .tool{height:32px;font-size:7.5px;padding:0 10px}.g-dock .zl{width:16px;font-size:7px}
.gallery-page.is-selecting .g-dock{opacity:0;visibility:hidden;transform:translate(-50%,12px);pointer-events:none}
.g-fade-enter-active,.g-fade-leave-active{transition:opacity .22s var(--ease-soft)}.g-fade-enter-from,.g-fade-leave-to{opacity:0}
.g-pop-enter-active,.g-pop-leave-active{transition:opacity .2s var(--ease-soft),transform .24s var(--ease-out)}.g-pop-enter-from,.g-pop-leave-to{opacity:0;transform:translateY(-5px)}
@media(max-width:820px){
  .gallery-page{padding:calc(var(--top) + 8px) 14px 0}
  .g-top,.g-searchbar{padding:0 14px;gap:10px}
  .g-brand b{font-size:14px;letter-spacing:.2em}.g-brand span{display:none}.g-context{display:none}
  .g-icon{width:29px;height:29px}.g-avatar{width:29px;height:29px}
  .g-filter-pop{right:14px;top:calc(var(--top) - 2px)}
  .g-dock{bottom:calc(10px + env(safe-area-inset-bottom));padding:5px 6px;gap:3px}
  .g-dock .zoom input{width:54px}.g-dock .zoom{gap:5px;padding-right:2px}.g-dock .tool{padding:0 8px;font-size:7px}
  .gallery-page :deep(.library-scroll){padding-bottom:82px}
}
@media(max-width:560px){.g-optional{display:none}.g-actions{gap:2px}.g-brand{max-width:90px}.g-brand b{font-size:13px;letter-spacing:.16em}.g-dock .zl{display:none}.g-dock .zoom input{width:48px}}
</style>
