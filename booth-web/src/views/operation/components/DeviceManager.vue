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
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="设备名称" required>
              <a-input v-model:value="deviceForm.name" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="相机序列号" required>
              <a-input v-model:value="deviceForm.deviceSn" />
            </a-form-item>
          </a-col>
        </a-row>
        <DeviceFormFields v-model="deviceFormFields" />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getDevices, createDevice, updateDevice, updateDeviceStatus, deleteDevice,
  type DeviceVO
} from '@/api/parking-manage'
import DeviceFormFields, { type DeviceFormData } from './DeviceFormFields.vue'

const props = defineProps<{ lotId: number }>()

const devices = ref<DeviceVO[]>([])
const loading = ref(false)
const modalVisible = ref(false)
const saving = ref(false)
const editing = ref<DeviceVO | null>(null)

const deviceForm = reactive({
  name: '',
  deviceSn: '',
  vendorId: undefined as number | undefined,
  modelId: undefined as number | undefined,
})

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
  deviceForm.name = ''
  deviceForm.deviceSn = ''
  deviceForm.vendorId = undefined
  deviceForm.modelId = undefined
  Object.assign(deviceFormFields.value, {
    name: '', deviceSn: '', vendorId: undefined, modelId: undefined,
    ipAddress: '', port: 80, subnetMask: '', gateway: '',
    deviceType: 'CAMERA', recognitionDirection: 1,
  })
  modalVisible.value = true
}

function showEditModal(device: DeviceVO) {
  editing.value = device
  deviceForm.name = device.name
  deviceForm.deviceSn = device.deviceSn
  modalVisible.value = true
}

async function handleSave() {
  if (!deviceForm.name.trim() || !deviceForm.deviceSn.trim()) {
    message.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateDevice(editing.value.id, {
        name: deviceForm.name,
        ipAddress: deviceFormFields.value.ipAddress,
        port: deviceFormFields.value.port,
        subnetMask: deviceFormFields.value.subnetMask,
        gateway: deviceFormFields.value.gateway,
      })
      message.success('设备更新成功')
    } else {
      await createDevice({
        parkingLotId: props.lotId,
        vendorId: deviceFormFields.value.vendorId || 0,
        modelId: deviceFormFields.value.modelId || 0,
        name: deviceForm.name,
        code: deviceForm.deviceSn,
        deviceSn: deviceForm.deviceSn,
        deviceType: 'CAMERA',
        ipAddress: deviceFormFields.value.ipAddress,
        port: deviceFormFields.value.port,
        subnetMask: deviceFormFields.value.subnetMask,
        gateway: deviceFormFields.value.gateway,
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

onMounted(() => fetchDevices())
</script>

<style lang="scss" scoped>
.device-manager { }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar .title { font-weight: 600; font-size: 14px; }
</style>
