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

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    NProgress.start()
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => {
    NProgress.done()
    return Promise.reject(error)
  }
)

// 防止并发 401 重复跳转
let isRedirectingLogin = false

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    NProgress.done()
    const { data } = response
    const silentError = (response.config as AxiosRequestConfig & { silentError?: boolean }).silentError
    // 业务成功码为 0
    if (data.code === 0) {
      return data.data
    }
    // 业务层未授权码 401 → 清除 token + 跳转登录页
    if (data.code === 401) {
      const authStore = useAuthStore()
      authStore.logout()
      const err = new Error(data.message || '未授权') as any
      err.traceId = data.traceId
      return Promise.reject(err)
    }
    // 业务错误（保留 traceId 供上层使用）
    if (!silentError) {
      message.error(data.message || '请求失败')
    }
    const err = new Error(data.message || '请求失败') as any
    err.traceId = data.traceId
    return Promise.reject(err)
  },
  (error) => {
    NProgress.done()
    if ((error.config as AxiosRequestConfig & { silentError?: boolean } | undefined)?.silentError) {
      return Promise.reject(error)
    }
    const { response } = error
    if (response) {
      const traceId = response.data?.traceId
      switch (response.status) {
        case 401:
          if (!isRedirectingLogin) {
            isRedirectingLogin = true
            message.error('登录已过期，请重新登录')
            const authStore = useAuthStore()
            authStore.logout()
            router.push('/login').finally(() => { isRedirectingLogin = false })
          }
          break
        case 403:
          message.error('没有权限访问')
          break
        case 404:
          message.error('请求资源不存在')
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

export default request
