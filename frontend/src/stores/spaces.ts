import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { spaceApi } from '@/api/space'
import { spaceUserApi } from '@/api/spaceUser'
import type { SpaceRole, SpaceVO } from '@/api/types'
import { useUserStore } from './user'

export type SpaceEntry = SpaceVO & { myRole: SpaceRole }
const PAGE = 20

export const useSpaceStore = defineStore('spaces', () => {
  const entries = ref<SpaceEntry[]>([])
  const loading = ref(false)
  const initialized = ref(false)
  const error = ref('')

  const totalUsed = computed(() => entries.value.reduce((s, x) => s + (x.totalSize || 0), 0))
  const totalMax = computed(() => entries.value.reduce((s, x) => s + (x.maxSize || 0), 0))
  const teamCount = computed(() => entries.value.filter(x => x.spaceType === 1).length)

  function roleFor(id: string): SpaceRole | null { return entries.value.find(s => s.id === id)?.myRole ?? null }
  function nameFor(id?: string | null): string { return entries.value.find(s => s.id === id)?.spaceName || '' }

  async function ensureLoaded(force = false) {
    if (loading.value) return
    if (initialized.value && !force) return
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) { entries.value = []; initialized.value = true; return }
    loading.value = true; error.value = ''
    try {
      const owned: SpaceEntry[] = []
      for (let current = 1; current <= 10; current++) {
        const page = await spaceApi.myListVOByPage({ current, pageSize: PAGE, sortField: 'createTime', sortOrder: 'descend' })
        owned.push(...page.records.map(s => ({ ...s, myRole: 'admin' as SpaceRole })))
        if (current >= page.pages) break
      }
      const joined = await spaceUserApi.listMine()
      const byId = new Map<string, SpaceEntry>()
      owned.forEach(s => byId.set(s.id, s))
      joined.forEach(su => {
        if (!su.space) return
        const existing = byId.get(su.space.id)
        if (existing) return
        byId.set(su.space.id, { ...su.space, myRole: su.spaceRole })
      })
      entries.value = [...byId.values()]
      initialized.value = true
    } catch (e) { error.value = (e as Error).message; initialized.value = true }
    finally { loading.value = false }
  }
  function upsert(space: SpaceVO, myRole: SpaceRole = 'admin') {
    const i = entries.value.findIndex(s => s.id === space.id)
    const entry: SpaceEntry = { ...space, myRole: i >= 0 ? entries.value[i].myRole : myRole }
    if (i >= 0) entries.value.splice(i, 1, entry); else entries.value.unshift(entry)
  }
  function removeLocal(id: string) { entries.value = entries.value.filter(s => s.id !== id) }
  function reset() { entries.value = []; initialized.value = false }

  return { entries, loading, initialized, error, totalUsed, totalMax, teamCount, roleFor, nameFor, ensureLoaded, upsert, removeLocal, reset }
})
