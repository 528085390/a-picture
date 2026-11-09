<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { useUiStore } from '@/stores/ui'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ hidden?: boolean }>()

const route = useRoute()
const router = useRouter()
const ui = useUiStore()
const user = useUserStore()

const items = computed(() => [
  { key: 'gallery', label: '图库', icon: 'grid', to: { name: 'gallery' } },
  { key: 'favorites', label: '收藏', icon: 'heart', to: { name: 'favorites' } },
  { key: 'live', label: 'Live', icon: 'live', to: { name: 'live' } },
  { key: 'spaces', label: '空间', icon: 'team', to: { name: 'spaces' } },
  { key: 'mine', label: '我的', icon: 'user', to: user.isLoggedIn ? { name: 'settings' } : { name: 'login' } },
])

function active(key: string) {
  if (key === 'spaces') return route.name === 'spaces' || route.name === 'space-detail'
  if (key === 'mine') return route.name === 'settings' || route.name === 'login'
  return route.name === key
}
function go(item: { key: string; to: any }) {
  if (item.key === 'spaces' && !user.isLoggedIn) { ui.toast('登录后可查看团队空间'); router.push({ name: 'login', query: { redirect: '/spaces' } }); return }
  if (route.name === item.to.name) return
  router.push(item.to)
}
</script>

<template>
  <nav class="m-tabbar" :class="{ 'is-hidden': props.hidden }" aria-label="主导航">
    <button v-for="item in items" :key="item.key" class="m-tab" :class="{ on: active(item.key) }" type="button" @click="go(item)">
      <Icon :name="item.icon" :size="26" />
      <span>{{ item.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.m-tabbar{position:absolute;left:0;right:0;bottom:0;z-index:40;flex:none;display:flex;align-items:stretch;height:calc(var(--m-tab) + var(--safe-bottom));padding-bottom:var(--safe-bottom);background:rgba(18,18,20,.94);border-top:.5px solid rgba(255,255,255,.12);transition:transform var(--dur-bar) var(--spring-bar);will-change:transform}
.m-tabbar.is-hidden{transform:translate3d(0,calc(100% + 1px),0);pointer-events:none}
.m-tab{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;color:rgba(235,235,245,.42);transition:color .22s ease,transform var(--dur-press) var(--spring-press);padding-top:5px}
.m-tab span{font-size:10px;letter-spacing:.02em;line-height:1}
.m-tab.on{color:var(--ios-blue)}
.m-tab:active{opacity:.6;transform:scale(.86);transition-duration:90ms,90ms;transition-timing-function:ease-out,ease-out}
</style>