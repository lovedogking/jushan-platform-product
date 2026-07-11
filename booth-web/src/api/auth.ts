import request from '@/utils/request'

/** 登录 */
export function login(data: { username: string; password: string }) {
  return request.post<{
    accessToken: string
    tokenType: string
    expiresInSeconds: number
    user: {
      userId: string
      username: string
      displayName: string
      roles: string[]
      permissions: string[]
    }
  }>('/auth/login', data)
}

/** 退出登录 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}

/** 获取当前会话用户信息 */
export function getSession(): Promise<{
  userId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}> {
  return request.get('/auth/session')
}
