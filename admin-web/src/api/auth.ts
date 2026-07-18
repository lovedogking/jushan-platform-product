import request from '@/utils/request'
import type { LoginParams, LoginResult, UserInfo } from '@/types'

/** 登录 */
export function login(data: LoginParams): Promise<LoginResult> {
  return request.post('/v1/auth/login', data)
}

/** 刷新 Token */
export function refreshToken(refreshToken: string): Promise<LoginResult> {
  return request.post('/v1/auth/refresh', { refreshToken })
}

/** 退出登录 */
export function logout(): Promise<void> {
  return request.post('/v1/auth/logout')
}

/** 获取当前会话用户信息 */
export function getUserInfo(): Promise<UserInfo> {
  return request.get('/v1/auth/userinfo')
}

/** 修改密码（首次登录强制改密） */
export function changePassword(data: { oldPassword: string; newPassword: string }): Promise<void> {
  return request.post('/v1/auth/change-password', data)
}
