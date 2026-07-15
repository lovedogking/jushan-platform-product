<template>
  <a-modal v-model:open="visible" :title="`设备控制 — ${deviceName}`" width="600px" :footer="null" @cancel="handleCancel">
    <a-tabs v-model:activeKey="activeTab">
      <!-- Tab 1: 显示屏控制 -->
      <a-tab-pane key="display" tab="显示屏控制">
        <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
          <a-form-item label="显示内容">
            <a-textarea
              v-model:value="displayTextContent"
              placeholder="如：欢迎光临&#10;剩余车位100"
              :rows="4"
              :auto-size="{ minRows: 3, maxRows: 6 }"
            />
            <div style="color: #999; font-size: 12px; margin-top: 4px">支持换行，每行独立显示</div>
          </a-form-item>
          <a-form-item label="显示方向">
            <a-radio-group v-model:value="displayDirection">
              <a-radio value="HORIZONTAL">横向</a-radio>
              <a-radio value="VERTICAL">纵向</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="字体大小">
            <a-radio-group v-model:value="displayFontSize">
              <a-radio :value="1">小</a-radio>
              <a-radio :value="2">中</a-radio>
              <a-radio :value="3">大</a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="颜色">
            <a-radio-group v-model:value="displayColor">
              <a-radio value="RED">
                <span style="color: #dc2626">红色</span>
              </a-radio>
              <a-radio value="GREEN">
                <span style="color: #16a34a">绿色</span>
              </a-radio>
              <a-radio value="YELLOW">
                <span style="color: #ca8a04">黄色</span>
              </a-radio>
            </a-radio-group>
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 6, span: 16 }">
            <a-button type="primary" :loading="displayTextLoading" @click="handleDisplayText">发送</a-button>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <!-- Tab 2: 显示屏配置 -->
      <a-tab-pane key="config" tab="显示屏配置">
        <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
          <a-form-item label="音量">
            <a-row :gutter="12" align="middle">
              <a-col :flex="1">
                <a-slider v-model:value="configVolume" :min="0" :max="100" :step="1" />
              </a-col>
              <a-col>
                <span style="min-width: 40px; display: inline-block">{{ configVolume }}%</span>
              </a-col>
            </a-row>
            <a-button size="small" type="primary" :loading="configVolumeLoading" @click="handleVolumeConfig">应用音量</a-button>
          </a-form-item>
          <a-form-item label="亮度">
            <a-row :gutter="12" align="middle">
              <a-col :flex="1">
                <a-slider v-model:value="configBrightness" :min="0" :max="100" :step="1" />
              </a-col>
              <a-col>
                <span style="min-width: 40px; display: inline-block">{{ configBrightness }}%</span>
              </a-col>
            </a-row>
            <a-button size="small" type="primary" :loading="configBrightnessLoading" @click="handleBrightnessConfig">应用亮度</a-button>
          </a-form-item>
          <a-divider />
          <a-form-item label="时间同步">
            <a-button type="primary" :loading="configTimeSyncLoading" @click="handleTimeSyncConfig">同步设备时间</a-button>
          </a-form-item>
        </a-form>
      </a-tab-pane>

      <!-- Tab 3: 语音播报 -->
      <a-tab-pane key="voice" tab="语音播报">
        <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
          <a-form-item label="语音模板">
            <a-select v-model:value="voiceTemplateId" placeholder="请选择语音模板" style="width: 100%">
              <a-select-option :value="1">欢迎光临</a-select-option>
              <a-select-option :value="2">请缴费XX元</a-select-option>
              <a-select-option :value="3">一路顺风</a-select-option>
              <a-select-option :value="4">车牌识别成功</a-select-option>
              <a-select-option :value="5">车位已满</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="变量参数">
            <a-input
              v-model:value="voiceVariable"
              placeholder="如：车牌号 京A12345"
            />
            <div style="color: #999; font-size: 12px; margin-top: 4px">
              用于替换模板中的占位符（如车牌号、金额等）
            </div>
          </a-form-item>
          <a-form-item :wrapper-col="{ offset: 6, span: 16 }">
            <a-space>
              <a-button type="primary" :loading="voicePlayLoading" @click="handleVoicePlay">播放</a-button>
              <a-button :loading="voiceStopLoading" @click="handleVoiceStop">停止</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-tab-pane>
    </a-tabs>

    <!-- 操作结果展示 -->
    <a-alert
      v-if="resultMessage"
      :message="resultMessage"
      :type="resultSuccess ? 'success' : 'error'"
      show-icon
      closable
      style="margin-top: 12px"
      @close="resultMessage = ''"
    />
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { displayText, displayConfig, voiceControl, type DisplayTextRequest, type DisplayConfigRequest, type VoiceControlRequest } from '@/api/device'

const props = defineProps<{
  open: boolean
  deviceId: number
  deviceName: string
  initialTab?: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const visible = ref(props.open)
watch(() => props.open, (val) => {
  visible.value = val
  if (val && props.initialTab) {
    activeTab.value = props.initialTab
  }
})
watch(visible, (val) => { emit('update:open', val) })

const activeTab = ref('display')

// ==================== Tab 1: 显示屏控制 ====================
const displayTextContent = ref('')
const displayDirection = ref('HORIZONTAL')
const displayFontSize = ref(2)
const displayColor = ref('RED')
const displayTextLoading = ref(false)

async function handleDisplayText() {
  if (!displayTextContent.value.trim()) {
    message.warning('请输入显示内容')
    return
  }
  displayTextLoading.value = true
  try {
    const data: DisplayTextRequest = {
      content: displayTextContent.value.trim(),
      direction: displayDirection.value,
      fontSize: displayFontSize.value,
      color: displayColor.value,
    }
    const res = await displayText(props.deviceId, data)
    resultSuccess.value = res.success
    resultMessage.value = res.success ? '显示屏文字发送成功' : `发送失败: ${res.message || '未知错误'}`
    if (res.success) message.success('显示屏文字发送成功')
    else message.error(`发送失败: ${res.message || '未知错误'}`)
  } catch {
    resultSuccess.value = false
    resultMessage.value = '显示屏文字发送失败（网络错误）'
    message.error('显示屏文字发送失败')
  } finally {
    displayTextLoading.value = false
  }
}

// ==================== Tab 2: 显示屏配置 ====================
const configVolume = ref(80)
const configBrightness = ref(80)
const configVolumeLoading = ref(false)
const configBrightnessLoading = ref(false)
const configTimeSyncLoading = ref(false)

async function handleVolumeConfig() {
  configVolumeLoading.value = true
  try {
    const data: DisplayConfigRequest = { configType: 'VOLUME', intValue: configVolume.value }
    const res = await displayConfig(props.deviceId, data)
    resultSuccess.value = res.success
    resultMessage.value = res.success ? '音量设置成功' : `音量设置失败: ${res.message || '未知错误'}`
    if (res.success) message.success('音量设置成功')
    else message.error(`音量设置失败: ${res.message || '未知错误'}`)
  } catch {
    resultSuccess.value = false
    resultMessage.value = '音量设置失败（网络错误）'
    message.error('音量设置失败')
  } finally {
    configVolumeLoading.value = false
  }
}

async function handleBrightnessConfig() {
  configBrightnessLoading.value = true
  try {
    const data: DisplayConfigRequest = { configType: 'BRIGHTNESS', intValue: configBrightness.value }
    const res = await displayConfig(props.deviceId, data)
    resultSuccess.value = res.success
    resultMessage.value = res.success ? '亮度设置成功' : `亮度设置失败: ${res.message || '未知错误'}`
    if (res.success) message.success('亮度设置成功')
    else message.error(`亮度设置失败: ${res.message || '未知错误'}`)
  } catch {
    resultSuccess.value = false
    resultMessage.value = '亮度设置失败（网络错误）'
    message.error('亮度设置失败')
  } finally {
    configBrightnessLoading.value = false
  }
}

async function handleTimeSyncConfig() {
  configTimeSyncLoading.value = true
  try {
    const data: DisplayConfigRequest = { configType: 'TIME_SYNC', intValue: 0 }
    const res = await displayConfig(props.deviceId, data)
    resultSuccess.value = res.success
    resultMessage.value = res.success ? '时间同步成功' : `时间同步失败: ${res.message || '未知错误'}`
    if (res.success) message.success('时间同步成功')
    else message.error(`时间同步失败: ${res.message || '未知错误'}`)
  } catch {
    resultSuccess.value = false
    resultMessage.value = '时间同步失败（网络错误）'
    message.error('时间同步失败')
  } finally {
    configTimeSyncLoading.value = false
  }
}

// ==================== Tab 3: 语音播报 ====================
const voiceTemplateId = ref<number | undefined>(undefined)
const voiceVariable = ref('')
const voicePlayLoading = ref(false)
const voiceStopLoading = ref(false)

async function handleVoicePlay() {
  if (!voiceTemplateId.value) {
    message.warning('请选择语音模板')
    return
  }
  voicePlayLoading.value = true
  try {
    const data: VoiceControlRequest = {
      action: 'PLAY',
      voiceId: voiceTemplateId.value,
      variable: voiceVariable.value || undefined,
    }
    const res = await voiceControl(props.deviceId, data)
    resultSuccess.value = res.success
    resultMessage.value = res.success ? '语音播报已发送' : `播报失败: ${res.message || '未知错误'}`
    if (res.success) message.success('语音播报已发送')
    else message.error(`播报失败: ${res.message || '未知错误'}`)
  } catch {
    resultSuccess.value = false
    resultMessage.value = '语音播报失败（网络错误）'
    message.error('语音播报失败')
  } finally {
    voicePlayLoading.value = false
  }
}

async function handleVoiceStop() {
  voiceStopLoading.value = true
  try {
    const data: VoiceControlRequest = { action: 'STOP', voiceId: voiceTemplateId.value || 0 }
    const res = await voiceControl(props.deviceId, data)
    resultSuccess.value = res.success
    resultMessage.value = res.success ? '语音播报已停止' : `停止失败: ${res.message || '未知错误'}`
    if (res.success) message.success('语音播报已停止')
    else message.error(`停止失败: ${res.message || '未知错误'}`)
  } catch {
    resultSuccess.value = false
    resultMessage.value = '停止语音播报失败（网络错误）'
    message.error('停止语音播报失败')
  } finally {
    voiceStopLoading.value = false
  }
}

// ==================== 结果展示 ====================
const resultMessage = ref('')
const resultSuccess = ref(true)

function handleCancel() {
  visible.value = false
}
</script>
