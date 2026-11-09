import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { pictureApi } from '@/api/picture'
import type { LivePictureVO, Page, PictureVO } from '@/api/types'

const PAGE_SIZE = 20
/** 后端 listVOByPage 把 pageSize 卡在 20（防爬），一次触发并发拉 3 页，滚动时一次多来 60 张 */
const LOAD_BATCH = 3
const INITIAL_PREFETCH_PAGES = 6
/** index.html 里的并发预取（和 JS 包同时发出）；首屏第一页优先复用它，省掉一次串行往返。 */
function takePrefetchedPage(): Promise<Page<PictureVO> | null> | null {
  const w = window as unknown as { __APIC_LIST_PREFETCH?: Promise<Page<PictureVO> | null> | null }
  const pending = w.__APIC_LIST_PREFETCH
  if (!pending) return null
  w.__APIC_LIST_PREFETCH = null
  return pending
}

export const useGalleryStore = defineStore('gallery', () => {
  const photos = ref<PictureVO[]>([])
  const total = ref(0)
  const loading = ref(false)
  const loadingMore = ref(false)
  const initialized = ref(false)
  const error = ref('')
  const liveTotal = ref(0)
  const pages = ref(0)
  const nextPage = ref(1)

  const hasMore = computed(() => nextPage.value <= pages.value)

  async function load(reset = false) {
    if (reset) { photos.value = []; total.value = 0; pages.value = 0; nextPage.value = 1; initialized.value = false }
    if (loading.value || loadingMore.value) return
    if (initialized.value && !hasMore.value) return
    const first = photos.value.length === 0
    if (first) loading.value = true; else loadingMore.value = true
    error.value = ''
    try {
      const current = nextPage.value
      const prefetched = current === 1 ? takePrefetchedPage() : null
      const page = (prefetched ? await prefetched : null)
        ?? await pictureApi.listVOByPage({ current, pageSize: PAGE_SIZE, sortField: 'createTime', sortOrder: 'descend' })
      const known = new Set(photos.value.map(p => p.id))
      total.value = page.total
      pages.value = page.pages
      if (first) {
        // 首屏只等第一页：先出图，其余页在后台预取后追加，避免白屏等待。
        photos.value = page.records
        page.records.forEach(p => known.add(p.id))
        pages.value = page.pages
        nextPage.value = 2
        initialized.value = true
        loading.value = false
        const lastPage = Math.min(page.pages, INITIAL_PREFETCH_PAGES)
        if (lastPage > 1) {
          nextPage.value = lastPage + 1
          void Promise.all(Array.from({ length: lastPage - 1 }, (_, i) =>
            pictureApi.listVOByPage({ current: i + 2, pageSize: PAGE_SIZE, sortField: 'createTime', sortOrder: 'descend' }).catch(() => null)
          )).then(rest => {
            const extra: PictureVO[] = []
            for (const r of rest) {
              if (!r) continue
              for (const p of r.records) if (!known.has(p.id)) { known.add(p.id); extra.push(p) }
            }
            if (extra.length) photos.value = [...photos.value, ...extra]
          })
        }
      } else {
        // 后端 pageSize 上限 20，一次并发拉多页再一次性追加：滚动时少触发几次、每次多来一批。
        // 一次性插入同样重要：分帧插入会让每一帧都重排整块网格。
        const batch = Math.max(1, Math.min(LOAD_BATCH, pages.value - current + 1))
        const results = await Promise.all(Array.from({ length: batch }, (_, i) =>
          pictureApi.listVOByPage({ current: current + i, pageSize: PAGE_SIZE, sortField: 'createTime', sortOrder: 'descend' })
            .then(page => ({ page }))
            .catch(() => ({ page: null as Page<PictureVO> | null }))))
        const fresh: PictureVO[] = []
        let advanced = 0
        for (const item of results) {
          if (!item.page) break
          pages.value = item.page.pages
          total.value = item.page.total
          for (const p of item.page.records) if (!known.has(p.id)) { known.add(p.id); fresh.push(p) }
          advanced++
        }
        if (fresh.length) photos.value = [...photos.value, ...fresh]
        nextPage.value = current + Math.max(1, advanced)
      }
      initialized.value = true
    } catch (e) { error.value = (e as Error).message; initialized.value = true }
    finally { loading.value = false; loadingMore.value = false }
  }
  async function ensureLoaded() { if (!initialized.value && !loading.value) await load(true) }
  async function refresh() { await load(true) }
  async function loadAll(maxPages = 30) {
    await ensureLoaded()
    let guard = 0
    while (hasMore.value && guard++ < maxPages && !loadingMore.value) await load(false)
  }
  async function fetchLiveTotal() {
    try { const p = await pictureApi.listLiveVOByPage({ current: 1, pageSize: 1 }); liveTotal.value = p.total }
    catch { liveTotal.value = 0 }
  }
  function removeLocal(id: string) { photos.value = photos.value.filter(p => p.id !== id); total.value = Math.max(0, total.value - 1) }
  return { photos, total, loading, loadingMore, initialized, error, liveTotal, hasMore, load, ensureLoaded, refresh, loadAll, fetchLiveTotal, removeLocal }
})

export async function fetchAllPublicPhotos(): Promise<PictureVO[]> {
  const all: PictureVO[] = []; let current = 1
  while (current <= 30) {
    const page = await pictureApi.listVOByPage({ current, pageSize: PAGE_SIZE, sortField: 'createTime', sortOrder: 'descend' })
    all.push(...page.records)
    if (current >= page.pages || page.records.length === 0) break
    current++
  }
  return all
}
export async function fetchAllLivePhotos(): Promise<LivePictureVO[]> {
  const all: LivePictureVO[] = []; let current = 1
  while (current <= 30) {
    const page = await pictureApi.listLiveVOByPage({ current, pageSize: 20 })
    all.push(...page.records)
    if (current >= page.pages || page.records.length === 0) break
    current++
  }
  return all
}
