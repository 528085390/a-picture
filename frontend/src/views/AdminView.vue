<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '@/components/AppShell.vue'
import Icon from '@/components/Icon.vue'
import { mediaUrl } from '@/utils/media'
import EditPhotoModal from '@/components/EditPhotoModal.vue'
import { userApi } from '@/api/user'
import { pictureApi, parsePictureTags } from '@/api/picture'
import { spaceApi, spacePictureApi } from '@/api/space'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'
import { formatBytes, formatDate } from '@/utils/format'
import type { Picture, PictureVO, Space, UserVO } from '@/api/types'

const user = useUserStore()
const ui = useUiStore()
const tab = ref<'users' | 'pictures' | 'spaces'>('users')
const loading = ref(false)
const totalUsers = ref(0), totalPictures = ref(0), totalSpaces = ref(0)
const users = ref<UserVO[]>([]), pictures = ref<Picture[]>([]), spaces = ref<Space[]>([])
const page = reactive({ users: 1, pictures: 1, spaces: 1 })
const pages = reactive({ users: 1, pictures: 1, spaces: 1 })
const userSearch = ref('')
const pictureSearch = ref('')

const userModal = ref(false)
const userEditing = ref<UserVO | null>(null)
const userForm = reactive({ userName: '', userAccount: '', userRole: 'user', userProfile: '' })
const userSaving = ref(false), userError = ref('')

const spaceModal = ref(false)
const spaceEditing = ref<Space | null>(null)
const spaceForm = reactive({ spaceName: '', spaceLevel: 0, maxSizeMB: 100, maxCount: 100, status: 0 })
const spaceSaving = ref(false), spaceError = ref('')

const photoEditOpen = ref(false)
const photoEditing = ref<PictureVO | null>(null)
const photoEditSpace = ref('')

const userDisplay = (u: UserVO) => u.userName || u.userAccount || '未命名'
const roleLabel = (r?: string) => r === 'admin' ? '管理员' : '用户'
const filteredUsers = computed(() => users.value)
async function loadSummary() {
  try {
    const [u, p, s] = await Promise.all([
      userApi.listVOByPage({ current: 1, pageSize: 1 }),
      pictureApi.listPage({ current: 1, pageSize: 1 }),
      spaceApi.adminListByPage({ current: 1, pageSize: 1 }),
    ])
    totalUsers.value = u.total; totalPictures.value = p.total; totalSpaces.value = s.total
  } catch { /* stats are non-critical */ }
}
async function loadUsers() {
  loading.value = true
  try { const p = await userApi.listVOByPage({ current: page.users, pageSize: 10, sortField: 'createTime', sortOrder: 'descend' }); users.value = p.records; pages.users = p.pages || 1; totalUsers.value = p.total } catch (e) { ui.toast((e as Error).message, 'error') } finally { loading.value = false }
}
async function loadPictures() {
  loading.value = true
  try { const p = await pictureApi.listPage({ current: page.pictures, pageSize: 12, sortField: 'createTime', sortOrder: 'descend' }); pictures.value = p.records; pages.pictures = p.pages || 1; totalPictures.value = p.total } catch (e) { ui.toast((e as Error).message, 'error') } finally { loading.value = false }
}
async function loadSpaces() {
  loading.value = true
  try { const p = await spaceApi.adminListByPage({ current: page.spaces, pageSize: 10, sortField: 'createTime', sortOrder: 'descend' }); spaces.value = p.records; pages.spaces = p.pages || 1; totalSpaces.value = p.total } catch (e) { ui.toast((e as Error).message, 'error') } finally { loading.value = false }
}
function switchTab(t: typeof tab.value) { tab.value = t; if (t === 'users') loadUsers(); else if (t === 'pictures') loadPictures(); else loadSpaces() }
function openUser(u?: UserVO) { userEditing.value = u || null; userForm.userName = u?.userName || ''; userForm.userAccount = u?.userAccount || ''; userForm.userRole = u?.userRole || 'user'; userForm.userProfile = u?.userProfile || ''; userError.value = ''; userModal.value = true }
async function saveUser() {
  if (!userForm.userAccount.trim()) { userError.value = '账号不能为空。'; return }
  if (!userEditing.value && !userForm.userName.trim()) { userError.value = '昵称不能为空。'; return }
  userSaving.value = true; userError.value = ''
  try {
    if (userEditing.value) await userApi.update({ id: userEditing.value.id, userName: userForm.userName.trim(), userAccount: userForm.userAccount.trim(), userRole: userForm.userRole as any, userProfile: userForm.userProfile.trim() })
    else await userApi.add({ userName: userForm.userName.trim(), userAccount: userForm.userAccount.trim(), userRole: userForm.userRole as any, userProfile: userForm.userProfile.trim() })
    ui.toast(userEditing.value ? '用户信息已更新' : '用户已创建，默认密码 12345678', 'success'); userModal.value = false; await loadUsers(); loadSummary()
  } catch (e) { userError.value = (e as Error).message } finally { userSaving.value = false }
}
async function deleteUser(u: UserVO) {
  const ok = await ui.confirm({ title: '删除用户', message: '删除 ' + userDisplay(u) + '？其名下数据可能保留。', confirmText: '删除', danger: true })
  if (!ok) return
  try { await userApi.delete(String(u.id)); ui.toast('用户已删除', 'success'); loadUsers(); loadSummary() } catch (e) { ui.toast((e as Error).message, 'error') }
}
function openPhotoEdit(p: Picture) { photoEditing.value = { ...p, tags: parsePictureTags(p) } as PictureVO; photoEditSpace.value = p.spaceId ? String(p.spaceId) : ''; photoEditOpen.value = true }
function onPhotoSaved(p: PictureVO) { const i = pictures.value.findIndex(x => String(x.id) === String(p.id)); if (i >= 0) pictures.value[i] = { ...pictures.value[i], ...p } as Picture; ui.toast('图片信息已更新', 'success') }
async function deletePicture(p: Picture) {
  const ok = await ui.confirm({ title: '删除图片', message: '从存储中永久删除这张图片？', confirmText: '删除', danger: true })
  if (!ok) return
  try { if (p.spaceId) await spacePictureApi.delete(String(p.id)); else await pictureApi.delete(String(p.id)); pictures.value = pictures.value.filter(x => String(x.id) !== String(p.id)); totalPictures.value--; ui.toast('图片已删除', 'success'); loadSummary() } catch (e) { ui.toast((e as Error).message, 'error') }
}
function openSpace(s: Space) { spaceEditing.value = s; spaceForm.spaceName = s.spaceName; spaceForm.spaceLevel = s.spaceLevel; spaceForm.maxSizeMB = Math.round((s.maxSize || 0) / 1024 / 1024); spaceForm.maxCount = s.maxCount || 0; spaceForm.status = s.status || 0; spaceError.value = ''; spaceModal.value = true }
async function saveSpace() {
  if (!spaceEditing.value) return
  if (!spaceForm.spaceName.trim()) { spaceError.value = '空间名称不能为空。'; return }
  spaceSaving.value = true; spaceError.value = ''
  try {
    await spaceApi.adminUpdate({ id: spaceEditing.value.id, spaceName: spaceForm.spaceName.trim(), spaceLevel: spaceForm.spaceLevel as any, maxSize: Math.round(spaceForm.maxSizeMB * 1024 * 1024), maxCount: spaceForm.maxCount, status: spaceForm.status as any })
    ui.toast('空间配置已更新', 'success'); spaceModal.value = false; loadSpaces()
  } catch (e) { spaceError.value = (e as Error).message } finally { spaceSaving.value = false }
}
async function deleteSpace(s: Space) {
  const ok = await ui.confirm({ title: '删除空间', message: '删除「' + s.spaceName + '」及其图片记录？', confirmText: '删除', danger: true })
  if (!ok) return
  try { await spaceApi.delete(String(s.id)); spaces.value = spaces.value.filter(x => String(x.id) !== String(s.id)); totalSpaces.value--; ui.toast('空间已删除', 'success'); loadSummary() } catch (e) { ui.toast((e as Error).message, 'error') }
}
function goPage(t: 'users' | 'pictures' | 'spaces', delta: number) { const p = Math.min(pages[t], Math.max(1, page[t] + delta)); page[t] = p; if (t === 'users') loadUsers(); else if (t === 'pictures') loadPictures(); else loadSpaces() }
onMounted(() => { loadUsers(); loadSummary() })
</script>
<template>
  <AppShell>
    <main class="library admin-page">
      <div class="lib-head"><div><div class="eyebrow">Administration / 管理</div><h1>控制室</h1><div class="lib-sub"><b>{{ totalUsers }}</b> 位用户 · {{ totalPictures }} 张图片 · {{ totalSpaces }} 个空间</div></div><div class="tools"><button v-if="tab === 'users'" class="btn gold" @click="openUser()"><Icon name="plus" :size="13" />新建用户</button></div></div>
      <div class="admin-tabs"><button v-for="t in [{k:'users',n:'用户',i:'users'},{k:'pictures',n:'图片',i:'image'},{k:'spaces',n:'空间',i:'grid'}]" :key="t.k" :class="{ on: tab === t.k }" @click="switchTab(t.k as any)"><Icon :name="t.i" :size="13" />{{ t.n }}<em>{{ t.k === 'users' ? totalUsers : t.k === 'pictures' ? totalPictures : totalSpaces }}</em></button></div>

      <div v-if="tab === 'users'" class="admin-body">
        <div class="admin-toolbar"><div class="search-wrap"><Icon name="search" :size="12" /><input v-model="userSearch" placeholder="在当前页搜索昵称 / 账号"></div><span class="admin-note">系统管理员 · 新建用户默认密码 12345678</span></div>
        <div v-if="loading" class="page-loading"><span class="spinner"></span>读取用户…</div>
        <div v-else class="table-wrap"><table class="data-table"><thead><tr><th>用户</th><th>账号</th><th>角色</th><th>简介</th><th>注册时间</th><th></th></tr></thead><tbody><tr v-for="u in filteredUsers.filter(x => !userSearch || (x.userName || '').includes(userSearch) || (x.userAccount || '').includes(userSearch))" :key="u.id"><td><div class="cell-user"><span class="cell-avatar"><img v-if="u.userAvatar" :src="u.userAvatar" alt=""><template v-else>{{ userDisplay(u)[0] }}</template></span><b>{{ userDisplay(u) }}</b></div></td><td class="mono">{{ u.userAccount }}</td><td><span class="role-pill" :class="u.userRole">{{ roleLabel(u.userRole) }}</span></td><td class="dim">{{ u.userProfile || '—' }}</td><td class="dim">{{ formatDate(u.createTime) }}</td><td><div class="row-actions"><button class="icon-btn" title="编辑" @click="openUser(u)"><Icon name="edit" :size="13" /></button><button class="icon-btn danger-hover" title="删除" @click="deleteUser(u)"><Icon name="trash" :size="13" /></button></div></td></tr></tbody></table><div v-if="!users.length" class="table-empty"><Icon name="users" :size="20" /><b>没有读到用户列表</b><p>暂时没有用户数据。</p></div></div>
        <div class="pager"><button :disabled="page.users <= 1" @click="goPage('users', -1)">上一页</button><span>{{ page.users }} / {{ pages.users }}</span><button :disabled="page.users >= pages.users" @click="goPage('users', 1)">下一页</button></div>
      </div>

      <div v-else-if="tab === 'pictures'" class="admin-body">
        <div class="admin-toolbar"><div class="search-wrap"><Icon name="search" :size="12" /><input v-model="pictureSearch" placeholder="在当前页搜索名称 / 分类"></div><span class="admin-note">含公共图库与空间图片 · 删除不可恢复</span></div>
        <div v-if="loading" class="page-loading"><span class="spinner"></span>读取图片…</div>
        <div v-else class="admin-photo-grid"><article v-for="p in pictures.filter(x => !pictureSearch || (x.name || '').includes(pictureSearch) || (x.category || '').includes(pictureSearch))" :key="p.id" class="admin-photo"><div class="admin-photo-img"><img :src="mediaUrl(p.previewUrl || p.url)" :alt="p.name" loading="lazy"><span v-if="p.spaceId" class="space-mark">SPACE</span></div><div class="admin-photo-info"><b>{{ p.name || '未命名' }}</b><span>{{ p.category || '未分类' }} · {{ formatBytes(p.picSize || 0) }}</span></div><div class="row-actions"><button class="icon-btn" title="编辑" @click="openPhotoEdit(p)"><Icon name="edit" :size="12" /></button><button class="icon-btn danger-hover" title="删除" @click="deletePicture(p)"><Icon name="trash" :size="12" /></button></div></article></div>
        <div v-if="!pictures.length && !loading" class="table-empty"><Icon name="image" :size="20" /><b>还没有图片</b><p>公共图库与空间图片会出现在这里。</p></div>
        <div class="pager"><button :disabled="page.pictures <= 1" @click="goPage('pictures', -1)">上一页</button><span>{{ page.pictures }} / {{ pages.pictures }}</span><button :disabled="page.pictures >= pages.pictures" @click="goPage('pictures', 1)">下一页</button></div>
      </div>

      <div v-else class="admin-body">
        <div class="admin-toolbar"><span class="admin-note">调整等级、容量、数量与状态；删除会同时删除空间记录</span></div>
        <div v-if="loading" class="page-loading"><span class="spinner"></span>读取空间…</div>
        <div v-else class="table-wrap"><table class="data-table"><thead><tr><th>空间</th><th>所有者</th><th>类型 / 等级</th><th>使用量</th><th>状态</th><th></th></tr></thead><tbody><tr v-for="s in spaces" :key="s.id"><td><b class="space-name">{{ s.spaceName }}</b><span class="dim block">{{ formatDate(s.createTime) }}</span></td><td class="dim">{{ s.userId }}</td><td><span class="role-pill">{{ s.spaceType === 1 ? '团队' : '私有' }}</span> <span class="dim">{{ ['普通','专业','旗舰'][s.spaceLevel] }}</span></td><td class="dim">{{ s.totalCount || 0 }} / {{ s.maxCount }} 张<br>{{ formatBytes(s.totalSize || 0) }} / {{ formatBytes(s.maxSize || 0) }}</td><td><span class="status-dot" :class="{ off: s.status === 1 }"></span>{{ s.status === 1 ? '已禁用' : '正常' }}</td><td><div class="row-actions"><button class="icon-btn" title="配置" @click="openSpace(s)"><Icon name="settings" :size="13" /></button><button class="icon-btn danger-hover" title="删除" @click="deleteSpace(s)"><Icon name="trash" :size="13" /></button></div></td></tr></tbody></table><div v-if="!spaces.length" class="table-empty"><Icon name="grid" :size="20" /><b>还没有空间</b><p>用户创建的空间会出现在这里。</p></div></div>
        <div class="pager"><button :disabled="page.spaces <= 1" @click="goPage('spaces', -1)">上一页</button><span>{{ page.spaces }} / {{ pages.spaces }}</span><button :disabled="page.spaces >= pages.spaces" @click="goPage('spaces', 1)">下一页</button></div>
      </div>
    </main>

    <EditPhotoModal v-model:open="photoEditOpen" :photo="photoEditing" :space-id="photoEditSpace" @saved="onPhotoSaved" />
    <Teleport to="body">
      <div v-if="userModal" class="modal-backdrop" @click.self="userModal = false"><div class="modal small"><header class="modal-head"><div><h2>{{ userEditing ? '编辑用户' : '新建用户' }}</h2><p>Administration / 账号与角色</p></div><button class="icon-btn" @click="userModal = false"><Icon name="close" :size="16" /></button></header><div class="modal-body"><div class="form-grid"><div class="field"><label>昵称</label><input v-model="userForm.userName" class="input" maxlength="30" placeholder="显示名称"></div><div class="field"><label>账号</label><input v-model="userForm.userAccount" class="input" maxlength="30" placeholder="登录账号" :disabled="!!userEditing"></div><div class="field"><label>角色</label><div class="seg-control"><button :class="{ on: userForm.userRole === 'user' }" @click="userForm.userRole = 'user'">普通用户</button><button :class="{ on: userForm.userRole === 'admin' }" @click="userForm.userRole = 'admin'">管理员</button></div></div><div class="field"><label>简介</label><textarea v-model="userForm.userProfile" class="input textarea" placeholder="可选"></textarea></div><p v-if="userError" class="form-error">{{ userError }}</p></div></div><footer class="modal-foot"><button class="btn" @click="userModal = false">取消</button><button class="btn gold" :disabled="userSaving" @click="saveUser">{{ userSaving ? '保存中…' : '保存' }}</button></footer></div></div>
      <div v-if="spaceModal" class="modal-backdrop" @click.self="spaceModal = false"><div class="modal small"><header class="modal-head"><div><h2>空间配置</h2><p>{{ spaceEditing?.spaceName }}</p></div><button class="icon-btn" @click="spaceModal = false"><Icon name="close" :size="16" /></button></header><div class="modal-body"><div class="form-grid"><div class="field"><label>空间名称</label><input v-model="spaceForm.spaceName" class="input" maxlength="30"></div><div class="field"><label>等级</label><div class="level-grid"><button v-for="(m, l) in [{n:'普通',q:'100 张 / 100 MB'},{n:'专业',q:'1,000 张 / 1 GB'},{n:'旗舰',q:'10,000 张 / 10 GB'}]" :key="l" :class="{ on: spaceForm.spaceLevel === l }" @click="spaceForm.spaceLevel = l; spaceForm.maxSizeMB = [100,1024,10240][l]; spaceForm.maxCount = [100,1000,10000][l]"><b>{{ m.n }}</b><span>{{ m.q }}</span></button></div></div><div class="form-row"><div class="field"><label>容量上限（MB）</label><input v-model.number="spaceForm.maxSizeMB" class="input" type="number" min="1"></div><div class="field"><label>数量上限</label><input v-model.number="spaceForm.maxCount" class="input" type="number" min="1"></div></div><div class="field"><label>状态</label><div class="seg-control"><button :class="{ on: spaceForm.status === 0 }" @click="spaceForm.status = 0">正常</button><button :class="{ on: spaceForm.status === 1 }" @click="spaceForm.status = 1">禁用</button></div></div><p v-if="spaceError" class="form-error">{{ spaceError }}</p></div></div><footer class="modal-foot"><button class="btn" @click="spaceModal = false">取消</button><button class="btn gold" :disabled="spaceSaving" @click="saveSpace">{{ spaceSaving ? '保存中…' : '保存配置' }}</button></footer></div></div>
    </Teleport>
  </AppShell>
</template>
<style scoped>
.admin-page{padding:0}.admin-tabs{display:flex;gap:26px;padding:0 40px;border-bottom:1px solid var(--line-soft)}.admin-tabs button{position:relative;display:flex;align-items:center;gap:7px;padding:17px 2px;font-size:10px;letter-spacing:.12em;color:var(--dim)}.admin-tabs button em{font-style:normal;font-size:7px;color:var(--dim)}.admin-tabs button.on{color:var(--gold2)}.admin-tabs button.on:after{content:'';position:absolute;left:0;right:0;bottom:-1px;height:1px;background:var(--gold)}.admin-body{padding:22px 40px 50px;max-width:1400px;margin:0 auto}.admin-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;gap:12px}.search-wrap{display:flex;align-items:center;gap:8px;border:1px solid var(--line-soft);padding:0 10px;color:var(--dim);width:260px}.search-wrap input{background:transparent;border:0;color:var(--ivory);font-size:9px;height:32px;width:100%;outline:none}.admin-note{font-size:7px;color:var(--dim);letter-spacing:.08em}
.table-wrap{border:1px solid var(--line-soft);overflow:auto}.data-table{width:100%;border-collapse:collapse}.data-table th{text-align:left;padding:11px 14px;font-size:7px;font-weight:400;letter-spacing:.18em;color:var(--dim);text-transform:uppercase;border-bottom:1px solid var(--line-soft);white-space:nowrap}.data-table td{padding:12px 14px;font-size:9px;color:var(--muted);border-bottom:1px solid rgba(236,229,215,.045);vertical-align:middle}.data-table tr:last-child td{border-bottom:0}.data-table tbody tr{transition:.2s}.data-table tbody tr:hover{background:rgba(201,169,106,.025)}.cell-user{display:flex;align-items:center;gap:10px}.cell-user b{font-weight:400;color:var(--ivory);font-size:10px}.cell-avatar{width:30px;height:30px;border-radius:50%;border:1px solid var(--line);display:grid;place-items:center;font-family:var(--serif);font-size:13px;color:var(--gold2);overflow:hidden;flex:none}.cell-avatar img{width:100%;height:100%;object-fit:cover}.mono{font-family:ui-monospace,monospace;font-size:8px!important}.dim{color:var(--dim)!important}.block{display:block;margin-top:3px;font-size:7px}.space-name{font-weight:400;color:var(--ivory);font-size:10px}.role-pill{display:inline-flex;font-size:7px;letter-spacing:.1em;padding:3px 7px;border:1px solid var(--line-soft);color:var(--muted)}.role-pill.admin{color:var(--gold2);border-color:var(--line)}.row-actions{display:flex;gap:4px;justify-content:flex-end}.icon-btn{width:28px;height:28px;display:grid;place-items:center;color:var(--dim);border:1px solid transparent;transition:.2s}.icon-btn:hover{color:var(--gold2);border-color:var(--line-soft)}.icon-btn.danger-hover:hover{color:#c47c7c;border-color:rgba(179,74,74,.35)}.table-empty{padding:60px 20px;text-align:center;color:var(--dim)}.table-empty svg{margin-bottom:10px}.table-empty b{display:block;font-family:var(--cn);font-size:18px;font-weight:300;color:var(--ivory);margin-bottom:6px}.table-empty p{font-size:8px;max-width:460px;margin:0 auto;line-height:1.8}
.admin-photo-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:10px}.admin-photo{border:1px solid var(--line-soft);background:var(--panel);padding:8px;position:relative;transition:.3s}.admin-photo:hover{border-color:var(--line)}.admin-photo-img{position:relative;aspect-ratio:1;background:#000;overflow:hidden}.admin-photo-img img{width:100%;height:100%;object-fit:cover;opacity:.82}.space-mark{position:absolute;top:6px;left:6px;font-size:6px;letter-spacing:.14em;padding:2px 5px;background:rgba(6,6,7,.7);color:var(--gold2);border:1px solid var(--line)}.admin-photo-info{padding:8px 2px 4px}.admin-photo-info b{display:block;font-size:9px;font-weight:400;color:var(--ivory);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.admin-photo-info span{font-size:7px;color:var(--dim)}.admin-photo .row-actions{position:absolute;right:8px;top:8px;opacity:0;background:rgba(6,6,7,.75);transition:.2s}.admin-photo:hover .row-actions{opacity:1}
.pager{display:flex;align-items:center;justify-content:center;gap:14px;margin-top:20px;font-size:8px;color:var(--dim)}.pager button{border:1px solid var(--line-soft);padding:6px 12px;font-size:8px;color:var(--muted)}.pager button:disabled{opacity:.35}.status-dot{display:inline-block;width:5px;height:5px;border-radius:50%;background:#7f9f74;margin-right:5px}.status-dot.off{background:#a35d5d}.level-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:6px}.level-grid button{border:1px solid var(--line-soft);padding:10px 8px;text-align:left}.level-grid button.on{border-color:rgba(201,169,106,.55);background:rgba(201,169,106,.06)}.level-grid b{display:block;font-size:9px;font-weight:400;color:var(--ivory)}.level-grid span{display:block;font-size:6px;color:var(--dim);margin-top:3px}.form-row{display:grid;grid-template-columns:1fr 1fr;gap:8px}.seg-control{display:flex;gap:4px}.seg-control button{flex:1;border:1px solid var(--line-soft);padding:9px;font-size:8px;color:var(--muted)}.seg-control button.on{border-color:var(--line);color:var(--gold2);background:rgba(201,169,106,.05)}.block{display:block}
@media(max-width:820px){.admin-tabs{padding:0 16px;gap:16px}.admin-body{padding:16px 14px 40px}.admin-toolbar{flex-direction:column;align-items:flex-start}.search-wrap{width:100%}.data-table th:nth-child(4),.data-table td:nth-child(4){display:none}.admin-photo-grid{grid-template-columns:repeat(2,1fr)}}
</style>
