<template>
  <div class="monitor-page">
    <!-- 顶部栏 -->
    <div class="page-header">
      <div class="header-left">
        <span class="page-title">实时监控面板</span>
        <a-tag :color="statusColor">{{ statusText }}</a-tag>
      </div>
      <div class="header-right">
        <a-button @click="refreshDevices">刷新设备</a-button>
        <a-badge :count="unhandledRecognitionFailedCount" :overflow-count="99">
          <a-button @click="tempPlateDrawerOpen = true">无牌车处理</a-button>
        </a-badge>
      </div>
    </div>

    <!-- 常开/常关状态条（GB-04/05 — V1.4） -->
    <div v-if="gateModeBanners.length > 0" class="gate-mode-banners">
      <a-alert
        v-for="banner in gateModeBanners"
        :key="banner.laneId"
        :type="banner.mode === 'ALWAYS_OPEN' ? 'success' : 'warning'"
        :message="banner.label"
        show-icon
        banner
        class="gate-mode-banner-item"
      />
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

    <!-- 三段式主体 -->
    <div class="monitor-body">
      <!-- 左侧：车场列表 -->
      <ParkingLotSidebar
        :lots="sidebarLots"
        :selected-id="selectedLotId"
        @select="handleLotSelect"
      />

      <!-- 右侧：上下分区 -->
      <div class="monitor-right">
        <!-- 右上：视频预览占位 -->
        <div class="video-placeholder">
          <div class="video-placeholder-content">
            <VideoCameraOutlined style="font-size: 48px; color: #d1d5db;" />
            <p>视频接入中，敬请期待</p>
          </div>
        </div>

        <!-- 右下：Tab 操作面板 -->
        <div class="monitor-panel">
          <MonitorTabs :parking-lot-id="selectedLotId ?? 0">
            <template #monitor>
              <!-- 原有监控内容：车位 + 车道网格 + 事件/告警 -->
              <div class="monitor-tab-content">
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
                          <a-popconfirm
                            :title="laneLockState[lane.laneId]?.open ? '确定取消常开？道闸将恢复正常起落' : '确定设置常开？道闸将锁定保持抬杆状态'"
                            @confirm="handleToggleLockOpen(lane.laneId)"
                          >
                            <a-button
                              :type="laneLockState[lane.laneId]?.open ? 'default' : 'primary'"
                              size="small"
                            >
                              {{ laneLockState[lane.laneId]?.open ? '取消常开' : '常开' }}
                            </a-button>
                          </a-popconfirm>
                          <a-popconfirm
                            :title="laneLockState[lane.laneId]?.close ? '确定取消常关？道闸将恢复正常起落' : '确定设置常关？白名单车辆将不再自动开闸'"
                            @confirm="handleToggleLockClose(lane.laneId)"
                          >
                            <a-button
                              :type="laneLockState[lane.laneId]?.close ? 'default' : 'primary'"
                              size="small"
                            >
                              {{ laneLockState[lane.laneId]?.close ? '取消常关' : '常关' }}
                            </a-button>
                          </a-popconfirm>
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

                <!-- 事件与异常（内嵌于监控Tab） -->
                <a-row :gutter="[12, 12]" style="margin-top: 12px">
                  <a-col :xs="24" :lg="12">
                    <a-card title="最近识别事件" :bordered="false" size="small" class="inner-card">
                      <a-list
                        :data-source="store.recentEvents"
                        :locale="{ emptyText: '暂无识别事件' }"
                        size="small"
                      >
                        <template #renderItem="{ item }">
                          <!-- EXIT 事件：点击弹出下拉菜单（收费处理 / 校正车牌） -->
                          <a-list-item
                            v-if="item.direction === 'EXIT'"
                            class="event-list-item"
                            :class="{ 'event-exit-unpaid': !item.paymentStatus }"
                          >
                            <a-dropdown :trigger="['click']">
                              <div class="event-row dropdown-trigger">
                                <div class="event-main">
                                  <span class="event-plate-text">{{ item.correctedPlate || item.plateNumber || '-' }}</span>
                                  <a-tag size="small" color="orange">出</a-tag>
                                  <a-tag v-if="item.correctedPlate" size="small" color="purple">已校正</a-tag>
                                  <a-tag size="small" :color="sourceColor(item.source)">{{ sourceLabel(item.source) }}</a-tag>
                                  <a-tag
                                    v-if="item.paymentStatus"
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
                                <div v-if="item.feeAmount != null && item.feeAmount > 0" class="event-fee">
                                  应收: ¥{{ item.feeAmount.toFixed(2) }}
                                </div>
                              </div>
                              <template #overlay>
                                <a-menu @click="(e: any) => handleExitMenuClick(e, item)">
                                  <a-menu-item key="charge">收费处理</a-menu-item>
                                  <a-menu-item v-if="!item.correctedPlate" key="correct">校正车牌</a-menu-item>
                                </a-menu>
                              </template>
                            </a-dropdown>
                          </a-list-item>

                          <!-- ENTRY 事件：点击直接打开校正弹窗 -->
                          <a-list-item
                            v-else
                            class="event-list-item"
                            @click="handleEventClick(item)"
                          >
                            <div class="event-row">
                              <div class="event-main">
                                <span class="event-plate-text">{{ item.correctedPlate || item.plateNumber || '-' }}</span>
                                <a-tag size="small" color="blue">入</a-tag>
                                <a-tag v-if="item.correctedPlate" size="small" color="purple">已校正</a-tag>
                                <a-tag size="small" :color="sourceColor(item.source)">{{ sourceLabel(item.source) }}</a-tag>
                              </div>
                              <div class="event-sub">
                                <span>{{ item.laneName || '未知车道' }}</span>
                                <span class="event-time-text">{{ store.formatTime(item.eventTime) }}</span>
                              </div>
                              <div v-if="item.feeAmount != null && item.feeAmount > 0" class="event-fee">
                                应收: ¥{{ item.feeAmount.toFixed(2) }}
                              </div>
                            </div>
                          </a-list-item>
                        </template>
                      </a-list>
                    </a-card>
                  </a-col>
                  <a-col :xs="24" :lg="12">
                    <a-card title="异常提醒" :bordered="false" size="small" class="inner-card">
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
              </div>
            </template>
          </MonitorTabs>
        </div>
      </div>
    </div>

    <!-- 收费面板 -->
    <ChargePanel />

    <!-- 人工放行弹窗（单通道） -->
    <ManualReleaseModal
      v-model:open="manualReleaseOpen"
      :lane-id="manualReleaseLaneId"
      plate-number=""
      @success="handleManualReleaseResult"
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

    <!-- 无牌车处理抽屉 -->
    <a-drawer
      v-model:open="tempPlateDrawerOpen"
      title="无牌车处理"
      placement="right"
      :width="400"
    >
      <div class="temp-plate-section">
        <a-divider orientation="left" style="margin: 4px 0 12px">手动入场</a-divider>
        <a-form layout="vertical">
          <a-form-item label="临时车牌号">
            <a-space style="width: 100%">
              <a-input v-model:value="tempPlateEntryPlate" placeholder="留空自动生成" :maxlength="20" style="flex: 1" />
              <a-button @click="handleSuggestTempPlate">建议</a-button>
            </a-space>
          </a-form-item>
          <a-form-item label="入口车道">
            <a-select v-model:value="tempPlateEntryLane" placeholder="选择入口车道" style="width: 100%">
              <a-select-option v-for="lane in entryLanes" :key="lane.laneId" :value="lane.laneId">
                {{ lane.laneName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="tempPlateEntering" block @click="handleTempPlateEntry">
              入场并开闸
            </a-button>
          </a-form-item>
        </a-form>

        <a-divider orientation="left" style="margin: 4px 0 12px">匹配出场</a-divider>
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

    <!-- 车牌校正弹窗 -->
    <PlateCorrectionModal
      v-model:open="correctionModalOpen"
      :event="correctionTarget"
      @corrected="handleCorrectionDone"
    />

  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { message, notification } from 'ant-design-vue'
import { SyncOutlined, ExclamationCircleOutlined, VideoCameraOutlined } from '@ant-design/icons-vue'
import { useMonitorStore } from '@/stores/monitor'
import { MonitorWebSocketClient, type ConnectionStatus } from '@/utils/websocket'
import type { DeviceStatus, RecognitionEventPayload, SpaceUpdatePayload, AlertPayload, RecognitionEvent, RemoteGateAlertPayload, LaneCamera } from '@/api/monitor-types'
import ChargePanel from '@/components/ChargePanel.vue'
import PlateCorrectionModal from '@/components/PlateCorrectionModal.vue'
import ManualReleaseModal from '@/components/ManualReleaseModal.vue'
import FeeRuleEditModal from '@/components/FeeRuleEditModal.vue'
import ParkingLotSidebar from './ParkingLotSidebar.vue'
import MonitorTabs from './MonitorTabs.vue'
import { getBoothParkingLots, type BoothParkingLot } from '@/api/parking-lot'

const TOKEN_KEY = 'jushan_access_token'
const LOT_ID_KEY = 'booth_selected_lot_id'

const store = useMonitorStore()
const selectedLotId = ref<number | null>(null)
let wsClient: MonitorWebSocketClient | null = null

// 人工放行
const manualReleaseOpen = ref(false)
const manualReleaseLaneId = ref(0)

/** 每个通道的锁定状态：{ [laneId]: { open: boolean, close: boolean } } */
const laneLockState = ref<Record<number, { open: boolean; close: boolean }>>({})

// 从快照初始化 laneLockState（修复 gateMode 刷新后状态丢失）
watch(() => store.lanes, (lanes) => {
  for (const lane of lanes) {
    const mode = lane.gateMode
    if (mode === 'ALWAYS_OPEN') {
      laneLockState.value[lane.id] = { open: true, close: false }
    } else if (mode === 'ALWAYS_CLOSE') {
      laneLockState.value[lane.id] = { open: false, close: true }
    } else {
      laneLockState.value[lane.id] = { open: false, close: false }
    }
  }
}, { immediate: true, deep: true })

// 收费规则编辑
const feeRuleEditOpen = ref(false)
const feeRuleEditLaneId = ref(0)
const currentFeeRule = ref<any>(null)

// 远程开闸弹窗
const remoteGateModalVisible = ref(false)
const currentRemoteGateAlert = ref<RemoteGateAlertPayload | null>(null)
let remoteGateDismissTimer: ReturnType<typeof setTimeout> | null = null

// 无牌车处理
const tempPlateDrawerOpen = ref(false)
const tempPlateSearch = ref('')
const tempPlateExitLane = ref<number | null>(null)
const tempPlateExiting = ref(false)
const tempPlateEntryPlate = ref('')
const tempPlateEntryLane = ref<number | null>(null)
const tempPlateEntering = ref(false)
/** 识别失败未处理计数 */
const unhandledRecognitionFailedCount = ref(0)

// 车牌校正
const correctionModalOpen = ref(false)
const correctionTarget = ref<RecognitionEvent | null>(null)

// 车场列表（左侧栏）
const sidebarLots = ref<{ id: number; name: string; status: string; currentVehicles?: number }[]>([])

/** 出口车道列表 */
const exitLanes = computed(() => {
  return store.lanes
    .filter((l: any) => l.direction === 'EXIT' || l.direction === 'MIXED')
    .map((l) => ({
      laneId: l.id,
      laneName: l.name || `车道 ${l.id}`,
    }))
})

/** 入口车道列表 */
const entryLanes = computed(() => {
  return store.lanes
    .filter((l: any) => l.direction === 'ENTRY' || l.direction === 'MIXED')
    .map((l) => ({
      laneId: l.id,
      laneName: l.name || `车道 ${l.id}`,
    }))
})

/** 建议临时车牌号 */
async function handleSuggestTempPlate() {
  if (!selectedLotId.value) { message.warning('请先选择停车场'); return }
  try {
    const res = await import('@/api/monitor').then(m => m.suggestTempPlate(selectedLotId.value!))
    tempPlateEntryPlate.value = res.tempPlate
  } catch (e: any) {
    message.error(e?.response?.data?.message || '获取建议临牌失败')
  }
}

/** 无牌车手动入场并开闸 */
async function handleTempPlateEntry() {
  if (!tempPlateEntryLane.value) { message.warning('请选择入口车道'); return }
  if (!selectedLotId.value) { message.warning('请先选择停车场'); return }
  tempPlateEntering.value = true
  try {
    const res = await import('@/api/monitor').then(m => m.manualTempPlateEntry({
      parkingLotId: selectedLotId.value!,
      laneId: tempPlateEntryLane.value!,
      tempPlate: tempPlateEntryPlate.value.trim() || undefined,
    }))
    message.success(`无牌车入场成功，临牌：${res.tempPlate}`)
    tempPlateEntryPlate.value = ''
    tempPlateEntryLane.value = null
  } catch (e: any) {
    message.error(e?.response?.data?.message || '入场失败')
  } finally {
    tempPlateEntering.value = false
  }
}

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

/** 加载车场列表（填充左侧栏） */
async function loadLotOptions() {
  try {
    const lots = await getBoothParkingLots()
    sidebarLots.value = (lots || []).map((lot: BoothParkingLot) => ({
      id: lot.id,
      name: lot.name,
      status: lot.status,
      currentVehicles: undefined,
    }))
  } catch {
    // 静默失败
  }
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

/** 切换常开/取消常开（锁定/解锁开闸继电器） */
async function handleToggleLockOpen(laneId: number) {
  if (!laneLockState.value[laneId]) {
    laneLockState.value[laneId] = { open: false, close: false }
  }
  const isLocked = laneLockState.value[laneId]!.open

  try {
    const { manualLockGate, manualUnlockGate } = await import('@/api/charge')
    if (isLocked) {
      const result = await manualUnlockGate(laneId, '岗亭取消常开')
      if (result.gateDeviceAck) {
        laneLockState.value[laneId]!.open = false
        message.success('取消常开成功')
      } else {
        message.warning(result.gateResult || '取消常开失败')
      }
    } else {
      const result = await manualLockGate(laneId, '岗亭设置常开')
      if (result.gateDeviceAck) {
        laneLockState.value[laneId]!.open = true
        message.success('常开成功（道闸已锁定）')
      } else {
        message.warning(result.gateResult || '常开失败')
      }
    }
  } catch (e: any) {
    message.error(e?.message || (isLocked ? '取消常开失败' : '常开失败'))
  }
}

/** 切换常关/取消常关（锁定/解锁关闸继电器） */
async function handleToggleLockClose(laneId: number) {
  if (!laneLockState.value[laneId]) {
    laneLockState.value[laneId] = { open: false, close: false }
  }
  const isLocked = laneLockState.value[laneId]!.close

  try {
    if (isLocked) {
      const { manualUnlockGate } = await import('@/api/charge')
      const result = await manualUnlockGate(laneId, '岗亭取消常关')
      if (result.gateDeviceAck) {
        laneLockState.value[laneId]!.close = false
        message.success('取消常关成功')
      } else {
        message.warning(result.gateResult || '取消常关失败')
      }
    } else {
      // 先关闸，视为常关
      const { manualCloseGate } = await import('@/api/charge')
      const result = await manualCloseGate(laneId, '岗亭常关')
      if (result.gateDeviceAck) {
        laneLockState.value[laneId]!.close = true
        message.success('常关成功')
      } else {
        message.warning(result.gateResult || '常关失败')
      }
    }
  } catch (e: any) {
    message.error(e?.message || (isLocked ? '取消常关失败' : '常关失败'))
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
  if (remoteGateDismissTimer) {
    clearTimeout(remoteGateDismissTimer)
    remoteGateDismissTimer = null
  }

  currentRemoteGateAlert.value = payload
  remoteGateModalVisible.value = true

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

// GB-04/05: 常开/常关状态条（V1.4）
const gateModeBanners = computed(() => {
  const banners: { laneId: number; mode: string; label: string }[] = []
  for (const lane of store.lanes) {
    const gateMode = (lane as any).gateMode
    if (gateMode === 'ALWAYS_OPEN') {
      banners.push({ laneId: lane.id, mode: 'ALWAYS_OPEN', label: `车道「${lane.name || `车道${lane.id}`}」处于常开模式 — 道闸已锁定，车辆可直接通行` })
    } else if (gateMode === 'ALWAYS_CLOSE') {
      banners.push({ laneId: lane.id, mode: 'ALWAYS_CLOSE', label: `车道「${lane.name || `车道${lane.id}`}」处于常关模式 — 白名单不会自动开闸，需人工放行` })
    }
  }
  return banners
})

// GB-07: WebSocket 事件提示音（V1.4 — 使用 Web Audio API 生成提示音）
let audioCtx: AudioContext | null = null
function playAlertSound() {
  try {
    if (!audioCtx) audioCtx = new AudioContext()
    const osc = audioCtx.createOscillator()
    const gain = audioCtx.createGain()
    osc.connect(gain); gain.connect(audioCtx.destination)
    osc.type = 'sine'
    osc.frequency.setValueAtTime(800, audioCtx.currentTime)
    osc.frequency.setValueAtTime(1000, audioCtx.currentTime + 0.1)
    gain.gain.setValueAtTime(0.3, audioCtx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.3)
    osc.start(audioCtx.currentTime); osc.stop(audioCtx.currentTime + 0.3)
  } catch { /* 浏览器不支持或用户未交互 */ }
}

// GB-08: 设备离线判定（V1.4 口径：最后状态包距今超过 120 秒即判定离线）
const DEVICE_OFFLINE_THRESHOLD_MS = 120_000
const lastDeviceStatusTime = ref<Record<number, number>>({}) // deviceId -> last seen timestamp (ms)

function isDeviceOfflineByTime(deviceId: number): boolean {
  const lastSeen = lastDeviceStatusTime.value[deviceId]
  if (!lastSeen) return false // 从未收到状态，暂不判定离线
  return (Date.now() - lastSeen) > DEVICE_OFFLINE_THRESHOLD_MS
}

interface LaneCard {
  laneId: number
  laneName: string
  direction: string
  deviceId?: number
  deviceOnline: boolean
  isOffline: boolean
  charging: boolean
  latestEvent?: RecognitionEventPayload
  cameras?: LaneCamera[]
  primaryOffline: boolean
  activeSourceLabel?: string
}

const laneCards = computed((): LaneCard[] => {
  return store.lanes.map((lane) => {
    const cameras = lane.cameras || []
    const hasMultiCameras = cameras.length > 0

    if (!hasMultiCameras) {
      const device = lane.deviceId
        ? store.deviceStatuses.find((d) => d.deviceId === lane.deviceId)
        : undefined
      const latestEvent = store.recentEvents.find((e) => e.laneId === lane.id)

      const charging =
        store.chargePanelVisible &&
        store.currentChargeInfo?.laneId === lane.id

      // GB-08: 120 秒时间阈值判定离线（V1.4）
      const timeOffline = lane.deviceId != null && isDeviceOfflineByTime(lane.deviceId)
      return {
        laneId: lane.id,
        laneName: lane.name || `车道 ${lane.id}`,
        direction: lane.direction,
        deviceId: lane.deviceId,
        deviceOnline: !!device?.online && !device?.stale && !timeOffline,
        isOffline: !device || !device.online || device.stale || timeOffline,
        charging,
        latestEvent: latestEvent as RecognitionEventPayload | undefined,
        cameras: [],
        primaryOffline: false,
        activeSourceLabel: undefined,
      }
    }

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

function sourceLabel(source?: string) {
  const map: Record<string, string> = {
    DEVICE_ACCESS: '设备识别',
    MOCK: '模拟测试',
    MANUAL: '手动操作',
  }
  return map[source || ''] || source || '未知'
}

/** 点击事件列表项：ENTRY 事件打开校正弹窗 */
function handleEventClick(item: RecognitionEvent) {
  correctionTarget.value = item
  correctionModalOpen.value = true
}

/** EXIT 事件下拉菜单点击处理 */
function handleExitMenuClick(e: { key: string }, item: RecognitionEvent) {
  if (e.key === 'charge') {
    store.showChargePanel(
      item.correctedPlate || item.plateNumber,
      item.laneId
    ).catch(() => {
      // 查询失败不阻塞
    })
  } else if (e.key === 'correct') {
    correctionTarget.value = item
    correctionModalOpen.value = true
  }
}

/** 校正完成后 */
function handleCorrectionDone() {
  // WebSocket 推送的校正后事件会自动更新 store.recentEvents
}

function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
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
        playAlertSound() // GB-07: 新事件提示音（V1.4）
        message.info(`${payload.direction === 'ENTRY' ? '入场' : '出场'}识别: ${payload.plateNumber}`)

        if (payload.direction === 'EXIT') {
          store.showChargePanel(payload.plateNumber, payload.laneId).catch(() => {
            // 查询失败时不阻塞，收费面板已显示（含错误提示）
          })
        }
      },
      onDeviceStatus: (payload: DeviceStatus) => {
        store.handleDeviceStatus(payload)
        // GB-08: 记录设备最后状态时间，用于 120 秒离线判定（V1.4）
        if (payload.deviceId != null) {
          lastDeviceStatusTime.value[payload.deviceId] = Date.now()
        }
      },
      onAlert: (payload: any) => {
        if (payload.type === 'RECOGNITION_FAILED') {
          unhandledRecognitionFailedCount.value++
          const key = `recognition-failed-${payload.eventId || Date.now()}`
          notification.warning({
            message: '识别失败',
            description: `${payload.laneName || ''} | ${payload.eventTime || ''} | ${payload.message || '车牌未识别'}`,
            placement: 'bottomRight',
            duration: 0,
            btn: () => h(
              'a-button',
              {
                size: 'small',
                type: 'primary',
                onClick: () => {
                  notification.close(key)
                  unhandledRecognitionFailedCount.value = Math.max(0, unhandledRecognitionFailedCount.value - 1)
                  tempPlateDrawerOpen.value = true
                },
              },
              '处理',
            ),
            onClose: () => {
              unhandledRecognitionFailedCount.value = Math.max(0, unhandledRecognitionFailedCount.value - 1)
            },
            key,
          })
        } else {
          store.handleAlert(payload)
        }
      },
      onRemoteGateAlert: (payload: RemoteGateAlertPayload) => {
        showRemoteGateAlert(payload)
      },
      onError: (error) => {
        console.warn('WebSocket 错误:', error)
      },
    },
  })
  wsClient.connect()
}

/** 切换车场（替代原 connectOrReconnect） */
async function handleLotSelect(lotId: number) {
  if (lotId === selectedLotId.value) return

  // 断开当前 WebSocket
  wsClient?.disconnect()

  // 清空 store
  store.reset()

  // 更新选中
  selectedLotId.value = lotId
  sessionStorage.setItem(LOT_ID_KEY, String(lotId))

  // 加载新快照
  try {
    await store.loadSnapshot(lotId)
    buildWsClient(lotId)
  } catch (e: any) {
    message.error(e?.message || '车场切换失败')
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
  const saved = sessionStorage.getItem(LOT_ID_KEY)
  if (saved) {
    handleLotSelect(Number(saved))
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
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  overflow: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;

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
    font-size: 16px;
    font-weight: 600;
    color: #1f2937;
  }
}

.gate-mode-banners {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.gate-mode-banner-item {
  border-radius: 0;
  margin: 0;
}

.critical-banner {
  flex-shrink: 0;
}

.monitor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.monitor-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.video-placeholder {
  height: 200px;
  min-height: 200px;
  background: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-bottom: 1px solid #d1d5db;
}

.video-placeholder-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #6b7280;
  font-size: 14px;

  p {
    margin: 0;
  }
}

.monitor-panel {
  flex: 1;
  overflow: hidden;
}

.monitor-tab-content {
  height: 100%;
  overflow: auto;
  padding: 8px;
}

.space-card {
  margin-bottom: 8px;

  .space-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
    text-align: center;
  }

  .space-item {
    padding: 8px;
  }

  .space-value {
    font-size: 28px;
    font-weight: 700;
    color: #1f2937;

    &.remaining {
      color: #10b981;
    }
  }

  .space-label {
    margin-top: 4px;
    color: #6b7280;
    font-size: 12px;
  }
}

.lane-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.lane-card {
  .lane-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .lane-name {
    font-weight: 600;
    font-size: 14px;
  }

  .lane-tags {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .lane-direction {
    margin-bottom: 8px;
  }

	  .lane-actions {
	    margin-top: 8px;

	    :deep(.ant-space) {
	      flex-wrap: wrap;
	      justify-content: flex-end;
	    }
	  }
  .lane-event {
    text-align: center;
    padding: 8px 0;
    background: #f9fafb;
    border-radius: 6px;

    .event-plate {
      font-size: 20px;
      font-weight: 700;
      color: #111827;
    }

    .event-empty {
      color: #9ca3af;
    }

    .event-time {
      margin-top: 2px;
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
}

.inner-card {
  max-height: 320px;
  overflow: auto;
}

.event-list-item {
  padding: 6px 0;
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

.dropdown-trigger {
  cursor: pointer;
  user-select: none;
}

.event-main {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.event-plate-text {
  font-weight: 600;
  font-size: 14px;
}

.event-sub {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #6b7280;
}

.event-time-text {
  font-family: monospace;
}

.event-fee {
  margin-top: 2px;
  font-size: 11px;
  font-weight: 600;
  color: $error-color;
  text-align: right;
}

.alert-list-item {
  padding: 6px 0;
}

.alert-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.alert-message {
  flex: 1;
  font-size: 13px;
  color: #374151;
}

.alert-time {
  font-size: 11px;
  color: #9ca3af;
}
</style>
