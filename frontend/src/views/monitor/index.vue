<template>
  <div class="monitor-page">
    <!-- ========== 顶部双层通栏导航 ========== -->
    <!-- 第一层：主导航 -->
    <div class="page-header">
      <div class="header-left">
        <span class="header-brand">飓山智慧停车</span>
        <span class="header-title">岗亭工作区</span>
        <span class="online-dot" />
        <span class="online-label">在线</span>
        <a-select
          v-model:value="selectedLotId"
          :options="lotOptions"
          placeholder="测试车场"
          size="small"
          class="lot-select"
          @change="handleLotSelect"
        />
      </div>
      <div class="header-right">
        <span class="current-time">{{ currentTime }}</span>
        <span class="username">gangting001</span>
        <a-button size="small" @click="handleLogout">退出</a-button>
      </div>
    </div>

    <!-- ========== 常开/常关横幅 ========== -->
    <div v-if="gateModeBanners.length > 0" class="gate-mode-banners">
      <a-alert
        v-for="b in gateModeBanners" :key="b.laneId"
        :type="b.mode === 'ALWAYS_OPEN' ? 'success' : 'warning'"
        :message="b.label" show-icon banner class="gate-mode-banner-item"
      />
    </div>

    <!-- ========== 主体：左 85% 四宫格 + 右 15% 侧边栏 ========== -->
    <div class="monitor-body">
      <div class="main-grid">
        <!-- 左上：入口车道控制 -->
        <div class="grid-card">
          <div class="card-topbar">
            <a-select
              v-model:value="entryLaneKey" :options="entryLaneOptions"
              placeholder="测试车道 入口" size="small" class="lane-select"
            />
            <span class="card-status" :class="entryLaneOnline ? 'online' : 'offline'">
              {{ entryLaneOnline ? '在线' : '离线' }}
            </span>
          </div>
          <div class="card-video">
            <VideoCameraOutlined class="video-icon" />
            <span class="video-text">视频接入中，敬请期待</span>
          </div>
          <div class="card-actions">
            <a-button size="small" type="primary" class="btn-open" :loading="directOpening" @click="handleDirectOpen(entryLaneKey, 1)">开闸</a-button>
            <a-button size="small" class="btn-close" @click="handleEntryClose">关闸</a-button>
            <a-button size="small" @click="manualReleaseOpenGate(entryLaneKey, 1)">人工放行</a-button>
            <a-popconfirm
              :title="(entryLockState.open ? '确定取消常开？' : '确定设置常开？道闸将锁定保持抬杆状态')"
              @confirm="handleEntryKeepOpen"
            >
              <a-button size="small" type="primary" class="btn-keep">{{ entryLockState.open ? '取消常开' : '道闸常开' }}</a-button>
            </a-popconfirm>
          </div>
        </div>

        <!-- 右上：出口车道控制 -->
        <div class="grid-card">
          <div class="card-topbar">
            <a-select
              v-model:value="exitLaneKey" :options="exitLaneOptions"
              placeholder="测试车道 出口" size="small" class="lane-select"
            />
            <span class="card-status" :class="exitLaneOnline ? 'online' : 'offline'">
              {{ exitLaneOnline ? '在线' : '离线' }}
            </span>
          </div>
          <div class="card-video">
            <VideoCameraOutlined class="video-icon" />
            <span class="video-text">视频接入中，敬请期待</span>
          </div>
          <div class="card-actions">
            <a-button size="small" type="primary" class="btn-open" :loading="directOpening" @click="handleDirectOpen(exitLaneKey, 2)">开闸</a-button>
            <a-button size="small" class="btn-close" @click="handleExitClose">关闸</a-button>
            <a-button size="small" @click="manualReleaseOpenGate(exitLaneKey, 2)">人工放行</a-button>
            <a-popconfirm
              :title="(exitLockState.open ? '确定取消常开？' : '确定设置常开？道闸将锁定保持抬杆状态')"
              @confirm="handleExitKeepOpen"
            >
              <a-button size="small" type="primary" class="btn-keep">{{ exitLockState.open ? '取消常开' : '道闸常开' }}</a-button>
            </a-popconfirm>
          </div>
        </div>

        <!-- 左下：当前车辆详情 -->
        <div class="grid-card vehicle-detail-card">
          <div class="detail-layout" v-if="displayVehicle">
            <div class="detail-thumbs">
              <div class="thumb-item">
                <div class="detail-thumb">
                  <a-image v-if="displayVehicle.entryImage" :src="displayVehicle.entryImage" />
                  <VideoCameraOutlined v-else class="thumb-placeholder" />
                </div>
                <span class="thumb-label">入场图片</span>
              </div>
              <div class="thumb-item">
                <div class="detail-thumb">
                  <a-image v-if="displayVehicle.exitImage" :src="displayVehicle.exitImage" />
                  <VideoCameraOutlined v-else class="thumb-placeholder" />
                </div>
                <span class="thumb-label">出场图片</span>
              </div>
            </div>
            <div class="detail-info">
              <div class="info-row"><span class="info-label">车牌</span><span class="info-value"><PlateTag :plate-number="displayVehicle.plate" size="small" /></span></div>
              <div class="info-row"><span class="info-label">类型</span><span class="info-value">{{ displayVehicle.vehicleType || '临时车' }}</span></div>
              <div class="info-row"><span class="info-label">入场时间</span><span class="info-value">{{ displayVehicle.entryTime || '--' }}</span></div>
              <div class="info-row"><span class="info-label">出场时间</span><span class="info-value">{{ displayVehicle.exitTime || '--' }}</span></div>
              <div class="info-row"><span class="info-label">车道</span><span class="info-value">{{ displayVehicle.laneName || '--' }}</span></div>
              <div class="info-row"><span class="info-label">金额</span><span class="info-value fee">{{ displayVehicle.feeAmount != null ? `¥${displayVehicle.feeAmount.toFixed(2)}` : '--' }}</span></div>
              <div class="info-row" v-if="displayVehicle.isSelected"><a @click="selectedRecord = null" style="font-size:12px">← 返回实时</a></div>
            </div>
          </div>
          <div class="detail-empty" v-else>
            <a-empty description="暂无车辆信息" :image-style="{ height: '40px' }" />
          </div>
        </div>

        <!-- 右下：通行记录表格 -->
        <div class="grid-card records-table-card">
          <div class="records-header">
            <span class="records-title">通行记录</span>
            <div class="records-tools">
              <a-input v-model:value="recordsSearch" placeholder="搜索车牌" size="small" style="width: 120px" allow-clear />
              <a-button size="small" type="primary" @click="searchRecords">查询</a-button>
            </div>
          </div>
          <a-table
            :data-source="displayedRecords" :columns="recordColumns" :pagination="false"
            size="small" :locale="{ emptyText: '无数据' }" row-key="key" class="records-table"
            :scroll="{ x: 580 }"
            :custom-row="(r: any) => ({ onClick: () => selectRecord(r), style: { cursor: 'pointer' } })"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'plateNumber'">
                <PlateTag :plate-number="record.plateNumber" size="small" />
              </template>
              <template v-if="column.key === 'vehicleType'">
                <a-tag :color="record.vehicleType === 'WHITE' || record.vehicleType === 'FIXED' ? 'green' : 'orange'" size="small">
                  {{ vehicleTypeLabel(record.vehicleType) }}
                </a-tag>
              </template>
              <template v-if="column.key === 'entryTime'">
                {{ record.entryTime ? dayjs(record.entryTime).format('MM-DD HH:mm') : '--' }}
              </template>
              <template v-if="column.key === 'exitTime'">
                {{ record.exitTime ? dayjs(record.exitTime).format('MM-DD HH:mm') : '--' }}
              </template>
              <template v-if="column.key === 'status'">
                <a-tag :color="record.exitTime ? 'default' : 'green'" size="small">
                  {{ record.exitTime ? '已出场' : '已入场' }}
                </a-tag>
              </template>
              <template v-if="column.key === 'entryLane'">
                {{ record.entryLaneName || '--' }}
              </template>
              <template v-if="column.key === 'exitLane'">
                {{ record.exitLaneName || (record.exitTime ? '--' : '') || '--' }}
              </template>
            </template>
          </a-table>
        </div>
      </div>

      <!-- 右侧侧边栏 -->
      <div class="right-sidebar">
        <div class="sidebar-header">
          <span class="sidebar-title">提示消息</span>
          <span class="sidebar-icons">
            <ReloadOutlined class="sidebar-icon" @click="refreshDevices" />
          </span>
        </div>
        <div class="sidebar-stats">
          <div class="stat-item">
            <span class="stat-label">车场总车位</span>
            <span class="stat-value">{{ parkingLot?.totalSpaces ?? '--' }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">在场车辆</span>
            <span class="stat-value">{{ parkingLot?.currentVehicles ?? '--' }}</span>
          </div>
          <div class="stat-item clickable" @click="spaceAdjustOpen = true">
            <span class="stat-label">剩余车位</span>
            <span class="stat-value remaining">{{ parkingLot?.remainingSpaces ?? '--' }}</span>
          </div>
        </div>
        <div class="sidebar-messages">
          <a-empty description="暂无消息" :image-style="{ height: '36px' }" />
        </div>
      </div>
    </div>

    <!-- ========== 弹窗 ========== -->
    <ChargePanel />
    <ManualReleaseModal
      v-model:open="manualReleaseOpen" :lane-id="manualReleaseLaneId"
      :plate-number="manualReleasePlate" :direction="manualReleaseDirection"
      @success="handleManualReleaseResult"
    />
    <FeeRuleEditModal
      v-model:open="feeRuleEditOpen" :lane-id="feeRuleEditLaneId"
      :fee-rule="currentFeeRule" @save="handleSaveFeeRule"
    />
    <a-modal v-model:open="remoteGateModalVisible" title="🚧 运营端远程开闸" :footer="null" width="420px" centered>
      <a-result status="success" title="远程开闸通知">
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
    <PlateCorrectionModal
      v-model:open="correctionModalOpen" :event="correctionTarget"
      @corrected="handleCorrectionDone"
    />
    <SpaceAdjustModal
      v-model:open="spaceAdjustOpen"
      :parking-lot-id="selectedLotId ?? 0"
      :current-remaining="parkingLot?.remainingSpaces ?? 0"
      :total-spaces="parkingLot?.totalSpaces ?? 0"
      @success="handleSpaceAdjustSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  VideoCameraOutlined, ReloadOutlined,
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useMonitorStore } from '@/stores/monitor'
import { MonitorWebSocketClient } from '@/utils/websocket'
import type { DeviceStatus, RecognitionEventPayload, SpaceUpdatePayload, RecognitionEvent, RemoteGateAlertPayload, ParkingSessionVO } from '@/api/monitor-types'
import ChargePanel from '@/components/ChargePanel.vue'
import PlateCorrectionModal from '@/components/PlateCorrectionModal.vue'
import ManualReleaseModal from '@/components/ManualReleaseModal.vue'
import FeeRuleEditModal from '@/components/FeeRuleEditModal.vue'
import SpaceAdjustModal from '@/components/SpaceAdjustModal.vue'
import PlateTag from '@/components/PlateTag.vue'
import { getBoothParkingLots, type BoothParkingLot } from '@/api/parking-lot'
import { getParkingSessions } from '@/api/vehicle-query'
import { captureImage, manualOpenGate } from '@/api/charge'

const TOKEN_KEY = 'jushan_access_token'
const LOT_ID_KEY = 'booth_selected_lot_id'

const store = useMonitorStore()
const selectedLotId = ref<number | null>(null)
let wsClient: MonitorWebSocketClient | null = null
const lotOptions = ref<{ value: number; label: string }[]>([])

// ========== 车道选择 ==========
const entryLaneKey = ref('')
const exitLaneKey = ref('')

const directOpening = ref(false)

// ========== 直接开闸（抓拍+日志+开闸）==========
async function handleDirectOpen(laneKey: Ref<string> | string, direction: number) {
  const key = typeof laneKey === 'string' ? laneKey : laneKey.value
  const { laneId } = parseLaneKey(key)
  if (!laneId) { message.warning('请先选择车道'); return }
  const latest = store.recentEvents.find(e => e.laneId === laneId)
  const plate = latest?.correctedPlate || latest?.plateNumber || ''
  directOpening.value = true
  try {
    // 先抓拍
    let imageUrl: string | undefined
    try {
      const cap = await captureImage(laneId, direction)
      imageUrl = cap.imageUrl
    } catch { /* 抓拍失败不阻塞开闸 */ }
    // 调开闸接口
    const r = await manualOpenGate(laneId, '岗亭手动开闸', {
      plateNumber: plate || undefined,
      entryImage: imageUrl,
      direction,
    })
    r.gateDeviceAck ? message.success('开闸成功') : message.warning(r.gateResult || '开闸失败')
    loadPresentVehicles() // 刷新通行记录
  } catch (e: any) {
    message.error(e?.message || '开闸失败')
  } finally { directOpening.value = false }
}
const manualReleaseOpen = ref(false)
const manualReleaseLaneId = ref(0)
const manualReleasePlate = ref('')
const manualReleaseDirection = ref<number | undefined>(undefined)
const laneLockState = ref<Record<string, { open: boolean; close: boolean }>>({})
const gateCapabilities = ref<Record<number, string[]>>({})
const feeRuleEditOpen = ref(false)
const feeRuleEditLaneId = ref(0)
const currentFeeRule = ref<any>(null)
const remoteGateModalVisible = ref(false)
const currentRemoteGateAlert = ref<RemoteGateAlertPayload | null>(null)
let remoteGateDismissTimer: ReturnType<typeof setTimeout> | null = null
const correctionModalOpen = ref(false)
const correctionTarget = ref<RecognitionEvent | null>(null)
const spaceAdjustOpen = ref(false)
const currentTime = ref('')
const currentUser = ref('')
let timeTimer: ReturnType<typeof setInterval> | null = null

// 设备在线宽限期
const DEVICE_GRACE_PERIOD_MS = 120_000
const lastDeviceOnlineTime = ref<Record<number, number>>({})

function isDeviceEffectivelyOnline(deviceId: number): boolean {
  const device = store.deviceStatuses.find(d => d.deviceId === deviceId)
  if (device?.online) { lastDeviceOnlineTime.value[deviceId] = Date.now(); return true }
  const lastOnline = lastDeviceOnlineTime.value[deviceId] || 0
  if (lastOnline === 0) return false
  return (Date.now() - lastOnline) < DEVICE_GRACE_PERIOD_MS
}

function updateTime() { currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss') }
function handleLogout() { sessionStorage.removeItem(TOKEN_KEY); window.location.href = '/login' }

// ========== 车场 ==========
async function loadLotOptions() {
  try {
    const lots = await getBoothParkingLots()
    lotOptions.value = (lots || []).map((l: BoothParkingLot) => ({ value: l.id, label: l.name }))
  } catch {}
}

async function handleLotSelect(lotId: number) {
  if (lotId === store.currentLotId) return
  wsClient?.disconnect(); store.reset()
  selectedLotId.value = lotId
  sessionStorage.setItem(LOT_ID_KEY, String(lotId))
  try {
    await store.loadSnapshot(lotId); await refreshGateCapabilities(); buildWsClient(lotId); autoSelectLanes()
    loadPresentVehicles()
  } catch (e: any) { message.error(e?.message || '车场切换失败') }
}

function autoSelectLanes() {
  const entry = store.lanes.find(l => l.direction === 'ENTRY' || l.direction === 'MIXED')
  const exit = store.lanes.find(l => l.direction === 'EXIT' || l.direction === 'MIXED')
  if (entry) entryLaneKey.value = entry.id + '-ENTRY'
  if (exit) exitLaneKey.value = exit.id + (exit.direction === 'MIXED' ? '-EXIT' : '')
}

// ========== 车道数据 ==========
interface LaneInfo { key: string; laneId: number; name: string; direction: string; deviceId?: number; online: boolean }

const allLaneInfos = computed((): LaneInfo[] => {
  const r: LaneInfo[] = []
  store.lanes.forEach(l => {
    const name = l.name || `车道${l.id}`
    const online = l.deviceId != null ? isDeviceEffectivelyOnline(l.deviceId) : false
    if (l.direction === 'MIXED') {
      r.push({ key: l.id + '-ENTRY', laneId: l.id, name: name + ' 入口', direction: 'ENTRY', deviceId: l.deviceId, online })
      r.push({ key: l.id + '-EXIT', laneId: l.id, name: name + ' 出口', direction: 'EXIT', deviceId: l.deviceId, online })
    } else {
      r.push({ key: l.id + '', laneId: l.id, name, direction: l.direction, deviceId: l.deviceId, online })
    }
  })
  return r
})

const entryLaneOptions = computed(() => allLaneInfos.value.filter(l => l.direction === 'ENTRY').map(l => ({ value: l.key, label: l.name })))
const exitLaneOptions = computed(() => allLaneInfos.value.filter(l => l.direction === 'EXIT').map(l => ({ value: l.key, label: l.name })))
const entryLaneOnline = computed(() => allLaneInfos.value.find(l => l.key === entryLaneKey.value)?.online ?? false)
const exitLaneOnline = computed(() => allLaneInfos.value.find(l => l.key === exitLaneKey.value)?.online ?? false)

function parseLaneKey(key: string): { laneId: number; direction: string } {
  const parts = key.split('-')
  return { laneId: parseInt(parts[0]) || 0, direction: parts[1] || '' }
}

// ========== 车辆详情 ==========
const selectedRecord = ref<any>(null)
const latestRecognition = computed(() => store.recentEvents[0] || null)
const prevRecognition = computed(() => store.recentEvents[1] || null)

// 当前显示的数据源：选中记录优先，否则实时识别
const displayVehicle = computed(() => {
  if (selectedRecord.value) {
    const r = selectedRecord.value
    return {
      plate: r.plateNumber || '',
      direction: r.status === 'IN' ? 'ENTRY' : 'EXIT',
      vehicleType: r.vehicleType,
      laneName: r.laneName || '',
      entryTime: r.entryTime || '',
      exitTime: r.exitTime || '',
      entryImage: r.entryImage || '',
      exitImage: r.exitImage || '',
      feeAmount: r.feeAmount,
      isSelected: true,
    }
  }
  const e = latestRecognition.value
  if (!e) return null
  return {
    plate: e.correctedPlate || e.plateNumber || '',
    direction: e.direction || '',
    vehicleType: vehicleType.value || '',
    laneName: e.laneName || '',
    entryTime: store.formatTime(e.eventTime),
    exitTime: '',
    entryImage: e.imagePath || '',
    exitImage: '',
    feeAmount: e.feeAmount,
    isSelected: false,
  }
})

const vehicleType = ref('')

watch(latestRecognition, async (event) => {
  vehicleType.value = ''
  if (!event) return
  // 新识别到来时清除选中记录
  selectedRecord.value = null
  const plate = event.correctedPlate || event.plateNumber
  if (!plate) return

  // 优先从已加载的在场车辆列表中查找
  const found = presentVehicles.value.find(
    v => v.plateNumber?.toUpperCase() === plate.toUpperCase()
  )
  if (found && found.vehicleType != null) {
    vehicleType.value = vehicleTypeLabel(found.vehicleType)
    return
  }

  // 回退到 getChargeInfo
  try {
    const { getChargeInfo } = await import('@/api/charge')
    const session = await getChargeInfo(plate)
    if (session) {
      vehicleType.value = vehicleTypeLabel((session as any).vehicleType || '')
    }
  } catch { /* 查询失败不影响 */ }
})

// ========== 通行记录 ==========

const recordsSearch = ref('')
const presentVehicles = ref<ParkingSessionVO[]>([])
const recordColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 100 },
  { title: '类型', key: 'vehicleType', width: 65 },
  { title: '入场通道', key: 'entryLane', width: 80 },
  { title: '出场通道', key: 'exitLane', width: 80 },
  { title: '入场时间', key: 'entryTime', width: 95 },
  { title: '出场时间', key: 'exitTime', width: 95 },
  { title: '状态', key: 'status', width: 60 },
]

const displayedRecords = computed(() => {
  let records = presentVehicles.value.map((v, i) => ({ ...v, key: (v as any).id || v.plateNumber + '-' + i }))
  if (recordsSearch.value) records = records.filter(r => (r.plateNumber || '').toUpperCase().includes(recordsSearch.value.toUpperCase()))
  return records
})

async function loadPresentVehicles() {
  if (!selectedLotId.value) return
  try {
    const result = await getParkingSessions({ parkingLotId: selectedLotId.value, size: 50 })
    presentVehicles.value = result.records || []
  } catch { presentVehicles.value = [] }
}

function searchRecords() { loadPresentVehicles() }

function selectRecord(record: any) { selectedRecord.value = record }

function formatDuration(minutes: number): string {
  if (minutes == null || minutes < 0) return '--'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}时${m}分` : `${m}分`
}

function vehicleTypeLabel(type: string): string {
  if (!type) return '临时车'
  const t = type.toUpperCase()
  if (t === 'WHITE' || t === 'FIXED' || t === 'FIXED_SPACE' || t === 'MONTHLY' || t === 'MONTHLY_PASS' || t === 'WHITELIST') return '固定车'
  if (t === 'TEMP' || t === 'TEMPORARY') return '临时车'
  return '临时车'
}

// ========== 车位 ==========
const parkingLot = computed(() => store.parkingLot)

// ========== 横幅 ==========
const gateModeBanners = computed(() => {
  const banners: { laneId: number; mode: string; label: string }[] = []
  for (const lane of store.lanes) {
    const gateMode = (lane as any).gateMode
    if (gateMode === 'ALWAYS_OPEN') banners.push({ laneId: lane.id, mode: 'ALWAYS_OPEN', label: `车道「${lane.name || `车道${lane.id}`}」处于常开模式` })
  }
  return banners
})

// ========== 控闸 ==========
async function refreshGateCapabilities() {
  const { getGateCapabilities } = await import('@/api/charge')
  const caps: Record<number, string[]> = {}
  for (const lane of store.lanes) { try { caps[lane.id] = await getGateCapabilities(lane.id) } catch { caps[lane.id] = [] } }
  gateCapabilities.value = caps
}

// ========== 入口操作 ==========
async function handleEntryClose() { await gateClose(entryLaneKey.value) }
async function handleEntryKeepOpen() { await toggleKeepOpen(entryLaneKey.value) }

const entryLockState = computed(() => {
  const key = getLockKey(entryLaneKey.value)
  return laneLockState.value[key] || { open: false, close: false }
})

// ========== 出口操作 ==========
async function handleExitClose() { await gateClose(exitLaneKey.value) }
async function handleExitKeepOpen() { await toggleKeepOpen(exitLaneKey.value) }

const exitLockState = computed(() => {
  const key = getLockKey(exitLaneKey.value)
  return laneLockState.value[key] || { open: false, close: false }
})

// ========== 闸控通用 ==========
function getLockKey(key: string) { const p = parseLaneKey(key); return p.direction ? `${p.laneId}-${p.direction}` : `${p.laneId}` }

async function gateClose(laneKey: string) {
  const { laneId } = parseLaneKey(laneKey)
  if (!laneId) { message.warning('请先选择车道'); return }
  try {
    const { manualCloseGate } = await import('@/api/charge')
    const r = await manualCloseGate(laneId, '岗亭手动关闸')
    r.gateDeviceAck ? message.success('关闸成功') : message.warning(r.gateResult || '关闸失败')
  } catch (e: any) { message.error(e?.message || '关闸失败') }
}

async function toggleKeepOpen(laneKey: string) {
  const { laneId } = parseLaneKey(laneKey)
  if (!laneId) { message.warning('请先选择车道'); return }
  const lockKey = getLockKey(laneKey)
  if (!laneLockState.value[lockKey]) laneLockState.value[lockKey] = { open: false, close: false }
  const isLocked = laneLockState.value[lockKey]!.open
  try {
    const { manualLockGate, manualUnlockGate } = await import('@/api/charge')
    if (isLocked) {
      const r = await manualUnlockGate(laneId, '岗亭取消常开')
      r.gateDeviceAck ? (laneLockState.value[lockKey] = { open: false, close: false }, message.success('取消常开成功')) : message.warning(r.gateResult || '取消常开失败')
    } else {
      const r = await manualLockGate(laneId, '岗亭设置常开')
      r.gateDeviceAck ? (laneLockState.value[lockKey] = { open: true, close: false }, message.success('常开成功')) : message.warning(r.gateResult || '常开失败')
    }
  } catch (e: any) { message.error(e?.message || (isLocked ? '取消常开失败' : '常开失败')) }
}

function manualReleaseOpenGate(laneKey: string, direction: number) {
  const { laneId } = parseLaneKey(laneKey)
  if (!laneId) { message.warning('请先选择车道'); return }
  const latest = store.recentEvents.find(e => e.laneId === laneId)
  manualReleaseLaneId.value = laneId
  manualReleasePlate.value = latest?.correctedPlate || latest?.plateNumber || ''
  manualReleaseDirection.value = direction
  manualReleaseOpen.value = true
}

function handleManualReleaseResult(r: { success: boolean; message: string }) {
  manualReleaseOpen.value = false
  if (r.success) {
    message.success('开闸成功')
    loadPresentVehicles() // 刷新通行记录
  } else {
    message.warning(r.message || '开闸失败')
  }
}

async function handleSaveFeeRule(data: any) {
  try {
    const { updateFeeRule } = await import('@/api/charge')
    if (currentFeeRule.value?.id) { await updateFeeRule(currentFeeRule.value.id, data); message.success('收费规则已更新') }
    feeRuleEditOpen.value = false
  } catch (e: any) { message.error(e?.message || '更新收费规则失败') }
}

// ========== 远程开闸 ==========
function showRemoteGateAlert(payload: RemoteGateAlertPayload) {
  if (remoteGateDismissTimer) { clearTimeout(remoteGateDismissTimer); remoteGateDismissTimer = null }
  currentRemoteGateAlert.value = payload; remoteGateModalVisible.value = true
  remoteGateDismissTimer = setTimeout(() => { remoteGateModalVisible.value = false; remoteGateDismissTimer = null }, (payload.autoDismissSeconds || 10) * 1000)
}

function handleCaptureError(e: Event) {
  const el = e.target as HTMLImageElement
  if (!el.dataset.retried) { el.dataset.retried = '1'; setTimeout(() => { el.src = `${el.src.split('?')[0]}?_r=1` }, 1500) }
  else { el.style.visibility = 'hidden' }
}
function handleCorrectionDone() {}

function handleSpaceAdjustSuccess() {
  // WebSocket 推送会自动更新 store 中的 remainingSpaces，
  // 这里额外刷新快照以确保数据一致
  if (selectedLotId.value) {
    store.loadSnapshot(selectedLotId.value).catch(() => {})
  }
}

async function refreshDevices() {
  if (!selectedLotId.value) { message.warning('请先选择停车场'); return }
  try { await store.refreshAllDevices(); await refreshGateCapabilities(); message.success('设备状态已刷新') }
  catch (e: any) { message.error(e?.message || '刷新失败') }
}

// ========== WebSocket ==========
function getToken() { return sessionStorage.getItem(TOKEN_KEY) }

function buildWsClient(lotId: number) {
  const token = getToken()
  if (!token) { message.error('登录已过期'); return }
  wsClient?.disconnect()
  wsClient = new MonitorWebSocketClient({
    token, parkingLotId: lotId,
    handlers: {
      onConnectionChange: s => store.setConnectionStatus(s),
      onSpaceUpdate: (p: SpaceUpdatePayload) => store.handleSpaceUpdate(p),
      onRecognitionEvent: (p: RecognitionEventPayload) => {
        if (!store.handleRecognitionEvent(p)) return; playAlertSound()
        loadPresentVehicles()
        // 出场识别：自动弹出收费面板（白名单已自动出场则不会弹出）
        if (p.direction === 'EXIT' && p.plateNumber) {
          store.showChargePanel(p.plateNumber, p.laneId).catch(() => {})
        }
      },
      onDeviceStatus: (p: DeviceStatus) => store.handleDeviceStatus(p),
      onAlert: (p: any) => store.handleAlert(p),
      onRemoteGateAlert: (p: RemoteGateAlertPayload) => showRemoteGateAlert(p),
      onError: e => console.warn('WS 错误:', e),
    },
  })
  wsClient.connect()
}

let audioCtx: AudioContext | null = null
function playAlertSound() {
  try {
    if (!audioCtx) audioCtx = new AudioContext()
    const o = audioCtx.createOscillator(); const g = audioCtx.createGain(); o.connect(g); g.connect(audioCtx.destination)
    o.type = 'sine'; o.frequency.setValueAtTime(800, audioCtx.currentTime); o.frequency.setValueAtTime(1000, audioCtx.currentTime + 0.1)
    g.gain.setValueAtTime(0.3, audioCtx.currentTime); g.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.3)
    o.start(audioCtx.currentTime); o.stop(audioCtx.currentTime + 0.3)
  } catch {}
}

// ========== 生命周期 ==========
watch(() => store.lanes, lanes => {
  for (const lane of lanes) {
    const mode = (lane as any).gateMode
    const state = mode === 'ALWAYS_OPEN' ? { open: true, close: false } : mode === 'ALWAYS_CLOSE' ? { open: false, close: true } : { open: false, close: false }
    if (lane.direction === 'MIXED') { laneLockState.value[`${lane.id}-ENTRY`] = { ...state }; laneLockState.value[`${lane.id}-EXIT`] = { ...state } }
    else laneLockState.value[`${lane.id}`] = state
  }
}, { immediate: true, deep: true })

onMounted(() => {
  loadLotOptions(); updateTime(); timeTimer = setInterval(updateTime, 1000)
  const saved = sessionStorage.getItem(LOT_ID_KEY)
  if (saved) handleLotSelect(Number(saved))
})

onUnmounted(() => {
  if (timeTimer) { clearInterval(timeTimer); timeTimer = null }
  wsClient?.disconnect()
  if (remoteGateDismissTimer) { clearTimeout(remoteGateDismissTimer); remoteGateDismissTimer = null }
})
</script>

<style lang="scss" scoped>
.monitor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f3f6fa;
  overflow: hidden;
}

// ========== 顶部导航栏第一层 ==========
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 48px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;

  .header-left { display: flex; align-items: center; gap: 8px; }
  .header-right { display: flex; align-items: center; gap: 14px; }

  .header-brand { font-size: 16px; font-weight: 700; color: #1f2937; }
  .header-title { font-size: 14px; font-weight: 600; color: #374151; }
  .online-dot { width: 8px; height: 8px; border-radius: 50%; background: #10b981; flex-shrink: 0; margin-left: 2px; }
  .online-label { font-size: 12px; color: #10b981; }
  .lot-select { width: 150px; }
  .current-time { color: #6b7280; font-size: 13px; font-variant-numeric: tabular-nums; }
  .username { color: #374151; font-size: 14px; }
}

// ========== 常开/常关横幅 ==========
.gate-mode-banners { flex-shrink: 0; }
.gate-mode-banner-item { border-radius: 0; margin: 0; }

// ========== 主体 ==========
.monitor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

// ========== 四宫格 ==========
.main-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 6px;
  padding: 6px;
  min-width: 0;
}

.grid-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;

  .lane-select { width: 150px; }
}

.card-status {
  font-size: 13px;
  &.online { color: #10b981; }
  &.offline { color: #ef4444; }
}

.card-video {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #f5f6f8;
}
.video-icon { font-size: 48px; color: #c5cdd8; }
.video-text { font-size: 14px; color: #9ca3af; margin: 0; }

.card-actions {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
  flex-shrink: 0;
  justify-content: center;

  :deep(.ant-btn) {
    font-size: 13px;
    height: 32px;
    padding: 0 16px;
    border-radius: 4px;
  }

  .btn-release { border-color: #d1d5db; color: #6b7280; }
  .btn-keep { background: #165dff; border-color: #165dff; }
  .btn-keep-close { border-color: #f59e0b; color: #f59e0b; }
  .btn-close { background: #dc2626; border-color: #dc2626; color: #fff; &:hover { background: #b91c1c; border-color: #b91c1c; color: #fff; } }
  .btn-open { background: #10b981; border-color: #10b981; }
}

// ========== 车辆详情卡片 ==========
.vehicle-detail-card { padding: 0; }

.detail-layout {
  display: flex;
  gap: 0;
  height: 100%;
}

.detail-thumbs {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
  width: 48%;
  padding: 10px;
  padding-right: 6px;
}

.thumb-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.detail-thumb {
  width: 100%;
  flex: 1;
  background: #f5f6f8;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  :deep(.ant-image) {
    width: 100%;
    height: 100%;
    .ant-image-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }
  }

  .thumb-placeholder { font-size: 28px; color: #d1d5db; padding: 16px 0; }
}

.thumb-label {
  font-size: 12px;
  color: #6b7280;
}

.detail-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0;
  padding: 10px;
  padding-left: 6px;
  min-width: 0;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 6px 0;
}

.info-label { font-size: 14px; color: #6b7280; width: 64px; flex-shrink: 0; text-align: right; margin-right: 10px; }
.info-value { font-size: 15px; color: #1f2937; font-weight: 500; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; &.fee { color: #dc2626; } }

.detail-empty {
  display: flex; align-items: center; justify-content: center; height: 100%;
}

// ========== 通行记录 ==========
.records-table-card { padding: 0; }

.records-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.records-title { font-size: 14px; font-weight: 600; color: #374151; }
.records-tools { display: flex; align-items: center; gap: 8px; }

.records-table {
  flex: 1; overflow: auto;
  :deep(.ant-table) { font-size: 13px; }
  :deep(.ant-table-thead th) { background: #fafbfc; font-size: 12px; padding: 8px 10px; }
  :deep(.ant-table-tbody td) { padding: 6px 10px; }
}

.table-thumb { width: 44px; height: auto; display: block; border-radius: 2px; cursor: pointer; }

// ========== 右侧侧边栏 ==========
.right-sidebar {
  width: 200px;
  min-width: 200px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #e5e7eb;
  overflow: hidden;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}
.sidebar-title { font-size: 14px; font-weight: 600; color: #374151; }
.sidebar-icon { font-size: 15px; color: #9ca3af; cursor: pointer; &:hover { color: #6b7280; } }

.sidebar-stats {
  padding: 18px 14px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f5f6f8;
  &:last-child { border-bottom: none; }
  &.clickable {
    cursor: pointer;
    border-radius: 4px;
    padding: 8px 6px;
    margin: 0 -6px;
    transition: background 0.15s;
    &:hover { background: #e6f7ff; }
  }
}
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 18px; font-weight: 700; color: #1f2937; &.remaining { color: #10b981; } }

.sidebar-messages {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
