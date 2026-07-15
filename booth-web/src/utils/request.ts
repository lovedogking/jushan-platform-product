import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

const TOKEN_KEY = 'jushan_access_token'

interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  traceId: string
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

/**
 * 判断请求是否针对登录接口。
 */
function isLoginRequest(config: AxiosRequestConfig): boolean {
  const url = config.url || ''
  return /\/auth\/login(?:\?.*)?$/.test(url.replace(/^\/api/, ''))
}

/**
 * 统一错误对象。
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

service.interceptors.request.use(
  (config) => {
    NProgress.start()
    // 登录请求不携带 Token
    if (!isLoginRequest(config)) {
      const token = localStorage.getItem(TOKEN_KEY)
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
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

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    NProgress.done()
    const { data } = response
    if (data.code === 200) return data.data

    // 业务层 code=401：清本地状态，跳转一次
    if (data.code === 401) {
      if (!isRedirectingLogin) {
        isRedirectingLogin = true
        localStorage.removeItem(TOKEN_KEY)
        window.location.href = '/login'
      }
      const err = new ApiError(data.message || '未授权', data.code, response.status, data.traceId)
      return Promise.reject(err)
    }

    message.error(data.message || '请求失败')
    const err = new ApiError(data.message || '请求失败', data.code, response.status, data.traceId)
    return Promise.reject(err)
  },
  (error) => {
    NProgress.done()
    const { response, config } = error
    if (response) {
      const traceId: string | undefined = response.data?.traceId
      const serverCode: number | undefined = response.data?.code
      const serverMessage: string | undefined = response.data?.message
      const httpStatus: number = response.status

      const isLogin = isLoginRequest(config || {})

      switch (httpStatus) {
        case 401:
          if (isLogin) {
            return Promise.reject(
              new ApiError(serverMessage || '账号或密码错误', serverCode, httpStatus, traceId),
            )
          }
          if (!isRedirectingLogin) {
            isRedirectingLogin = true
            message.error('登录已过期，请重新登录')
            localStorage.removeItem(TOKEN_KEY)
            window.location.href = '/login'
          }
          break
        case 403:
          message.error(serverMessage || '没有权限访问')
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

/**
 * 提取操作描述（用于离线队列 UI 展示）。
 */
function extractOpLabel(config: AxiosRequestConfig): string {
  const url = config.url || ''
  if (url.includes('/alerts/') && url.includes('/ack')) return '确认异常提醒'
  if (url.includes('/devices/refresh')) return '刷新设备状态'
  if (url.includes('/auth/logout')) return '退出登录'
  const method = (config.method || 'GET').toUpperCase()
  if (method === 'POST') return '数据提交'
  if (method === 'PUT') return '数据更新'
  if (method === 'DELETE') return '数据删除'
  return 'API 请求'
}

/**
 * 将请求加入离线队列。
 */
async function queueOffline(config: AxiosRequestConfig): Promise<any> {
  const { offlineQueue } = await import('./offline-queue')

  const url = config.url || ''
  const method = (config.method || 'GET').toUpperCase() as 'POST' | 'PUT' | 'DELETE'

  const opType = method === 'DELETE' ? 'EXCEPTION_HANDLE' : 'MANUAL_RELEASE'

  const id = await offlineQueue.enqueue({
    type: opType,
    url,
    method,
    body: config.data ? JSON.stringify(config.data) : undefined,
    params: config.params ? JSON.stringify(config.params) : undefined,
    label: extractOpLabel(config),
  })

  message.info(`网络离线，操作已暂存，恢复网络后自动提交`)

  return { queued: true, offlineOpId: id }
}

/**
 * 判断是否为写操作且当前离线。
 */
function shouldQueueOffline(config: AxiosRequestConfig): boolean {
  const method = (config.method || 'GET').toUpperCase()
  if (method === 'GET') return false
  if (isLoginRequest(config)) return false
  return !navigator.onLine
}

const request = {
  get<T = any>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, { params, ...config })
  },
  async post<T = any>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
    const mergedConfig = { ...config, url, method: 'POST', data }
    if (shouldQueueOffline(mergedConfig)) {
      return queueOffline(mergedConfig) as Promise<T>
    }
    return service.post(url, data, config)
  },
  async put<T = any>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
    const mergedConfig = { ...config, url, method: 'PUT', data }
    if (shouldQueueOffline(mergedConfig)) {
      return queueOffline(mergedConfig) as Promise<T>
    }
    return service.put(url, data, config)
  },
  async delete<T = any>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    const mergedConfig = { ...config, url, method: 'DELETE', params }
    if (shouldQueueOffline(mergedConfig)) {
      return queueOffline(mergedConfig) as Promise<T>
    }
    return service.delete(url, { params, ...config })
  },
}

export { ApiError }
export default request
