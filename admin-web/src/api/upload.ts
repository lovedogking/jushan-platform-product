import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

/**
 * 上传图片文件，返回可访问的 URL。
 * 使用独立的 axios 调用（multipart/form-data）。
 */
export async function uploadFile(file: File): Promise<{ url: string }> {
  const formData = new FormData()
  formData.append('file', file)

  const authStore = useAuthStore()
  const resp = await axios.post('/api/v1/admin/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      Authorization: `Bearer ${authStore.token}`,
    },
  })

  if (resp.data.code === 200) {
    return resp.data.data as { url: string }
  }
  throw new Error(resp.data.message || '上传失败')
}
