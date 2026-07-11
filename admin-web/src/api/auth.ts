import request from '@/utils/request'
import type { LoginParams, LoginResult, UserInfo } from '@/types'

/** 登录 */
export function login(data: LoginParams): Promise<LoginResult> {
  return request.post('/auth/login', data)
}

/** 退出登录 */
export function logout(): Promise<void> {
  return request.post('/auth/logout')
}

/** 获取当前会话用户信息 */
export function getUserInfo(): Promise<UserInfo> {
  return request.get('/auth/session')
}
