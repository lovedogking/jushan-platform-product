<template>
  <a-modal
    v-model:open="visible"
    title="车牌校正"
    :confirm-loading="submitting"
    :ok-text="readonly ? '关闭' : '提交校正'"
    :cancel-text="readonly ? undefined : '取消'"
    :ok-button-props="readonly ? { style: { display: 'none' } } : undefined"
    :cancel-button-props="readonly ? { style: { display: 'none' } } : undefined"
    @ok="handleSubmit"
    @cancel="handleClose"
  >
    <a-descriptions :column="1" bordered size="small" style="margin-bottom: 16px">
      <a-descriptions-item label="原始识别车牌">{{ event?.plateNumber || '—' }}</a-descriptions-item>
      <a-descriptions-item label="车道">{{ event?.laneName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="方向">
        <a-tag :color="event?.direction === 'ENTRY' ? 'blue' : 'orange'">
          {{ event?.direction === 'ENTRY' ? '入场' : '出场' }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag :color="statusColor">{{ statusLabel }}</a-tag>
      </a-descriptions-item>
    </a-descriptions>

    <template v-if="event?.correctedPlate">
      <a-alert
        type="info"
        show-icon
        message="该事件已校正"
        :description="`校正后车牌：${event.correctedPlate}`"
        style="margin-bottom: 12px"
      />
    </template>

    <template v-else>
      <a-form layout="vertical">
        <a-form-item label="校正后车牌号" required>
          <a-input
            v-model:value="correctedPlate"
            :maxlength="20"
            placeholder="请输入校正后的车牌号"
            :disabled="readonly"
          />
        </a-form-item>
      </a-form>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { correctPlate } from '@/api/monitor'
import type { RecognitionEvent } from '@/api/monitor-types'

const props = defineProps<{
  open: boolean
  event: RecognitionEvent | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'corrected'): void
}>()

const visible = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val),
})

const correctedPlate = ref('')
const submitting = ref(false)
const readonly = computed(() => props.event?.correctedPlate != null)

const STATUS_LABELS: Record<string, string> = {
  RECEIVED: '已接收',
  PROCESSING: '处理中',
  PROCESSED: '已处理',
  FAILED: '处理失败',
}
const STATUS_COLORS: Record<string, string> = {
  RECEIVED: 'default',
  PROCESSING: 'processing',
  PROCESSED: 'success',
  FAILED: 'error',
}
const statusLabel = computed(() => STATUS_LABELS[props.event?.status || ''] || props.event?.status || '—')
const statusColor = computed(() => STATUS_COLORS[props.event?.status || ''] || 'default')

watch(
  () => props.open,
  (val) => {
    if (val) {
      correctedPlate.value = ''
    }
  }
)

async function handleSubmit() {
  if (readonly.value) {
    visible.value = false
    return
  }
  if (!correctedPlate.value.trim()) {
    message.warning('请输入校正后的车牌号')
    return
  }
  if (!props.event?.logId) {
    message.error('事件数据异常，无法校正')
    return
  }
  submitting.value = true
  try {
    await correctPlate(props.event.logId, correctedPlate.value.trim())
    message.success('车牌校正成功')
    emit('corrected')
    visible.value = false
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  visible.value = false
}
</script>
