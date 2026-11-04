<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import type { PictureVO } from '@/api/types'
import { pictureApi } from '@/api/picture'
import { useUiStore } from '@/stores/ui'
import Icon from './Icon.vue'
import { mediaUrl } from '@/utils/media'

const props = defineProps<{ open: boolean; photo: PictureVO | null }>()
const emit = defineEmits<{ 'update:open': [v: boolean]; saved: [photo: PictureVO] }>()
const ui = useUiStore()
const tab = ref<'ratio' | 'pixel'>('ratio')
const submitting = ref(false)
const taskId = ref('')
const status = ref('')
const resultUrl = ref('')
const taskMessage = ref('')
const elapsed = ref(0)
const error = ref('')
let pollTimer = 0, startAt = 0, stopped = false
const form = reactive({ angle: 0, outputRatio: '', xScale: 1.4, yScale: 1.4, topOffset: 0, bottomOffset: 0, leftOffset: 0, rightOffset: 0, bestQuality: false, limitImageSize: true, addWatermark: false })
const running = computed(() => !!taskId.value && !resultUrl.value && status.value !== 'FAILED' && status.value !== 'UNKNOWN')
const progress = computed(() => Math.min(96, Math.round(elapsed.value / 3)))
const statusText = computed(() => ({ PENDING: '排队中', RUNNING: '正在生成', SUSPENDED: '任务挂起', SUCCEEDED: '生成完成', FAILED: '生成失败', UNKNOWN: '任务状态未知' } as Record<string, string>)[status.value] || (status.value ? status.value : '等待开始'))
const estimate = computed(() => {
  const w = props.photo?.picWidth || 0, h = props.photo?.picHeight || 0
  if (!w || !h) return ''
  if (form.outputRatio) return form.outputRatio
  if (tab.value === 'ratio') return (w * form.xScale | 0) + ' × ' + (h * form.yScale | 0)
  return (w + form.leftOffset + form.rightOffset) + ' × ' + (h + form.topOffset + form.bottomOffset)
})
watch(() => props.open, v => { if (v) reset(); else stop() })
function reset() {
  stop(); taskId.value = ''; status.value = ''; resultUrl.value = ''; taskMessage.value = ''; error.value = ''; elapsed.value = 0; submitting.value = false; startAt = 0
}
function stop() { window.clearTimeout(pollTimer); pollTimer = 0; stopped = true }
function close() { stop(); emit('update:open', false) }
function payload() {
  const base = { angle: form.angle, outputRatio: form.outputRatio, bestQuality: form.bestQuality, limitImageSize: form.limitImageSize, addWatermark: form.addWatermark }
  if (tab.value === 'ratio') return { ...base, xScale: form.xScale, yScale: form.yScale }
  return { ...base, topOffset: form.topOffset, bottomOffset: form.bottomOffset, leftOffset: form.leftOffset, rightOffset: form.rightOffset }
}
async function createTask() {
  if (!props.photo || submitting.value) return
  submitting.value = true; error.value = ''; resultUrl.value = ''; taskMessage.value = ''; status.value = ''; elapsed.value = 0
  try {
    const res = await pictureApi.createOutPaintingTask(props.photo.id, payload())
    const output = res.output || res
    taskId.value = output.taskId || ''
    status.value = output.taskStatus || 'PENDING'
    if (!taskId.value) throw new Error(output.message || '任务创建失败，请稍后重试')
    startAt = Date.now(); stopped = false; schedulePoll(2200)
  } catch (e) { error.value = (e as Error).message } finally { submitting.value = false }
}
function schedulePoll(delay: number) {
  window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(poll, delay)
}
async function poll() {
  if (stopped || !taskId.value) return
  if (document.hidden) { schedulePoll(5000); return }
  elapsed.value = Math.floor((Date.now() - startAt) / 1000)
  try {
    const res = await pictureApi.getOutPaintingTask(taskId.value)
    const out = res.output || res
    status.value = out.taskStatus || status.value
    taskMessage.value = out.message || ''
    if (out.outputImageUrl) { resultUrl.value = out.outputImageUrl; stop(); status.value = 'SUCCEEDED'; return }
    if (['SUCCEEDED', 'FAILED', 'UNKNOWN'].includes(status.value)) {
      if (status.value === 'SUCCEEDED' && !out.outputImageUrl) error.value = '任务已完成，但没有返回图像地址'
      stop(); return
    }
  } catch (e) { taskMessage.value = (e as Error).message }
  const delay = Math.min(10000, 2200 + Math.floor(elapsed.value / 15) * 800)
  if (Date.now() - startAt > 180000) { error.value = '任务超过 3 分钟仍未完成，已停止轮询'; stop(); return }
  schedulePoll(delay)
}
function downloadResult() {
  if (!resultUrl.value) return
  const a = document.createElement('a'); a.href = resultUrl.value; a.download = (props.photo?.name || 'outpainting') + '-ai.jpg'; a.target = '_blank'; a.rel = 'noopener'; a.click()
  ui.toast('结果图将在新窗口打开，请右键保存')
}
onBeforeUnmount(stop)
</script>
<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="!running && close()">
      <div class="modal wide outpaint-modal">
        <header class="modal-head"><div><h2>AI 扩图工作室</h2><p>Outpainting / 让画面向想象之外延伸</p></div><button class="icon-btn" :disabled="running" @click="close"><Icon name="close" :size="16" /></button></header>
        <div class="outpaint-body">
          <div class="outpaint-preview">
            <img v-if="!resultUrl" :src="mediaUrl(photo?.url || photo?.previewUrl)" :alt="photo?.name || ''">
            <img v-else :src="resultUrl" alt="AI 扩图结果">
            <div v-if="running" class="scan"><i></i><span>AI 正在补全画面</span></div>
            <div class="preview-caption"><span>{{ resultUrl ? 'RESULT / 生成结果' : 'SOURCE / 原始影像' }}</span><b v-if="estimate">{{ estimate }}</b></div>
          </div>
          <div class="outpaint-panel">
            <template v-if="!resultUrl">
              <div class="seg"><button :class="{ on: tab === 'ratio' }" @click="tab = 'ratio'">比例扩展</button><button :class="{ on: tab === 'pixel' }" @click="tab = 'pixel'">像素扩展</button></div>
              <div v-if="tab === 'ratio'" class="op-fields">
                <label class="field"><span>水平扩展 <b>{{ form.xScale.toFixed(1) }}×</b></span><input v-model.number="form.xScale" type="range" min="1" max="3" step="0.1"></label>
                <label class="field"><span>垂直扩展 <b>{{ form.yScale.toFixed(1) }}×</b></span><input v-model.number="form.yScale" type="range" min="1" max="3" step="0.1"></label>
              </div>
              <div v-else class="op-fields">
                <div class="op-grid"><label class="field"><span>上</span><input v-model.number="form.topOffset" class="input" type="number" min="0" max="2000" step="10"></label><label class="field"><span>下</span><input v-model.number="form.bottomOffset" class="input" type="number" min="0" max="2000" step="10"></label><label class="field"><span>左</span><input v-model.number="form.leftOffset" class="input" type="number" min="0" max="2000" step="10"></label><label class="field"><span>右</span><input v-model.number="form.rightOffset" class="input" type="number" min="0" max="2000" step="10"></label></div>
              </div>
              <label class="field"><span>输出画幅</span><select v-model="form.outputRatio" class="select"><option value="">保持原比例</option><option value="1:1">1 : 1 方形</option><option value="3:4">3 : 4 竖幅</option><option value="4:3">4 : 3 横幅</option><option value="9:16">9 : 16 手机</option><option value="16:9">16 : 9 宽银幕</option></select></label>
              <label class="field"><span>逆时针旋转 <b>{{ form.angle }}°</b></span><input v-model.number="form.angle" type="range" min="0" max="359" step="1"></label>
              <div class="op-switches">
                <label><input v-model="form.bestQuality" type="checkbox"><span>最佳质量<em>耗时更长</em></span></label>
                <label><input v-model="form.limitImageSize" type="checkbox"><span>限制文件大小<em>默认开启</em></span></label>
                <label><input v-model="form.addWatermark" type="checkbox"><span>AI 水印<em>Generated by AI</em></span></label>
              </div>
              <p v-if="error" class="form-error">{{ error }}</p>
              <p v-if="taskMessage && !error" class="hint">{{ taskMessage }}</p>
              <button class="btn gold wide" :disabled="submitting || running" @click="createTask">{{ submitting ? '正在创建任务…' : running ? 'AI 生成中…' : '开始 AI 扩图' }}</button>
              <div v-if="taskId" class="task-state">
                <div class="task-head"><span>{{ statusText }}</span><b>{{ elapsed }}s</b></div>
                <div class="task-bar"><i :style="{ width: progress + '%' }"></i></div>
                <p>任务 ID {{ taskId }} · 轮询会在页面隐藏时暂停</p>
              </div>
            </template>
            <template v-else>
              <div class="result-kicker">OUTPAINTING COMPLETE</div>
              <h3>画面已经延伸。</h3>
              <p class="hint">生成结果不会自动保存到图库。请先下载，再上传到你的空间。</p>
              <div class="result-actions"><button class="btn gold wide" @click="downloadResult"><Icon name="download" :size="13" />下载结果图</button><a class="btn wide" :href="resultUrl" target="_blank" rel="noopener">在新窗口打开</a><button class="btn wide" @click="reset">再试一次</button></div>
            </template>
          </div>
        </div>
        <footer class="modal-foot"><span class="hint" style="margin-right:auto">阿里云 image-out-painting · 结果需手动保存</span><button class="btn" :disabled="running" @click="close">关闭</button></footer>
      </div>
    </div>
  </Teleport>
</template>
<style scoped>
.outpaint-modal{width:min(1040px,94vw)}
.outpaint-body{display:grid;grid-template-columns:1.1fr .9fr;min-height:460px}
.outpaint-preview{position:relative;background:#030304;display:flex;align-items:center;justify-content:center;overflow:hidden;border-right:1px solid var(--line-soft)}
.outpaint-preview img{max-width:100%;max-height:520px;object-fit:contain;display:block}
.preview-caption{position:absolute;left:16px;right:16px;bottom:12px;display:flex;justify-content:space-between;font-size:7px;letter-spacing:.14em;color:var(--gold-soft)}
.preview-caption b{font-weight:400;color:var(--muted);letter-spacing:.08em}
.scan{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:rgba(4,4,6,.55);backdrop-filter:blur(2px)}
.scan i{position:absolute;left:0;right:0;height:1px;background:linear-gradient(90deg,transparent,var(--gold),transparent);animation:scan 2.2s var(--ease-soft) infinite}
.scan span{font-size:8px;letter-spacing:.24em;color:var(--gold2);text-transform:uppercase}
@keyframes scan{0%{top:8%}50%{top:90%}100%{top:8%}}
.outpaint-panel{padding:24px;display:flex;flex-direction:column;gap:14px;max-height:560px;overflow:auto}
.seg{display:grid;grid-template-columns:1fr 1fr;border:1px solid var(--line-soft);padding:3px}
.seg button{padding:9px;font-size:9px;letter-spacing:.1em;color:var(--muted)}
.seg button.on{background:rgba(201,169,106,.09);color:var(--gold2)}
.op-fields{display:flex;flex-direction:column;gap:12px}
.op-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px}
.field span{display:flex;justify-content:space-between;font-size:8px;letter-spacing:.12em;color:var(--muted);text-transform:uppercase;margin-bottom:7px}
.field span b{font-weight:400;color:var(--gold2);letter-spacing:.04em}
.op-switches{display:flex;flex-direction:column;gap:8px;border-top:1px solid var(--line-soft);border-bottom:1px solid var(--line-soft);padding:12px 0}
.op-switches label{display:flex;align-items:flex-start;gap:8px;font-size:9px;color:var(--muted);cursor:pointer}
.op-switches input{accent-color:#c9a96a;margin-top:2px}
.op-switches em{display:block;font-style:normal;font-size:7px;color:var(--dim);margin-top:2px}
.task-state{border:1px solid var(--line-soft);padding:12px}
.task-head{display:flex;justify-content:space-between;font-size:8px;letter-spacing:.1em;color:var(--gold2);text-transform:uppercase}
.task-head b{font-weight:400;color:var(--muted)}
.task-bar{height:1px;background:rgba(201,169,106,.12);margin:9px 0;overflow:hidden}.task-bar i{display:block;height:100%;background:var(--gold);transition:width .7s var(--ease-soft)}
.task-state p{font-size:7px;color:var(--dim);letter-spacing:.04em;word-break:break-all}
.result-kicker{font-size:7px;letter-spacing:.24em;color:var(--gold);text-transform:uppercase}
.outpaint-panel h3{font-family:var(--serif);font-size:30px;font-weight:400;color:var(--ivory);line-height:1.05}
.result-actions{display:flex;flex-direction:column;gap:8px;margin-top:auto}
@media(max-width:760px){.outpaint-body{grid-template-columns:1fr}.outpaint-preview{min-height:240px;border-right:0;border-bottom:1px solid var(--line-soft)}.outpaint-panel{max-height:none}}
</style>
