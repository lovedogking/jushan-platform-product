import request from '@/utils/request'

export interface RegisterParams {
  companyName: string
  contactPerson: string
  contactPhone: string
  password: string
}

/** 客户自助注册（公开接口，无需登录） */
export function register(data: RegisterParams): Promise<void> {
  return request.post('/v1/register', data)
}
