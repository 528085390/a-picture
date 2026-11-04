import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { userApi, type LoginRequest, type RegisterRequest } from '@/api/user'
import type { LoginUserVO } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const user = ref<LoginUserVO | null>(null)
  const initialized = ref(false)
  const loading = ref(false)
  let inflight: Promise<void> | null = null

  const isLoggedIn = computed(() => !!user.value)
  const isAdmin = computed(() => user.value?.userRole === 'admin')
  const displayName = computed(() => user.value?.userName || user.value?.userAccount || '访客')
  const initial = computed(() => (displayName.value || 'N').trim().slice(0, 1).toUpperCase())

  async function fetchLogin(force = false) {
    if (initialized.value && !force) return
    if (inflight && !force) return inflight
    loading.value = true
    inflight = (async () => {
      try { user.value = await userApi.getLogin() }
      catch { user.value = null }
      finally { initialized.value = true; loading.value = false; inflight = null }
    })()
    return inflight
  }
  async function login(data: LoginRequest) {
    user.value = await userApi.login(data)
    initialized.value = true
    return user.value
  }
  async function register(data: RegisterRequest) {
    await userApi.register(data)
    return login({ userAccount: data.userAccount, userPassword: data.userPassword })
  }
  async function logout() {
    try { await userApi.logout() } catch { /* session may already be gone */ }
    user.value = null
  }
  function clear() { user.value = null }

  return { user, initialized, loading, isLoggedIn, isAdmin, displayName, initial, fetchLogin, login, register, logout, clear }
})
