import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { pictureApi } from '@/api/picture'
import type { LivePictureVO, PictureVO } from '@/api/types'

const PAGE_SIZE = 20
const APPEND_CHUNK = 4
const INITIAL_PREFETCH_PAGES = 6
const nextFrame = () => new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
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
      const page = await pictureApi.listVOByPage({ current, pageSize: PAGE_SIZE, sortField: 'createTime', sortOrder: 'descend' })
      const known = new Set(photos.value.map(p => p.id))
      total.value = page.total
      pages.value = page.pages
      if (first) {
        // 首屏一次性预取前几页，避免用户滚到底时再插入新卡片造成绘制卡顿。
        const collected = [...page.records]
        page.records.forEach(p => known.add(p.id))
        const lastPage = Math.min(page.pages, INITIAL_PREFETCH_PAGES)
        if (lastPage > 1) {
          const rest = await Promise.all(Array.from({ length: lastPage - 1 }, (_, i) =>
            pictureApi.listVOByPage({ current: i + 2, pageSize: PAGE_SIZE, sortField: 'createTime', sortOrder: 'descend' }).catch(() => null)
          ))
          for (const r of rest) {
            if (!r) continue
            for (const p of r.records) if (!known.has(p.id)) { known.add(p.id); collected.push(p) }
          }
        }
        photos.value = collected
        nextPage.value = lastPage + 1
      } else {
        const fresh = page.records.filter(p => !known.has(p.id))
        for (let i = 0; i < fresh.length; i += APPEND_CHUNK) {
          photos.value = [...photos.value, ...fresh.slice(i, i + APPEND_CHUNK)]
          if (i + APPEND_CHUNK < fresh.length) await nextFrame()
        }
        nextPage.value = current + 1
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
