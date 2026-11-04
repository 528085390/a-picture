import { reactive, watch } from 'vue'
const KEY = 'nocturne.favorites.v1'
function read(): string[] { try { const v = JSON.parse(localStorage.getItem(KEY) || '[]'); return Array.isArray(v) ? v.map(String) : [] } catch { return [] } }
const state = reactive({ ids: read() })
watch(() => state.ids.slice(), ids => localStorage.setItem(KEY, JSON.stringify(ids)), { deep: true })
export function useFavorites() {
  const has = (id: string) => state.ids.includes(id)
  const toggle = (id: string) => { const i = state.ids.indexOf(id); if (i >= 0) state.ids.splice(i, 1); else state.ids.push(id) }
  return { favorites: state, has, toggle, count: () => state.ids.length }
}
export const favoriteState = state
