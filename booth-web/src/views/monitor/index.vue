<template>
  <div class="monitor-page">
    <!-- 顶部栏 -->
    <div class="page-header">
      <div class="header-left">
        <span class="page-title">实时监控面板</span>
        <a-tag :color="statusColor">{{ statusText }}</a-tag>
      </div>
      <div class="header-right">
        <a-select
          v-model:value="selectedLotId"
          placeholder="选择停车场"
          style="width: 200px"
          :disabled="wsClient?.status === 'connected'"
          :options="lotOptions"
          :loading="loadingLots"
          show-search
          :filter-option="filterLotOption"
          allow-clear
        />
        <a-button
          type="primary"
          :loading="store.loading"
          :disabled="!selectedLotId"
          @click="connectOrReconnect"
        >
          {{ wsClient?.status === 'connected' ? '重连' : '连接' }}
        </a-button>
        <a-button @click="refreshDevices">刷新设备</a-button>
        <a-button @click="openBatchRelease">批量开闸</a-button>
        <a-button @click="tempPlateDrawerOpen = true">无牌车处理</a-button>
        <a-badge :count="store.remoteGateAlerts.length" :overflow-count="99">
          <a-button @click="historyDrawerOpen = true">
            <template #icon><BellOutlined /></template>
            历史通知
          </a-button>
        </a-badge>
      </div>
    </div>

    <!-- 严重异常横幅 -->
    <a-alert
      v-if="store.criticalAlerts.length > 0"
      type="error"
      :message="`严重异常 (${store.criticalAlerts.length})`"
      :description="criticalAlertMessages"
      show-icon
      banner
      closable
      class="critical-banner"
    />

    <!-- 主体内容 -->
    <a-row :gutter="[16, 16]" class="monitor-body">
      <!-- 左侧：车位与车道 -->
      <a-col :xs="24" :lg="16">
        <!-- 车位大卡 -->
        <a-card :bordered="false" class="space-card">
          <div class="space-grid">
            <div class="space-item">
              <div class="space-value remaining">{{ parkingLot?.remainingSpaces ?? '-' }}</div>
              <div class="space-label">剩余车位</div>
            </div>
            <div class="space-item">
              <div class="space-value">{{ parkingLot?.currentVehicles ?? '-' }}</div>
              <div class="space-label">在场车辆</div>
            </div>
            <div class="space-item">
              <div class="space-value">{{ parkingLot?.totalSpaces ?? '-' }}</div>
              <div class="space-label">总车位</div>
            </div>
          </div>
        </a-card>

        <!-- 车道网格 -->
        <a-spin :spinning="store.loading">
          <div class="lane-grid">
            <a-card
              v-for="lane in laneCards"
              :key="lane.laneId"
              :bordered="false"
              class="lane-card"
              :class="{
                'lane-offline': lane.isOffline,
                'lane-charging': lane.charging,
                'lane-primary-offline': lane.primaryOffline,
              }"
            >
              <div class="lane-header">
                <span class="lane-name">
                  {{ lane.laneName }}
                  <span v-if="lane.primaryOffline" style="color: #f59e0b; margin-left: 6px;">
                    <ExclamationCircleOutlined />
                    <span style="font-size: 12px; margin-left: 2px;">主相机离线</span>
                  </span>
                </span>
                <div class="lane-tags">
                  <a-tag v-if="lane.charging" color="processing">
                    <SyncOutlined :spin="true" style="margin-right: 2px" />收费中
                  </a-tag>
                  <template v-if="!lane.cameras || lane.cameras.length === 0">
                    <a-tag :color="lane.deviceOnline ? 'success' : 'error'">
                      {{ lane.deviceOnline ? '在线' : '离线' }}
                    </a-tag>
                  </template>
                  <template v-else>
                    <a-tag
                      v-for="cam in lane.cameras"
                      :key="cam.deviceId"
                      :color="cam.online ? (cam.isActive ? 'blue' : 'green') : 'error'"
                    >
                      {{ cam.role === 'PRIMARY' ? '主' : '备' }}:{{ cam.direction === 'ENTRY' ? '入' : '出' }}
                    </a-tag>
                  </template>
                </div>
              </div>
              <div class="lane-direction">
                <a-tag :color="lane.direction === 'EXIT' ? 'orange' : 'blue'">
                  {{ lane.direction === 'ENTRY' ? '入口' : lane.direction === 'EXIT' ? '出口' : '混合' }}
                </a-tag>
              </div>
              <div class="lane-event">
                <div v-if="lane.latestEvent" class="event-plate">
                  {{ lane.latestEvent.plateNumber }}
                </div>
                <div v-else class="event-empty">暂无事件</div>
                <div v-if="lane.latestEvent" class="event-time">
                  {{ store.formatTime(lane.latestEvent.eventTime) }}
                </div>
              </div>
              <div class="lane-actions">
                <a-space>
                  <a-button
                    type="primary"
                    size="small"
                    @click="handleManualOpenGate(lane.laneId)"
                  >
                    开闸
                  </a-button>
                  <a-button
                    size="small"
                    @click="handleManualCloseGate(lane.laneId)"
                  >
                    关闸
                  </a-button>
                  <a-button
                    size="small"
                    @click="handleEditFeeRule(lane.laneId)"
                  >
                    修改收费
                  </a-button>
                </a-space>
              </div>
            </a-card>
          </div>
        </a-spin>
      </a-col>

      <!-- 右侧：事件与异常 -->
      <a-col :xs="24" :lg="8">
        <!-- 最近识别事件 -->
        <a-card title="最近识别事件" :bordered="false" class="right-card">
          <a-list
            :data-source="store.recentEvents"
            :locale="{ emptyText: '暂无识别事件' }"
            size="small"
          >
            <template #renderItem="{ item }">
              <a-list-item
                class="event-list-item"
                :class="{ 'event-exit-unpaid': item.direction === 'EXIT' && !item.paymentStatus }"
                @click="handleEventClick(item)"
              >
                <div class="event-row">
                  <div class="event-main">
                    <span class="event-plate-text">{{ item.plateNumber || '-' }}</span>
                    <a-tag size="small" :color="item.direction === 'EXIT' ? 'orange' : 'blue'">
                      {{ item.direction === 'ENTRY' ? '入' : '出' }}
                    </a-tag>
                    <a-tag size="small" :color="sourceColor(item.source)">
                      {{ item.source }}
                    </a-tag>
                    <!-- 支付状态标记 -->
                    <a-tag
                      v-if="item.direction === 'EXIT' && item.paymentStatus"
                      size="small"
                      :color="item.paymentStatus === 'PAID' ? 'success' : 'warning'"
                    >
                      {{ item.paymentStatus === 'PAID' ? '已支付' : '待支付' }}
                    </a-tag>
                  </div>
                  <div class="event-sub">
                    <span>{{ item.laneName || '未知车道' }}</span>
                    <span class="event-time-text">{{ store.formatTime(item.eventTime) }}</span>
                  </div>
                  <!-- 费用信息 -->
                  <div v-if="item.feeAmount != null && item.feeAmount > 0" class="event-fee">
                    应收: ¥{{ item.feeAmount.toFixed(2) }}
                  </div>
                </div>
              </a-list-item>
            </template>
          </a-list>
        </a-card>

        <!-- 异常提醒 -->
        <a-card title="异常提醒" :bordered="false" class="right-card">
          <a-list
            :data-source="store.alerts"
            :locale="{ emptyText: '暂无异常提醒' }"
            size="small"
          >
            <template #renderItem="{ item }">
              <a-list-item class="alert-list-item">
                <div class="alert-row">
                  <a-tag :color="item.severity === 'CRITICAL' ? 'error' : 'warning'">
                    {{ item.severity === 'CRITICAL' ? '严重' : '警告' }}
                  </a-tag>
                  <span class="alert-message">{{ item.message }}</span>
                  <a-button type="link" size="small" @click="store.ackAlert(item.id)">
                    确认
                  </a-button>
                </div>
                <div class="alert-time">{{ store.formatTime(item.createdAt) }}</div>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>
    </a-row>

    <!-- 收费面板 -->
    <ChargePanel />

    <!-- 人工放行弹窗（单通道） -->
    <ManualReleaseModal
      v-model:open="manualReleaseOpen"
      :lane-id="manualReleaseLaneId"
      plate-number=""
      @success="handleManualReleaseResult"
    />

    <!-- 批量开闸弹窗（Phase 2 D5） -->
    <ManualReleaseModal
      v-model:open="batchReleaseOpen"
      :lane-id="0"
      plate-number=""
      :batch="true"
      :batch-lanes="batchLaneOptions"
      @success="handleBatchReleaseResult"
    />

    <!-- 收费规则编辑弹窗 -->
    <FeeRuleEditModal
      v-model:open="feeRuleEditOpen"
      :lane-id="feeRuleEditLaneId"
      :fee-rule="currentFeeRule"
      @save="handleSaveFeeRule"
    />

    <!-- 远程开闸弹窗 -->
    <a-modal
      v-model:open="remoteGateModalVisible"
      title="🚧 运营端远程开闸"
      :footer="null"
      width="420px"
      centered
      :closable="true"
      :mask-closable="true"
    >
      <a-result
        status="success"
        title="远程开闸通知"
      >
        <template #subTitle>
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="操作人">{{ currentRemoteGateAlert?.operatorName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="操作时间">{{ currentRemoteGateAlert?.operationTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="车场">{{ currentRemoteGateAlert?.parkingLotName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="通道">{{ currentRemoteGateAlert?.laneName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="原因">{{ currentRemoteGateAlert?.reason || '-' }}</a-descriptions-item>
          </a-descriptions>
        </template>
      </a-result>
    </a-modal>

    <!-- 远程开闸历史通知抽屉 -->
    <a-drawer
      v-model:open="historyDrawerOpen"
      title="远程开闸历史通知"
      placement="right"
      width="400px"
    >
      <template v-if="store.remoteGateAlerts.length > 0">
        <a-list :data-source="store.remoteGateAlerts" size="small">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta>
                <template #title>
                  <span>{{ item.operatorName }} @ {{ item.laneName }}</span>
                </template>
                <template #description>
                  <div>时间：{{ item.operationTime }}</div>
                  <div>原因：{{ item.reason }}</div>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </template>
      <template v-else>
        <a-empty description="暂无远程开闸记录" />
      </template>
    </a-drawer>
    <!-- 无牌车处理抽屉 -->
    <a-drawer
      v-model:open="tempPlateDrawerOpen"
      title="无牌车处理"
      placement="right"
      :width="400"
    >
      <div class="temp-plate-section">
        <a-form layout="vertical">
          <a-form-item label="临时车牌号">
            <a-input v-model:value="tempPlateSearch" placeholder="输入临时车牌号" :maxlength="20" />
          </a-form-item>
          <a-form-item label="出口车道">
            <a-select v-model:value="tempPlateExitLane" placeholder="选择出口车道" style="width: 100%">
              <a-select-option v-for="lane in exitLanes" :key="lane.laneId" :value="lane.laneId">
                {{ lane.laneName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="tempPlateExiting" block @click="handleTempPlateExit">
              匹配出场并计费
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </a-drawer>

    <!-- 识别失败告警弹窗 -->
    <TempPlateAlertModal
      :alert="recognitionFailedAlert"
      @close="recognitionFailedAlert = null"
      @confirmed="onTempPlateConfirmed"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { SyncOutlined, BellOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { useMonitorStore } from '@/stores/monitor'
import { MonitorWebSocketClient, type ConnectionStatus } from '@/utils/websocket'
import type { DeviceStatus, RecognitionEventPayload, SpaceUpdatePayload, AlertPayload, RecognitionEvent, RemoteGateAlertPayload, LaneCamera } from '@/api/monitor-types'
import ChargePanel from '@/components/ChargePanel.vue'
import ManualReleaseModal, { type BatchLaneOption } from '@/components/ManualReleaseModal.vue'
import FeeRuleEditModal from '@/components/FeeRuleEditModal.vue'
import TempPlateAlertModal, { type RecognitionFailedAlert } from '@/components/TempPlateAlertModal.vue'
import { getBoothParkingLots, type BoothParkingLot } from '@/api/parking-lot'

const TOKEN_KEY = 'jushan_access_token'
const LOT_ID_KEY = 'booth_selected_lot_id'

const store = useMonitorStore()
const selectedLotId = ref<number | null>(null)
let wsClient: MonitorWebSocketClient | null = null

// 人工放行
const manualReleaseOpen = ref(false)
const manualReleaseLaneId = ref(0)

// 收费规则编辑
const feeRuleEditOpen = ref(false)
const feeRuleEditLaneId = ref(0)
const currentFeeRule = ref<any>(null)

// 远程开闸弹窗（Phase 1 B2）
const remoteGateModalVisible = ref(false)
const currentRemoteGateAlert = ref<RemoteGateAlertPayload | null>(null)
let remoteGateDismissTimer: ReturnType<typeof setTimeout> | null = null

// 远程开闸历史通知抽屉
const historyDrawerOpen = ref(false)

// Phase 2 D5：批量开闸
const batchReleaseOpen = ref(false)
const batchLaneOptions = ref<BatchLaneOption[]>([])

// 无牌车处理
const tempPlateDrawerOpen = ref(false)
const tempPlateSearch = ref('')
const tempPlateExitLane = ref<number | null>(null)
const tempPlateExiting = ref(false)
const recognitionFailedAlert = ref<RecognitionFailedAlert | null>(null)

// Phase 2 D6：车场下拉选项
const lotOptions = ref<{ value: number; label: string }[]>([])
const loadingLots = ref(false)

/** 打开批量开闸弹窗 */
function openBatchRelease() {
  // 从 store 的 lanes 构建批量选项
  batchLaneOptions.value = store.lanes.map((lane) => ({
    id: lane.id,
    name: lane.name || `车道 ${lane.id}`,
    direction: lane.direction,
    deviceId: lane.deviceId,
    hasDevice: !!lane.deviceId,
  }))
  batchReleaseOpen.value = true
}

/** 批量开闸结果处理 */
function handleBatchReleaseResult(result: { success: boolean; message: string; gateOpened: boolean | null }) {
  if (result.success) {
    message.success(result.message)
  } else {
    message.warning(result.message || '批量开闸部分失败')
  }
}

/** 出口车道列表 */
const exitLanes = computed(() => {
  return store.lanes
    .filter((l: any) => l.direction === 'EXIT' || l.direction === 'MIXED')
    .map((l) => ({
      laneId: l.id,
      laneName: l.name || `车道 ${l.id}`,
    }))
})

/** 无牌车匹配出场并计费 */
async function handleTempPlateExit() {
  if (!tempPlateSearch.value.trim()) { message.warning('请输入临时车牌号'); return }
  if (!tempPlateExitLane.value) { message.warning('请选择出口车道'); return }
  if (!selectedLotId.value) { message.warning('请先选择停车场'); return }
  tempPlateExiting.value = true
  try {
    const res = await import('@/api/monitor').then(m => m.manualTempPlateExit({
      tempPlate: tempPlateSearch.value.trim(),
      parkingLotId: selectedLotId.value!,
      laneId: tempPlateExitLane.value!,
    }))
    message.success(`无牌车出场成功，费用：${(res.feeCents / 100).toFixed(2)} 元`)
    tempPlateSearch.value = ''
    tempPlateExitLane.value = null
    tempPlateDrawerOpen.value = false
  } catch (e: any) {
    message.error(e?.response?.data?.message || '出场失败')
  } finally {
    tempPlateExiting.value = false
  }
}

/** 无牌车入场确认回调 */
function onTempPlateConfirmed(recordId: number, tempPlate: string) {
  console.log('Temp plate confirmed:', recordId, tempPlate)
}

/** 加载车场列表 */
async function loadLotOptions() {
  loadingLots.value = true
  try {
    const lots = await getBoothParkingLots()
    lotOptions.value = (lots || []).map((lot: BoothParkingLot) => ({
      value: lot.id,
      label: lot.name,
    }))
  } catch {
    // 静默失败
  } finally {
    loadingLots.value = false
  }
}

function filterLotOption(input: string, option: { value: number; label: string } | undefined) {
  return option?.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

function handleManualOpenGate(laneId: number) {
  manualReleaseLaneId.value = laneId
  manualReleaseOpen.value = true
}

/** 直接关闸（无需选择原因） */
async function handleManualCloseGate(laneId: number) {
  try {
    const { manualCloseGate } = await import('@/api/charge')
    const result = await manualCloseGate(laneId, '岗亭手动关闸')
    if (result.gateDeviceAck) {
      message.success('关闸成功')
    } else {
      message.warning(result.gateResult || '关闸失败')
    }
  } catch (e: any) {
    message.error(e?.message || '关闸失败')
  }
}

/** 修改收费规则 */
async function handleEditFeeRule(laneId: number) {
  feeRuleEditLaneId.value = laneId
  feeRuleEditOpen.value = true
  currentFeeRule.value = null
  try {
    const { getCurrentFeeRule } = await import('@/api/charge')
    const lotId = store.currentLotId
    if (!lotId) {
      message.warning('请先连接停车场')
      return
    }
    // 查询当前生效规则（简化：按车场查询，不区分区域）
    const rule = await getCurrentFeeRule(lotId)
    currentFeeRule.value = rule
  } catch (e: any) {
    message.error(e?.message || '加载收费规则失败')
  }
}

/** 保存收费规则调整 */
async function handleSaveFeeRule(data: any) {
  try {
    const { updateFeeRule } = await import('@/api/charge')
    if (currentFeeRule.value?.id) {
      await updateFeeRule(currentFeeRule.value.id, data)
      message.success('收费规则已更新')
    }
    feeRuleEditOpen.value = false
  } catch (e: any) {
    message.error(e?.message || '更新收费规则失败')
  }
}

/** 显示远程开闸弹窗并启动自动消失计时器 */
function showRemoteGateAlert(payload: RemoteGateAlertPayload) {
  // 清除旧计时器
  if (remoteGateDismissTimer) {
    clearTimeout(remoteGateDismissTimer)
    remoteGateDismissTimer = null
  }

  currentRemoteGateAlert.value = payload
  remoteGateModalVisible.value = true

  // 自动消失
  const dismissMs = (payload.autoDismissSeconds || 10) * 1000
  remoteGateDismissTimer = setTimeout(() => {
    remoteGateModalVisible.value = false
    remoteGateDismissTimer = null
  }, dismissMs)
}

function handleManualReleaseResult(result: { success: boolean; message: string; gateOpened: boolean | null }) {
  manualReleaseOpen.value = false
  if (result.success) {
    message.success('开闸成功')
  } else {
    message.warning(result.message || '开闸失败')
  }
}

const parkingLot = computed(() => store.parkingLot)

const statusText = computed(() => {
  const map: Record<ConnectionStatus, string> = {
    connecting: '连接中',
    connected: '已连接',
    disconnected: '已断开',
    reconnecting: '重连中',
  }
  return map[store.connectionStatus] || store.connectionStatus
})

const statusColor = computed(() => {
  const map: Record<ConnectionStatus, string> = {
    connecting: 'processing',
    connected: 'success',
    disconnected: 'default',
    reconnecting: 'warning',
  }
  return map[store.connectionStatus] || 'default'
})

const criticalAlertMessages = computed(() => {
  return store.criticalAlerts.map((a) => a.message).join('；')
})

interface LaneCard {
  laneId: number
  laneName: string
  direction: string
  deviceId?: number
  deviceOnline: boolean
  isOffline: boolean
  /** 是否正在收费中 */
  charging: boolean
  latestEvent?: RecognitionEventPayload
  /** 多相机模式下的相机详情 */
  cameras?: LaneCamera[]
  /** 主相机是否离线（用于高亮告警） */
  primaryOffline: boolean
  /** 当前活跃相机描述文本 */
  activeSourceLabel?: string
}

const laneCards = computed((): LaneCard[] => {
  return store.lanes.map((lane) => {
    const cameras = lane.cameras || []
    const hasMultiCameras = cameras.length > 0

    // 单相机模式（兼容现有逻辑）
    if (!hasMultiCameras) {
      const device = lane.deviceId
        ? store.deviceStatuses.find((d) => d.deviceId === lane.deviceId)
        : undefined
      const latestEvent = store.recentEvents.find((e) => e.laneId === lane.id)

      // 判断当前车道是否处于收费中状态（收费面板打开且对应此车道）
      const charging =
        store.chargePanelVisible &&
        store.currentChargeInfo?.laneId === lane.id

      return {
        laneId: lane.id,
        laneName: lane.name || `车道 ${lane.id}`,
        direction: lane.direction,
        deviceId: lane.deviceId,
        deviceOnline: !!device?.online && !device?.stale,
        isOffline: !device || !device.online || device.stale,
        charging,
        latestEvent: latestEvent as RecognitionEventPayload | undefined,
        cameras: [],
        primaryOffline: false,
        activeSourceLabel: undefined,
      }
    }

    // 多相机模式：cameras 已由后端 BoothMonitorService.loadLanes 填充
    // 在线状态从后端 cameras[].online 直接使用（loadLanes 已查询 DeviceStatusVO 快照）
    const primaryCameras = cameras.filter(c => c.role === 'PRIMARY')
    const primaryOffline = primaryCameras.some(c => !c.online)
    const activeCamera = cameras.find(c => c.isActive)
    const activeSourceLabel = activeCamera
      ? `当前: ${activeCamera.role === 'PRIMARY' ? '主相机' : '备相机'}`
      : undefined

    return {
      laneId: lane.id,
      laneName: lane.name || `车道 ${lane.id}`,
      direction: lane.direction,
      deviceOnline: cameras.some(c => c.isActive && c.online),
      isOffline: cameras.every(c => !c.online),
      charging: store.chargePanelVisible && store.currentChargeInfo?.laneId === lane.id,
      latestEvent: store.recentEvents.find((e) => e.laneId === lane.id) as RecognitionEventPayload | undefined,
      cameras,
      primaryOffline,
      activeSourceLabel,
    }
  })
})

function sourceColor(source?: string) {
  if (source === 'DEVICE_ACCESS') return 'green'
  if (source === 'MOCK') return 'purple'
  if (source === 'MANUAL') return 'blue'
  return 'default'
}

/** 点击事件列表项：EXIT 事件打开收费面板 */
function handleEventClick(item: RecognitionEvent) {
  if (item.direction === 'EXIT') {
    store.showChargePanel(item.plateNumber, item.laneId).catch(() => {
      // 查询失败不阻塞
    })
  }
}

function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

function buildWsClient(lotId: number) {
  const token = getToken()
  if (!token) {
    message.error('登录已过期，请重新登录')
    return
  }

  wsClient?.disconnect()
  wsClient = new MonitorWebSocketClient({
    token,
    parkingLotId: lotId,
    handlers: {
      onConnectionChange: (status) => {
        store.setConnectionStatus(status)
      },
      onSpaceUpdate: (payload: SpaceUpdatePayload) => {
        store.handleSpaceUpdate(payload)
      },
      onRecognitionEvent: (payload: RecognitionEventPayload) => {
        store.handleRecognitionEvent(payload)
        message.info(`${payload.direction === 'ENTRY' ? '入场' : '出场'}识别: ${payload.plateNumber}`)

        // EXIT 出场事件自动弹出收费面板
        if (payload.direction === 'EXIT') {
          store.showChargePanel(payload.plateNumber, payload.laneId).catch(() => {
            // 查询失败时不阻塞，收费面板已显示（含错误提示）
          })
        }
      },
      onDeviceStatus: (payload: DeviceStatus) => {
        store.handleDeviceStatus(payload)
      },
      onAlert: (payload: any) => {
        if (payload.type === 'RECOGNITION_FAILED') {
          recognitionFailedAlert.value = {
            eventId: payload.eventId,
            logId: payload.logId,
            parkingLotId: payload.parkingLotId,
            laneId: payload.laneId,
            laneName: payload.laneName,
            direction: payload.direction,
            imagePath: payload.imagePath,
            eventTime: payload.eventTime,
            message: payload.message,
          }
        } else {
          store.handleAlert(payload)
        }
      },
      onRemoteGateAlert: (payload: RemoteGateAlertPayload) => {
        store.handleRemoteGateAlert(payload)
        showRemoteGateAlert(payload)
      },
      onError: (error) => {
        console.warn('WebSocket 错误:', error)
      },
    },
  })
  wsClient.connect()
}

async function connectOrReconnect() {
  if (!selectedLotId.value) {
    message.warning('请选择停车场')
    return
  }

  localStorage.setItem(LOT_ID_KEY, String(selectedLotId.value))

  try {
    await store.loadSnapshot(selectedLotId.value)
    buildWsClient(selectedLotId.value)
  } catch (e: any) {
    message.error(e?.message || '连接失败')
  }
}

async function refreshDevices() {
  if (!selectedLotId.value) {
    message.warning('请先选择停车场')
    return
  }
  try {
    await store.refreshAllDevices()
    message.success('设备状态已刷新')
  } catch (e: any) {
    message.error(e?.message || '刷新失败')
  }
}

onMounted(() => {
  loadLotOptions()
  const saved = localStorage.getItem(LOT_ID_KEY)
  if (saved) {
    selectedLotId.value = Number(saved)
    connectOrReconnect()
  }
})

onUnmounted(() => {
  wsClient?.disconnect()
  if (remoteGateDismissTimer) {
    clearTimeout(remoteGateDismissTimer)
    remoteGateDismissTimer = null
  }
})
</script>

<style lang="scss" scoped>
.monitor-page {
  padding: 16px;
  min-height: 100vh;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
  }
}

.critical-banner {
  margin-bottom: 16px;
}

.space-card {
  margin-bottom: 16px;

  .space-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    text-align: center;
  }

  .space-item {
    padding: 16px;
  }

  .space-value {
    font-size: 36px;
    font-weight: 700;
    color: #1f2937;

    &.remaining {
      color: #10b981;
    }
  }

  .space-label {
    margin-top: 8px;
    color: #6b7280;
    font-size: 14px;
  }
}

.lane-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.lane-card {
  .lane-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  .lane-name {
    font-weight: 600;
    font-size: 15px;
  }

  .lane-direction {
    margin-bottom: 12px;
  }

  .lane-actions {
    margin-top: 8px;
    text-align: right;
  }

  .lane-event {
    text-align: center;
    padding: 12px 0;
    background: #f9fafb;
    border-radius: 6px;

    .event-plate {
      font-size: 22px;
      font-weight: 700;
      color: #111827;
    }

    .event-empty {
      color: #9ca3af;
    }

    .event-time {
      margin-top: 4px;
      font-size: 12px;
      color: #6b7280;
    }
  }

  &.lane-offline {
    border: 1px solid #fca5a5;
  }

  &.lane-charging {
    border: 2px solid $primary-color;
    box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.15);
  }

  &.lane-primary-offline {
    border: 2px solid #f59e0b;
    background-color: rgba(245, 158, 11, 0.04);
  }

  .lane-tags {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.right-card {
  margin-bottom: 16px;
  max-height: 420px;
  overflow: auto;
}

.event-list-item {
  padding: 8px 0;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #f0f5ff;
  }

  &.event-exit-unpaid {
    border-left: 3px solid $warning-color;
    padding-left: 9px;
    background-color: rgba(245, 158, 11, 0.04);
  }
}

.event-row {
  width: 100%;
}

.event-main {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.event-plate-text {
  font-weight: 600;
  font-size: 15px;
}

.event-sub {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
}

.event-time-text {
  font-family: monospace;
}

.event-fee {
  margin-top: 2px;
  font-size: 12px;
  font-weight: 600;
  color: $error-color;
  text-align: right;
}

.alert-list-item {
  padding: 8px 0;
}

.alert-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.alert-message {
  flex: 1;
  font-size: 13px;
  color: #374151;
}

.alert-time {
  font-size: 12px;
  color: #9ca3af;
}
</style>
