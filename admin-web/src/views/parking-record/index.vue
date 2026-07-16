<template>
  <div class="parking-record-page">
    <!-- 筛选栏 -->
    <div class="query-bar">
      <a-space wrap>
        <a-input v-model:value="query.plateNumber" placeholder="车牌号" allow-clear style="width: 140px" @press-enter="handleQuery" />
        <a-select
          v-model:value="query.parkingLotId"
          placeholder="车场"
          allow-clear
          show-search
          :filter-option="filterLotOption"
          style="width: 180px"
          :options="lotOptions"
        />
        <a-select
          v-model:value="query.laneId"
          placeholder="入场通道"
          allow-clear
          style="width: 140px"
          :options="laneOptions"
        />
        <a-range-picker
          v-model:value="query.dateRange"
          :show-time="{ format: 'HH:mm' }"
          format="YYYY-MM-DD HH:mm"
          style="width: 340px"
        />
        <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 120px" :options="statusOptions" />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </a-space>
      <a-button type="primary" @click="handleExport">
        <template #icon><DownloadOutlined /></template>
        导出 Excel
      </a-button>
    </div>

    <!-- 通行记录列表 -->
    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">
            {{ record.statusLabel || RECORD_STATUS_MAP[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-if="column.key === 'amount'">
          <span v-if="record.feeAmount != null">¥ {{ formatYuan(record.feeAmount) }}</span>
          <span v-else>—</span>
        </template>
        <template v-if="column.key === 'paid'">
          <span v-if="record.paidAmount != null">¥ {{ formatYuan(record.paidAmount) }}</span>
          <span v-else>—</span>
        </template>
        <template v-if="column.key === 'duration'">
          {{ formatDuration(record.parkingDurationMinutes) || '—' }}
        </template>
        <template v-if="column.key === 'time'">
          <span>{{ formatDateTime(record.entryTime) }}</span>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="暂无通行记录" />
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'

import {
  getParkingRecordPage,
  exportParkingRecords,
  RECORD_STATUS_MAP,
  type ParkingRecordAdminVO,
} from '@/api/parking-record'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'
import { getParkingLanes, type ParkingLaneVO } from '@/api/parking-lane'

// ==================== 选项 ====================
const statusOptions = Object.entries(RECORD_STATUS_MAP).map(([value, label]) => ({ value, label }))

function statusColor(status: string) {
  switch (status) {
    case 'PARKING': return 'processing'
    case 'COMPLETED': return 'default'
    case 'CANCELLED': return 'default'
    default: return 'default'
  }
}

// ==================== 表格列 ====================
const columns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 120 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 180 },
  { title: '状态', key: 'status', width: 90 },
  { title: '入场时间', key: 'time', width: 160 },
  { title: '出场时间', dataIndex: 'exitTime', key: 'exitTime', width: 160 },
  { title: '停车时长', key: 'duration', width: 110 },
  { title: '应收金额', key: 'amount', width: 100 },
  { title: '实付金额', key: 'paid', width: 100 },
  { title: '支付方式', dataIndex: 'payChannelLabel', key: 'payChannel', width: 100 },
  { title: '入场通道', dataIndex: 'entryLaneName', key: 'entryLane', width: 110 },
  { title: '操作人', dataIndex: 'operatorName', key: 'operator', width: 100 },
]

// ==================== 查询 ====================
const query = reactive({
  plateNumber: '',
  parkingLotId: undefined as number | undefined,
  laneId: undefined as number | undefined,
  dateRange: undefined as [Dayjs, Dayjs] | undefined,
  status: undefined as string | undefined,
})

const loading = ref(false)
const dataSource = ref<ParkingRecordAdminVO[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

async function fetchData() {
  loading.value = true
  try {
    const params: any = {
      page: pagination.current,
      size: pagination.pageSize,
    }
    if (query.plateNumber?.trim()) params.plateNumber = query.plateNumber.trim()
    if (query.parkingLotId) params.parkingLotId = query.parkingLotId
    if (query.laneId) params.laneId = query.laneId
    if (query.status) params.status = query.status
    if (query.dateRange && query.dateRange.length === 2) {
      params.startTime = query.dateRange[0]?.format('YYYY-MM-DDTHH:mm:ss')
      params.endTime = query.dateRange[1]?.format('YYYY-MM-DDTHH:mm:ss')
    }
    const res = await getParkingRecordPage(params)
    dataSource.value = res.records || []
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  query.plateNumber = ''
  query.parkingLotId = undefined
  query.laneId = undefined
  query.dateRange = undefined
  query.status = undefined
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

// ==================== 车场选项 ====================
const lotOptions = ref<{ value: number; label: string }[]>([])
async function loadLotOptions() {
  try {
    const res = await getParkingLots({ page: 1, size: 999 })
    lotOptions.value = (res.records || []).map((lot: ParkingLotVO) => ({
      value: lot.id,
      label: lot.name,
    }))
  } catch {
    // 静默失败
  }
}
function filterLotOption(input: string, option: { value: number; label: string } | undefined) {
  return option?.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

// ==================== 通道选项 ====================
const laneOptions = ref<{ value: number; label: string }[]>([])
async function loadLaneOptions() {
  try {
    const res = await getParkingLanes({ page: 1, size: 999 })
    laneOptions.value = (res.records || []).map((lane: ParkingLaneVO) => ({
      value: lane.id,
      label: lane.name || lane.laneNo,
    }))
  } catch {
    // 静默失败
  }
}

// ==================== 导出 ====================
async function handleExport() {
  try {
    const params: any = {}
    if (query.plateNumber?.trim()) params.plateNumber = query.plateNumber.trim()
    if (query.parkingLotId) params.parkingLotId = query.parkingLotId
    if (query.laneId) params.laneId = query.laneId
    if (query.status) params.status = query.status
    if (query.dateRange && query.dateRange.length === 2) {
      params.startTime = query.dateRange[0]?.format('YYYY-MM-DDTHH:mm:ss')
      params.endTime = query.dateRange[1]?.format('YYYY-MM-DDTHH:mm:ss')
    }

    const blob = await exportParkingRecords(params)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const fileName = `通行记录导出_${dayjs().format('YYYYMMDD_HHmmss')}.csv`
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (err: any) {
    if (err?.response?.data instanceof Blob) {
      const text = await err.response.data.text()
      try {
        const json = JSON.parse(text)
        if (json.message) {
          message.warning(json.message)
          return
        }
      } catch {
        // 不是 JSON，忽略
      }
    }
    message.error('导出失败')
  }
}

// ==================== 工具函数 ====================
function formatYuan(cents?: number) {
  if (cents == null) return '0.00'
  return (cents / 100).toFixed(2)
}

function formatDateTime(dt?: string) {
  if (!dt) return ''
  return dayjs(dt).format('YYYY-MM-DD HH:mm:ss')
}

function formatDuration(minutes?: number) {
  if (minutes == null || minutes <= 0) return ''
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  if (hours > 0) {
    return `${hours}小时${mins}分钟`
  }
  return `${mins}分钟`
}

// ==================== 初始化 ====================
onMounted(() => {
  fetchData()
  loadLotOptions()
  loadLaneOptions()
})
</script>

<style lang="scss" scoped>
.parking-record-page {
  background: #fff;
  border-radius: $border-radius-base;
  padding: $spacing-lg;
}

.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
