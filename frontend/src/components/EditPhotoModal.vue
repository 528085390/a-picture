<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { PictureVO } from '@/api/types'
import { pictureApi } from '@/api/picture'
import { spacePictureApi } from '@/api/space'
import { useUiStore } from '@/stores/ui'
import Icon from './Icon.vue'

const props = defineProps<{ open: boolean; photo: PictureVO | null; spaceId?: string | null }>()
const emit = defineEmits<{ 'update:open': [v: boolean]; saved: [photo: PictureVO] }>()
const ui = useUiStore()
const saving = ref(false)
const error = ref('')
const form = reactive({ name: '', introduction: '', category: '', tags: [] as string[] })
const tagDraft = ref('')

watch(() => props.open, v => {
  if (!v || !props.photo) return
  form.name = props.photo.name || ''
  form.introduction = props.photo.introduction || ''
  form.category = props.photo.category || ''
  form.tags = [...(props.photo.tags || [])]
  error.value = ''; tagDraft.value = ''
})
function addTag() {
  const t = tagDraft.value.trim().replace(/^#/, '')
  if (t && !form.tags.includes(t)) form.tags.push(t)
  tagDraft.value = ''
}
function removeTag(t: string) { form.tags = form.tags.filter(x => x !== t) }
async function submit() {
  if (!props.photo) return
  if (!form.name.trim()) { error.value = '影像需要一个名字。'; return }
  saving.value = true; error.value = ''
  try {
    const data = { id: props.photo.id, name: form.name.trim(), introduction: form.introduction.trim(), category: form.category.trim(), tags: form.tags }
    if (props.spaceId) await spacePictureApi.edit(data)
    else await pictureApi.edit(data)
    const updated = { ...props.photo, ...data }
    emit('saved', updated); emit('update:open', false)
    ui.toast('照片信息已更新', 'success')
  } catch (e) { error.value = (e as Error).message } finally { saving.value = false }
}
</script>
<template>
  <Teleport to="body">
    <div v-if="open" class="modal-backdrop" @click.self="emit('update:open', false)">
      <div class="modal">
        <header class="modal-head"><div><h2>编辑影像档案</h2><p>Edit metadata / 元数据只影响这一张照片</p></div><button class="icon-btn" type="button" @click="emit('update:open', false)"><Icon name="close" :size="16" /></button></header>
        <div class="modal-body">
          <div class="form-grid">
            <div class="field"><label>Title / 标题</label><input v-model="form.name" class="input" maxlength="80" placeholder="给这张照片一个名字"></div>
            <div class="form-grid two">
              <div class="field"><label>Category / 分类</label><input v-model="form.category" class="input" maxlength="40" placeholder="风景、人物、建筑…"></div>
              <div class="field"><label>Tags / 标签</label>
                <div class="tag-input">
                  <span v-for="t in form.tags" :key="t" class="chip">{{ t }}<button type="button" @click="removeTag(t)">×</button></span>
                  <input v-model="tagDraft" placeholder="输入后回车" @keydown.enter.prevent="addTag" @keydown.backspace="!tagDraft && form.tags.pop()">
                </div>
              </div>
            </div>
            <div class="field"><label>Story / 影像手记</label><textarea v-model="form.introduction" class="textarea" maxlength="500" placeholder="光线、地点、当时的心情…"></textarea></div>
            <p v-if="error" class="form-error">{{ error }}</p>
          </div>
        </div>
        <footer class="modal-foot"><button class="btn" @click="emit('update:open', false)">取消</button><button class="btn gold" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存修改' }}</button></footer>
      </div>
    </div>
  </Teleport>
</template>
