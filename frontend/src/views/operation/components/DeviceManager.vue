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
          <a-popconfirm title="确定触发识别？设备将立即抓拍识别" @confirm="handleTrigger(record.id)">
            <a-button type="link" size="small">触发识别</a-button>
          </a-popconfirm>
          <a-popconfirm title="确定重启此设备？" @confirm="handleReboot(record.id)">
            <a-button type="link" size="small">重启</a-button>
          </a-popconfirm>
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

          <!-- Tab 1: 识别联动 -->
          <a-tab-pane key="linkage" tab="识别联动">
            <div style="margin-top: 8px">
              <a-form-item label="启用自动联动">
                <a-switch v-model:checked="voiceEnabled" checked-children="开" un-checked-children="关" />
                <span style="margin-left:8px;color:#888;font-size:12px">识别车牌后自动播报语音和显示文字</span>
              </a-form-item>

              <!-- 入场 -->
              <div style="background:#f6ffed;border:1px solid #b7eb8f;border-radius:6px;padding:12px;margin-bottom:12px">
                <div style="font-weight:600;margin-bottom:8px;color:#389e0d">入场</div>
                <a-input v-model:value="voiceEntryWelcome" :placeholder="voiceEntryWelcomePlaceholder" size="small" style="margin-bottom:6px">
                  <template #addonBefore>播报</template>
                </a-input>
                <a-input v-model:value="displayEntryWelcome" :placeholder="displayEntryWelcomePlaceholder" size="small">
                  <template #addonBefore>显示</template>
                </a-input>
                <div v-if="voiceEntryWelcome || displayEntryWelcome" style="margin-top:6px;font-size:12px;color:#389e0d;line-height:1.6">
                  预览：播报「<b>{{ entryPreview.voice }}</b>」 屏幕「<b>{{ entryPreview.display }}</b>」
                </div>
              </div>

              <!-- 出场 -->
              <div style="background:#e6f7ff;border:1px solid #91d5ff;border-radius:6px;padding:12px;margin-bottom:12px">
                <div style="font-weight:600;margin-bottom:8px;color:#096dd9">出场</div>
                <a-input v-model:value="voiceExitWelcome" :placeholder="voiceExitWelcomePlaceholder" size="small" style="margin-bottom:6px">
                  <template #addonBefore>播报</template>
                </a-input>
                <a-input v-model:value="displayExitWelcome" :placeholder="displayExitWelcomePlaceholder" size="small">
                  <template #addonBefore>显示</template>
                </a-input>
                <div v-if="voiceExitWelcome || displayExitWelcome" style="margin-top:6px;font-size:12px;color:#096dd9;line-height:1.6">
                  预览：播报「<b>{{ exitPreview.voice }}</b>」 屏幕「<b>{{ exitPreview.display }}</b>」
                </div>
              </div>

              <!-- 禁止通行 -->
              <div style="background:#fff2f0;border:1px solid #ffccc7;border-radius:6px;padding:12px;margin-bottom:12px">
                <div style="font-weight:600;margin-bottom:8px;color:#cf1322">禁止通行</div>
                <a-input v-model:value="voiceDenyMsg" :placeholder="voiceDenyPlaceholder" size="small" style="margin-bottom:6px">
                  <template #addonBefore>播报</template>
                </a-input>
                <a-input v-model:value="displayDenyMsg" :placeholder="displayDenyPlaceholder" size="small">
                  <template #addonBefore>显示</template>
                </a-input>
                <div v-if="voiceDenyMsg || displayDenyMsg" style="margin-top:6px;font-size:12px;color:#cf1322;line-height:1.6">
                  预览：播报「<b>{{ denyPreview.voice }}</b>」 屏幕「<b>{{ denyPreview.display }}</b>」
                </div>
              </div>

              <span style="color:#999;font-size:11px">{plate}=车牌号 {type}=车辆类型。留空表示不触发该项。</span>

              <a-divider style="margin:12px 0 8px">待机恢复</a-divider>
              <a-row :gutter="8">
                <a-col :span="16">
                  <a-input v-model:value="displayIdleText" placeholder="识别后显示几秒恢复为此文字（留空不恢复）" size="small">
                    <template #addonBefore>待机文字</template>
                  </a-input>
                </a-col>
                <a-col :span="8">
                  <a-input-number v-model:value="displayDurationSec" :min="1" :max="60" size="small" style="width:100%">
                    <template #addonBefore>停留</template>
                    <template #addonAfter>秒</template>
                  </a-input-number>
                </a-col>
              </a-row>
            </div>
          </a-tab-pane>

          <!-- Tab 2: 设备参数 -->
          <a-tab-pane key="hardware" tab="设备参数">
            <a-form layout="vertical" style="margin-top: 8px">
              <a-card size="small" title="屏幕设置" style="margin-bottom:12px">
                <a-row :gutter="16">
                  <a-col :span="12">
                    <a-form-item label="文字颜色" style="margin-bottom:8px">
                      <a-select v-model:value="displayTextColor" size="small">
                        <a-select-option :value="0">白色</a-select-option>
                        <a-select-option :value="1">红色</a-select-option>
                        <a-select-option :value="2">蓝色</a-select-option>
                        <a-select-option :value="3">绿色</a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                  <a-col :span="12">
                    <a-form-item label="屏幕方向" style="margin-bottom:8px">
                      <a-select v-model:value="displayRotateMode" size="small">
                        <a-select-option :value="0">正常</a-select-option>
                        <a-select-option :value="1">上下翻转</a-select-option>
                      </a-select>
                    </a-form-item>
                  </a-col>
                </a-row>
                <a-form-item label="亮度" style="margin-bottom:4px">
                  <a-slider v-model:value="displayBrightness" :min="0" :max="5" :marks="{0:'熄',3:'中',5:'最亮'}" />
                </a-form-item>
              </a-card>

              <a-card size="small" title="语音设置">
                <a-row :gutter="16">
                  <a-col :span="12">
                    <a-form-item label="语音类型" style="margin-bottom:8px">
                      <a-radio-group v-model:value="voiceMale" size="small">
                        <a-radio :value="0">男声</a-radio>
                        <a-radio :value="1">女声</a-radio>
                      </a-radio-group>
                    </a-form-item>
                  </a-col>
                  <a-col :span="12">
                    <a-form-item label="屏显音量" style="margin-bottom:8px">
                      <a-slider v-model:value="displayVolume" :min="0" :max="5" :marks="{0:'静',5:'最大'}" />
                    </a-form-item>
                  </a-col>
                </a-row>
                <a-form-item label="语音音量" style="margin-bottom:4px">
                  <a-slider v-model:value="voiceVolume" :min="1" :max="100" :marks="{20:'20',50:'50',80:'80'}" />
                </a-form-item>
              </a-card>
            </a-form>
          </a-tab-pane>

          <!-- Tab 3: 手动测试 -->
          <a-tab-pane key="test" tab="手动测试">
            <div style="margin-top: 8px">
              <div style="background:#fafafa;border:1px solid #e8e8e8;border-radius:6px;padding:12px;margin-bottom:12px">
                <div style="font-weight:600;margin-bottom:8px">测试显示</div>
                <a-textarea v-model:value="displayContent" :rows="2" placeholder="输入测试文字，如：欢迎光临" :maxlength="200" size="small" />
                <a-button type="primary" :loading="displaySending" @click="sendDisplay" size="small" block style="margin-top:8px">发送显示</a-button>
              </div>
              <div style="background:#fafafa;border:1px solid #e8e8e8;border-radius:6px;padding:12px">
                <div style="font-weight:600;margin-bottom:8px">测试语音</div>
                <a-textarea v-model:value="voiceText" :rows="2" placeholder="输入测试文字，如：京A12345,欢迎光临" :maxlength="200" size="small" />
                <a-space style="margin-top:8px">
                  <a-button type="primary" :loading="voicePlaying" @click="playVoice" size="small"><SoundOutlined /> 播放</a-button>
                  <a-button danger :loading="voiceStopping" @click="stopVoice" size="small">停止</a-button>
                </a-space>
              </div>
            </div>
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
  deviceDisplayText, deviceVoiceControl, deviceReboot,
  deviceTriggerRecognition,
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

const controlTab = ref('linkage')
const displayContent = ref('')
const displaySending = ref(false)
const voiceText = ref('')
const voicePlaying = ref(false)
const voiceStopping = ref(false)

// 显示屏参数
const displayTextColor = ref(0)
const displayRotateMode = ref(0)
const displayBrightness = ref(3)
const displayVolume = ref(3)
const voiceVolume = ref(80)
const voiceMale = ref(0)

// 入场/出场模板
const voiceEntryWelcome = ref('')
const voiceExitWelcome = ref('')
const displayEntryWelcome = ref('')
const displayExitWelcome = ref('')

// 旧模板保留用于待机恢复

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
  // strip known prefixes
  if (t.startsWith('{plate},{type},')) t = t.substring(14)
  else if (t.startsWith('{plate} {type} ')) t = t.substring(14)
  else if (t.startsWith('{plate},')) t = t.substring(8)
  else if (t.startsWith('{plate} ')) t = t.substring(8)
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
const voiceEntryWelcomePlaceholder = '欢迎光临'
const voiceExitWelcomePlaceholder = '一路平安'
const displayEntryWelcomePlaceholder = '欢迎光临'
const displayExitWelcomePlaceholder = '一路平安'


// 预览
const SAMPLE_PLATE = '鲁Q12345'
const SAMPLE_TYPE = '临时车'
const entryPreview = computed(() => ({
  voice: voiceEntryWelcome.value ? `${SAMPLE_PLATE},${SAMPLE_TYPE},${voiceEntryWelcome.value}` : '',
  display: displayEntryWelcome.value ? `${SAMPLE_PLATE} ${SAMPLE_TYPE} ${displayEntryWelcome.value}` : '',
}))
const exitPreview = computed(() => ({
  voice: voiceExitWelcome.value ? `${SAMPLE_PLATE},${SAMPLE_TYPE},${voiceExitWelcome.value}` : '',
  display: displayExitWelcome.value ? `${SAMPLE_PLATE} ${SAMPLE_TYPE} ${displayExitWelcome.value}` : '',
}))
const denyPreview = computed(() => ({
  voice: voiceDenyMsg.value ? `${SAMPLE_PLATE},${voiceDenyMsg.value}` : '',
  display: displayDenyMsg.value ? `${SAMPLE_PLATE} ${displayDenyMsg.value}` : '',
}))

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
  // 入场/出场模板
  voiceEntryWelcome.value = extractMsg(device.voiceEntryWelcomeTemplate) || extractMsg(device.voiceWelcomeTemplate)
  voiceExitWelcome.value = extractMsg(device.voiceExitWelcomeTemplate)
  displayEntryWelcome.value = extractMsg(device.displayEntryWelcomeTemplate) || extractMsg(device.displayWelcomeTemplate)
  displayExitWelcome.value = extractMsg(device.displayExitWelcomeTemplate)
  // 旧模板保留兼容
  voiceWelcomeMsg.value = extractMsg(device.voiceWelcomeTemplate)
  voiceDenyMsg.value = extractMsg(device.voiceDenyTemplate)
  displayWelcomeMsg.value = extractMsg(device.displayWelcomeTemplate)
  displayDenyMsg.value = extractMsg(device.displayDenyTemplate)
  displayIdleText.value = device.displayIdleText || ''
  displayDurationSec.value = device.displayDurationSec ?? 5
  // 显示屏参数
  displayTextColor.value = device.displayTextColor ?? 0
  displayRotateMode.value = device.displayRotateMode ?? 0
  displayBrightness.value = device.displayBrightness ?? 3
  displayVolume.value = device.displayVolume ?? 3
  voiceVolume.value = device.voiceVolume ?? 80
  voiceMale.value = device.voiceMale ?? 0
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
        displayTextColor: displayTextColor.value,
        displayRotateMode: displayRotateMode.value,
        displayBrightness: displayBrightness.value,
        displayVolume: displayVolume.value,
        voiceVolume: voiceVolume.value,
        voiceMale: voiceMale.value,
        voiceEntryWelcomeTemplate: voiceEntryWelcome.value ? `{plate},{type},${voiceEntryWelcome.value}` : null,
        voiceExitWelcomeTemplate: voiceExitWelcome.value ? `{plate},{type},${voiceExitWelcome.value}` : null,
        displayEntryWelcomeTemplate: displayEntryWelcome.value ? `{plate} {type} ${displayEntryWelcome.value}` : null,
        displayExitWelcomeTemplate: displayExitWelcome.value ? `{plate} {type} ${displayExitWelcome.value}` : null,
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

async function handleReboot(id: number) {
  const r = await deviceReboot(id)
  if (r.success) { message.success('重启命令已发送') }
  else { message.info(r.message || '重启失败') }
}

async function handleTrigger(id: number) {
  const r = await deviceTriggerRecognition(id)
  if (r.success) { message.success('触发识别命令已发送') }
  else { message.info(r.message || '触发识别失败') }
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
