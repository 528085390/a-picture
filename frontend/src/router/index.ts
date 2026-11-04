import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, saved) { return saved || (to.path === from.path ? undefined : { top: 0 }) },
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/gallery', name: 'gallery', component: () => import('@/views/GalleryView.vue') },
    { path: '/favorites', name: 'favorites', component: () => import('@/views/GalleryView.vue') },
    { path: '/live', name: 'live', component: () => import('@/views/GalleryView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/AuthView.vue'), meta: { guest: true } },
    { path: '/spaces', name: 'spaces', component: () => import('@/views/SpacesView.vue'), meta: { requiresAuth: true } },
    { path: '/spaces/:id', name: 'space-detail', component: () => import('@/views/SpaceDetailView.vue'), meta: { requiresAuth: true } },
    { path: '/admin', name: 'admin', component: () => import('@/views/AdminView.vue'), meta: { requiresAuth: true, admin: true } },
    { path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { requiresAuth: true } },
    { path: '/404', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/404' },
  ],
})

router.beforeEach(async to => {
  const user = useUserStore()
  const ui = useUiStore()
  if (!user.initialized) await user.fetchLogin()
  if (to.meta.requiresAuth && !user.isLoggedIn) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.admin && !user.isAdmin) { ui.toast('需要管理员权限', 'error'); return { name: 'gallery' } }
  if (to.meta.guest && user.isLoggedIn) return { name: 'gallery' }
})
export default router
