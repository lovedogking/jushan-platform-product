<template>
  <div class="manual-gate-page">
    <!-- 筛选栏 -->
    <div class="query-bar">
      <a-space wrap>
        <a-range-picker
          v-model:value="query.dateRange"
          :show-time="{ format: 'HH:mm' }"
          format="YYYY-MM-DD HH:mm"
          style="width: 340px"
        />
        <a-select
          v-model:value="query.parkingLotId"
          placeholder="车场"
          allow-clear
          show-search
          :filter-option="filterLotOption"
          style="width: 180px"
          :options="lotOptions"
        />
        <a-input v-model:value="query.operatorName" placeholder="操作人" allow-clear style="width: 140px" @press-enter="handleQuery" />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </a-space>
    </div>

    <!-- 开闸记录列表 -->
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
          <a-tag :color="STATUS_COLOR_MAP[record.commandStatus] || 'default'">
            {{ COMMAND_STATUS_MAP[record.commandStatus] || record.commandStatus }}
          </a-tag>
        </template>
        <template v-if="column.key === 'fee'">
          <span v-if="record.feeCents != null">¥ {{ formatYuan(record.feeCents) }}</span>
          <span v-else>—</span>
        </template>
        <template v-if="column.key === 'plate'">
          <span v-if="record.plateNumber">{{ record.plateNumber }}</span>
          <span v-else class="text-muted">—</span>
        </template>
        <template v-if="column.key === 'reason'">
          <span v-if="record.reason">{{ record.reason }}</span>
          <span v-else class="text-muted">—</span>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="暂无开闸记录" />
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'

import {
  getManualGateRecordPage,
  COMMAND_STATUS_MAP,
  STATUS_COLOR_MAP,
  type ManualGateRecordAdminVO,
} from '@/api/manual-gate-record'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

// ==================== 表格列 ====================
const columns = [
  { title: '操作人', dataIndex: 'operatorName', key: 'operator', width: 130 },
  { title: '操作时间', dataIndex: 'operationTime', key: 'time', width: 160 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'lot', width: 180 },
  { title: '通道', dataIndex: 'laneName', key: 'lane', width: 120 },
  { title: '放行原因', key: 'reason', width: 200 },
  { title: '车牌号', key: 'plate', width: 120 },
  { title: '状态', key: 'status', width: 100 },
  { title: '金额', key: 'fee', width: 100 },
]

// ==================== 查询 ====================
const query = reactive({
  dateRange: undefined as [Dayjs, Dayjs] | undefined,
  parkingLotId: undefined as number | undefined,
  operatorName: '',
})

const loading = ref(false)
const dataSource = ref<ManualGateRecordAdminVO[]>([])
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
    if (query.parkingLotId) params.parkingLotId = query.parkingLotId
    if (query.operatorName?.trim()) params.operatorName = query.operatorName.trim()
    if (query.dateRange && query.dateRange.length === 2) {
      params.startTime = query.dateRange[0]?.format('YYYY-MM-DDTHH:mm:ss')
      params.endTime = query.dateRange[1]?.format('YYYY-MM-DDTHH:mm:ss')
    }
    const res = await getManualGateRecordPage(params)
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
  query.dateRange = undefined
  query.parkingLotId = undefined
  query.operatorName = ''
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

// ==================== 工具函数 ====================
function formatYuan(cents?: number) {
  if (cents == null) return '0.00'
  return (cents / 100).toFixed(2)
}

// ==================== 初始化 ====================
onMounted(() => {
  fetchData()
  loadLotOptions()
})
</script>

<style lang="scss" scoped>
.manual-gate-page {
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

.text-muted {
  color: rgba(0, 0, 0, 0.25);
}
</style>
