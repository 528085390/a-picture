import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/request'
import { useUserStore } from './stores/user'
import './styles/nocturne.css'
import './styles/app.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

setUnauthorizedHandler(() => {
  const user = useUserStore(pinia)
  const current = router.currentRoute.value
  user.clear()
  // 公开图库允许访客浏览；只有受保护页面才在会话失效时跳登录。
  if (current.meta.requiresAuth && current.name !== 'login') {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
