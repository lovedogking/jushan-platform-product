import request from '@/utils/request'

/** 后端登录响应原始结构 */
interface LoginRawResponse {
  token: string
  refreshToken: string
  userInfo: {
    userId: string
    username: string
    realName: string
    level: number
    tenantId: number | null
    mustChangePassword: number
  }
  permissions: string[]
}

/** 前端统一使用的登录结果 */
export interface LoginResult {
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
}

/** 从 JWT payload 中解析 roles（不验证签名，仅提取数据） */
function parseRolesFromToken(token: string): string[] {
  try {
    const payload = token.split('.')[1]
    const json = JSON.parse(atob(payload))
    const roles: string[] = []

    // JWT 中的 roles 字段（可能是 JSON 字符串或数组）
    if (json.roles) {
      const parsed = typeof json.roles === 'string' ? JSON.parse(json.roles) : json.roles
      if (Array.isArray(parsed)) roles.push(...parsed)
    }

    // userType 映射为路由角色：platform → 超管路由, tenant → 租户路由
    if (json.userType === 'platform') {
      roles.push('platform')
    } else if (json.userType === 'tenant') {
      roles.push('tenant')
    }

    return roles
  } catch {
    return []
  }
}

/** 登录 */
export async function login(data: { username: string; password: string }): Promise<LoginResult> {
  const raw = await request.post<LoginRawResponse>('/v1/auth/login', data)

  const roles = parseRolesFromToken(raw.token)

  return {
    accessToken: raw.token,
    tokenType: 'Bearer',
    expiresInSeconds: 7200, // 2h，与后端 JWT 配置一致
    user: {
      userId: raw.userInfo.userId,
      username: raw.userInfo.username,
      displayName: raw.userInfo.realName,
      roles,
      permissions: raw.permissions || [],
    },
  }
}

/** 退出登录 */
export function logout(): Promise<void> {
  return request.post('/v1/auth/logout')
}

/** 获取当前会话用户信息 */
export function getSession(): Promise<{
  userId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}> {
  return request.get('/v1/auth/session')
}
