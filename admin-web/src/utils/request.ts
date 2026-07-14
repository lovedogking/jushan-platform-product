import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import type { ApiResponse } from '@/types'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

// 创建 axios 实例
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * 判断请求是否针对登录接口。
 * 兼容相对 URL、带 /api 前缀、带查询参数的 URL。
 */
function isLoginRequest(config: AxiosRequestConfig): boolean {
  const url = config.url || ''
  // 匹配 /auth/login（可选 /api 前缀、可选查询参数）
  return /\/auth\/login(?:\?.*)?$/.test(url.replace(/^\/api/, ''))
}

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    NProgress.start()
    // 登录请求不携带 Token
    if (!isLoginRequest(config)) {
      const authStore = useAuthStore()
      if (authStore.token) {
        config.headers.Authorization = `Bearer ${authStore.token}`
      }
    }
    return config
  },
  (error) => {
    NProgress.done()
    return Promise.reject(error)
  },
)

// 防止并发 401 重复跳转
let isRedirectingLogin = false

/**
 * 统一错误对象，保留 message/code/status/traceId。
 */
class ApiError extends Error {
  code?: number
  status?: number
  traceId?: string

  constructor(message: string, code?: number, status?: number, traceId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.traceId = traceId
  }
}

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    NProgress.done()
    const { data, config } = response
    const silentError = (config as AxiosRequestConfig & { silentError?: boolean }).silentError
    // 业务成功码为 200（与后端 CommonErrorCode.SUCCESS 对齐）
    if (data.code === 200) {
      return data.data
    }
    // 业务错误（保留 traceId 供上层使用）
    const err = new ApiError(
      data.message || '请求失败',
      data.code,
      response.status,
      data.traceId,
    )
    // 防御性：业务层 code=401（后端修复后不应再通过 HTTP 200 返回）
    // 直接清理本地状态，不调用远程 logout
    if (data.code === 401) {
      const authStore = useAuthStore()
      authStore.clearLocalAuth()
      if (!isRedirectingLogin) {
        isRedirectingLogin = true
        router.push('/login').finally(() => { isRedirectingLogin = false })
      }
      return Promise.reject(err)
    }
    if (!silentError) {
      message.error(data.message || '请求失败')
    }
    return Promise.reject(err)
  },
  (error) => {
    NProgress.done()
    if ((error.config as AxiosRequestConfig & { silentError?: boolean } | undefined)?.silentError) {
      return Promise.reject(error)
    }
    const { response, config } = error
    if (response) {
      const traceId: string | undefined = response.data?.traceId
      const serverCode: number | undefined = response.data?.code
      const serverMessage: string | undefined = response.data?.message
      const httpStatus: number = response.status

      // 判断是否是登录请求，登录 401 不触发跳转
      const isLogin = isLoginRequest(config || {})

      switch (httpStatus) {
        case 401:
          if (isLogin) {
            // 登录接口 401：不调用 logout、不清除表单、不跳转
            // 正常传递错误给登录页处理
            return Promise.reject(
              new ApiError(serverMessage || '账号或密码错误', serverCode, httpStatus, traceId),
            )
          }
          // 普通接口 401：只清理本地状态，不调用远程 logout
          if (!isRedirectingLogin) {
            isRedirectingLogin = true
            message.error('登录已过期，请重新登录')
            const authStore = useAuthStore()
            authStore.clearLocalAuth()
            router.push('/login').finally(() => { isRedirectingLogin = false })
          }
          break
        case 403:
          // 403：不清 Token、不跳登录
          message.error(serverMessage || '没有权限访问')
          break
        case 404:
          message.error(serverMessage || '请求资源不存在')
          break
        case 500: {
          const errorMessage = serverMessage || '服务器内部错误'
          message.error(traceId ? `${errorMessage}（traceId: ${traceId}）` : errorMessage)
          break
        }
        default:
          message.error(serverMessage || '请求失败')
      }
      return Promise.reject(
        new ApiError(serverMessage || '请求失败', serverCode, httpStatus, traceId),
      )
    } else {
      message.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  },
)

// 封装请求方法
const request = {
  get<T = any>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, { params, ...config })
  },
  post<T = any>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config)
  },
  put<T = any>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config)
  },
  delete<T = any>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, { params, ...config })
  },
}

export { ApiError }
export default request
