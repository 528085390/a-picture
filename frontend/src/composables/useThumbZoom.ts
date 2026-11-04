import { computed, ref } from 'vue'
// 保留 4 档缩放：116 / 176 / 238 / 300，滚轮每次只推进一格。
export const ZOOM_STEPS = [116, 176, 238, 300] as const
const saved = Number(localStorage.getItem('nocturne.thumb') || 176)
const initial = ZOOM_STEPS.reduce((best, s) => Math.abs(s - saved) < Math.abs(best - saved) ? s : best, 176)
const thumb = ref<number>(initial)
const label = computed(() => thumb.value <= 116 ? '小' : thumb.value <= 176 ? '中' : thumb.value <= 238 ? '大' : '特大')
function setZoom(v: number) {
  let n: number = ZOOM_STEPS[0]
  ZOOM_STEPS.forEach(s => { if (Math.abs(s - v) < Math.abs(n - v)) n = s })
  thumb.value = n
  localStorage.setItem('nocturne.thumb', String(n))
}
function stepZoom(dir: number) {
  let idx = 0
  ZOOM_STEPS.forEach((s, i) => { if (Math.abs(s - thumb.value) < Math.abs(ZOOM_STEPS[idx] - thumb.value)) idx = i })
  const next = Math.max(0, Math.min(ZOOM_STEPS.length - 1, idx + dir))
  if (ZOOM_STEPS[next] !== thumb.value) setZoom(ZOOM_STEPS[next])
}
export function useThumbZoom() { return { thumb, label, setZoom, stepZoom } }
