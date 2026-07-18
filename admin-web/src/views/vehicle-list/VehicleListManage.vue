<template>
  <div class="vehicle-list-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select
          v-model:value="queryLotId"
          placeholder="选择停车场"
          allow-clear
          style="width: 180px"
          @change="handleQuery"
        >
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select
          v-model:value="queryListType"
          placeholder="名单类型"
          allow-clear
          style="width: 130px"
          @change="handleQuery"
        >
          <a-select-option v-for="opt in LIST_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
        <a-input
          v-model:value="queryPlate"
          placeholder="车牌号"
          allow-clear
          style="width: 160px"
          @press-enter="handleQuery"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <a-button type="primary" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增名单
      </a-button>
    </div>

    <!-- 表格区 -->
    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'listType'">
          <a-tag :color="record.listType === 'BLACK' ? 'red' : 'green'">
            {{ LIST_TYPE_MAP[record.listType] || record.listTypeLabel }}
          </a-tag>
        </template>
        <template v-if="column.key === 'triggerType'">
          <a-tag :color="triggerTypeColor(record.triggerType)">
            {{ record.triggerTypeLabel || (record.triggerType ? TRIGGER_TYPE_MAP[record.triggerType] : '-') }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">
            {{ record.statusLabel || LIST_STATUS_MAP[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-if="column.key === 'validity'">
          <span v-if="record.startDate && record.endDate">
            {{ record.startDate }} ~ {{ record.endDate }}
          </span>
          <span v-else style="color: #999">永久有效</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a-divider type="vertical" />
            <a-popconfirm title="确认删除该名单记录？" @confirm="handleDelete(record)">
              <a style="color: #dc2626">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="暂无黑白名单数据" />
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <VehicleListFormModal
      v-model:open="formModalOpen"
      :is-editing="isEditing"
      :editing-record="editingRecord"
      :parking-lot-options="parkingLotOptions"
      @submit="handleFormSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import VehicleListFormModal from './VehicleListFormModal.vue'
import {
  getVehicleListPage,
  createVehicleList,
  updateVehicleList,
  deleteVehicleList,
  LIST_TYPE_OPTIONS,
  LIST_TYPE_MAP,
  TRIGGER_TYPE_MAP,
  LIST_STATUS_MAP,
  type VehicleListRecord,
} from '@/api/vehicle-list'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 140 },
  { title: '名单类型', key: 'listType', width: 100 },
  { title: '停车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 180 },
  { title: '有效期', key: 'validity', width: 200 },
  { title: '黑名单类型', key: 'triggerType', width: 120 },
  { title: '状态', key: 'status', width: 90 },
  { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<VehicleListRecord[]>([])
const queryLotId = ref<number | undefined>(undefined)
const queryListType = ref<string | undefined>(undefined)
const queryPlate = ref('')
const parkingLotOptions = ref<ParkingLotVO[]>([])

const pagination = reactive({
  current: 1, pageSize: 10, total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 新增/编辑弹窗
const formModalOpen = ref(false)
const isEditing = ref(false)
const editingRecord = ref<VehicleListRecord | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getVehicleListPage({
      page: pagination.current,
      size: pagination.pageSize,
      parkingLotId: queryLotId.value,
      listType: queryListType.value,
      plateNumber: queryPlate.value?.trim() || undefined,
    })
    dataSource.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function loadParkingLots() {
  try {
    const res = await getParkingLots({ page: 1, size: 100 })
    parkingLotOptions.value = res.records
  } catch { /* ignore */ }
}

function handleQuery() { pagination.current = 1; fetchData() }
function handleReset() {
  queryLotId.value = undefined; queryListType.value = undefined; queryPlate.value = ''
  pagination.current = 1; fetchData()
}
function handleTableChange(pag: any) {
  pagination.current = pag.current; pagination.pageSize = pag.pageSize; fetchData()
}

function handleCreate() {
  isEditing.value = false; editingRecord.value = null; formModalOpen.value = true
}

function handleEdit(record: VehicleListRecord) {
  isEditing.value = true; editingRecord.value = record; formModalOpen.value = true
}

async function handleFormSubmit(data: Record<string, any>) {
  try {
    if (isEditing.value && editingRecord.value) {
      await updateVehicleList(editingRecord.value.id, data)
      message.success('更新成功')
    } else {
      await createVehicleList(data as any)
      message.success('创建成功')
    }
    formModalOpen.value = false
    fetchData()
  } catch (err: any) {
    const msg = err?.message || ''
    if (msg.includes('互斥') || msg.includes('同时存在') || msg.includes('黑名单') || msg.includes('白名单')) {
      Modal.warning({ title: '互斥冲突', content: msg })
    }
  }
}

async function handleDelete(record: VehicleListRecord) {
  try {
    await deleteVehicleList(record.id)
    message.success('删除成功')
    fetchData()
  } catch { /* ignore */ }
}

function triggerTypeColor(type?: string) {
  switch (type) {
    case 'ARREARS': return 'red'
    case 'MANAGEMENT': return 'orange'
    case 'OTHER': return 'default'
    default: return 'default'
  }
}

function statusColor(status: string) {
  switch (status) {
    case 'ACTIVE': return 'green'
    case 'EXPIRED': return 'orange'
    case 'DISABLED': return 'default'
    default: return 'default'
  }
}

onMounted(() => { loadParkingLots(); fetchData() })
</script>

<style lang="scss" scoped>
.vehicle-list-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}
.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
</style>
