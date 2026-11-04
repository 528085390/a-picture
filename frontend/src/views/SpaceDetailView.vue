<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import Icon from '@/components/Icon.vue'
import { thumbUrl } from '@/utils/media'
import PhotoGrid from '@/components/PhotoGrid.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import EditPhotoModal from '@/components/EditPhotoModal.vue'
import OutpaintingModal from '@/components/OutpaintingModal.vue'
import BatchEditModal from '@/components/BatchEditModal.vue'
import CollabEditor from '@/components/CollabEditor.vue'
import { spaceApi, spacePictureApi } from '@/api/space'
import { spaceUserApi } from '@/api/spaceUser'
import { analyzeApi } from '@/api/analyze'
import { useSpaceStore } from '@/stores/spaces'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'
import { formatBytes, formatDate } from '@/utils/format'
import type { CategoryAnalyze, PictureVO, SizeAnalyze, SpaceRole, SpaceUsage, SpaceUserVO, SpaceVO, TagAnalyze, UserAnalyze } from '@/api/types'

const route = useRoute()
const router = useRouter()
const spaces = useSpaceStore()
const user = useUserStore()
const ui = useUiStore()
const id = computed(() => String(route.params.id))
const space = ref<SpaceVO | null>(null)
const loading = ref(true)
const error = ref('')
const tab = ref<'photos' | 'members' | 'insights'>('photos')

const photos = ref<PictureVO[]>([])
const photoTotal = ref(0)
const photoPage = ref(1)
const photoLoading = ref(false)
const photoHasMore = ref(true)
const selectMode = ref(false)
const selectedIds = ref<string[]>([])
const selected = computed(() => photos.value.filter(p => selectedIds.value.includes(String(p.id))))
const viewerOpen = ref(false)
const viewerIndex = ref(0)
const editOpen = ref(false)
const editing = ref<PictureVO | null>(null)
const outpaintOpen = ref(false)
const outpaintPhoto = ref<PictureVO | null>(null)
const batchOpen = ref(false)
const collabOpen = ref(false)

const members = ref<SpaceUserVO[]>([])
const memberLoading = ref(false)
const memberOpen = ref(false)
const memberError = ref('')
const memberForm = reactive({ userId: '', spaceRole: 'viewer' as SpaceRole })
const memberSaving = ref(false)

const usage = ref<SpaceUsage | null>(null)
const categories = ref<CategoryAnalyze[]>([])
const tags = ref<TagAnalyze[]>([])
const sizes = ref<SizeAnalyze[]>([])
const trend = ref<UserAnalyze[]>([])
const trendDim = ref<'day' | 'week' | 'month'>('month')
const insightsLoading = ref(false)

const settingsOpen = ref(false)
const settingsName = ref('')
const settingsSaving = ref(false)
const settingsError = ref('')

const myRole = computed<SpaceRole | ''>(() => spaces.roleFor(id.value) || (space.value && String(space.value.userId) === String(user.user?.id) ? 'admin' : ''))
const otherMembers = computed(() => members.value.filter(m => String(m.userId) !== String(space.value?.userId)))
const isAdmin = computed(() => user.isAdmin)
const canManage = computed(() => isAdmin.value || myRole.value === 'admin')
const canEdit = computed(() => canManage.value || myRole.value === 'editor')
const isTeam = computed(() => space.value?.spaceType === 1)
const levelMeta = computed(() => [{ name: '普通', quota: '100 张 · 100 MB' }, { name: '专业', quota: '1,000 张 · 1 GB' }, { name: '旗舰', quota: '10,000 张 · 10 GB' }][space.value?.spaceLevel ?? 0])
const roleText = (r: string) => ({ admin: '管理员', editor: '编辑者', viewer: '观看者' } as Record<string, string>)[r] || r
const displayRole = computed(() => isAdmin.value ? 'admin' : (myRole.value || 'viewer'))
const usedPct = computed(() => Math.min(100, Number(space.value?.totalSize || 0) / Math.max(1, Number(space.value?.maxSize || 1)) * 100))
const countPct = computed(() => Math.min(100, Number(space.value?.totalCount || 0) / Math.max(1, Number(space.value?.maxCount || 1)) * 100))
const cover = computed(() => thumbUrl(photos.value[0]?.previewUrl || photos.value[0]?.url || '', 360))
const maxCat = computed(() => Math.max(1, ...categories.value.map(x => x.count)))
const maxTag = computed(() => Math.max(1, ...tags.value.map(x => x.count)))
const maxSize = computed(() => Math.max(1, ...sizes.value.map(x => x.count)))
const maxTrend = computed(() => Math.max(1, ...trend.value.map(x => x.count)))

async function loadSpace() {
  loading.value = true; error.value = ''
  try {
    space.value = await spaceApi.getVO(id.value)
    settingsName.value = space.value.spaceName
    await spaces.ensureLoaded(true)
    await loadPhotos(true)
    if (canManage.value) await loadMembers()
  } catch (e) { error.value = (e as Error).message } finally { loading.value = false }
}
async function loadPhotos(reset = false) {
  if (photoLoading.value) return
  if (reset) { photoPage.value = 1; photoHasMore.value = true; photos.value = [] }
  if (!photoHasMore.value) return
  photoLoading.value = true
  try {
    const p = await spacePictureApi.listVOByPage({ spaceId: id.value, current: photoPage.value, pageSize: 20 })
    photos.value = reset ? p.records : [...photos.value, ...p.records]
    photoTotal.value = p.total
    photoHasMore.value = p.records.length > 0 && photos.value.length < p.total
    if (photoHasMore.value) photoPage.value++
  } catch (e) { ui.toast((e as Error).message, 'error') } finally { photoLoading.value = false }
}
function openViewer(i: number) { viewerIndex.value = i; viewerOpen.value = true }
function openEdit(p: PictureVO) { editing.value = p; editOpen.value = true }
function onSaved(p: PictureVO) { const i = photos.value.findIndex(x => String(x.id) === String(p.id)); if (i >= 0) photos.value[i] = { ...photos.value[i], ...p }; ui.toast('影像信息已更新', 'success') }
async function deletePhoto(p: PictureVO) {
  const ok = await ui.confirm({ title: '移出空间', message: '这张照片会从当前空间移除，操作不可撤销。', confirmText: '移出', danger: true })
  if (!ok) return
  try { await spacePictureApi.delete(String(p.id)); photos.value = photos.value.filter(x => String(x.id) !== String(p.id)); photoTotal.value--; space.value && (space.value.totalCount = Math.max(0, (space.value.totalCount || 1) - 1)); viewerOpen.value = false; ui.toast('已移出空间', 'success'); spaces.ensureLoaded(true) } catch (e) { ui.toast((e as Error).message, 'error') }
}
async function deleteSelected() {
  if (!selected.value.length) return
  const ok = await ui.confirm({ title: '批量移出', message: '即将从空间移出选中的 ' + selected.value.length + ' 张照片。', confirmText: '移出', danger: true })
  if (!ok) return
  let done = 0
  for (const p of selected.value) { try { await spacePictureApi.delete(String(p.id)); done++ } catch { /* keep going */ } }
  const ids = new Set(selectedIds.value)
  photos.value = photos.value.filter(x => !ids.has(String(x.id)))
  photoTotal.value = Math.max(0, photoTotal.value - done)
  selectedIds.value = []; selectMode.value = false
  ui.toast('已移出 ' + done + ' 张', 'success')
  spaces.ensureLoaded(true)
}

async function loadMembers() {
  if (!canManage.value) return
  memberLoading.value = true
  try { members.value = await spaceUserApi.list(id.value) } catch (e) { ui.toast((e as Error).message, 'error') } finally { memberLoading.value = false }
}
async function addMember() {
  if (!memberForm.userId.trim()) { memberError.value = '填写对方的用户 ID。'; return }
  memberSaving.value = true; memberError.value = ''
  try {
    await spaceUserApi.add({ spaceId: id.value, userId: memberForm.userId.trim(), spaceRole: memberForm.spaceRole })
    memberOpen.value = false; memberForm.userId = ''; memberForm.spaceRole = 'viewer'; ui.toast('成员已加入空间', 'success'); await loadMembers(); spaces.ensureLoaded(true)
  } catch (e) { memberError.value = (e as Error).message } finally { memberSaving.value = false }
}
async function changeRole(m: SpaceUserVO, role: SpaceRole) {
  if (role === m.spaceRole) return
  try { await spaceUserApi.edit({ id: String(m.id), spaceRole: role }); m.spaceRole = role; ui.toast('成员角色已调整', 'success') } catch (e) { ui.toast((e as Error).message, 'error') }
}
async function removeMember(m: SpaceUserVO) {
  const ok = await ui.confirm({ title: '移除成员', message: '将 ' + (m.user?.userName || '该成员') + ' 移出空间？', confirmText: '移除', danger: true })
  if (!ok) return
  try { await spaceUserApi.remove(String(m.id)); members.value = members.value.filter(x => String(x.id) !== String(m.id)); ui.toast('成员已移除', 'success') } catch (e) { ui.toast((e as Error).message, 'error') }
}
async function loadInsights() {
  insightsLoading.value = true
  const base = { spaceId: id.value }
  try {
    const [u, c, t, s, d] = await Promise.all([analyzeApi.usage(base), analyzeApi.category(base), analyzeApi.tag(base), analyzeApi.size(base), analyzeApi.user({ ...base, timeDimension: trendDim.value })])
    usage.value = u; categories.value = c; tags.value = t; sizes.value = s; trend.value = d
  } catch (e) { ui.toast((e as Error).message, 'error') } finally { insightsLoading.value = false }
}
async function reloadTrend() { try { trend.value = await analyzeApi.user({ spaceId: id.value, timeDimension: trendDim.value }) } catch (e) { ui.toast((e as Error).message, 'error') } }
watch(tab, v => { if (v === 'members') loadMembers(); if (v === 'insights') loadInsights() })
watch(trendDim, reloadTrend)

function openSettings() { settingsName.value = space.value?.spaceName || ''; settingsError.value = ''; settingsOpen.value = true }
async function saveSettings() {
  if (!settingsName.value.trim()) { settingsError.value = '名称不能为空。'; return }
  settingsSaving.value = true; settingsError.value = ''
  try {
    await spaceApi.edit({ id: id.value, spaceName: settingsName.value.trim() })
    if (space.value) space.value.spaceName = settingsName.value.trim()
    settingsOpen.value = false; ui.toast('空间名称已更新', 'success'); spaces.ensureLoaded(true)
  } catch (e) { settingsError.value = (e as Error).message } finally { settingsSaving.value = false }
}
async function deleteSpace() {
  const ok = await ui.confirm({ title: '删除空间', message: '空间与其中的图片记录都会被删除，此操作不可撤销。', confirmText: '删除空间', danger: true })
  if (!ok) return
  try { await spaceApi.delete(id.value); spaces.removeLocal(id.value); ui.toast('空间已删除', 'success'); router.push({ name: 'spaces' }) } catch (e) { ui.toast((e as Error).message, 'error') }
}
function openCollab() { if (photos.value[0]) { editing.value = photos.value[0]; collabOpen.value = true } }
function onUploaded(e: Event) { const sid = String((e as CustomEvent).detail?.spaceId || ''); if (sid === id.value) { loadPhotos(true); loadMembers(); spaces.ensureLoaded(true) } }
watch(() => route.params.id, (v, old) => { if (v && v !== old) loadSpace() })
onMounted(() => { loadSpace(); window.addEventListener('nocturne:uploaded', onUploaded) })
onBeforeUnmount(() => window.removeEventListener('nocturne:uploaded', onUploaded))
</script>
<template>
  <AppShell>
    <main class="library space-detail">
      <div v-if="loading" class="page-loading"><span class="spinner"></span>正在进入空间…</div>
      <div v-else-if="error" class="empty-rich space-error"><div class="glyph">×</div><h3>无法打开这个空间</h3><p>{{ error }}</p><button class="btn" @click="router.push({ name: 'spaces' })">返回空间列表</button></div>
      <template v-else-if="space">
        <header class="space-head" :class="{ 'has-cover': !!cover }">
          <div class="space-head-bg" :style="cover ? { backgroundImage: 'url(' + cover + ')' } : {}"></div>
          <div class="space-head-veil"></div>
          <div class="space-head-inner">
            <button class="back-btn" @click="router.push({ name: 'spaces' })"><Icon name="arrow-left" :size="13" /> 空间</button>
            <div class="space-title-row"><div><div class="eyebrow">{{ isTeam ? 'TEAM SPACE' : 'PRIVATE SPACE' }} · {{ levelMeta.name }}</div><h1>{{ space.spaceName }}</h1><div class="space-sub"><span class="role-pill" :class="myRole">{{ roleText(displayRole) }}</span><span>{{ photoTotal }} 张照片</span><span>{{ formatBytes(space.totalSize || 0) }} / {{ formatBytes(space.maxSize || 0) }}</span><span v-if="space.createTime">建立于 {{ formatDate(space.createTime) }}</span></div></div><div class="space-actions"><button v-if="canEdit" class="btn gold" @click="ui.openUpload(id)"><Icon name="upload" :size="13" />上传</button><button v-if="canManage" class="icon-btn bordered" title="空间设置" @click="openSettings"><Icon name="settings" :size="15" /></button></div></div>
            <div class="space-bars"><div><span>容量</span><div class="bar"><i :style="{ width: usedPct + '%' }"></i></div><b>{{ usedPct.toFixed(0) }}%</b></div><div><span>数量</span><div class="bar"><i :style="{ width: countPct + '%' }"></i></div><b>{{ space.totalCount || 0 }} / {{ space.maxCount || 0 }}</b></div></div>
          </div>
        </header>
        <nav class="space-tabs"><button :class="{ on: tab === 'photos' }" @click="tab = 'photos'">照片 <em>{{ photoTotal }}</em></button><button v-if="canManage" :class="{ on: tab === 'members' }" @click="tab = 'members'">成员 <em>{{ otherMembers.length }}</em></button><button :class="{ on: tab === 'insights' }" @click="tab = 'insights'">数据</button></nav>

        <section v-if="tab === 'photos'" class="space-panel">
          <div class="panel-toolbar"><div class="hint-line"><Icon name="mouse" :size="12" /><span>滚轮缩放 · 按住拖动 · 松手滑行 · Shift 框选</span></div><div class="toolbar-actions"><button v-if="canEdit && selectedIds.length" class="btn" @click="batchOpen = true">批量编辑 {{ selected.length }}</button><button v-if="isTeam && canEdit && photos.length" class="btn" @click="openCollab"><Icon name="users" :size="13" />协同编辑</button><button v-if="canEdit" class="tool" :class="{ active: selectMode }" @click="selectMode = !selectMode; selectedIds = []">{{ selectMode ? '完成' : '选择' }}</button></div></div>
          <PhotoGrid :photos="photos" :loading="photoLoading" :loading-more="photoLoading && photos.length > 0" :has-more="photoHasMore" :select-mode="selectMode" :can-batch-delete="canEdit" :show-live-badge="true" empty-title="这个空间还没有照片" empty-text="把第一批影像放进来，空间才算真正开始。" @open="openViewer" @load-more="loadPhotos()" @update:select-mode="selectMode = $event" @batch-delete="deleteSelected" @selection-change="selectedIds = $event"><template #empty-action><button v-if="canEdit" class="btn gold" @click="ui.openUpload(id)">上传照片</button></template></PhotoGrid>
        </section>

        <section v-else-if="tab === 'members'" class="space-panel members-panel">
          <div class="panel-toolbar"><div class="hint-line"><Icon name="users" :size="12" /><span>成员通过用户 ID 加入 · 角色决定可执行的操作</span></div><button class="btn gold" @click="memberOpen = true; memberError = ''"><Icon name="plus" :size="13" />添加成员</button></div>
          <div v-if="memberLoading" class="page-loading"><span class="spinner"></span>读取成员…</div>
          <div v-else class="member-list">
            <div class="member owner"><span class="member-avatar">{{ (space.user?.userName || '所')[0] }}</span><div class="member-info"><b>{{ space.user?.userName || '空间所有者' }}</b><span>{{ space.user?.userAccount || 'OWNER' }}</span></div><em class="role-pill admin">所有者</em></div>
            <div v-for="m in otherMembers" :key="m.id" class="member"><span class="member-avatar"><img v-if="m.user?.userAvatar" :src="m.user.userAvatar" alt=""><template v-else>{{ (m.user?.userName || '?')[0] }}</template></span><div class="member-info"><b>{{ m.user?.userName || '用户 ' + m.userId }}</b><span>ID {{ m.userId }}</span></div><select class="role-select" :value="m.spaceRole" @change="changeRole(m, ($event.target as HTMLSelectElement).value as SpaceRole)"><option value="viewer">观看者</option><option value="editor">编辑者</option><option value="admin">管理员</option></select><button class="icon-btn danger-hover" title="移除" @click="removeMember(m)"><Icon name="trash" :size="13" /></button></div>
            <div v-if="!members.length" class="member-empty">还没有其他成员。团队空间可以邀请观看者、编辑者或管理员。</div>
          </div>
        </section>

        <section v-else class="space-panel insights-panel">
          <div v-if="insightsLoading" class="page-loading"><span class="spinner"></span>正在分析这个空间…</div>
          <template v-else>
            <div class="insight-grid">
              <div class="insight-card wide usage-card"><div class="eyebrow">Storage / 容量</div><div class="usage-nums"><strong>{{ formatBytes(usage?.usedSize || 0) }}</strong><span>/ {{ formatBytes(usage?.maxSize || space.maxSize || 0) }}</span></div><div class="bar big"><i :style="{ width: Math.min(100, usage?.sizeUsageRatio || 0) * 100 + '%' }"></i></div><div class="usage-foot"><span>{{ ((usage?.sizeUsageRatio || 0) * 100).toFixed(0) }}% 已使用</span><span>{{ usage?.usedCount || 0 }} / {{ usage?.maxCount || space.maxCount || 0 }} 张</span></div></div>
              <div class="insight-card"><div class="eyebrow">Categories / 分类</div><div v-for="c in categories" :key="c.category" class="stat-row"><span class="stat-label">{{ c.category || '未分类' }}</span><div class="stat-bar"><i :style="{ width: c.count / maxCat * 100 + '%' }"></i></div><b>{{ c.count }}</b></div><p v-if="!categories.length" class="insight-empty">还没有分类数据</p></div>
              <div class="insight-card"><div class="eyebrow">Formats & Size / 体积分布</div><div v-for="s in sizes" :key="s.sizeRange" class="stat-row"><span class="stat-label">{{ s.sizeRange }}</span><div class="stat-bar"><i :style="{ width: s.count / maxSize * 100 + '%' }"></i></div><b>{{ s.count }}</b></div><p v-if="!sizes.length" class="insight-empty">还没有体积数据</p></div>
              <div class="insight-card wide"><div class="eyebrow">Tags / 标签</div><div class="tag-cloud"><span v-for="t in tags" :key="t.tag" :style="{ fontSize: 9 + t.count / maxTag * 9 + 'px', opacity: .55 + t.count / maxTag * .45 }">{{ t.tag }} <em>{{ t.count }}</em></span><p v-if="!tags.length" class="insight-empty">还没有标签数据</p></div></div>
              <div class="insight-card wide"><div class="eyebrow">Uploads / 上传节奏 <div class="dim-switch"><button v-for="d in [{k:'day',l:'日'},{k:'week',l:'周'},{k:'month',l:'月'}]" :key="d.k" :class="{ on: trendDim === d.k }" @click="trendDim = d.k as any">{{ d.l }}</button></div></div><div class="trend-chart"><div v-for="(p, i) in trend" :key="p.period" class="trend-col"><div class="trend-bar-wrap"><i :style="{ height: Math.max(2, p.count / maxTrend * 100) + '%', animationDelay: i * 30 + 'ms' }"></i></div><span>{{ String(p.period).slice(5) }}</span><b>{{ p.count }}</b></div><p v-if="!trend.length" class="insight-empty">这个维度还没有上传记录</p></div></div>
            </div>
          </template>
        </section>
      </template>
    </main>

    <PhotoViewer v-if="space" v-model:open="viewerOpen" v-model:index="viewerIndex" :photos="photos" :can-edit="canEdit" :collab="isTeam" @edit="openEdit" @delete="deletePhoto" @collab="collabOpen = true; editing = photos[viewerIndex]" @outpainting="p => { outpaintPhoto = p; outpaintOpen = true }" />
    <EditPhotoModal v-model:open="editOpen" :photo="editing" :space-id="id" @saved="onSaved" />
    <OutpaintingModal v-model:open="outpaintOpen" :photo="outpaintPhoto" />
    <BatchEditModal v-model:open="batchOpen" :space-id="id" :ids="selectedIds" @saved="loadPhotos(true); selectedIds = []; batchOpen = false" />
    <CollabEditor v-if="isTeam && editing" v-model:open="collabOpen" :photo="editing" />

    <Teleport to="body">
      <div v-if="settingsOpen" class="modal-backdrop" @click.self="settingsOpen = false"><div class="modal small"><header class="modal-head"><div><h2>空间设置</h2><p>{{ space?.spaceName }}</p></div><button class="icon-btn" @click="settingsOpen = false"><Icon name="close" :size="16" /></button></header><div class="modal-body"><div class="field"><label>空间名称</label><input v-model="settingsName" class="input" maxlength="30" @keyup.enter="saveSettings"></div><div class="info-strip"><span>类型</span><b>{{ isTeam ? '团队空间' : '私有空间' }}</b><span>等级</span><b>{{ levelMeta.name }} · {{ levelMeta.quota }}</b><span>状态</span><b>{{ space?.status === 1 ? '已禁用' : '正常' }}</b></div><p v-if="settingsError" class="form-error">{{ settingsError }}</p><div class="danger-zone"><div><b>删除空间</b><span>此操作不可撤销。</span></div><button class="btn danger" @click="deleteSpace">删除</button></div></div><footer class="modal-foot"><button class="btn" @click="settingsOpen = false">取消</button><button class="btn gold" :disabled="settingsSaving" @click="saveSettings">{{ settingsSaving ? '保存中…' : '保存' }}</button></footer></div></div>
      <div v-if="memberOpen" class="modal-backdrop" @click.self="memberOpen = false"><div class="modal small"><header class="modal-head"><div><h2>添加成员</h2><p>把一个人带进这个空间</p></div><button class="icon-btn" @click="memberOpen = false"><Icon name="close" :size="16" /></button></header><div class="modal-body"><div class="field"><label>用户 ID</label><input v-model="memberForm.userId" class="input" placeholder="对方注册后获得的用户 ID"><p class="field-hint">让对方在「账号设置」中查看自己的用户 ID。</p></div><div class="field"><label>角色</label><div class="role-grid"><button v-for="r in [{k:'viewer',n:'观看者',d:'只能浏览与查看'},{k:'editor',n:'编辑者',d:'上传 / 编辑 / 删除'},{k:'admin',n:'管理员',d:'全部权限 + 成员管理'}]" :key="r.k" type="button" :class="{ on: memberForm.spaceRole === r.k }" @click="memberForm.spaceRole = r.k as SpaceRole"><b>{{ r.n }}</b><span>{{ r.d }}</span></button></div></div><p v-if="memberError" class="form-error">{{ memberError }}</p></div><footer class="modal-foot"><button class="btn" @click="memberOpen = false">取消</button><button class="btn gold" :disabled="memberSaving" @click="addMember">{{ memberSaving ? '添加中…' : '添加成员' }}</button></footer></div></div>
    </Teleport>
  </AppShell>
</template>
<style scoped>
.space-detail{padding:0}.space-head{position:relative;height:280px;overflow:hidden;border-bottom:1px solid var(--line-soft)}.space-head-bg{position:absolute;inset:0;background-size:cover;background-position:center;filter:saturate(.5) brightness(.45);transform:scale(1.05)}.space-head-veil{position:absolute;inset:0;background:linear-gradient(90deg,rgba(6,6,7,.96),rgba(6,6,7,.58) 55%,rgba(6,6,7,.9)),linear-gradient(0deg,var(--bg),transparent 60%)}.space-head-inner{position:relative;z-index:2;height:100%;display:flex;flex-direction:column;justify-content:flex-end;padding:26px 40px 24px;max-width:1400px;margin:0 auto}.back-btn{position:absolute;top:24px;left:40px;display:flex;align-items:center;gap:7px;font-size:8px;letter-spacing:.18em;color:var(--muted);text-transform:uppercase}.back-btn:hover{color:var(--gold2)}.space-title-row{display:flex;justify-content:space-between;align-items:flex-end;gap:20px}.space-title-row h1{font-family:var(--cn);font-size:44px;font-weight:300;letter-spacing:.08em;color:var(--ivory);line-height:1.1;margin:8px 0 10px}.space-sub{display:flex;align-items:center;gap:14px;font-size:8px;color:var(--muted);letter-spacing:.05em;flex-wrap:wrap}.role-pill{display:inline-flex;font-size:7px;letter-spacing:.14em;text-transform:uppercase;border:1px solid var(--line);padding:4px 8px;color:var(--gold2)}.role-pill.viewer{color:var(--muted);border-color:rgba(236,229,215,.15)}.role-pill.editor{color:var(--ivory)}.space-actions{display:flex;gap:8px;align-items:center;padding-bottom:4px}.bordered{border:1px solid var(--line-soft)!important;width:34px;height:34px}.space-bars{display:flex;gap:32px;margin-top:18px}.space-bars>div{display:flex;align-items:center;gap:10px;width:260px}.space-bars span{font-size:7px;letter-spacing:.16em;color:var(--dim);text-transform:uppercase;width:26px}.bar{height:2px;flex:1;background:rgba(236,229,215,.1);overflow:hidden}.bar i{display:block;height:100%;background:linear-gradient(90deg,var(--gold-soft),var(--gold2));transition:width .8s var(--ease-out)}.space-bars b{font-size:7px;font-weight:400;color:var(--muted);min-width:56px;text-align:right}
.space-tabs{display:flex;gap:28px;padding:0 40px;border-bottom:1px solid var(--line-soft);max-width:1400px;margin:0 auto;width:100%;box-sizing:border-box}.space-tabs button{position:relative;padding:18px 2px;font-size:10px;letter-spacing:.15em;color:var(--dim);transition:.25s}.space-tabs button em{font-style:normal;font-size:7px;color:var(--dim);margin-left:4px}.space-tabs button.on{color:var(--gold2)}.space-tabs button.on:after{content:'';position:absolute;left:0;right:0;bottom:-1px;height:1px;background:var(--gold)}.space-panel{padding:22px 40px 50px;max-width:1400px;margin:0 auto;width:100%;box-sizing:border-box}.panel-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:18px;gap:12px}.hint-line{display:flex;align-items:center;gap:7px;font-size:7px;color:var(--dim);letter-spacing:.08em}.toolbar-actions{display:flex;gap:7px;align-items:center}
.member-list{display:flex;flex-direction:column;border-top:1px solid var(--line-soft);max-width:800px}.member{display:flex;align-items:center;gap:14px;padding:16px 4px;border-bottom:1px solid var(--line-soft)}.member-avatar{width:38px;height:38px;border-radius:50%;border:1px solid var(--line);display:grid;place-items:center;font-family:var(--serif);font-size:16px;color:var(--gold2);overflow:hidden;flex:none}.member-avatar img{width:100%;height:100%;object-fit:cover}.member-info{flex:1;display:flex;flex-direction:column;gap:3px}.member-info b{font-size:11px;font-weight:400;color:var(--ivory)}.member-info span{font-size:7px;color:var(--dim);letter-spacing:.08em}.role-select{background:transparent;border:1px solid var(--line-soft);color:var(--muted);font-size:8px;padding:7px 10px}.role-select:focus{border-color:var(--gold)}.member-empty{padding:40px 0;font-size:9px;color:var(--dim);letter-spacing:.04em}
.insight-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.insight-card{border:1px solid var(--line-soft);background:rgba(10,10,12,.55);padding:22px}.insight-card.wide{grid-column:1/-1}.usage-nums{display:flex;align-items:baseline;gap:8px;margin:14px 0 10px}.usage-nums strong{font-family:var(--serif);font-size:38px;font-weight:400;color:var(--ivory)}.usage-nums span{font-size:9px;color:var(--dim)}.bar.big{height:4px}.usage-foot{display:flex;justify-content:space-between;font-size:7px;color:var(--dim);margin-top:10px;letter-spacing:.08em}.stat-row{display:flex;align-items:center;gap:10px;margin-top:12px}.stat-label{width:90px;font-size:8px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.stat-bar{flex:1;height:2px;background:rgba(236,229,215,.07)}.stat-bar i{display:block;height:100%;background:var(--gold-soft);transition:width .8s var(--ease-out)}.stat-row b{width:24px;text-align:right;font-size:8px;font-weight:400;color:var(--dim)}.insight-empty{font-size:8px;color:var(--dim);margin-top:14px}.tag-cloud{display:flex;flex-wrap:wrap;gap:8px 16px;align-items:baseline;margin-top:16px;min-height:40px}.tag-cloud span{font-family:var(--serif);color:var(--gold2);letter-spacing:.04em}.tag-cloud em{font-family:var(--sans);font-style:normal;font-size:6px;color:var(--dim);margin-left:2px}.dim-switch{display:inline-flex;gap:4px;margin-left:12px;vertical-align:middle}.dim-switch button{font-size:7px;padding:3px 7px;border:1px solid var(--line-soft);color:var(--dim)}.dim-switch button.on{color:var(--gold2);border-color:var(--line)}.trend-chart{display:flex;align-items:flex-end;gap:8px;height:140px;margin-top:18px;padding-top:10px}.trend-col{flex:1;display:flex;flex-direction:column;align-items:center;height:100%;gap:5px;min-width:18px}.trend-bar-wrap{flex:1;width:100%;display:flex;align-items:flex-end}.trend-bar-wrap i{display:block;width:100%;background:linear-gradient(180deg,var(--gold),rgba(201,169,106,.12));transform-origin:bottom;animation:trendIn .7s var(--ease-out) backwards}.trend-col span{font-size:6px;color:var(--dim);white-space:nowrap}.trend-col b{font-size:7px;font-weight:400;color:var(--muted)}@keyframes trendIn{from{transform:scaleY(0)}}
.role-grid{display:grid;grid-template-columns:1fr;gap:6px}.role-grid button{display:flex;flex-direction:column;gap:3px;text-align:left;border:1px solid var(--line-soft);padding:10px 12px}.role-grid button.on{border-color:rgba(201,169,106,.55);background:rgba(201,169,106,.06)}.role-grid b{font-size:10px;font-weight:400;color:var(--ivory)}.role-grid span{font-size:7px;color:var(--dim)}.info-strip{display:grid;grid-template-columns:80px 1fr;gap:8px 10px;margin:18px 0;padding:14px;border:1px solid var(--line-soft);font-size:8px}.info-strip span{color:var(--dim)}.info-strip b{font-weight:400;color:var(--muted)}.danger-zone{display:flex;justify-content:space-between;align-items:center;margin-top:24px;padding-top:18px;border-top:1px solid rgba(179,74,74,.25)}.danger-zone b{display:block;font-size:9px;font-weight:400;color:#c47c7c}.danger-zone span{display:block;font-size:7px;color:var(--dim);margin-top:3px}.btn.danger{color:#d79292;border-color:rgba(179,74,74,.4)}.space-error{max-width:500px;margin:100px auto}
@media(max-width:820px){.space-head{height:260px}.space-head-inner{padding:20px 18px}.back-btn{left:18px;top:18px}.space-title-row h1{font-size:30px}.space-actions .btn{padding:8px 12px}.space-bars{gap:14px;flex-direction:column}.space-bars>div{width:100%}.space-tabs{padding:0 18px;gap:18px}.space-panel{padding:16px 14px 40px}.insight-grid{grid-template-columns:1fr}.panel-toolbar{align-items:flex-start;flex-direction:column;gap:10px}.hint-line{display:none}.toolbar-actions{width:100%;overflow:auto}.member{flex-wrap:wrap}.member-info{min-width:120px}}
</style>
