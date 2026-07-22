import request from '@/utils/request'

/** 模拟识别事件触发请求（后端仅 local/test 环境注册该接口） */
export interface MockRecognitionRequest {
  /** 平台设备主键（相机设备） */
  deviceId: number
  /** 车牌号 */
  plateNumber: string
  /** 方向：ENTRY-入场, EXIT-出场 */
  direction: 'ENTRY' | 'EXIT'
  /** 识别置信度 0-100（可选） */
  confidence?: number
  /** 全景图 URL（可选，用于测试抓拍图展示链路） */
  imagePath?: string
}

/** 模拟识别事件触发响应 */
export interface MockRecognitionResult {
  eventId: string
  logId: number
  plateNumber: string
  direction: string
  source: string
  eventTime: string
}

/**
 * 触发模拟识别事件（岗亭端「手动抓拍」测试入口）。
 * 走与真实识别事件完全一致的 RecognitionEventService 业务管线：
 * 事件落库 + 入场/出场处理 + 余位刷新 + WebSocket 推送 + 自动开闸。
 * 生产环境后端不注册此接口（@Profile local/test），调用会 404。
 *
 * POST /api/v1/internal/mock/recognition-event
 */
export function triggerMockRecognition(data: MockRecognitionRequest) {
  return request.post<MockRecognitionResult>('/v1/internal/mock/recognition-event', data)
}
