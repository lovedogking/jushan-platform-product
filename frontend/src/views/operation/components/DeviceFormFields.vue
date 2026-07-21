<template>
  <div class="device-form-fields">
    <a-row :gutter="16">
      <a-col :span="12">
        <a-form-item label="设备名称" required>
          <a-input v-model:value="model.name" placeholder="如 东门入口相机" />
        </a-form-item>
      </a-col>
      <a-col :span="12">
        <a-form-item label="相机序列号" required>
          <a-input v-model:value="model.deviceSn" placeholder="厂商序列号" />
        </a-form-item>
      </a-col>
    </a-row>
    <a-row :gutter="16">
      <a-col :span="12">
        <a-form-item label="设备厂商" required>
          <a-select v-model:value="model.vendorId" :options="vendorOptions" placeholder="选择厂商" @change="onVendorChange" />
        </a-form-item>
      </a-col>
      <a-col :span="12">
        <a-form-item label="设备型号" required>
          <a-select v-model:value="model.modelId" :options="modelOptions" placeholder="如 臻识 C5" />
        </a-form-item>
      </a-col>
    </a-row>
    <a-row :gutter="16" v-if="showRecognitionDirection">
      <a-col :span="12">
        <a-form-item label="识别方向" required>
          <a-select v-model:value="model.recognitionDirection" :options="directionOptions" placeholder="选择识别方向" />
        </a-form-item>
      </a-col>
    </a-row>
    <a-divider style="margin: 8px 0">网络配置</a-divider>
    <a-row :gutter="16">
      <a-col :span="8">
        <a-form-item label="IP 地址">
          <a-input v-model:value="model.ipAddress" placeholder="192.168.1.100" />
        </a-form-item>
      </a-col>
      <a-col :span="4">
        <a-form-item label="端口">
          <a-input-number v-model:value="model.port" :min="1" :max="65535" style="width: 100%" />
        </a-form-item>
      </a-col>
      <a-col :span="6">
        <a-form-item label="子网掩码">
          <a-input v-model:value="model.subnetMask" placeholder="255.255.255.0" />
        </a-form-item>
      </a-col>
      <a-col :span="6">
        <a-form-item label="网关地址">
          <a-input v-model:value="model.gateway" placeholder="192.168.1.1" />
        </a-form-item>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getDeviceVendors, getDeviceModels, type DeviceVendor, type DeviceModel } from '@/api/parking-manage'

export interface DeviceFormData {
  name: string
  deviceSn: string
  vendorId?: number
  modelId?: number
  ipAddress: string
  port: number
  subnetMask: string
  gateway: string
  deviceType: string
  recognitionDirection: number
}

// Vue 3.4+ defineModel — 双向绑定自动同步，避免 deep watch 循环
const model = defineModel<DeviceFormData>({ required: true })

// 车道嵌套表单的场景下识别方向由入口/出口 Tab 隐含，可隐藏此选择器
withDefaults(defineProps<{ showRecognitionDirection?: boolean }>(), {
  showRecognitionDirection: true,
})

const directionOptions = [
  { value: 1, label: '入口' },
  { value: 2, label: '出口' },
]

const vendors = ref<DeviceVendor[]>([])
const models = ref<DeviceModel[]>([])

const vendorOptions = computed(() => vendors.value.map(v => ({ value: v.id, label: v.name })))
const modelOptions = computed(() =>
  models.value
    .filter(m => model.value.vendorId === undefined || m.vendorId === model.value.vendorId)
    .map(m => ({ value: m.id, label: m.name }))
)

function onVendorChange() {
  const current = models.value.find(m => m.id === model.value.modelId)
  if (!current || current.vendorId !== model.value.vendorId) {
    model.value.modelId = undefined
  }
}

onMounted(async () => {
  vendors.value = await getDeviceVendors()
  models.value = await getDeviceModels()
})
</script>
