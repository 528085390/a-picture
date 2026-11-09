<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import Icon from '@/components/Icon.vue'
import { originThumbUrl } from '@/utils/media'
import { useSpaceStore } from '@/stores/spaces'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'
import { spaceApi, spacePictureApi } from '@/api/space'
import { formatBytes, formatDate } from '@/utils/format'
import type { SpaceLevel } from '@/api/types'

const router = useRouter()
const spaces = useSpaceStore()
const user = useUserStore()
const ui = useUiStore()
const filter = ref<'all' | 'team' | 'private'>('all')
const createOpen = ref(false)
const creating = ref(false)
const error = ref('')
const covers = ref<Record<string, string>>({})
const form = reactive<{ spaceName: string; spaceLevel: SpaceLevel; spaceType: 0 | 1 }>({ spaceName: '', spaceLevel: 1, spaceType: 1 })
const filtered = computed(() => spaces.entries.filter(s => filter.value === 'all' || (filter.value === 'team' ? s.spaceType === 1 : s.spaceType === 0)))
const totalPhotos = computed(() => spaces.entries.reduce((n, s) => n + (s.totalCount || 0), 0))
const levelMeta = (l: SpaceLevel) => [{ name: '普通', quota: '100 张 · 100 MB' }, { name: '专业', quota: '1,000 张 · 1 GB' }, { name: '旗舰', quota: '10,000 张 · 10 GB' }][l] || { name: '未知', quota: '' }
const roleText = (r: string) => ({ admin: '管理员', editor: '编辑者', viewer: '观看者' } as Record<string, string>)[r] || r
const isOwner = (s: any) => String(s.userId) === String(user.user?.id)
async function load() {
  await spaces.ensureLoaded(true)
  spaces.entries.forEach(async s => {
    if (covers.value[s.id]) return
    try { const p = await spacePictureApi.listVOByPage({ spaceId: s.id, current: 1, pageSize: 1 }); const pic = p.records[0]; if (pic) covers.value[s.id] = originThumbUrl(pic.previewUrl || pic.url || '', 360) } catch { /* cover is optional */ }
  })
}
function openCreate() { if (!user.isLoggedIn) { router.push({ name: 'login', query: { redirect: '/spaces' } }); return } form.spaceName = ''; form.spaceLevel = 1; form.spaceType = 1; error.value = ''; createOpen.value = true }
async function create() {
  if (!form.spaceName.trim()) { error.value = '给空间起一个名字。'; return }
  creating.value = true; error.value = ''
  try {
    const id = await spaceApi.add({ spaceName: form.spaceName.trim(), spaceLevel: form.spaceLevel, spaceType: form.spaceType })
    ui.toast('空间已建立', 'success'); createOpen.value = false; await spaces.ensureLoaded(true); router.push({ name: 'space-detail', params: { id } })
  } catch (e) { error.value = (e as Error).message } finally { creating.value = false }
}
onMounted(load)
</script>
<template>
  <AppShell mobile-headless>
    <main class="library">
      <div class="lib-head"><div><div class="eyebrow">Spaces / 空间</div><h1>团队与收藏</h1><div class="lib-sub"><b>{{ spaces.entries.length }}</b> 个空间 · {{ totalPhotos }} 张影像 · {{ formatBytes(spaces.totalUsed) }}</div></div><div class="tools"><button class="tool" :class="{ active: filter === 'all' }" @click="filter = 'all'">全部</button><button class="tool" :class="{ active: filter === 'team' }" @click="filter = 'team'">团队</button><button class="tool" :class="{ active: filter === 'private' }" @click="filter = 'private'">私有</button><button class="btn gold" @click="openCreate"><Icon name="plus" :size="13" />新建空间</button></div></div>
      <div class="library-scroll">
        <div v-if="!user.isLoggedIn" class="space-guest"><div class="eyebrow">Members only</div><h2>空间需要一扇门。</h2><p>登录后可以创建私有空间、邀请成员，或加入别人的团队空间。</p><button class="btn gold" @click="router.push({ name: 'login', query: { redirect: '/spaces' } })">登录 / 注册</button></div>
        <div v-else-if="spaces.loading && !spaces.entries.length" class="page-loading"><span class="spinner"></span>正在打开空间…</div>
        <div v-else class="space-grid">
          <button class="space-card create-card" @click="openCreate"><div class="create-ring"><Icon name="plus" :size="20" /></div><b>新建空间</b><span>PRIVATE OR TEAM · 建立一个新的影像房间</span></button>
          <article v-for="(s, i) in filtered" :key="s.id" class="space-card" :style="{ '--delay': i * 40 + 'ms' }" @click="router.push({ name: 'space-detail', params: { id: s.id } })">
            <div class="space-cover" :class="{ empty: !covers[s.id] }"><img v-if="covers[s.id]" :src="covers[s.id]" alt=""><div class="cover-shade"></div><div class="cover-top"><span>{{ s.spaceType === 1 ? 'TEAM SPACE' : 'PRIVATE SPACE' }}</span><em>{{ levelMeta(s.spaceLevel).name }}</em></div><div class="cover-bottom"><h2>{{ s.spaceName }}</h2><p>{{ s.totalCount || 0 }} 张照片 · {{ formatBytes(s.totalSize || 0) }}</p></div><span class="role-tag" :class="s.myRole">{{ roleText(s.myRole) }}</span></div>
            <div class="space-meta"><div><span>容量</span><b>{{ Math.round((s.totalSize || 0) / Math.max(1, s.maxSize || 1) * 100) }}%</b></div><div><span>成员</span><b>{{ s.spaceType === 1 ? '团队' : '仅自己' }}</b></div><div><span>建立</span><b>{{ formatDate(s.createTime).slice(5) }}</b></div></div>
            <div class="space-meter"><i :style="{ width: Math.min(100, (s.totalSize || 0) / Math.max(1, s.maxSize || 1) * 100) + '%' }"></i></div>
          </article>
        </div>
        <div v-if="user.isLoggedIn && !spaces.loading && !filtered.length" class="empty-rich"><div class="glyph">S</div><h3>这个分类下还没有空间</h3><p>创建一个团队空间，把照片分享给编辑者与观看者。</p><button class="btn gold" @click="openCreate">新建空间</button></div>
      </div>
    </main>
    <Teleport to="body">
      <div v-if="createOpen" class="modal-backdrop" @click.self="createOpen = false"><div class="modal"><header class="modal-head"><div><h2>新建空间</h2><p>Space / 一个只属于某些人的房间</p></div><button class="icon-btn" @click="createOpen = false"><Icon name="close" :size="16" /></button></header><div class="modal-body"><div class="form-grid"><div class="field"><label>空间名称</label><input v-model="form.spaceName" class="input" maxlength="30" placeholder="例如：北欧之行、品牌素材库" @keyup.enter="create"></div><div class="field"><label>空间等级</label><div class="level-grid"><button v-for="(m, l) in [{name:'普通',quota:'100 张 · 100 MB'},{name:'专业',quota:'1,000 张 · 1 GB'},{name:'旗舰',quota:'10,000 张 · 10 GB'}]" :key="l" type="button" :class="{ on: form.spaceLevel === l }" @click="form.spaceLevel = l as SpaceLevel"><b>{{ m.name }}</b><span>{{ m.quota }}</span></button></div></div><div class="field"><label>空间类型</label><div class="type-grid"><button type="button" :class="{ on: form.spaceType === 0 }" @click="form.spaceType = 0"><Icon name="lock" :size="15" /><b>私有空间</b><span>只有你能看见</span></button><button type="button" :class="{ on: form.spaceType === 1 }" @click="form.spaceType = 1"><Icon name="users" :size="15" /><b>团队空间</b><span>邀请成员协作</span></button></div></div><p v-if="error" class="form-error">{{ error }}</p></div></div><footer class="modal-foot"><button class="btn" @click="createOpen = false">取消</button><button class="btn gold" :disabled="creating" @click="create">{{ creating ? '正在建立…' : '建立空间' }}</button></footer></div></div>
    </Teleport>
  </AppShell>
</template>
<style scoped>
.space-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:12px;padding-bottom:40px}.space-card{text-align:left;border:1px solid var(--line-soft);background:var(--panel);overflow:hidden;transition:transform .45s var(--ease-soft),border-color .3s,box-shadow .45s;animation:cardIn .6s var(--ease-out) backwards;animation-delay:var(--delay,0ms)}.space-card:hover{transform:translateY(-3px);border-color:rgba(201,169,106,.4);box-shadow:0 24px 60px rgba(0,0,0,.45)}
.space-cover{position:relative;height:210px;overflow:hidden;background:radial-gradient(circle at 70% 20%,rgba(201,169,106,.18),transparent 55%),#0a0a0c}.space-cover.empty{background:radial-gradient(circle at 70% 20%,rgba(201,169,106,.22),transparent 50%),linear-gradient(140deg,#0e0d0b,#070708)}.space-cover img{width:100%;height:100%;object-fit:cover;opacity:.72;transition:transform .8s var(--ease-soft),opacity .4s}.space-card:hover .space-cover img{transform:scale(1.05);opacity:.9}.cover-shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(6,6,7,.1),rgba(6,6,7,.15) 40%,rgba(6,6,7,.92))}.cover-top{position:absolute;top:14px;left:16px;right:16px;display:flex;justify-content:space-between;font-size:6px;letter-spacing:.2em;color:rgba(236,229,215,.65);text-transform:uppercase}.cover-top em{font-style:normal;color:var(--gold2);border:1px solid rgba(201,169,106,.35);padding:3px 6px;background:rgba(6,6,7,.35)}.cover-bottom{position:absolute;left:16px;right:16px;bottom:14px}.cover-bottom h2{font-family:var(--cn);font-weight:300;font-size:25px;letter-spacing:.06em;color:var(--ivory);line-height:1.1}.cover-bottom p{font-size:8px;color:var(--muted);margin-top:6px;letter-spacing:.06em}.role-tag{position:absolute;left:16px;top:44px;font-size:6px;letter-spacing:.16em;color:var(--gold);border-left:1px solid var(--gold);padding-left:7px;text-transform:uppercase}.role-tag.viewer{color:var(--muted);border-color:var(--muted)}
.space-meta{display:grid;grid-template-columns:repeat(3,1fr);padding:12px 16px}.space-meta div{display:flex;flex-direction:column;gap:4px}.space-meta span{font-size:6px;letter-spacing:.18em;color:var(--dim);text-transform:uppercase}.space-meta b{font-size:9px;font-weight:400;color:var(--muted)}.space-meter{height:1px;background:rgba(236,229,215,.07);margin:0 16px 14px}.space-meter i{display:block;height:100%;background:var(--gold-soft)}
.create-card{min-height:310px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;border-style:dashed;background:transparent}.create-card:hover{border-color:var(--gold)}.create-ring{width:48px;height:48px;border:1px solid var(--line);border-radius:50%;display:grid;place-items:center;color:var(--gold);margin-bottom:4px;transition:.3s}.create-card:hover .create-ring{border-color:var(--gold);box-shadow:0 0 30px rgba(201,169,106,.18)}.create-card b{font-family:var(--cn);font-size:17px;font-weight:300;letter-spacing:.08em;color:var(--ivory)}.create-card span{font-size:6px;letter-spacing:.18em;color:var(--dim)}
.space-guest{max-width:480px;margin:80px auto;text-align:center}.space-guest h2{font-family:var(--cn);font-size:30px;font-weight:300;margin:12px 0 10px;color:var(--ivory)}.space-guest p{font-size:9px;color:var(--dim);line-height:1.9;margin-bottom:22px}
.level-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:6px}.level-grid button,.type-grid button{border:1px solid var(--line-soft);padding:12px 10px;text-align:left;transition:.2s}.level-grid button.on,.type-grid button.on{border-color:rgba(201,169,106,.55);background:rgba(201,169,106,.06)}.level-grid b{display:block;font-size:10px;font-weight:400;color:var(--ivory)}.level-grid span{display:block;font-size:7px;color:var(--dim);margin-top:4px}.type-grid{display:grid;grid-template-columns:1fr 1fr;gap:6px}.type-grid button{display:flex;flex-direction:column;gap:5px;color:var(--muted)}.type-grid b{font-size:10px;font-weight:400;color:var(--ivory)}.type-grid span{font-size:7px;color:var(--dim)}
@media(max-width:700px){.space-grid{grid-template-columns:1fr}.space-cover{height:180px}}
@media(max-width:860px){
  .space-grid{grid-template-columns:1fr;gap:14px;padding-bottom:120px}
  .space-cover{height:210px}
  .cover-top{font-size:10px;letter-spacing:.14em}
  .cover-bottom h2{font-size:26px}
  .cover-bottom p{font-size:12px;margin-top:8px}
  .role-tag{font-size:10px;top:46px}
  .space-meta{padding:14px 16px}
  .space-meta span{font-size:10px;letter-spacing:.14em}
  .space-meta b{font-size:13px}
  .space-meter{margin:0 16px 16px}
  .create-card{min-height:190px}
  .create-card b{font-size:19px}
  .create-card span{font-size:10px;letter-spacing:.14em;text-align:center;padding:0 20px}
  .lib-head h1{font-size:30px}
  .space-guest{margin:48px auto}
}
</style>
