import request from '@/utils/request'

/** 远程开闸请求 */
export interface RemoteGateOpenRequest {
  parkingLotId: number
  laneId: number
  reason: string
}

/** 开闸命令执行结果 */
export interface CommandResult {
  success: boolean
  deviceCode: number
  message: string
}

/** 远程开闸 */
export function openRemoteGate(data: RemoteGateOpenRequest) {
  return request.post<CommandResult>('/v1/admin/remote-gate/open', data)
}
