import type { Directive } from 'vue'

/* 缩略图按需加载：一次性把上百张图塞进网络与解码队列，是「还在加载时滑起来卡」的主因。
   交给 IntersectionObserver，视口上下各留 900px 的缓冲，滚近了才真正设 src。
   每个滚动容器一个 observer（WeakMap 持有，容器销毁即回收）。 */
const observers = new WeakMap<Element, IntersectionObserver>()
const roots = new WeakMap<HTMLImageElement, Element>()

function observerFor(root: Element) {
  let observer = observers.get(root)
  if (!observer) {
    observer = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue
        const img = entry.target as HTMLImageElement
        observer!.unobserve(img)
        const src = img.dataset.src || ''
        if (src && !img.getAttribute('src')) img.src = src
      }
    }, { root, rootMargin: '900px 0px' })
    observers.set(root, observer)
  }
  return observer
}

function rootOf(img: HTMLImageElement): Element | null {
  return roots.get(img) || img.closest('[data-scroll-root]')
}

export const vLazySrc: Directive<HTMLImageElement, string> = {
  mounted(img, binding) {
    const root = img.closest('[data-scroll-root]')
    img.dataset.src = binding.value || ''
    if (!root) { if (binding.value) img.src = binding.value; return }
    roots.set(img, root)
    observerFor(root).observe(img)
  },
  updated(img, binding) {
    const next = binding.value || ''
    if (next === (img.dataset.src || '')) return
    img.dataset.src = next
    // 已经出过图的（缩略图档位变了）才立刻重取；没出过的继续等进入视口。
    if (img.getAttribute('src')) { if (next) img.src = next; return }
    const root = rootOf(img)
    if (next && root) observerFor(root).observe(img)
  },
  unmounted(img) {
    const root = rootOf(img)
    if (root) observers.get(root)?.unobserve(img)
    roots.delete(img)
  }
}
