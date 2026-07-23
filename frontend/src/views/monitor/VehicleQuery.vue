<template>
  <div class="vehicle-query">
    <a-tabs v-model:activeKey="queryTab">
      <a-tab-pane key="present" tab="在场车辆">
        <div class="query-toolbar">
          <a-space>
            <span>排序：</span>
            <a-select v-model:value="presentSortBy" style="width: 130px" @change="loadPresentVehicles">
              <a-select-option value="entryTime">入场时间</a-select-option>
              <a-select-option value="duration">停车时长</a-select-option>
            </a-select>
            <a-select v-model:value="presentSortDir" style="width: 80px" @change="loadPresentVehicles">
              <a-select-option value="desc">降序</a-select-option>
              <a-select-option value="asc">升序</a-select-option>
            </a-select>
          </a-space>
        </div>
        <a-table
          :data-source="presentVehicles"
          :columns="presentColumns"
          :pagination="presentPagination"
          :loading="loadingPresent"
          row-key="parkingRecordId"
          size="small"
      @change="handlePresentPageChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'vehicleType'">
              <a-tag :color="vehicleTypeColor(record.vehicleType)">
                {{ vehicleTypeLabel(record.vehicleType) }}
              </a-tag>
              <a-tag v-if="record.isMonthlyPass" color="green">月卡</a-tag>
              <a-tag v-if="record.isFixedSpace" color="blue">固定车位</a-tag>
            </template>
            <template v-if="column.key === 'durationMinutes'">
              {{ formatDuration(record.durationMinutes) }}
            </template>
            <template v-if="column.key === 'entryTime'">
              {{ formatTime(record.entryTime) }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="history" tab="历史记录">
        <div class="query-toolbar">
          <a-space wrap>
            <a-input v-model:value="historyPlateSearch" placeholder="车牌号（模糊）" allow-clear style="width: 140px" />
            <a-date-picker v-model:value="historyStartTime" placeholder="开始时间" />
            <a-date-picker v-model:value="historyEndTime" placeholder="结束时间" />
            <a-button type="primary" @click="loadHistoryRecords">查询</a-button>
          </a-space>
        </div>
        <a-table
          :data-source="historyRecords"
          :columns="historyColumns"
          :pagination="historyPagination"
          :loading="loadingHistory"
          row-key="plateNumber"
          size="small"
      @change="handleHistoryPageChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'entryImage'">
              <a-image
                v-if="record.entryImage"
                :src="record.entryImage"
                :width="64"
                :height="48"
                style="object-fit: cover; border-radius: 4px"
              />
              <span v-else>-</span>
            </template>
            <template v-if="column.key === 'feeAmount'">
              {{ record.feeAmount != null ? `¥${record.feeAmount.toFixed(2)}` : '-' }}
            </template>
            <template v-if="column.key === 'paymentStatus'">
              <a-tag v-if="record.paymentStatus === 'PAID'" color="success">已支付</a-tag>
              <a-tag v-else-if="record.paymentStatus === 'UNPAID'" color="warning">待支付</a-tag>
              <span v-else>-</span>
            </template>
            <template v-if="column.key === 'entryTime'">
              {{ formatTime(record.entryTime) }}
            </template>
            <template v-if="column.key === 'exitTime'">
              {{ record.exitTime ? formatTime(record.exitTime) : '-' }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import dayjs from 'dayjs'
import {
  getPresentVehicles,
  getVehicleHistory,
  type PresentVehicle,
  type VehicleHistoryRecord,
} from '@/api/vehicle-query'

const props = defineProps<{
  parkingLotId: number
}>()

// 在场车辆
const queryTab = ref('present')
const presentVehicles = ref<PresentVehicle[]>([])
const loadingPresent = ref(false)
const presentSortBy = ref<'entryTime' | 'duration'>('entryTime')
const presentSortDir = ref<'asc' | 'desc'>('desc')
const presentPagination = ref({ current: 1, pageSize: 10, total: 0 })

const presentColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 140 },
  { title: '入场时间', key: 'entryTime', width: 170 },
  { title: '停车时长', key: 'durationMinutes', width: 130 },
  { title: '车辆类型', key: 'vehicleType', width: 160 },
]

// 历史记录
const historyRecords = ref<VehicleHistoryRecord[]>([])
const loadingHistory = ref(false)
const historyPlateSearch = ref('')
const historyStartTime = ref<any>(null)
const historyEndTime = ref<any>(null)
const historyPagination = ref({ current: 1, pageSize: 10, total: 0 })

const historyColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 140 },
  { title: '抓拍图', key: 'entryImage', width: 90 },
  { title: '入场时间', key: 'entryTime', width: 170 },
  { title: '出场时间', key: 'exitTime', width: 170 },
  { title: '费用', key: 'feeAmount', width: 100 },
  { title: '支付状态', key: 'paymentStatus', width: 100 },
  { title: '车道', dataIndex: 'laneName', key: 'laneName', width: 100 },
]

function vehicleTypeLabel(type: string): string {
  const map: Record<string, string> = {
    WHITE: '固定车',
    TEMP: '临时车',
    TEMPORARY: '临时车',
    MONTHLY: '月卡',
    FIXED: '固定车位',
  }
  return map[type] || type || '未知'
}

function vehicleTypeColor(type: string): string {
  const map: Record<string, string> = {
    WHITE: 'green',
    TEMP: 'orange',
    TEMPORARY: 'orange',
    MONTHLY: 'green',
    FIXED: 'blue',
  }
  return map[type] || 'default'
}

function formatDuration(minutes: number): string {
  if (minutes == null || minutes < 0) return '-'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}时${m}分` : `${m}分`
}

function formatTime(t: string): string {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function loadPresentVehicles() {
  loadingPresent.value = true
  try {
    const result = await getPresentVehicles({
      parkingLotId: props.parkingLotId,
      sortBy: presentSortBy.value,
      sortDir: presentSortDir.value,
      page: presentPagination.value.current,
      size: presentPagination.value.pageSize,
    })
    presentVehicles.value = result.records || []
    presentPagination.value.total = result.total || 0
  } catch {
    presentVehicles.value = []
  } finally {
    loadingPresent.value = false
  }
}

function handlePresentPageChange(pag: { current: number; pageSize: number }) {
  presentPagination.value.current = pag.current
  presentPagination.value.pageSize = pag.pageSize
  loadPresentVehicles()
}

async function loadHistoryRecords() {
  loadingHistory.value = true
  try {
    const result = await getVehicleHistory({
      parkingLotId: props.parkingLotId,
      plateNumber: historyPlateSearch.value || undefined,
      startTime: historyStartTime.value ? dayjs(historyStartTime.value).format('YYYY-MM-DD 00:00:00') : undefined,
      endTime: historyEndTime.value ? dayjs(historyEndTime.value).format('YYYY-MM-DD 23:59:59') : undefined,
      page: historyPagination.value.current,
      size: historyPagination.value.pageSize,
    })
    historyRecords.value = result.records || []
    historyPagination.value.total = result.total || 0
  } catch {
    historyRecords.value = []
  } finally {
    loadingHistory.value = false
  }
}

function handleHistoryPageChange(pag: { current: number; pageSize: number }) {
  historyPagination.value.current = pag.current
  historyPagination.value.pageSize = pag.pageSize
  loadHistoryRecords()
}

onMounted(() => {
  loadPresentVehicles()
})

watch(() => props.parkingLotId, () => {
  loadPresentVehicles()
  historyRecords.value = []
})
</script>

<style lang="scss" scoped>
.vehicle-query {
  padding: 8px;
}

.query-toolbar {
  margin-bottom: 12px;
}
</style>
