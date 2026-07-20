<template>
  <div class="device-manager">
    <div class="toolbar">
      <span class="title">设备列表</span>
      <a-button type="primary" size="small" @click="showCreateModal"><PlusOutlined /> 新增设备</a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="devices"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'deviceType'">
          <a-tag :color="record.deviceType === 'CAMERA' ? 'blue' : 'orange'">
            {{ record.deviceType === 'CAMERA' ? '相机' : '闸机' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'red'">
            {{ record.status === 'ENABLED' ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
          <a-popconfirm title="确定删除此设备？" @confirm="handleDelete(record.id)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑设备弹窗 -->
    <a-modal v-model:open="modalVisible" :title="editing ? '编辑设备' : '新增设备'" @ok="handleSave" :confirm-loading="saving" width="640px">
      <a-form layout="vertical">
        <a-form-item label="绑定车道">
          <a-select
            v-model:value="bindLaneId"
            :options="laneOptions"
            placeholder="选择车道（可不绑定）"
            allow-clear
          />
        </a-form-item>
        <DeviceFormFields v-model="deviceFormFields" />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getDevices, createDevice, updateDevice, updateDeviceStatus,
  getParkingLanes, bindDeviceLane, unbindDeviceLane,
  type DeviceVO, type ParkingLaneVO
} from '@/api/parking-manage'
import DeviceFormFields, { type DeviceFormData } from './DeviceFormFields.vue'

const props = defineProps<{ lotId: number }>()

const devices = ref<DeviceVO[]>([])
const loading = ref(false)
const modalVisible = ref(false)
const saving = ref(false)
const editing = ref<DeviceVO | null>(null)
const bindLaneId = ref<number | undefined>(undefined)
const lanes = ref<ParkingLaneVO[]>([])

const laneOptions = computed(() => lanes.value.map(l => ({ value: l.id, label: l.name })))

const deviceFormFields = ref<DeviceFormData>({
  name: '',
  deviceSn: '',
  vendorId: undefined,
  modelId: undefined,
  ipAddress: '',
  port: 80,
  subnetMask: '',
  gateway: '',
  deviceType: 'CAMERA',
  recognitionDirection: 1,
})

const columns = [
  { title: '设备名称', dataIndex: 'name', key: 'name' },
  { title: '类型', key: 'deviceType' },
  { title: '序列号', dataIndex: 'deviceSn', key: 'deviceSn' },
  { title: '绑定车道', dataIndex: 'laneName', key: 'laneName' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 150 },
]

async function fetchDevices() {
  loading.value = true
  try {
    const res = await getDevices({ page: 1, size: 100, parkingLotId: props.lotId })
    devices.value = res.records
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  editing.value = null
  bindLaneId.value = undefined
  Object.assign(deviceFormFields.value, {
    name: '', deviceSn: '', vendorId: undefined, modelId: undefined,
    ipAddress: '', port: 80, subnetMask: '', gateway: '',
    deviceType: 'CAMERA', recognitionDirection: 1,
  })
  modalVisible.value = true
}

function showEditModal(device: DeviceVO) {
  editing.value = device
  bindLaneId.value = device.laneId ?? undefined
  Object.assign(deviceFormFields.value, {
    name: device.name,
    deviceSn: device.deviceSn,
    vendorId: device.vendorId,
    modelId: device.modelId,
    ipAddress: device.ipAddress || '',
    port: device.port || 80,
    subnetMask: device.subnetMask || '',
    gateway: device.gateway || '',
    deviceType: device.deviceType || 'CAMERA',
    recognitionDirection: device.recognitionDirection ?? 1,
  })
  modalVisible.value = true
}

async function handleSave() {
  const f = deviceFormFields.value
  if (!f.name.trim() || !f.deviceSn.trim() || f.vendorId === undefined || f.modelId === undefined) {
    message.warning('请填写必填项：设备名称、相机序列号、设备厂商、设备型号')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      const deviceId = editing.value.id
      await updateDevice(deviceId, {
        name: f.name,
        vendorId: f.vendorId,
        modelId: f.modelId,
        deviceSn: f.deviceSn,
        recognitionDirection: f.recognitionDirection,
        ipAddress: f.ipAddress,
        port: f.port,
        subnetMask: f.subnetMask,
        gateway: f.gateway,
      })
      // 车道绑定变更走独立接口（后端 update 不处理 laneId）
      const oldLaneId = editing.value.laneId ?? null
      const newLaneId = bindLaneId.value ?? null
      if (oldLaneId !== newLaneId) {
        if (newLaneId !== null) {
          await bindDeviceLane(deviceId, newLaneId)
        } else {
          await unbindDeviceLane(deviceId)
        }
      }
      message.success('设备更新成功')
    } else {
      await createDevice({
        parkingLotId: props.lotId,
        vendorId: f.vendorId!,
        modelId: f.modelId!,
        name: f.name,
        code: f.deviceSn,
        deviceSn: f.deviceSn,
        deviceType: 'CAMERA',
        laneId: bindLaneId.value ?? null,
        recognitionDirection: f.recognitionDirection,
        ipAddress: f.ipAddress,
        port: f.port,
        subnetMask: f.subnetMask,
        gateway: f.gateway,
      })
      message.success('设备创建成功')
    }
    modalVisible.value = false
    await fetchDevices()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  // 先禁用再提示（后端无 physical delete for device）
  await updateDeviceStatus(id, 'DISABLED')
  message.success('设备已停用')
  await fetchDevices()
}

async function fetchLanes() {
  const res = await getParkingLanes({ page: 1, size: 100, parkingLotId: props.lotId })
  lanes.value = res.records
}

onMounted(() => {
  fetchDevices()
  fetchLanes()
})
</script>

<style lang="scss" scoped>
.device-manager { }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar .title { font-weight: 600; font-size: 14px; }
</style>
