import { request } from './request'
import type { SpaceRole, SpaceUserVO } from './types'
export const spaceUserApi = {
  add: (data: { spaceId: string; userId: string; spaceRole: SpaceRole }) => request<string>('/spaceUser/add', { method: 'POST', body: data }),
  remove: (id: string) => request<boolean>('/spaceUser/delete', { method: 'POST', body: { id } }),
  get: (data: { spaceId: string; userId: string }) => request<SpaceUserVO>('/spaceUser/get', { method: 'POST', body: data }),
  list: (spaceId: string) => request<SpaceUserVO[]>('/spaceUser/list', { method: 'POST', body: { spaceId } }),
  edit: (data: { id: string; spaceRole: SpaceRole }) => request<boolean>('/spaceUser/edit', { method: 'POST', body: data }),
  listMine: () => request<SpaceUserVO[]>('/spaceUser/list/my', { method: 'POST', body: {} }),
}
