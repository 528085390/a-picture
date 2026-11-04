import { request, toQuery } from './request'
import type { Page, PageRequest, PictureVO, Space, SpaceVO, SpaceUserVO } from './types'

export const spaceApi = {
  add: (data: { spaceName: string; spaceLevel: number; spaceType: number }) => request<string>('/space/add', { method: 'POST', body: data }),
  edit: (data: { id: string; spaceName: string }) => request<boolean>('/space/edit', { method: 'POST', body: data }),
  delete: (id: string) => request<boolean>('/space/delete', { method: 'POST', body: { id } }),
  getVO: (id: string) => request<SpaceVO>('/space/get/vo' + toQuery({ id })),
  listVOByPage: (data: PageRequest) => request<Page<SpaceVO>>('/space/list/page/vo', { method: 'POST', body: data }),
  myListVOByPage: (data: PageRequest) => request<Page<SpaceVO>>('/space/my/list/page/vo', { method: 'POST', body: data }),
  adminUpdate: (data: Partial<Space>) => request<boolean>('/space/admin/update', { method: 'POST', body: data }),
  adminListByPage: (data: PageRequest) => request<Page<Space>>('/space/admin/list/page', { method: 'POST', body: data }),
}

export const spacePictureApi = {
  upload: (file: File, spaceId: string, id?: string, type?: string) => {
    const fd = new FormData(); fd.append('file', file); fd.append('spaceId', spaceId); if (id) fd.append('id', id); if (type) fd.append('type', type)
    return request<PictureVO>('/space/picture/upload', { method: 'POST', rawBody: fd })
  },
  uploadLive: (file: File, spaceId: string, id?: string) => {
    const fd = new FormData(); fd.append('file', file); fd.append('spaceId', spaceId); if (id) fd.append('id', id)
    return request<PictureVO>('/space/picture/upload/live', { method: 'POST', rawBody: fd })
  },
  edit: (data: { id: string; name?: string; introduction?: string; category?: string; tags?: string[] }) => request<boolean>('/space/picture/edit', { method: 'POST', body: data }),
  batchEdit: (data: { pictureIdList: string[]; spaceId: string; category?: string; tags?: string[]; nameRule?: string }) => request<boolean>('/space/picture/edit/batch', { method: 'POST', body: data }),
  delete: (id: string) => request<boolean>('/space/picture/delete', { method: 'POST', body: { id } }),
  getVO: (id: string) => request<PictureVO>('/space/picture/get/vo' + toQuery({ id })),
  listVOByPage: (data: PageRequest & { spaceId: string }) => request<Page<PictureVO>>('/space/picture/list/page/vo', { method: 'POST', body: data }),
  listLiveVOByPage: (data: PageRequest & { spaceId: string }) => request<Page<{ pictureVO: PictureVO; videoUrl?: string }>>('/space/picture/list/page/live/vo', { method: 'POST', body: data }),
}
export type { SpaceUserVO }
