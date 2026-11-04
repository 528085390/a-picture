import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Toast { id: number; message: string; tone: 'default' | 'error' | 'success' }
export interface ConfirmState { title: string; message: string; confirmText: string; danger: boolean; resolve: ((v: boolean) => void) | null }

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<Toast[]>([])
  const uploadOpen = ref(false)
  const uploadSpaceId = ref<string | null>(null)
  const search = ref('')
  const confirmState = ref<ConfirmState>({ title: '', message: '', confirmText: '确认', danger: false, resolve: null })
  let toastId = 0

  function toast(message: string, tone: Toast['tone'] = 'default') {
    const id = ++toastId
    toasts.value.push({ id, message, tone })
    window.setTimeout(() => { toasts.value = toasts.value.filter(t => t.id !== id) }, 2800)
  }
  function openUpload(spaceId: string | null = null) { uploadSpaceId.value = spaceId; uploadOpen.value = true }
  function closeUpload() { uploadOpen.value = false; uploadSpaceId.value = null }
  function confirm(opts: { title: string; message?: string; confirmText?: string; danger?: boolean }): Promise<boolean> {
    return new Promise(resolve => {
      confirmState.value = { title: opts.title, message: opts.message || '', confirmText: opts.confirmText || '确认', danger: !!opts.danger, resolve }
    })
  }
  function answerConfirm(v: boolean) { confirmState.value.resolve?.(v); confirmState.value.resolve = null }

  return { toasts, uploadOpen, uploadSpaceId, search, confirmState, toast, openUpload, closeUpload, confirm, answerConfirm }
})
