// 统一响应格式
export interface ApiResponse<T = any> {
  code: number
  msg: string
  data: T
  timestamp?: number
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

// 登录返回
export interface LoginResult {
  token: string
  userId: number
  username: string
  nickname?: string
  avatar?: string
}

// 用户信息
export interface UserInfo {
  userId: number
  username: string
  nickname?: string
  avatar?: string
  phone?: string
  email?: string
  status?: number
  createTime?: string
}
