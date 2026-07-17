import { ref } from 'vue'

export interface ApiResult {
  success: boolean
  message?: string
  code?: number
}

export function useApi(props: { deviceId: string; apiBase: string; getHeaders: () => Record<string, string> }) {
  const apiBase = () => props.apiBase.replace(/\/$/, '')

  async function apiPost(path: string, body: Record<string, any>): Promise<ApiResult> {
    try {
      const response = await fetch(`${apiBase()}/devices/${props.deviceId}${path}`, {
        method: 'POST',
        headers: props.getHeaders(),
        body: JSON.stringify(body),
      })
      const data = await response.json()
      if (data.code === 200 && data.data && data.data.success) {
        return { success: true, message: data.data.message || '操作成功', code: data.code }
      } else {
        return { success: false, message: data.message || data.data?.message || '操作失败', code: data.code }
      }
    } catch (error: any) {
      return { success: false, message: '网络错误: ' + (error.message || '无法连接到服务器') }
    }
  }

  async function lockGate(): Promise<ApiResult> {
    return apiPost('/gate/lock', {})
  }

  async function unlockGate(): Promise<ApiResult> {
    return apiPost('/gate/unlock', {})
  }

  async function apiGet(path: string): Promise<any> {
    try {
      const response = await fetch(`${apiBase()}/devices/${props.deviceId}${path}`, {
        method: 'GET',
        headers: props.getHeaders(),
      })
      return await response.json()
    } catch (error) {
      return null
    }
  }

  return { apiPost, apiGet, lockGate, unlockGate }
}
