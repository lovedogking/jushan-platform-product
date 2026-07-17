<template>
  <teleport to="body">
    <div v-if="visible" class="temp-plate-alert-overlay">
      <div class="temp-plate-alert-modal">
        <div class="alert-header">
          <span class="alert-title">
            <ExclamationCircleOutlined style="color: #faad14" />
            入口识别失败 — 无牌车辆
          </span>
          <a-button type="text" size="small" @click="handleIgnore">
            <CloseOutlined />
          </a-button>
        </div>
        <div class="alert-body">
          <div class="alert-info">
            <span>车道：{{ alert?.laneName || `车道 #${alert?.laneId}` }}</span>
            <span>时间：{{ alert?.eventTime }}</span>
            <span>方向：{{ alert?.direction === 'ENTRY' ? '入场' : '出场' }}</span>
          </div>
          <div class="plate-input-row">
            <span class="input-label">临时车牌：</span>
            <a-input
              v-model:value="tempPlate"
              placeholder="输入临时车牌号"
              style="width: 180px"
              :maxlength="20"
              @pressEnter="handleConfirm"
            />
            <a-button type="primary" size="small" :loading="confirming" @click="handleConfirm">
              确认入场
            </a-button>
          </div>
          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        </div>
      </div>
    </div>
  </teleport>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ExclamationCircleOutlined, CloseOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { suggestTempPlate, manualTempPlateEntry } from '@/api/monitor'

export interface RecognitionFailedAlert {
  eventId: string
  logId: number
  parkingLotId: number
  laneId: number
  laneName?: string
  direction: string
  imagePath?: string
  eventTime: string
  message?: string
}

const props = defineProps<{
  alert: RecognitionFailedAlert | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'confirmed', recordId: number, tempPlate: string): void
}>()

const visible = ref(false)
const tempPlate = ref('')
const confirming = ref(false)
const errorMsg = ref('')

watch(
  () => props.alert,
  (newAlert) => {
    if (newAlert) {
      visible.value = true
      tempPlate.value = ''
      errorMsg.value = ''
      // Auto-fetch suggested plate number
      suggestTempPlate(newAlert.parkingLotId)
        .then((res) => {
          tempPlate.value = res.tempPlate || ''
        })
        .catch(() => { /* ignore */ })
    }
  },
  { immediate: true }
)

function handleIgnore() {
  visible.value = false
  errorMsg.value = ''
  emit('close')
}

async function handleConfirm() {
  const plate = tempPlate.value?.trim()
  if (!plate) {
    errorMsg.value = '请输入临时车牌号'
    return
  }
  if (!props.alert) return

  confirming.value = true
  errorMsg.value = ''
  try {
    const res = await manualTempPlateEntry({
      parkingLotId: props.alert.parkingLotId,
      laneId: props.alert.laneId,
      tempPlate: plate,
    })
    message.success(`无牌车入场成功：${plate}`)
    visible.value = false
    emit('confirmed', res.recordId, plate)
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '入场失败，请重试'
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.temp-plate-alert-overlay {
  position: fixed;
  bottom: 16px;
  right: 16px;
  z-index: 2000;
}
.temp-plate-alert-modal {
  width: 380px;
  background: #fff;
  border: 1px solid #faad14;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  overflow: hidden;
}
.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #fffbe6;
  border-bottom: 1px solid #ffe58f;
}
.alert-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}
.alert-body {
  padding: 14px;
}
.alert-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #666;
}
.plate-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.input-label {
  font-size: 13px;
  white-space: nowrap;
}
.error-msg {
  color: #ff4d4f;
  font-size: 12px;
  margin-top: 8px;
}
</style>
