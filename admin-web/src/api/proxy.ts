import request from '@/utils/request'

/** 代理状态 */
export interface ProxyStatus {
  active: boolean
  targetTenantId: number | null
  targetTenantName: string | null
  reason: string | null
  startedAt: string | null
}

/** 启动代理 */
export function startProxy(tenantId: number, reason: string) {
  return request.post('/admin/proxy/start', { tenantId, reason })
}

/** 停止代理 */
export function stopProxy() {
  return request.post('/admin/proxy/stop')
}

/** 获取代理状态 */
export function getProxyStatus() {
  return request.get<ProxyStatus>('/admin/proxy/status')
}
