<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { PictureVO } from '@/api/types'
import { useUserStore } from '@/stores/user'
import Icon from './Icon.vue'
import { mediaUrl } from '@/utils/media'

const props = defineProps<{ open: boolean; photo: PictureVO | null }>()
const emit = defineEmits<{ 'update:open': [v: boolean] }>()
const user = useUserStore()
const ws = ref<WebSocket | null>(null)
const connected = ref(false)
const connecting = ref(false)
const editorId = ref('')
const editorName = ref('')
const logs = ref<{ text: string; kind: string; time: string }[]>([])
const error = ref('')
const scale = ref(1)
const rotation = ref(0)
const isEditor = computed(() => !!editorId.value && editorId.value === user.user?.id)
const someoneEditing = computed(() => !!editorId.value)
const statusText = computed(() => connecting.value ? '正在连接' : !connected.value ? '已断开' : isEditor.value ? '你正在编辑' : someoneEditing.value ? editorName.value + ' 正在编辑' : '只读观看')
function log(text: string, kind = 'info') { logs.value.unshift({ text, kind, time: new Date().toLocaleTimeString('zh-CN', { hour12: false }) }); if (logs.value.length > 24) logs.value.pop() }
function url() { if (!props.photo) return ''; return (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/api/ws/picture/edit?pictureId=' + encodeURIComponent(props.photo.id) }
function connect() {
  if (!props.photo || connecting.value || ws.value) return
  connecting.value = true; error.value = ''; logs.value = []
  try {
    const socket = new WebSocket(url())
    ws.value = socket
    socket.onopen = () => { connecting.value = false; connected.value = true; log('已进入协同频道，正在申请编辑权限…'); send('ENTER_EDIT') }
    socket.onmessage = ev => {
      let msg: any; try { msg = JSON.parse(ev.data) } catch { return }
      const uid = String(msg.user?.id || '')
      if (msg.type === 'ERROR') { error.value = msg.message || '协同编辑发生错误'; log(error.value, 'error'); return }
      if (msg.type === 'INFO') { log(msg.message || '', 'info'); return }
      if (msg.type === 'ENTER_EDIT') {
        editorId.value = uid; editorName.value = msg.user?.userName || msg.user?.userAccount || '一位成员'
        log((isEditor.value ? '你获得了编辑锁' : editorName.value + ' 开始编辑'), 'edit')
        return
      }
      if (msg.type === 'EXIT_EDIT') { editorId.value = ''; editorName.value = ''; log((msg.user?.userName || '成员') + ' 退出编辑', 'info'); return }
      if (msg.type === 'EDIT_ACTION') {
        log(msg.message || ('收到 ' + msg.editAction), 'action')
        applyAction(msg.editAction)
      }
    }
    socket.onerror = () => { error.value = '无法建立 WebSocket 连接，请确认已登录且拥有编辑权限'; connecting.value = false; connected.value = false }
    socket.onclose = () => { connected.value = false; connecting.value = false; ws.value = null; editorId.value = ''; log('协同频道已关闭', 'info') }
  } catch (e) { connecting.value = false; error.value = (e as Error).message }
}
function send(type: string, editAction?: string) {
  if (!ws.value || ws.value.readyState !== WebSocket.OPEN) return
  ws.value.send(JSON.stringify({ type, editAction }))
}
function applyLocal(action: string) {
  if (!isEditor.value) return
  applyAction(action); send('EDIT_ACTION', action)
}
function applyAction(action?: string) {
  if (action === 'ZOOM_IN') scale.value = Math.min(3, +(scale.value + .15).toFixed(2))
  else if (action === 'ZOOM_OUT') scale.value = Math.max(.4, +(scale.value - .15).toFixed(2))
  else if (action === 'ROTATE_LEFT') rotation.value = (rotation.value - 90 + 360) % 360
  else if (action === 'ROTATE_RIGHT') rotation.value = (rotation.value + 90) % 360
}
function leave() { if (isEditor.value) send('EXIT_EDIT'); window.setTimeout(() => { ws.value?.close(); ws.value = null; emit('update:open', false) }, 80) }
function close() { leave() }
watch(() => props.open, v => { if (v) { scale.value = 1; rotation.value = 0; connect() } else { if (isEditor.value) send('EXIT_EDIT'); ws.value?.close(); ws.value = null; connected.value = false } }, { immediate: true })
onBeforeUnmount(() => { ws.value?.close(); ws.value = null; document.body.classList.remove('locked') })
</script>
<template>
  <Teleport to="body">
    <div v-if="open" class="collab-overlay">
      <header class="collab-top"><div><b>协同编辑 ·</b><span>{{ photo?.name || '未命名影像' }}</span></div><div class="collab-status" :class="{ live: connected, editing: isEditor }"><i></i>{{ statusText }}</div><button class="btn" @click="close">退出协同</button></header>
      <div class="collab-stage">
        <div class="collab-canvas"><img v-if="photo" :src="mediaUrl(photo.url || photo.previewUrl)" :style="{ transform: 'scale(' + scale + ') rotate(' + rotation + 'deg)' }" :alt="photo.name || ''"></div>
        <div v-if="!isEditor" class="collab-lock"><Icon name="eye" :size="18" /><b>{{ someoneEditing ? '正在观看 ' + editorName + ' 编辑' : '等待编辑者进入' }}</b><span>{{ someoneEditing ? '编辑动作会实时同步到这块画布' : '你可以留在频道中观看' }}</span></div>
        <div class="collab-tools" :class="{ disabled: !isEditor }"><button title="放大" @click="applyLocal('ZOOM_IN')"><Icon name="zoom-in" :size="18" /></button><button title="缩小" @click="applyLocal('ZOOM_OUT')"><Icon name="zoom-out" :size="18" /></button><button title="向左旋转" @click="applyLocal('ROTATE_LEFT')"><Icon name="rotate" :size="18" /></button><button title="向右旋转" @click="applyLocal('ROTATE_RIGHT')"><Icon name="rotate" :size="18" style="transform:scaleX(-1)" /></button></div>
      </div>
      <aside class="collab-log"><div class="log-head">SESSION LOG / 协同记录</div><div v-for="(l, i) in logs" :key="i" class="log-row" :class="l.kind"><time>{{ l.time }}</time><span>{{ l.text }}</span></div><div v-if="!logs.length" class="log-empty">等待频道消息…</div><div v-if="error" class="log-error">{{ error }}</div><p class="collab-note">同一时间只有一个人能编辑。其他人自动成为观众，所有缩放与旋转会实时广播。</p></aside>
    </div>
  </Teleport>
</template>
<style scoped>
.collab-overlay{position:fixed;inset:0;z-index:120;background:#050506;display:grid;grid-template-rows:60px 1fr;grid-template-columns:1fr 290px;grid-template-areas:"top top" "stage log"}
.collab-top{grid-area:top;display:flex;align-items:center;gap:18px;padding:0 22px;border-bottom:1px solid var(--line-soft);background:#08080a}
.collab-top>div:first-child{display:flex;gap:9px;align-items:baseline;font-size:10px;color:var(--muted)}.collab-top b{font-family:var(--serif);font-size:16px;font-weight:400;color:var(--ivory)}
.collab-status{margin-left:auto;display:flex;align-items:center;gap:7px;font-size:8px;letter-spacing:.12em;color:var(--dim);text-transform:uppercase}.collab-status i{width:5px;height:5px;border-radius:50%;background:var(--dim)}.collab-status.live i{background:var(--gold);box-shadow:0 0 8px var(--gold)}.collab-status.editing{color:var(--gold2)}
.collab-stage{grid-area:stage;position:relative;display:flex;align-items:center;justify-content:center;overflow:hidden;background:radial-gradient(circle at 50% 42%,rgba(201,169,106,.05),transparent 55%)}
.collab-canvas{width:min(78%,900px);height:78%;display:flex;align-items:center;justify-content:center}.collab-canvas img{max-width:100%;max-height:100%;object-fit:contain;transition:transform .48s var(--ease-soft);box-shadow:0 30px 80px rgba(0,0,0,.65),0 0 0 1px rgba(201,169,106,.28)}
.collab-lock{position:absolute;inset:auto 0 0;display:flex;flex-direction:column;align-items:center;gap:5px;padding:22px;background:linear-gradient(transparent,rgba(5,5,6,.8));color:var(--muted);font-size:10px}.collab-lock b{font-weight:400;color:var(--ivory)}.collab-lock span{font-size:8px;color:var(--dim)}
.collab-tools{position:absolute;left:50%;bottom:24px;transform:translateX(-50%);display:flex;gap:1px;border:1px solid var(--line);background:rgba(8,8,10,.9);backdrop-filter:blur(12px);padding:3px}.collab-tools button{width:42px;height:38px;display:grid;place-items:center;color:var(--muted)}.collab-tools button:hover{color:var(--gold2);background:rgba(201,169,106,.07)}.collab-tools.disabled{opacity:.3;pointer-events:none}
.collab-log{grid-area:log;border-left:1px solid var(--line-soft);padding:20px 16px;overflow:auto;display:flex;flex-direction:column}.log-head{font-size:7px;letter-spacing:.2em;color:var(--gold-soft);margin-bottom:16px}.log-row{display:flex;gap:8px;padding:5px 0;border-bottom:1px solid rgba(255,255,255,.025);font-size:8px;line-height:1.5;color:var(--muted)}.log-row time{color:var(--dim);flex:none;font-family:var(--sans)}.log-row.edit span{color:var(--gold2)}.log-row.error span,.log-error{color:var(--danger)}.log-empty{font-size:8px;color:var(--dim)}.log-error{font-size:8px;margin-top:10px}.collab-note{margin-top:auto;padding-top:16px;border-top:1px solid var(--line-soft);font-size:8px;line-height:1.7;color:var(--dim)}
@media(max-width:760px){.collab-overlay{grid-template-columns:1fr;grid-template-areas:"top" "stage"}.collab-log{display:none}.collab-canvas{width:92%;height:70%}}
</style>
