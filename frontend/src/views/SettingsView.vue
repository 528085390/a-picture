<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import Icon from '@/components/Icon.vue'
import { useUserStore } from '@/stores/user'
import { useSpaceStore } from '@/stores/spaces'
import { useGalleryStore } from '@/stores/gallery'
import { favoriteState } from '@/composables/useFavorites'
import { formatBytes } from '@/utils/format'

const router = useRouter()
const user = useUserStore()
const spaces = useSpaceStore()
const gallery = useGalleryStore()
const usedPct = computed(() => spaces.totalMax ? Math.min(100, spaces.totalUsed / spaces.totalMax * 100) : 0)
async function logout() { await user.logout(); spaces.reset(); router.push('/login') }
</script>
<template>
  <AppShell mobile-headless>
    <main class="library">
      <div class="lib-head"><div><div class="eyebrow">Account / 账号</div><h1>账号设置</h1><div class="lib-sub">你的身份、存储与这个档案馆的连接</div></div></div>
      <div class="library-scroll">
        <div class="profile-grid">
          <section class="profile-card"><div class="profile-avatar"><img v-if="user.user?.userAvatar" :src="user.user.userAvatar" alt=""><template v-else>{{ user.initial }}</template></div><h3>{{ user.displayName }}</h3><p>{{ user.user?.userAccount }}</p><span class="role-pill" :class="{ admin: user.isAdmin }">{{ user.isAdmin ? 'Administrator' : 'Collector' }}</span><div class="profile-rule"></div><p class="profile-note">账号资料由系统管理员维护。这里展示当前会话中的身份与权限。</p><button class="btn danger wide" @click="logout"><Icon name="logout" :size="13" />退出登录</button></section>
          <div class="settings-main">
            <section class="panel"><div class="panel-title">Archive / 档案概览</div><div class="stat-grid"><div class="stat"><span>Public photos</span><b>{{ gallery.total }}<em>张</em></b></div><div class="stat"><span>Favorites</span><b>{{ favoriteState.ids.length }}<em>张</em></b></div><div class="stat"><span>Spaces</span><b>{{ spaces.entries.length }}<em>个</em></b></div><div class="stat gold"><span>Storage used</span><b>{{ formatBytes(spaces.totalUsed) }}</b><div class="meter"><i :style="{ width: usedPct + '%' }"></i></div></div></div></section>
            <section class="panel"><div class="panel-title">Identity / 身份信息</div><div class="kv-list"><div><span>User ID</span><b>{{ user.user?.id || '—' }}</b></div><div><span>Account</span><b>{{ user.user?.userAccount || '—' }}</b></div><div><span>Display name</span><b>{{ user.user?.userName || '未设置' }}</b></div><div><span>Role</span><b>{{ user.user?.userRole === 'admin' ? '系统管理员' : '普通用户' }}</b></div><div><span>Joined</span><b>{{ user.user?.createTime ? String(user.user.createTime).replace('T',' ').slice(0,16) : '—' }}</b></div></div></section>
            <section class="panel"><div class="panel-title">Shortcuts / 快捷入口</div><div class="shortcut-grid"><button @click="router.push('/spaces')"><Icon name="team" :size="16" /><b>团队空间</b><span>与成员共享影像</span></button><button @click="router.push('/favorites')"><Icon name="heart" :size="16" /><b>我的收藏</b><span>点亮过的照片</span></button><button v-if="user.isAdmin" @click="router.push('/admin')"><Icon name="settings" :size="16" /><b>管理控制台</b><span>用户、图片与空间</span></button></div></section>
          </div>
        </div>
      </div>
    </main>
  </AppShell>
</template>
<style scoped>
.profile-card .role-pill{display:inline-block;margin-top:12px;padding:4px 10px;border:1px solid var(--line);font-size:7px;letter-spacing:.18em;text-transform:uppercase;color:var(--gold-soft)}.profile-card .role-pill.admin{color:var(--gold2);border-color:rgba(201,169,106,.45)}
.profile-rule{height:1px;background:var(--line-soft);margin:22px 0 14px}.profile-note{font-size:8px;line-height:1.8;color:var(--dim);margin-bottom:20px}.wide{width:100%}
.settings-main{display:flex;flex-direction:column;gap:16px}.kv-list{display:flex;flex-direction:column}.kv-list>div{display:flex;justify-content:space-between;gap:20px;padding:10px 0;border-bottom:1px solid var(--line-soft);font-size:9px}.kv-list>div:last-child{border:0}.kv-list span{color:var(--dim);letter-spacing:.12em;text-transform:uppercase;font-size:7px}.kv-list b{font-weight:400;color:var(--ivory);word-break:break-all;text-align:right}
.shortcut-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:1px;background:var(--line-soft)}.shortcut-grid button{background:var(--bg);padding:20px 16px;text-align:left;display:flex;flex-direction:column;gap:7px;transition:.25s}.shortcut-grid button:hover{background:rgba(201,169,106,.05)}.shortcut-grid svg{color:var(--gold-soft)}.shortcut-grid b{font-size:11px;font-weight:400;color:var(--ivory)}.shortcut-grid span{font-size:8px;color:var(--dim)}
@media(max-width:900px){.profile-grid{grid-template-columns:1fr}.shortcut-grid{grid-template-columns:1fr}}
</style>
