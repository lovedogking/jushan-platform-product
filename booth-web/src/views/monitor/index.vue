<template>
  <div class="monitor-page">
    <!-- 顶部栏 -->
    <div class="page-header">
      <div class="header-left">
        <span class="page-title">实时监控面板</span>
        <a-tag :color="statusColor">{{ statusText }}</a-tag>
      </div>
      <div class="header-right">
        <a-input-number
          v-model:value="selectedLotId"
          :min="1"
          placeholder="停车场 ID"
          style="width: 160px"
          :disabled="wsClient?.status === 'connected'"
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
              }"
            >
              <div class="lane-header">
                <span class="lane-name">{{ lane.laneName }}</span>
                <div class="lane-tags">
                  <a-tag v-if="lane.charging" color="processing">
                    <SyncOutlined :spin="true" style="margin-right: 2px" />收费中
                  </a-tag>
                  <a-tag :color="lane.deviceOnline ? 'success' : 'error'">
                    {{ lane.deviceOnline ? '在线' : '离线' }}
                  </a-tag>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { SyncOutlined } from '@ant-design/icons-vue'
import { useMonitorStore } from '@/stores/monitor'
import { MonitorWebSocketClient, type ConnectionStatus } from '@/utils/websocket'
import type { DeviceStatus, RecognitionEventPayload, SpaceUpdatePayload, AlertPayload, RecognitionEvent } from '@/api/monitor-types'
import ChargePanel from '@/components/ChargePanel.vue'

const TOKEN_KEY = 'jushan_access_token'
const LOT_ID_KEY = 'booth_selected_lot_id'

const store = useMonitorStore()
const selectedLotId = ref<number | null>(null)
let wsClient: MonitorWebSocketClient | null = null

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
}

const laneCards = computed((): LaneCard[] => {
  return store.lanes.map((lane) => {
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
      onAlert: (payload: AlertPayload) => {
        store.handleAlert(payload)
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
  const saved = localStorage.getItem(LOT_ID_KEY)
  if (saved) {
    selectedLotId.value = Number(saved)
    connectOrReconnect()
  }
})

onUnmounted(() => {
  wsClient?.disconnect()
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
