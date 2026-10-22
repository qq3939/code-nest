import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { Result, TokenVO } from '@/types'
import { tokenStore } from '@/utils/auth'

/** 业务错误：带后端的 code，调用方可以按 code 做分支（例如 10006 重复提交）。 */
export class ApiError extends Error {
  code: number
  status: number
  constructor(code: number, message: string, status: number) {
    super(message)
    this.code = code
    this.status = status
  }
}

// 这些错误码表示 access token 过期或缺失，可以用 refresh token 换一个新的再重试
const REFRESHABLE = new Set([10002, 20005])

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = tokenStore.access()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/**
 * 单飞刷新：多个请求同时遇到 401 时只发一次 /auth/refresh，其余请求等同一个 Promise。
 * 否则并发刷新会让旧 refresh token 被使用两次，后端轮换后第二次必然失败，用户被踢下线。
 */
let refreshing: Promise<string> | null = null

export function refreshAccessToken(): Promise<string> {
  if (!refreshing) {
    const refreshToken = tokenStore.refresh()
    refreshing = (async () => {
      if (!refreshToken) throw new ApiError(20006, '请重新登录', 401)
      // 用裸 axios，避免进入下面的拦截器形成递归
      const resp = await axios.post<Result<TokenVO>>('/api/auth/refresh', { refreshToken })
      if (resp.data.code !== 0) throw new ApiError(resp.data.code, resp.data.message, 401)
      tokenStore.save(resp.data.data)
      return resp.data.data.accessToken
    })().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

let onAuthExpired: () => void = () => {}
/** 由 main.ts 注入：刷新失败时清理登录状态并跳到登录页。 */
export function setAuthExpiredHandler(fn: () => void) {
  onAuthExpired = fn
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean; silent?: boolean }

http.interceptors.response.use(
  (resp) => {
    const body = resp.data as Result<unknown>
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
      return Promise.reject(new ApiError(body.code, body.message, resp.status))
    }
    return resp
  },
  async (error: AxiosError<Result<unknown>>) => {
    const config = error.config as RetriableConfig | undefined
    const body = error.response?.data
    const status = error.response?.status ?? 0
    const code = body?.code ?? (status === 0 ? -1 : status)
    if (status === 401 && config && !config._retried && REFRESHABLE.has(code) && tokenStore.refresh()) {
      config._retried = true
      try {
        const token = await refreshAccessToken()
        config.headers.Authorization = `Bearer ${token}`
        return http(config)
      } catch {
        tokenStore.clear()
        onAuthExpired()
        return Promise.reject(new ApiError(20006, '登录已过期，请重新登录', 401))
      }
    }
    if (status === 401 && code === 20006) {
      tokenStore.clear()
      onAuthExpired()
    }
    const message = body?.message ?? (status === 0 ? '网络异常，请检查后端是否启动' : `请求失败（${status}）`)
    const err = new ApiError(code, message, status)
    if (!config?.silent) {
      ElMessage.error(status === 429 ? '操作太频繁，请稍后再试' : message)
    }
    return Promise.reject(err)
  },
)

export interface RequestOptions extends AxiosRequestConfig {
  /** 为 true 时出错不弹提示，由调用方自己处理。 */
  silent?: boolean
}

async function unwrap<T>(p: Promise<{ data: Result<T> }>): Promise<T> {
  const resp = await p
  return resp.data.data
}

export const api = {
  get: <T>(url: string, params?: object, opts?: RequestOptions) => unwrap<T>(http.get(url, { ...opts, params })),
  post: <T>(url: string, data?: unknown, opts?: RequestOptions) => unwrap<T>(http.post(url, data, opts)),
  put: <T>(url: string, data?: unknown, opts?: RequestOptions) => unwrap<T>(http.put(url, data, opts)),
  del: <T>(url: string, opts?: RequestOptions) => unwrap<T>(http.delete(url, opts)),
}

export default http
