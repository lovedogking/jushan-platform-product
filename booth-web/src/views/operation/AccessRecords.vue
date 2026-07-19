<template>
  <div class="access-records-page">
    <div class="page-header">
      <h3>通行记录 (OP-04 / GB-06)</h3>
    </div>

    <a-card class="filter-bar">
      <a-form layout="inline" :model="filters">
        <a-form-item label="车牌号">
          <a-input v-model:value="filters.plateNumber" placeholder="请输入车牌号" allow-clear />
        </a-form-item>
        <a-form-item label="入场触发方式">
          <a-select v-model:value="filters.entryTrigger" placeholder="全部" allow-clear style="width: 160px">
            <a-select-option value="whitelist_auto">白名单自动</a-select-option>
            <a-select-option value="manual_open">人工放行</a-select-option>
            <a-select-option value="always_open_period">常开时段</a-select-option>
            <a-select-option value="manual_entry">人工补录</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="会话状态">
          <a-select v-model:value="filters.status" placeholder="全部" allow-clear style="width: 140px">
            <a-select-option value="IN">在场</a-select-option>
            <a-select-option value="OUT">已出场</a-select-option>
            <a-select-option value="EXCEPTION">异常</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="table-card">
      <a-table
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'entryTrigger'">
            <a-tag v-if="record.entryTrigger === 'whitelist_auto'" color="green">白名单自动</a-tag>
            <a-tag v-else-if="record.entryTrigger === 'manual_open'" color="blue">人工放行</a-tag>
            <a-tag v-else-if="record.entryTrigger === 'manual_entry'" color="orange">人工补录</a-tag>
            <template v-else>-</template>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag v-if="record.status === 'IN'" color="processing">在场</a-tag>
            <a-tag v-else-if="record.status === 'OUT'" color="default">已出场</a-tag>
            <a-tag v-else-if="record.status === 'EXCEPTION'" color="error">异常</a-tag>
            <template v-else>{{ record.status }}</template>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { getParkingSessions } from '@/api/vehicle-query'
import type { ParkingSessionVO } from '@/api/monitor-types'

interface SessionRecord extends ParkingSessionVO {
  laneName?: string
  parkingLotName?: string
}

const loading = ref(false)
const records = ref<SessionRecord[]>([])

const filters = reactive({
  plateNumber: '',
  entryTrigger: undefined as string | undefined,
  status: undefined as string | undefined,
})

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
})

const columns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber' },
  { title: '入场时间', dataIndex: 'entryTime', key: 'entryTime' },
  { title: '出场时间', dataIndex: 'exitTime', key: 'exitTime' },
  { title: '触发方式', key: 'entryTrigger' },
  { title: '状态', key: 'status' },
  { title: '通道ID', dataIndex: 'laneId', key: 'laneId' },
  { title: '车场ID', dataIndex: 'parkingLotId', key: 'parkingLotId' },
]

async function loadData() {
  loading.value = true
  try {
    const result = await getParkingSessions({
      current: pagination.current,
      size: pagination.pageSize,
      plateNumber: filters.plateNumber || undefined,
      status: filters.status || undefined,
    })
    records.value = result.records || []
    pagination.total = result.total || 0
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadData()
}

function handleReset() {
  filters.plateNumber = ''
  filters.entryTrigger = undefined
  filters.status = undefined
  pagination.current = 1
  loadData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadData()
}
</script>

<style lang="scss" scoped>
.access-records-page {
  .page-header { margin-bottom: 16px; h3 { margin: 0; } }
  .filter-bar { margin-bottom: 16px; }
  .table-card { overflow: auto; }
}
</style>
