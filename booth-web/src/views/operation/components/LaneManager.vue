<template>
  <div class="lane-manager">
    <div class="toolbar">
      <span class="title">车道列表</span>
      <a-button type="primary" size="small" @click="showCreateModal"><PlusOutlined /> 新增车道</a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="lanes"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'type'">
          <a-tag>{{ typeToLabel(record.type) }}</a-tag>
        </template>
        <template v-if="column.key === 'gateMode'">
          <a-tag :color="gateModeColor(record.gateMode)">{{ gateModeToLabel(record.gateMode) }}</a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'red'">{{ record.status === 1 ? '启用' : '停用' }}</a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
          <a-popconfirm title="确定删除此车道？" @confirm="handleDelete(record.id)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑车道弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingLane ? '编辑车道' : '新增车道'"
      @ok="handleSave"
      :confirm-loading="saving"
      width="640px"
    >
      <a-form :model="laneForm" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="车道编号" required>
              <a-input v-model:value="laneForm.laneNo" placeholder="如 A1" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="车道名称" required>
              <a-input v-model:value="laneForm.name" placeholder="如 东门入口" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="出入口类型" required>
              <a-select v-model:value="laneForm.type" :options="directionOptions" @change="onDirectionChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="闸机模式">
              <a-select v-model:value="laneForm.gateMode" :options="gateModeOptions" />
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 设备配置区域 -->
        <a-divider>设备配置</a-divider>
        <a-tabs v-model:activeKey="deviceTab" v-if="laneForm.type !== undefined">
          <!-- 入口相机（入口和双向都显示） -->
          <a-tab-pane
            v-if="laneForm.type === 1 || laneForm.type === 3"
            key="entry"
            tab="入口相机"
          >
            <DeviceFormFields v-model="entryDeviceForm" />
          </a-tab-pane>
          <!-- 出口相机（出口和双向都显示） -->
          <a-tab-pane
            v-if="laneForm.type === 2 || laneForm.type === 3"
            key="exit"
            tab="出口相机"
          >
            <DeviceFormFields v-model="exitDeviceForm" />
          </a-tab-pane>
        </a-tabs>
        <div v-else style="color: #999; text-align: center; padding: 20px;">
          请先选择出入口类型
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getParkingLanes, createParkingLane, updateParkingLane, deleteParkingLane,
  createDevice, type ParkingLaneVO
} from '@/api/parking-manage'
import DeviceFormFields from './DeviceFormFields.vue'
import type { DeviceFormData } from './DeviceFormFields.vue'

const props = defineProps<{ lotId: number }>()

const lanes = ref<ParkingLaneVO[]>([])
const loading = ref(false)
const modalVisible = ref(false)
const saving = ref(false)
const editingLane = ref<ParkingLaneVO | null>(null)
const deviceTab = ref('entry')

const directionOptions = [
  { value: 1, label: '入口' },
  { value: 2, label: '出口' },
  { value: 3, label: '双向' },
]

const gateModeOptions = [
  { value: 'AUTO', label: '自动 (AUTO)' },
  { value: 'ALWAYS_OPEN', label: '常开 (ALWAYS_OPEN)' },
  { value: 'ALWAYS_CLOSE', label: '常关 (ALWAYS_CLOSE)' },
]

const laneForm = reactive({
  name: '',
  laneNo: '',
  type: undefined as number | undefined,
  gateMode: 'AUTO' as string,
})

// 设备表单数据（使用 ref 兼容 DeviceFormFields 的 defineModel）
const entryDeviceForm = ref<DeviceFormData>(getDefaultDeviceForm(1))
const exitDeviceForm = ref<DeviceFormData>(getDefaultDeviceForm(2))

function getDefaultDeviceForm(direction: number) {
  return {
    name: direction === 1 ? '入口相机' : '出口相机',
    deviceSn: '',
    vendorId: undefined as number | undefined,
    modelId: undefined as number | undefined,
    ipAddress: '',
    port: 80,
    subnetMask: '',
    gateway: '',
    deviceType: 'CAMERA',
    recognitionDirection: direction,
  }
}

function gateModeColor(mode: string) {
  const map: Record<string, string> = { AUTO: 'blue', ALWAYS_OPEN: 'green', ALWAYS_CLOSE: 'orange' }
  return map[mode] || 'default'
}

function gateModeToLabel(mode: string) {
  const map: Record<string, string> = { AUTO: '自动', ALWAYS_OPEN: '常开', ALWAYS_CLOSE: '常关' }
  return map[mode] || mode
}

function typeToLabel(type: number) {
  const map: Record<number, string> = { 1: '入口', 2: '出口', 3: '双向' }
  return map[type] || String(type)
}

const columns = [
  { title: '车道编号', dataIndex: 'laneNo', key: 'laneNo' },
  { title: '车道名称', dataIndex: 'name', key: 'name' },
  { title: '类型', key: 'type' },
  { title: '闸机模式', key: 'gateMode' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 150 },
]

async function fetchLanes() {
  loading.value = true
  try {
    const res = await getParkingLanes({ page: 1, size: 100, parkingLotId: props.lotId })
    lanes.value = res.records
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  editingLane.value = null
  laneForm.name = ''
  laneForm.laneNo = ''
  laneForm.type = undefined
  laneForm.gateMode = 'AUTO'
  deviceTab.value = 'entry'
  resetDeviceForms()
  modalVisible.value = true
}

function showEditModal(lane: ParkingLaneVO) {
  editingLane.value = lane
  laneForm.name = lane.name
  laneForm.laneNo = lane.laneNo
  laneForm.type = lane.type
  laneForm.gateMode = lane.gateMode
  deviceTab.value = lane.type === 2 ? 'exit' : 'entry'
  resetDeviceForms()
  modalVisible.value = true
}

function resetDeviceForms() {
  entryDeviceForm.value = getDefaultDeviceForm(1)
  exitDeviceForm.value = getDefaultDeviceForm(2)
}

function onDirectionChange() {
  if (laneForm.type === 2) {
    deviceTab.value = 'exit'
  } else {
    deviceTab.value = 'entry'
  }
}

async function handleSave() {
  if (!laneForm.name.trim() || !laneForm.laneNo.trim() || laneForm.type === undefined) {
    message.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    let laneId: number
    if (editingLane.value) {
      const updated = await updateParkingLane(editingLane.value.id, {
        name: laneForm.name,
        laneNo: laneForm.laneNo,
        type: laneForm.type,
        gateMode: laneForm.gateMode,
      })
      laneId = updated.id
    } else {
      const created = await createParkingLane({
        lotId: props.lotId,
        name: laneForm.name,
        laneNo: laneForm.laneNo,
        type: laneForm.type,
        gateMode: laneForm.gateMode,
      })
      laneId = created.id
    }

    // 仅新建车道时同时创建设备（编辑时不处理设备）
    const isNew = !editingLane.value
    if (isNew) {
      if (laneForm.type === 1 || laneForm.type === 3) {
        await createDeviceForLane(entryDeviceForm.value, laneId, props.lotId)
      }
      if (laneForm.type === 2 || laneForm.type === 3) {
        await createDeviceForLane(exitDeviceForm.value, laneId, props.lotId)
      }
    }

    message.success(editingLane.value ? '车道更新成功' : '车道创建成功')
    modalVisible.value = false
    await fetchLanes()
  } finally {
    saving.value = false
  }
}

async function createDeviceForLane(form: any, laneId: number, lotId: number) {
  if (!form.deviceSn.trim() && !form.name.trim()) return
  await createDevice({
    parkingLotId: lotId,
    vendorId: form.vendorId || 0,
    modelId: form.modelId || 0,
    name: form.name,
    code: form.deviceSn,
    deviceSn: form.deviceSn,
    deviceType: 'CAMERA',
    laneId,
    ipAddress: form.ipAddress,
    port: form.port,
    subnetMask: form.subnetMask,
    gateway: form.gateway,
  })
}

async function handleDelete(id: number) {
  await deleteParkingLane(id)
  message.success('车道已删除')
  await fetchLanes()
}

onMounted(() => fetchLanes())
</script>

<style lang="scss" scoped>
.lane-manager { }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar .title { font-weight: 600; font-size: 14px; }
</style>
