// ========== 从 frontend/src/api/monitor-types.ts 复制 ==========

/** 在场车辆记录 */
export interface ParkingSessionVO {
  id: number
  parkingLotId: number
  laneId: number
  plateNumber: string
  plateColor?: string
  vehicleType: string
  entryTime: string
  entryImage?: string
  exitTime?: string
  exitLaneId?: number
  exitImage?: string
  entryLaneName?: string
  exitLaneName?: string
  status: string
  feeAmount: number
  feeCents: number
  paidAmount: number
  durationMinutes: number
  entryTrigger?: string
  entryOperator?: number
  createdAt: string
  updatedAt: string
}

// ========== H5 专用类型 ==========

/** H5 查费结果（单条记录，来自 GET /api/v1/h5/fee/query） */
export interface H5FeeVO {
  /** 订单 ID（复用已有 PENDING_PAY 订单或新建） */
  orderId: number
  /** 停车记录 ID */
  recordId: number
  /** 停车场 ID */
  parkingLotId: number
  /** 车场名称 */
  parkName: string
  /** 标准化车牌号 */
  plate: string
  /** 入场时间 */
  entryTime: string
  /** 停车时长（分钟） */
  durationMinutes: number
  /** 费用（分） */
  feeCents: number
  /** 费用（元），2 位小数 */
  feeYuan: string
  /** 是否可支付 */
  payable: boolean
}

/** H5 支付结果（来自 POST /api/v1/h5/pay/prepare 和 GET /api/v1/h5/pay/query） */
export interface H5PayResultVO {
  /** 订单 ID */
  orderId: number
  /** 订单号 */
  orderNo: string
  /** 车牌号 */
  plate: string
  /** 车场名称 */
  parkName: string
  /** 应付金额（分） */
  payableAmount: number
  /** 应付金额（元） */
  payableAmountYuan: string
  /** 订单状态 */
  status: string
  /** 是否模拟支付模式 */
  mock: boolean
  /** 实付金额（分），仅 query 返回 */
  paidAmount?: number
  /** 支付时间，仅 query 返回 */
  paidTime?: string
}
