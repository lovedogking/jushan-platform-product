import request from '@/utils/request'

/** 车场级参数视图（对应后端 ParkingLotParamVO） */
export interface ParkingLotParamVO {
  /** 参数键 */
  key: string
  /** 参数说明 */
  description?: string
  /** 参数分组 */
  groupName: string
  /** 值类型：STRING / INT / BOOLEAN / ENUM */
  valueType: string
  /** ENUM 可选值，JSON 数组字符串 */
  options?: string
  /** 当前车场生效值（覆盖值或继承值） */
  value?: string
  /** 继承值（全局值 → 默认值），未覆盖时用于展示 */
  inheritedValue?: string
  /** 是否存在车场级覆盖（true=已覆盖，false=继承） */
  overridden: boolean
  /** 生效来源：LOT / GLOBAL / DEFAULT */
  source: string
}

/** 查询某车场的 7 项车场级参数（含继承信息） */
export function getLotParams(lotId: number) {
  return request.get<ParkingLotParamVO[]>(`/admin/parking-lots/${lotId}/params`)
}

/** 设置某车场的参数覆盖值（返回刷新后的全部参数） */
export function setLotParam(lotId: number, key: string, value: string) {
  return request.put<ParkingLotParamVO[]>(
    `/admin/parking-lots/${lotId}/params/${encodeURIComponent(key)}`,
    { value },
  )
}

/** 重置某车场的参数为"继承全局/默认值"（返回刷新后的全部参数） */
export function resetLotParam(lotId: number, key: string) {
  return request.delete<ParkingLotParamVO[]>(
    `/admin/parking-lots/${lotId}/params/${encodeURIComponent(key)}`,
  )
}
