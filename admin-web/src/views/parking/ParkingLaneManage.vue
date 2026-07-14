<template>
  <div class="parking-lane-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select
          v-model:value="queryLotId"
          placeholder="选择停车场"
          allow-clear
          style="width: 200px"
          @change="handleLotChange"
        >
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select
          v-model:value="queryZoneId"
          placeholder="选择区域"
          allow-clear
          style="width: 180px"
          :disabled="!queryLotId"
          @change="handleQuery"
        >
          <a-select-option v-for="zone in zoneOptions" :key="zone.id" :value="zone.id">{{ zone.name }}</a-select-option>
        </a-select>
        <a-select v-model:value="queryType" placeholder="全部类型" allow-clear style="width: 120px" @change="handleQuery">
          <a-select-option v-for="opt in LANE_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 120px" @change="handleQuery">
          <a-select-option v-for="opt in LANE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <a-button type="primary" :disabled="!queryLotId" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增通道
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
        <template v-if="column.key === 'type'">
          <a-tag :color="typeColor(record.type)">
            {{ typeText(record.type) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">
            {{ statusText(record.status) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'cameraMode'">
          {{ cameraModeText(record.cameraMode) }}
        </template>
        <template v-if="column.key === 'tideMode'">
          {{ tideModeText(record.tideMode) }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a-divider type="vertical" />
            <a v-if="record.status !== 1" @click="handleToggleStatus(record, 1)">启用</a>
            <a v-else style="color: #dc2626" @click="handleToggleStatus(record, 2)">禁用</a>
            <a-divider type="vertical" />
            <a-popconfirm title="确认删除该通道？" @confirm="handleDelete(record)">
              <a style="color: #dc2626">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="formModalOpen"
      :title="formModalTitle"
      :confirm-loading="formLoading"
      width="640px"
      @ok="handleFormSubmit"
    >
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="所属停车场" required>
          <a-select v-model:value="formData.lotId" placeholder="请选择停车场" :disabled="isEditing" @change="handleFormLotChange">
            <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="所属区域">
          <a-select v-model:value="formData.zoneId" placeholder="请选择区域" :disabled="!formData.lotId || isEditing" allow-clear>
            <a-select-option v-for="zone in formZoneOptions" :key="zone.id" :value="zone.id">{{ zone.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="通道编号" required>
          <a-input v-model:value="formData.laneNo" placeholder="如：A1" />
        </a-form-item>
        <a-form-item label="通道名称" required>
          <a-input v-model:value="formData.name" placeholder="如：东大门入口" />
        </a-form-item>
        <a-form-item label="通道类型" required>
          <a-select v-model:value="formData.type" placeholder="请选择通道类型">
            <a-select-option v-for="opt in LANE_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="showEntryCamera" label="入口相机ID">
          <a-input-number v-model:value="formData.entryCameraId" placeholder="入口相机ID" style="width: 100%" />
        </a-form-item>
        <a-form-item v-if="showExitCamera" label="出口相机ID">
          <a-input-number v-model:value="formData.exitCameraId" placeholder="出口相机ID" style="width: 100%" />
        </a-form-item>
        <a-form-item v-if="formData.type === 3" label="潮汐模式">
          <a-select v-model:value="formData.tideMode" placeholder="请选择潮汐模式">
            <a-select-option v-for="opt in TIDE_MODE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="formData.type === 3" label="相机模式">
          <a-select v-model:value="formData.cameraMode" placeholder="请选择相机模式">
            <a-select-option v-for="opt in CAMERA_MODE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="formData.status" placeholder="请选择状态">
            <a-select-option v-for="opt in LANE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import {
  getParkingLanes,
  createParkingLane,
  updateParkingLane,
  deleteParkingLane,
  updateParkingLaneStatus,
  LANE_TYPE_OPTIONS,
  LANE_STATUS_OPTIONS,
  TIDE_MODE_OPTIONS,
  CAMERA_MODE_OPTIONS,
  type ParkingLaneVO,
} from '@/api/parking-lane'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'
import { getParkingZonesByLotId, type ParkingZoneVO } from '@/api/parking-zone'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '通道编号', dataIndex: 'laneNo', key: 'laneNo', width: 100 },
  { title: '通道名称', dataIndex: 'name', key: 'name', width: 140 },
  { title: '类型', key: 'type', width: 90 },
  { title: '相机模式', key: 'cameraMode', width: 110 },
  { title: '潮汐模式', key: 'tideMode', width: 120 },
  { title: '状态', key: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<ParkingLaneVO[]>([])
const queryType = ref<number | undefined>(undefined)
const queryStatus = ref<number | undefined>(undefined)
const queryLotId = ref<number | undefined>(undefined)
const queryZoneId = ref<number | undefined>(undefined)
const parkingLotOptions = ref<ParkingLotVO[]>([])
const zoneOptions = ref<ParkingZoneVO[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 新增/编辑弹窗
const formModalOpen = ref(false)
const formLoading = ref(false)
const formModalTitle = ref('')
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive({
  lotId: undefined as number | undefined,
  zoneId: undefined as number | undefined,
  laneNo: '',
  name: '',
  type: 1,
  entryCameraId: undefined as number | undefined,
  exitCameraId: undefined as number | undefined,
  tideMode: 0,
  cameraMode: 1,
  status: 1,
})
const formZoneOptions = ref<ParkingZoneVO[]>([])

const showEntryCamera = computed(() => formData.type === 1 || formData.type === 3)
const showExitCamera = computed(() => formData.type === 2 || formData.type === 3)

async function fetchData() {
  loading.value = true
  try {
    const res = await getParkingLanes({
      current: pagination.current,
      size: pagination.pageSize,
      lotId: queryLotId.value,
      zoneId: queryZoneId.value,
      type: queryType.value,
      status: queryStatus.value,
    })
    dataSource.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function loadParkingLots() {
  try {
    const res = await getParkingLots({ current: 1, size: 100 })
    parkingLotOptions.value = res.records
  } catch {
    // ignore
  }
}

async function loadZonesByLotId(lotId: number) {
  try {
    const res = await getParkingZonesByLotId(lotId)
    return res
  } catch {
    return []
  }
}

async function handleLotChange() {
  queryZoneId.value = undefined
  if (queryLotId.value) {
    zoneOptions.value = await loadZonesByLotId(queryLotId.value)
  } else {
    zoneOptions.value = []
  }
  handleQuery()
}

async function handleFormLotChange() {
  formData.zoneId = undefined
  if (formData.lotId) {
    formZoneOptions.value = await loadZonesByLotId(formData.lotId)
  } else {
    formZoneOptions.value = []
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryType.value = undefined
  queryStatus.value = undefined
  queryLotId.value = undefined
  queryZoneId.value = undefined
  zoneOptions.value = []
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

function handleCreate() {
  isEditing.value = false
  editingId.value = null
  formModalTitle.value = '新增通道'
  formData.lotId = queryLotId.value
  formData.zoneId = queryZoneId.value
  formData.laneNo = ''
  formData.name = ''
  formData.type = 1
  formData.entryCameraId = undefined
  formData.exitCameraId = undefined
  formData.tideMode = 0
  formData.cameraMode = 1
  formData.status = 1
  if (formData.lotId) {
    handleFormLotChange()
  }
  formModalOpen.value = true
}

function handleEdit(record: any) {
  isEditing.value = true
  editingId.value = record.id
  formModalTitle.value = '编辑通道'
  formData.lotId = record.lotId
  formData.zoneId = record.zoneId || undefined
  formData.laneNo = record.laneNo
  formData.name = record.name
  formData.type = record.type || 1
  formData.entryCameraId = record.entryCameraId || undefined
  formData.exitCameraId = record.exitCameraId || undefined
  formData.tideMode = record.tideMode || 0
  formData.cameraMode = record.cameraMode || 1
  formData.status = record.status || 1
  if (formData.lotId) {
    handleFormLotChange()
  }
  formModalOpen.value = true
}

async function handleFormSubmit() {
  if (!formData.lotId) {
    message.warning('请选择所属停车场')
    return
  }
  if (!formData.laneNo.trim()) {
    message.warning('请输入通道编号')
    return
  }
  if (!formData.name.trim()) {
    message.warning('请输入通道名称')
    return
  }
  formLoading.value = true
  try {
    const payload = {
      lotId: formData.lotId,
      zoneId: formData.zoneId,
      laneNo: formData.laneNo.trim(),
      name: formData.name.trim(),
      type: formData.type,
      entryCameraId: formData.entryCameraId,
      exitCameraId: formData.exitCameraId,
      tideMode: formData.type === 3 ? formData.tideMode : undefined,
      cameraMode: formData.type === 3 ? formData.cameraMode : undefined,
      status: formData.status,
    }
    if (isEditing.value && editingId.value) {
      await updateParkingLane(editingId.value, payload)
      message.success('更新成功')
    } else {
      await createParkingLane(payload)
      message.success('创建成功')
    }
    formModalOpen.value = false
    fetchData()
  } catch {
    // 错误已处理
  } finally {
    formLoading.value = false
  }
}

function handleToggleStatus(record: any, status: number) {
  const actionText = status === 1 ? '启用' : '禁用'
  updateParkingLaneStatus(record.id, status).then(() => {
    message.success(`已${actionText}`)
    fetchData()
  }).catch(() => {})
}

async function handleDelete(record: any) {
  try {
    await deleteParkingLane(record.id)
    message.success('删除成功')
    fetchData()
  } catch {
    // ignore
  }
}

function typeText(type: number) {
  const opt = LANE_TYPE_OPTIONS.find(o => o.value === type)
  return opt?.label || '未知'
}

function typeColor(type: number) {
  const map: Record<number, string> = { 1: 'blue', 2: 'orange', 3: 'purple' }
  return map[type] || 'default'
}

function statusText(status: number) {
  const opt = LANE_STATUS_OPTIONS.find(o => o.value === status)
  return opt?.label || '未知'
}

function statusColor(status: number) {
  const map: Record<number, string> = { 1: 'green', 2: 'red', 3: 'orange' }
  return map[status] || 'default'
}

function cameraModeText(mode: number) {
  const opt = CAMERA_MODE_OPTIONS.find(o => o.value === mode)
  return opt?.label || '-'
}

function tideModeText(mode: number) {
  const opt = TIDE_MODE_OPTIONS.find(o => o.value === mode)
  return opt?.label || '-'
}

onMounted(() => {
  loadParkingLots()
  fetchData()
})
</script>

<style lang="scss" scoped>
.parking-lane-page {
  background: #fff;
  border-radius: $border-radius-base;
  padding: $spacing-lg;
}

.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}
</style>
