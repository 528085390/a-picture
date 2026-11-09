<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'
import { useGalleryStore } from '@/stores/gallery'
import { useSpaceStore } from '@/stores/spaces'
import { favoriteState } from '@/composables/useFavorites'
import { formatBytes } from '@/utils/format'
import Icon from './Icon.vue'

const props = defineProps<{ mobileHeadless?: boolean }>()

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const ui = useUiStore()
const gallery = useGalleryStore()
const spaces = useSpaceStore()
const menuOpen = ref(false)
const search = ref(typeof route.query.q === 'string' ? route.query.q : '')

const isGallery = computed(() => route.name === 'gallery' || route.name === 'favorites' || route.name === 'live')
const usedBytes = computed(() => spaces.totalUsed)
const maxBytes = computed(() => spaces.totalMax || 100 * 1024 * 1024 * 1024)
const usagePct = computed(() => Math.min(100, Math.max(2, usedBytes.value / maxBytes.value * 100)))

function submitSearch() {
  const q = search.value.trim()
  if (!isGallery.value) router.push({ name: 'gallery', query: q ? { q } : {} })
  else router.replace({ query: q ? { ...route.query, q } : Object.fromEntries(Object.entries(route.query).filter(([k]) => k !== 'q')) })
}
function onSearchInput() {
  if (!isGallery.value) return
  const q = search.value.trim()
  const current = typeof route.query.q === 'string' ? route.query.q : ''
  if (q === current) return
  router.replace({ query: q ? { ...route.query, q } : Object.fromEntries(Object.entries(route.query).filter(([k]) => k !== 'q')) })
}
watch(() => route.query.q, q => { search.value = typeof q === 'string' ? q : '' })
async function signOut() {
  await user.logout(); spaces.reset(); menuOpen.value = false
  ui.toast('已退出夜之馆')
  router.push({ name: 'login' })
}
onMounted(async () => {
  await user.fetchLogin()
  if (user.isLoggedIn) { spaces.ensureLoaded(); gallery.fetchLiveTotal() }
})
</script>
<template>
  <div class="shell" :class="{ 'is-mobile-headless': props.mobileHeadless }">
    <div class="noise"></div><div class="spot"></div>
    <header class="topbar">
      <RouterLink class="brand" to="/" aria-label="NOCTURNE 夜之馆"><b>NOCTURNE</b><span>夜之馆 · 私人图库</span></RouterLink>
      <label class="search">
        <Icon name="search" :size="12" />
        <input v-model="search" type="search" placeholder="搜索照片、地点或标签" autocomplete="off" @input="onSearchInput" @keyup.enter="submitSearch">
      </label>
      <div class="top-right">
        <span class="backup"><i></i>{{ user.isLoggedIn ? 'R2 · 已连接' : '浏览模式' }}</span>
        <button class="top-btn optional" type="button" @click="router.push({ name: 'spaces' })">空间</button>
        <button class="top-btn gold optional" type="button" @click="ui.openUpload()">＋ 上传</button>
        <div class="avatar-wrap">
          <button class="avatar" type="button" :aria-label="user.isLoggedIn ? user.displayName : '登录'" @click="user.isLoggedIn ? (menuOpen = !menuOpen) : router.push({ name: 'login', query: { redirect: route.fullPath } })">{{ user.isLoggedIn ? user.initial : 'N' }}</button>
          <Transition name="menu">
            <div v-if="menuOpen" class="user-menu" @mouseleave="menuOpen = false">
              <div class="user-menu-head"><b>{{ user.displayName }}</b><span>{{ user.isAdmin ? '管理员' : '收藏者' }}</span></div>
              <RouterLink to="/settings" @click="menuOpen = false"><Icon name="user" :size="13" />账号设置</RouterLink>
              <RouterLink v-if="user.isAdmin" to="/admin" @click="menuOpen = false"><Icon name="settings" :size="13" />管理控制台</RouterLink>
              <button type="button" @click="signOut"><Icon name="logout" :size="13" />退出登录</button>
            </div>
          </Transition>
        </div>
      </div>
    </header>

    <aside class="sidebar">
      <div class="side-kicker">Library / 图库</div>
      <nav class="nav">
        <RouterLink to="/gallery" :class="{ active: route.name === 'gallery' && route.query.view !== 'recent' }"><Icon name="grid" :size="13" />全部照片<b>{{ gallery.total || '—' }}</b></RouterLink>
        <RouterLink :to="{ name: 'gallery', query: { view: 'recent' } }" :class="{ active: route.query.view === 'recent' }"><Icon name="clock" :size="13" />最近上传</RouterLink>
        <RouterLink :to="{ name: 'favorites' }" :class="{ active: route.name === 'favorites' }"><Icon name="heart" :size="13" />收藏<b>{{ favoriteState.ids.length || '—' }}</b></RouterLink>
        <RouterLink :to="{ name: 'live' }" :class="{ active: route.name === 'live' }"><Icon name="live" :size="13" />Live 照片<b>{{ gallery.liveTotal || '—' }}</b></RouterLink>
        <RouterLink :to="{ name: 'spaces' }" :class="{ active: route.name === 'spaces' || route.name === 'space-detail' }"><Icon name="team" :size="13" />团队空间<b>{{ spaces.teamCount || '—' }}</b></RouterLink>
        <RouterLink v-if="user.isAdmin" :to="{ name: 'admin' }" :class="{ active: route.name === 'admin' }"><Icon name="settings" :size="13" />管理控制台</RouterLink>
      </nav>
      <div class="side-rule"></div>
      <div class="side-block">
        <div class="side-label">Spaces / 空间</div>
        <div class="space-mini">
          <RouterLink v-for="s in spaces.entries.slice(0, 4)" :key="s.id" :to="{ name: 'space-detail', params: { id: s.id } }" class="space-mini-row">
            <i></i><span>{{ s.spaceName }}</span><em>{{ s.totalCount || 0 }}</em>
          </RouterLink>
          <div v-if="!spaces.entries.length" class="space-mini-empty">尚未创建空间</div>
        </div>
      </div>
      <div class="side-rule"></div>
      <div class="side-block">
        <div class="side-label">Storage / 存储</div>
        <div class="capacity"><i :style="{ width: usagePct + '%' }"></i></div>
        <div class="cap-row"><span><b>{{ formatBytes(usedBytes) }}</b> 已使用</span><span>{{ formatBytes(maxBytes) }}</span></div>
      </div>
      <div class="side-foot"><i></i>R2 · 已同步</div>
    </aside>

    <slot />

    <nav class="mobile-nav">
      <RouterLink to="/gallery" :class="{ active: route.name === 'gallery' }"><Icon name="grid" :size="26" />图库</RouterLink>
      <RouterLink :to="{ name: 'favorites' }" :class="{ active: route.name === 'favorites' }"><Icon name="heart" :size="26" />收藏</RouterLink>
      <RouterLink :to="{ name: 'live' }" :class="{ active: route.name === 'live' }"><Icon name="live" :size="26" />Live</RouterLink>
      <RouterLink :to="{ name: 'spaces' }" :class="{ active: route.name === 'spaces' || route.name === 'space-detail' }"><Icon name="team" :size="26" />空间</RouterLink>
      <RouterLink :to="user.isLoggedIn ? '/settings' : '/login'" :class="{ active: route.name === 'settings' }"><Icon name="user" :size="26" />我的</RouterLink>
    </nav>
  </div>
</template>
<style scoped>
.user-menu{position:absolute;right:0;top:38px;width:190px;background:#0b0b0d;border:1px solid var(--line);box-shadow:0 25px 60px rgba(0,0,0,.7);padding:8px;z-index:100}
.avatar-wrap{position:relative}
.user-menu-head{padding:10px 10px 12px;border-bottom:1px solid var(--line-soft);margin-bottom:6px}
.user-menu-head b{display:block;font-size:11px;font-weight:400;color:var(--ivory)}.user-menu-head span{font-size:7px;color:var(--gold-soft);letter-spacing:.14em;text-transform:uppercase}
.user-menu a,.user-menu button{display:flex;align-items:center;gap:9px;width:100%;padding:9px 10px;font-size:9px;color:var(--muted);letter-spacing:.05em;text-align:left;transition:.2s}
.user-menu a:hover,.user-menu button:hover{color:var(--gold);background:rgba(201,169,106,.05)}
.menu-enter-active,.menu-leave-active{transition:opacity .2s,transform .25s var(--ease-out)}.menu-enter-from,.menu-leave-to{opacity:0;transform:translateY(-5px)}
.space-mini{display:flex;flex-direction:column;gap:1px;padding:0 2px}
.space-mini-row{display:flex;align-items:center;gap:8px;padding:6px 10px;font-size:9px;color:var(--muted);border-radius:2px}
.space-mini-row:hover{color:var(--ivory);background:rgba(255,255,255,.025)}
.space-mini-row i{width:3px;height:3px;border-radius:50%;background:var(--gold-soft);flex:none}
.space-mini-row span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.space-mini-row em{margin-left:auto;font-style:normal;font-size:7px;color:var(--dim)}
.space-mini-empty{font-size:8px;color:var(--dim);padding:3px 10px 5px}
@media(max-width:860px){.topbar .search{max-width:none}.avatar-wrap{display:none}}
</style>
