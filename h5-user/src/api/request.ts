import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { showToast } from 'vant'

const TOKEN_KEY = 'jushan_h5_access_token'

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
    if (!isLoginRequest(config)) {
      const token = sessionStorage.getItem(TOKEN_KEY)
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  },
)

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { data } = response
    if (data.code === 200) return data.data

    // H5 端业务错误 Toast 提示
    showToast(data.message || '请求失败')

    const err = new ApiError(data.message || '请求失败', data.code, response.status, data.traceId)
    return Promise.reject(err)
  },
  (error) => {
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
          showToast('登录已过期，请重新登录')
          sessionStorage.removeItem(TOKEN_KEY)
          break
        case 403:
          showToast(serverMessage || '没有权限访问')
          break
        case 500: {
          const errorMessage = serverMessage || '服务器内部错误'
          showToast(traceId ? `${errorMessage}（traceId: ${traceId}）` : errorMessage)
          break
        }
        default:
          showToast(serverMessage || '请求失败')
      }
      return Promise.reject(
        new ApiError(serverMessage || '请求失败', serverCode, httpStatus, traceId),
      )
    } else {
      showToast('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  },
)

const request = {
  get<T = any>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, { params, ...config })
  },
  post<T = any>(url: string, data?: object, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config)
  },
}

export { ApiError }
export default request
