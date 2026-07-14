import request from '@/utils/request'

/** 费用计算结果 */
export interface FeeCalculateResultVO {
  lotId: number
  zoneId: number | null
  plateNumber: string
  vehicleType: string
  parkingDuration: number
  freeMinutes: number
  billingDuration: number
  originalAmount: string
  discountAmount: string
  payableAmount: string
  feeRuleId: number
  feeRuleName: string
  breakdown: {
    itemName: string
    duration: number
    unitCount: number
    amount: string
  }[]
}

/** 车辆类型选项 */
export const VEHICLE_TYPE_OPTIONS = [
  { label: '临时车', value: 'TEMP' },
  { label: '月卡车', value: 'MONTH' },
  { label: '储值车', value: 'WALLET' },
  { label: '免费车', value: 'FREE' },
  { label: 'VIP车', value: 'VIP' },
  { label: '超级车牌', value: 'SUPER' },
  { label: '访客车', value: 'VISITOR' },
]

/** 费用试算 */
export function calculateFee(data: {
  lotId: number
  zoneId?: number
  plateNumber: string
  vehicleType: string
  entryTime: string
  exitTime: string
}) {
  return request.post<FeeCalculateResultVO>('/api/v1/fee/calculate', data)
}
