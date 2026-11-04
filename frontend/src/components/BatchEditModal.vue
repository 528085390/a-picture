<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { spacePictureApi } from '@/api/space'
import { useUiStore } from '@/stores/ui'
import Icon from './Icon.vue'

const props = defineProps<{ open: boolean; spaceId: string; ids: string[] }>()
const emit = defineEmits<{ 'update:open': [v: boolean]; saved: [] }>()
const ui = useUiStore()
const saving = ref(false)
const error = ref('')
const form = reactive({ nameRule: '', category: '', tags: [] as string[], tagDraft: '' })
watch(() => props.open, v => { if (v) { form.nameRule = ''; form.category = ''; form.tags = []; form.tagDraft = ''; error.value = '' } })
function addTag() { const t = form.tagDraft.trim().replace(/^#/, ''); if (t && !form.tags.includes(t)) form.tags.push(t); form.tagDraft = '' }
async function submit() {
  if (!props.ids.length) return
  if (!form.nameRule && !form.category && !form.tags.length) { error.value = '至少填写一项要修改的内容。'; return }
  saving.value = true; error.value = ''
  try {
    await spacePictureApi.batchEdit({ pictureIdList: props.ids, spaceId: props.spaceId, nameRule: form.nameRule || undefined, category: form.category || undefined, tags: form.tags.length ? form.tags : undefined })
    ui.toast('已批量更新 ' + props.ids.length + ' 张照片', 'success')
    emit('saved'); emit('update:open', false)
  } catch (e) { error.value = (e as Error).message } finally { saving.value = false }
}
</script>
<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="emit('update:open', false)">
      <div class="modal">
        <header class="modal-head"><div><h2>批量编辑</h2><p>{{ ids.length }} 张照片 · 只修改填写过的字段</p></div><button class="icon-btn" @click="emit('update:open', false)"><Icon name="close" :size="16" /></button></header>
        <div class="modal-body"><div class="form-grid">
          <div class="field"><label>命名规则</label><input v-model="form.nameRule" class="input" placeholder="例如：杭州行 · {序号}"><span class="hint">使用 {序号} 自动生成递增编号</span></div>
          <div class="field"><label>分类</label><input v-model="form.category" class="input" placeholder="统一归类，如：旅行"></div>
          <div class="field"><label>标签</label><div class="tag-input"><span v-for="t in form.tags" :key="t" class="chip">{{ t }}<button @click="form.tags = form.tags.filter(x => x !== t)">×</button></span><input v-model="form.tagDraft" placeholder="输入后回车" @keydown.enter.prevent="addTag"></div></div>
          <p v-if="error" class="form-error">{{ error }}</p>
        </div></div>
        <footer class="modal-foot"><button class="btn" @click="emit('update:open', false)">取消</button><button class="btn gold" :disabled="saving" @click="submit">{{ saving ? '应用中…' : '应用到 ' + ids.length + ' 张' }}</button></footer>
      </div>
    </div>
  </Teleport>
</template>
