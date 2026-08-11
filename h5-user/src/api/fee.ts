import request from './request'
import type { H5FeeVO } from './types'

/**
 * 免登录查费（按车牌查询在场记录和费用）。
 * POST /api/v1/h5/fee/query
 */
export function queryFeeByPlate(plate: string): Promise<H5FeeVO[]> {
  return request.post<H5FeeVO[]>('/v1/h5/fee/query', { plate })
}
