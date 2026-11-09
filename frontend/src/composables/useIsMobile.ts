import { onBeforeUnmount, ref } from 'vue'

const QUERY = '(max-width: 860px), (pointer: coarse)'

/** 判断是否走移动端（手机 / 窄窗口 / 触摸设备）。 */
export function useIsMobile() {
  const mql = window.matchMedia(QUERY)
  const isMobile = ref(mql.matches)
  const onChange = (e: MediaQueryListEvent) => { isMobile.value = e.matches }
  mql.addEventListener('change', onChange)
  onBeforeUnmount(() => mql.removeEventListener('change', onChange))
  return isMobile
}

export function isStandalone(): boolean {
  return window.matchMedia('(display-mode: standalone)').matches || (navigator as any).standalone === true
}
export function isIOS(): boolean {
  return /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && (navigator as any).maxTouchPoints > 1)
}
