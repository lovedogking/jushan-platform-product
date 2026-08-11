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
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="控闸设备">
              <a-select
                v-model:value="laneForm.gateDeviceId"
                :options="gateDeviceOptions"
                placeholder="自动检测（可不选）"
                allow-clear
              />
              <div style="color: #999; font-size: 11px">
                不选则由系统自动按设备能力和类型查找控闸设备
              </div>
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
            <DeviceFormFields v-model="entryDeviceForm" :show-recognition-direction="false" />
          </a-tab-pane>
          <!-- 出口相机（出口和双向都显示） -->
          <a-tab-pane
            v-if="laneForm.type === 2 || laneForm.type === 3"
            key="exit"
            tab="出口相机"
          >
            <DeviceFormFields v-model="exitDeviceForm" :show-recognition-direction="false" />
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
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getParkingLanes, createParkingLane, updateParkingLane, deleteParkingLane,
  getDevices, createDevice, updateDevice, type ParkingLaneVO, type DeviceVO
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
  gateDeviceId: undefined as number | undefined,
})

// 当前车场所有设备（用于控闸设备下拉）
const allLotDevices = ref<DeviceVO[]>([])

const gateDeviceOptions = computed(() =>
  allLotDevices.value.map(d => ({
    value: d.id,
    label: `${d.name} (${d.deviceSn})`,
  }))
)

// 设备表单数据（使用 ref 兼容 DeviceFormFields 的 defineModel）
const entryDeviceForm = ref<DeviceFormData>(getDefaultDeviceForm(1))
const exitDeviceForm = ref<DeviceFormData>(getDefaultDeviceForm(2))
// 编辑车道时已存在的相机设备（入口/出口）
const existingEntryDevice = ref<DeviceVO | null>(null)
const existingExitDevice = ref<DeviceVO | null>(null)

function getDefaultDeviceForm(direction: number) {
  return {
    name: '',
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
    // 同时加载车场所有设备（用于控闸设备下拉）
    const devRes = await getDevices({ page: 1, size: 500, parkingLotId: props.lotId })
    allLotDevices.value = devRes.records
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
  laneForm.gateDeviceId = undefined
  deviceTab.value = 'entry'
  resetDeviceForms()
  modalVisible.value = true
}

async function showEditModal(lane: ParkingLaneVO) {
  editingLane.value = lane
  laneForm.name = lane.name
  laneForm.laneNo = lane.laneNo
  laneForm.type = lane.type
  laneForm.gateMode = lane.gateMode
  laneForm.gateDeviceId = lane.gateDeviceId
  deviceTab.value = lane.type === 2 ? 'exit' : 'entry'
  resetDeviceForms()
  await loadLaneDevices(lane)
  modalVisible.value = true
}

async function loadLaneDevices(lane: ParkingLaneVO) {
  try {
    const res = await getDevices({ page: 1, size: 100, parkingLotId: props.lotId })
    const laneDevices = res.records.filter(d => String(d.laneId) === String(lane.id))
    existingEntryDevice.value = laneDevices.find(d => d.recognitionDirection === 1) || null
    existingExitDevice.value = laneDevices.find(d => d.recognitionDirection === 2) || null
    if (existingEntryDevice.value) entryDeviceForm.value = deviceToForm(existingEntryDevice.value, 1)
    if (existingExitDevice.value) exitDeviceForm.value = deviceToForm(existingExitDevice.value, 2)
  } catch {
    // 错误已由拦截器提示
  }
}

function deviceToForm(d: DeviceVO, direction: number): DeviceFormData {
  return {
    name: d.name || '',
    deviceSn: d.deviceSn || '',
    vendorId: d.vendorId,
    modelId: d.modelId,
    ipAddress: d.ipAddress || '',
    port: d.port ?? 80,
    subnetMask: d.subnetMask || '',
    gateway: d.gateway || '',
    deviceType: d.deviceType || 'CAMERA',
    recognitionDirection: direction,
  }
}

function resetDeviceForms() {
  entryDeviceForm.value = getDefaultDeviceForm(1)
  exitDeviceForm.value = getDefaultDeviceForm(2)
  existingEntryDevice.value = null
  existingExitDevice.value = null
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
  // 校验嵌套设备表单：完全空白则跳过，部分填写则四项必填
  const tabs: Array<{ form: DeviceFormData; label: string }> = []
  if (laneForm.type === 1 || laneForm.type === 3) tabs.push({ form: entryDeviceForm.value, label: '入口相机' })
  if (laneForm.type === 2 || laneForm.type === 3) tabs.push({ form: exitDeviceForm.value, label: '出口相机' })
  for (const { form, label } of tabs) {
    const allEmpty = !form.deviceSn.trim() && !form.name.trim() && form.vendorId === undefined && form.modelId === undefined
    if (allEmpty) continue
    if (!form.name.trim() || !form.deviceSn.trim() || form.vendorId === undefined || form.modelId === undefined) {
      message.warning(`${label}：设备名称、相机序列号、设备厂商、设备型号为必填项`)
      return
    }
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
        gateDeviceId: laneForm.gateDeviceId,
      })
      laneId = updated.id
    } else {
      const created = await createParkingLane({
        lotId: props.lotId,
        name: laneForm.name,
        laneNo: laneForm.laneNo,
        type: laneForm.type,
        gateMode: laneForm.gateMode,
        gateDeviceId: laneForm.gateDeviceId,
      })
      laneId = created.id
    }

    // 同步相机设备：已存在则更新，未填写则跳过，否则新建
    try {
      if (laneForm.type === 1 || laneForm.type === 3) {
        await syncDeviceForLane(entryDeviceForm.value, existingEntryDevice.value, laneId, props.lotId)
      }
      if (laneForm.type === 2 || laneForm.type === 3) {
        await syncDeviceForLane(exitDeviceForm.value, existingExitDevice.value, laneId, props.lotId)
      }
    } catch (e) {
      message.warning(editingLane.value
        ? '车道已更新，但相机保存失败，请重新编辑该车道补存相机'
        : '车道已创建，但相机保存失败，请编辑该车道补录相机')
      modalVisible.value = false
      await fetchLanes()
      return
    }

    message.success(editingLane.value ? '车道更新成功' : '车道创建成功')
    modalVisible.value = false
    await fetchLanes()
  } finally {
    saving.value = false
  }
}

async function syncDeviceForLane(form: DeviceFormData, existing: DeviceVO | null, laneId: number, lotId: number) {
  if (!form.deviceSn.trim() && !form.name.trim() && form.vendorId === undefined && form.modelId === undefined) return
  const payload = {
    vendorId: form.vendorId!,
    modelId: form.modelId!,
    name: form.name,
    code: form.deviceSn,
    deviceSn: form.deviceSn,
    deviceType: 'CAMERA',
    capabilities: 'OPEN_GATE,KEEP_OPEN',
    laneId,
    recognitionDirection: form.recognitionDirection,
    ipAddress: form.ipAddress,
    port: form.port,
    subnetMask: form.subnetMask,
    gateway: form.gateway,
  }
  if (existing) {
    await updateDevice(Number(existing.id), payload)
  } else {
    await createDevice({ parkingLotId: lotId, ...payload })
  }
}

async function handleDelete(id: number) {
  await deleteParkingLane(id)
  message.success('车道已删除')
  await fetchLanes()
}

onMounted(() => fetchLanes())
watch(() => props.lotId, () => fetchLanes())
</script>

<style lang="scss" scoped>
.lane-manager { }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar .title { font-weight: 600; font-size: 14px; }
</style>
