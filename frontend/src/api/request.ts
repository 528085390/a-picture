import type { BaseResponse } from './types'

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) { super(message); this.code = code; this.name = 'ApiError' }
}

type UnauthorizedHandler = () => void
let onUnauthorized: UnauthorizedHandler | null = null
export function setUnauthorizedHandler(fn: UnauthorizedHandler) { onUnauthorized = fn }

const LONG_ID_KEYS = /("(?:id|userId|spaceId|pictureId|taskId|picId|ownerId|createBy|updateBy)"\s*:\s*)(-?\d{16,})(?=\s*[,}])/g

/** Jackson may emit snowflake ids as JSON numbers; quote them before JSON.parse. */
function parseJsonSafely(text: string): unknown {
  if (!text) return null
  const safe = text.replace(LONG_ID_KEYS, '$1"$2"').replace(/"data"(\s*):(\s*)(-?\d{16,})(?=\s*[,}])/g, '"data"$1:$2"$3"')
  return JSON.parse(safe)
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  rawBody?: BodyInit
  silent?: boolean
}

export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers(options.headers)
  let body: BodyInit | undefined
  if (options.rawBody !== undefined) body = options.rawBody
  else if (options.body !== undefined) { headers.set('Content-Type', 'application/json'); body = JSON.stringify(options.body) }
  const res = await fetch('/api' + url, { ...options, headers, body, credentials: 'include' })
  const text = await res.text()
  let payload: BaseResponse<T> | null = null
  try { payload = parseJsonSafely(text) as BaseResponse<T> } catch { /* non-json */ }
  if (!res.ok) {
    const message = payload?.message || ('HTTP ' + res.status + ' · 服务暂时不可用')
    if (res.status === 401) onUnauthorized?.()
    throw new ApiError(payload?.code ?? res.status, message)
  }
  if (!payload) throw new ApiError(-1, '响应格式异常')
  if (payload.code !== 0) {
    if (payload.code === 40100) onUnauthorized?.()
    throw new ApiError(payload.code, payload.message || '请求失败')
  }
  return payload.data
}

export function toQuery(params: Record<string, unknown> | object): string {
  const q = new URLSearchParams()
  Object.entries(params as Record<string, unknown>).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v))
  })
  const s = q.toString()
  return s ? '?' + s : ''
}

export async function requestBlob(url: string): Promise<Blob> {
  const res = await fetch('/api' + url, { credentials: 'include' })
  if (!res.ok) throw new ApiError(res.status, '文件下载失败')
  return res.blob()
}
