import { request, toQuery } from './request'
import type { LoginUserVO, Page, PageRequest, User, UserVO } from './types'

export interface LoginRequest { userAccount: string; userPassword: string }
export interface RegisterRequest extends LoginRequest { checkPassword: string }
export const userApi = {
  login: (data: LoginRequest) => request<LoginUserVO>('/user/login', { method: 'POST', body: data }),
  register: (data: RegisterRequest) => request<string>('/user/register', { method: 'POST', body: data }),
  getLogin: () => request<LoginUserVO>('/user/get/login'),
  logout: () => request<boolean>('/user/logout', { method: 'POST' }),
  getVO: (id: string) => request<UserVO>('/user/get/vo' + toQuery({ id })),
  add: (data: Partial<User>) => request<string>('/user/add', { method: 'POST', body: data }),
  update: (data: Partial<User>) => request<boolean>('/user/update', { method: 'POST', body: data }),
  delete: (id: string) => request<boolean>('/user/delete', { method: 'POST', body: { id } }),
  listVOByPage: (data: PageRequest) => request<Page<UserVO>>('/user/list/page/vo', { method: 'POST', body: data }),
  getById: (id: string) => request<User>('/user/get' + toQuery({ id })),
}
