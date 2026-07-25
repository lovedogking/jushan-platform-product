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
    <a-modal v-model:open="modalVisible" :title="editing ? '编辑设备' : '新增设备'" @ok="handleSave" :confirm-loading="saving" width="720px">
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

      <!-- 设备控制（仅在编辑模式下显示） -->
      <template v-if="editing">
        <a-divider style="margin: 16px 0 12px">设备控制</a-divider>
        <a-tabs v-model:activeKey="controlTab" size="small">
          <!-- 显示屏文字 -->
          <a-tab-pane key="display" tab="显示屏">
            <a-form layout="vertical" style="margin-top: 8px">
              <a-form-item label="显示内容">
                <a-textarea
                  v-model:value="displayContent"
                  :rows="3"
                  placeholder="输入要显示的文字，如：欢迎光临"
                  :maxlength="200"
                  show-count
                />
              </a-form-item>
              <a-button type="primary" :loading="displaySending" @click="sendDisplay" block>
                发送显示
              </a-button>
            </a-form>
          </a-tab-pane>

          <!-- 语音播报 -->
          <a-tab-pane key="voice" tab="语音播报">
            <a-form layout="vertical" style="margin-top: 8px">
              <a-form-item label="播报文字">
                <a-textarea
                  v-model:value="voiceText"
                  :rows="2"
                  placeholder="输入要播报的文字，如：京A12345,欢迎光临"
                  :maxlength="200"
                  show-count
                />
              </a-form-item>
              <a-space>
                <a-button type="primary" :loading="voicePlaying" @click="playVoice">
                  <SoundOutlined /> 播放
                </a-button>
                <a-button danger :loading="voiceStopping" @click="stopVoice">
                  停止
                </a-button>
              </a-space>
            </a-form>
          </a-tab-pane>

          <!-- 识别联动配置 -->
          <a-tab-pane key="linkage" tab="识别联动">
            <a-form layout="vertical" style="margin-top: 8px">
              <a-form-item label="自动播报">
                <a-switch v-model:checked="voiceEnabled" checked-children="开" un-checked-children="关" />
                <span style="margin-left:8px;color:#888;font-size:12px">识别到车牌后自动播报语音 + 显示文字</span>
              </a-form-item>

              <!-- 允许通行 -->
              <a-card size="small" :title="linkageAllowLabel" style="margin-bottom:12px">
                <a-row :gutter="12">
                  <a-col :span="12">
                    <a-form-item label="播报语音" style="margin-bottom:4px">
                      <a-input v-model:value="voiceWelcomeMsg" :placeholder="voiceWelcomePlaceholder" size="small" />
                    </a-form-item>
                  </a-col>
                  <a-col :span="12">
                    <a-form-item label="显示文字" style="margin-bottom:4px">
                      <a-input v-model:value="displayWelcomeMsg" :placeholder="displayWelcomePlaceholder" size="small" />
                    </a-form-item>
                  </a-col>
                </a-row>
                <div v-if="voiceWelcomeMsg" style="background:#f6ffed;padding:6px 10px;border-radius:4px;font-size:12px;color:#52c41a">
                  例：播报「<b>{{ voiceWelcomePreview }}</b>」屏幕显示「<b>{{ displayWelcomePreview }}</b>」
                </div>
              </a-card>

              <!-- 禁止通行 -->
              <a-card size="small" :title="linkageDenyLabel" style="margin-bottom:8px">
                <a-row :gutter="12">
                  <a-col :span="12">
                    <a-form-item label="播报语音" style="margin-bottom:4px">
                      <a-input v-model:value="voiceDenyMsg" :placeholder="voiceDenyPlaceholder" size="small" />
                    </a-form-item>
                  </a-col>
                  <a-col :span="12">
                    <a-form-item label="显示文字" style="margin-bottom:4px">
                      <a-input v-model:value="displayDenyMsg" :placeholder="displayDenyPlaceholder" size="small" />
                    </a-form-item>
                  </a-col>
                </a-row>
                <div v-if="voiceDenyMsg" style="background:#fff2f0;padding:6px 10px;border-radius:4px;font-size:12px;color:#ff4d4f">
                  例：播报「<b>{{ voiceDenyPreview }}</b>」屏幕显示「<b>{{ displayDenyPreview }}</b>」
                </div>
              </a-card>

              <span style="color:#888;font-size:11px">车牌号自动加在最前面，不用填 {plate}。留空则不触发。</span>

              <a-divider style="margin:12px 0 8px">待机恢复</a-divider>
              <a-row :gutter="12">
                <a-col :span="16">
                  <a-form-item label="识别后显示几秒自动恢复为" style="margin-bottom:0">
                    <a-input v-model:value="displayIdleText" placeholder="飓山智慧停车（留空不自动恢复）" size="small" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="停留秒数" style="margin-bottom:0">
                    <a-input-number v-model:value="displayDurationSec" :min="1" :max="60" size="small" style="width:100%" />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { PlusOutlined, SoundOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getDevices, createDevice, updateDevice, updateDeviceStatus, deleteDevice,
  getParkingLanes, bindDeviceLane, unbindDeviceLane,
  deviceDisplayText, deviceVoiceControl,
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

// ============ 设备控制（显示屏 / 语音） ============

const controlTab = ref('display')
const displayContent = ref('')
const displaySending = ref(false)
const voiceText = ref('')
const voicePlaying = ref(false)
const voiceStopping = ref(false)

// 识别联动配置
const voiceEnabled = ref(false)
const voiceWelcomeMsg = ref('')
const displayWelcomeMsg = ref('')
const voiceDenyMsg = ref('')
const displayDenyMsg = ref('')
const displayIdleText = ref('')
const displayDurationSec = ref(5)

// 预览（系统自动拼车牌号）
const samplePlate = '川A88888'
const voiceWelcomePreview = computed(() => voiceWelcomeMsg.value ? `${samplePlate},${extractMsg(voiceWelcomeMsg.value)}` : '')
const displayWelcomePreview = computed(() => displayWelcomeMsg.value ? `${samplePlate} ${extractMsg(displayWelcomeMsg.value)}` : '')
const voiceDenyPreview = computed(() => voiceDenyMsg.value ? `${samplePlate},${extractMsg(voiceDenyMsg.value)}` : '')
const displayDenyPreview = computed(() => displayDenyMsg.value ? `${samplePlate} ${extractMsg(displayDenyMsg.value)}` : '')

// 从完整模板提取消息文本（去掉 "{plate},"、"{plate}\n" 或 "{plate}" 前缀）
function extractMsg(template: string | undefined | null): string {
  if (!template) return ''
  let t = template
  if (t.startsWith('{plate},')) t = t.substring(8)
  else if (t.startsWith('{plate}\n')) t = t.substring(8)
  else if (t.startsWith('{plate}')) t = t.substring(7)
  return t
}

// 根据相机方向动态标签
const linkageAllowLabel = computed(() => {
  const dir = editing.value?.recognitionDirection
  return dir === 2 ? '允许出场（已缴费）' : '允许入场（白名单）'
})
const linkageDenyLabel = computed(() => {
  const dir = editing.value?.recognitionDirection
  return dir === 2 ? '禁止出场（未缴费）' : '禁止入场（黑名单）'
})
const voiceWelcomePlaceholder = computed(() => editing.value?.recognitionDirection === 2 ? '一路顺风' : '欢迎光临')
const displayWelcomePlaceholder = computed(() => editing.value?.recognitionDirection === 2 ? '一路顺风' : '欢迎光临')
const voiceDenyPlaceholder = computed(() => editing.value?.recognitionDirection === 2 ? '请缴费后出场' : '禁止通行')
const displayDenyPlaceholder = computed(() => editing.value?.recognitionDirection === 2 ? '请缴费后出场' : '禁止通行')


async function sendDisplay() {
  if (!displayContent.value.trim()) {
    message.warning('请输入显示内容')
    return
  }
  if (!editing.value) return
  displaySending.value = true
  try {
    const res = await deviceDisplayText(editing.value.id, {
      content: displayContent.value,
    })
    if (res.success) {
      message.success('显示内容已发送')
    } else {
      message.error(res.message || '发送失败')
    }
  } catch {
    message.error('发送失败，请检查设备连接')
  } finally {
    displaySending.value = false
  }
}

async function playVoice() {
  if (!voiceText.value.trim()) {
    message.warning('请输入播报文字')
    return
  }
  if (!editing.value) return
  voicePlaying.value = true
  try {
    const res = await deviceVoiceControl(editing.value.id, {
      action: 'PLAY',
      voiceText: voiceText.value,
      opt: 1,
    })
    if (res.success) {
      message.success('语音播报已发送')
    } else {
      message.error(res.message || '播报失败')
    }
  } catch {
    message.error('播报失败，请检查设备连接')
  } finally {
    voicePlaying.value = false
  }
}

async function stopVoice() {
  if (!editing.value) return
  voiceStopping.value = true
  try {
    const res = await deviceVoiceControl(editing.value.id, { action: 'STOP' })
    if (res.success) {
      message.success('语音已停止')
    } else {
      message.error(res.message || '停止失败')
    }
  } catch {
    message.error('停止失败，请检查设备连接')
  } finally {
    voiceStopping.value = false
  }
}

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
  // 识别联动配置
  voiceEnabled.value = device.voiceEnabled === 1
  voiceWelcomeMsg.value = extractMsg(device.voiceWelcomeTemplate)
  voiceDenyMsg.value = extractMsg(device.voiceDenyTemplate)
  displayWelcomeMsg.value = extractMsg(device.displayWelcomeTemplate)
  displayDenyMsg.value = extractMsg(device.displayDenyTemplate)
  displayIdleText.value = device.displayIdleText || ''
  displayDurationSec.value = device.displayDurationSec ?? 5
  // 重置控制面板
  displayContent.value = ''
  voiceText.value = ''
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
        voiceEnabled: voiceEnabled.value ? 1 : 0,
        voiceWelcomeTemplate: voiceWelcomeMsg.value ? `{plate},${extractMsg(voiceWelcomeMsg.value)}` : null,
        voiceDenyTemplate: voiceDenyMsg.value ? `{plate},${extractMsg(voiceDenyMsg.value)}` : null,
        displayWelcomeTemplate: displayWelcomeMsg.value ? `{plate} ${extractMsg(displayWelcomeMsg.value)}` : null,
        displayDenyTemplate: displayDenyMsg.value ? `{plate} ${extractMsg(displayDenyMsg.value)}` : null,
        displayIdleText: displayIdleText.value || null,
        displayDurationSec: displayDurationSec.value,
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
  await deleteDevice(id)
  message.success('设备已删除')
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
watch(() => props.lotId, () => {
  fetchDevices()
  fetchLanes()
})
</script>

<style lang="scss" scoped>
.device-manager { }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar .title { font-weight: 600; font-size: 14px; }
</style>
