// 统一响应格式（与后端 R<T> 对齐）
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  traceId: string
}

// 分页请求参数
export interface PageParams {
  pageNum?: number
  pageSize?: number
}

// 分页返回结果
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// 登录请求
export interface LoginParams {
  username: string
  password: string
}

// 登录返回（与后端 /api/auth/login 响应 data 对齐）
export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: LoginUser
}

// 登录用户
export interface LoginUser {
  userId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

// 当前会话用户（与后端 /api/auth/session 响应 data 对齐）
export interface UserInfo {
  userId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}
