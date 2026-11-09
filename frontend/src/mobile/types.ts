import type { PictureVO } from '@/api/types'

/** 图库浏览层级：年 / 月 / 日 / 全部照片 */
export type LibraryMode = 'years' | 'months' | 'days' | 'all'

export interface Rect { x: number; y: number; width: number; height: number }

/** 网格列数档位（对应 iOS 照片的双指缩放） */
export const COLUMN_STEPS = [2, 3, 4, 5, 6] as const
export const COLUMN_MIN = 2
export const COLUMN_MAX = 6

export interface PhotoGroup { key: string; title: string; caption?: string; photos: PictureVO[] }

export function measurePhoto(id: string | number): Rect | null {
  const el = document.querySelector('[data-photo-id="' + id + '"]') as HTMLElement | null
  if (!el) return null
  const r = el.getBoundingClientRect()
  if (!r.width || !r.height) return null
  return { x: r.x, y: r.y, width: r.width, height: r.height }
}
