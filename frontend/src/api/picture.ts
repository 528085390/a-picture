import { request, requestBlob, toQuery } from './request'
import type { BaseResponse, LivePictureVO, OutPaintingParams, OutPaintingTaskResponse, Page, PageRequest, Picture, PictureEditRequest, PictureVO, PictureUploadRequest } from './types'
import { storageKey } from '@/utils/media'

export const pictureApi = {
  upload: (file: File, data: PictureUploadRequest = {}) => {
    const fd = new FormData(); fd.append('file', file); fd.append('id', data.id ?? '')
    return request<PictureVO>('/picture/upload', { method: 'POST', rawBody: fd })
  },
  uploadLive: (file: File, data: PictureUploadRequest = {}) => {
    const fd = new FormData(); fd.append('file', file); if (data.id) fd.append('id', data.id)
    return request<PictureVO>('/picture/upload/live', { method: 'POST', rawBody: fd })
  },
  download: (url: string) => requestBlob('/picture/download' + toQuery({ key: storageKey(url) })),
  delete: (id: string) => request<boolean>('/picture/delete', { method: 'POST', body: { id } }),
  update: (data: Partial<Picture>) => request<boolean>('/picture/update', { method: 'POST', body: data }),
  get: (id: string) => request<Picture>('/picture/get' + toQuery({ id })),
  getVO: (id: string) => request<PictureVO>('/picture/get/vo' + toQuery({ id })),
  listPage: (data: PageRequest) => request<Page<Picture>>('/picture/list/page', { method: 'POST', body: data }),
  listVOByPage: (data: PageRequest) => request<Page<PictureVO>>('/picture/list/page/vo', { method: 'POST', body: data }),
  listLiveVOByPage: (data: PageRequest) => request<Page<LivePictureVO>>('/picture/list/page/live/vo', { method: 'POST', body: data }),
  edit: (data: PictureEditRequest) => request<boolean>('/picture/edit', { method: 'POST', body: data }),
  createOutPaintingTask: (pictureId: string, parameters: OutPaintingParams) => request<OutPaintingTaskResponse>('/picture/out_painting/create_task', { method: 'POST', body: { pictureId, parameters } }),
  getOutPaintingTask: (taskId: string) => request<OutPaintingTaskResponse>('/picture/out_painting/get_task' + toQuery({ taskId })),
}

export function parsePictureTags(p: Picture | PictureVO): string[] {
  const raw: unknown = (p as any).tags
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string' && raw.trim()) { try { const v = JSON.parse(raw); return Array.isArray(v) ? v : [] } catch { return [] } }
  return []
}
export type { BaseResponse }
