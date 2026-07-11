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

service.interceptors.request.use(
  (config) => {
    NProgress.start()
    const token = localStorage.getItem(TOKEN_KEY)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => { NProgress.done(); return Promise.reject(error) }
)

// 防止并发 401 重复跳转
let isRedirectingLogin = false

service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    NProgress.done()
    const { data } = response
    // 业务成功码为 0
    if (data.code === 0) return data.data
    // 业务层 401 → 清除 token + 跳转登录
    if (data.code === 401) {
      if (!isRedirectingLogin) {
        isRedirectingLogin = true
        localStorage.removeItem(TOKEN_KEY)
        window.location.href = '/login'
      }
      const err = new Error(data.message || '未授权') as any
      err.traceId = data.traceId
      return Promise.reject(err)
    }
    message.error(data.message || '请求失败')
    const err = new Error(data.message || '请求失败') as any
    err.traceId = data.traceId
    return Promise.reject(err)
  },
  (error) => {
    NProgress.done()
    const { response } = error
    if (response) {
      const traceId = response.data?.traceId
      switch (response.status) {
        case 401:
          if (!isRedirectingLogin) {
            isRedirectingLogin = true
            message.error('登录已过期，请重新登录')
            localStorage.removeItem(TOKEN_KEY)
            window.location.href = '/login'
          }
          break
        case 403:
          message.error('没有权限访问')
          break
        case 500: {
          const serverMsg = response.data?.message || '服务器内部错误'
          message.error(traceId ? `${serverMsg}（traceId: ${traceId}）` : serverMsg)
          break
        }
        default:
          message.error(response.data?.message || '请求失败')
      }
    } else {
      message.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  }
)

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

export default request
