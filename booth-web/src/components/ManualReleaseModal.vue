<template>
  <a-modal
    :open="open"
    title="人工放行"
    :confirm-loading="releasing"
    :mask-closable="false"
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <a-form :model="formState" layout="vertical">
      <a-form-item label="放行车辆">
        <a-input :value="plateNumber" disabled />
      </a-form-item>

      <a-form-item label="放行原因" required>
        <a-select
          v-model:value="formState.reason"
          placeholder="请选择放行原因"
          :options="RELEASE_REASON_OPTIONS.map((r) => ({ value: r.value, label: r.label }))"
          :disabled="releasing"
        />
      </a-form-item>

      <a-form-item v-if="formState.reason === 'OTHER'" label="备注说明">
        <a-textarea
          v-model:value="formState.remark"
          placeholder="请输入放行备注"
          :rows="2"
          :maxlength="200"
          :disabled="releasing"
        />
      </a-form-item>
    </a-form>

    <!-- 放行结果 -->
    <a-result
      v-if="releaseResult"
      :status="releaseResult.success ? 'success' : 'error'"
      :title="releaseResult.success ? '开闸成功' : '开闸失败'"
      :sub-title="releaseResult.message"
    >
      <template #extra>
        <a-space>
          <a-button v-if="!releaseResult.gateOpened" type="primary" danger @click="handleRetry">
            重新开闸
          </a-button>
          <a-button @click="handleCancel">关闭</a-button>
        </a-space>
      </template>
    </a-result>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { manualOpenGate } from '@/api/charge'
import { RELEASE_REASON_OPTIONS } from '@/api/monitor-types'
import type { ReleaseReason } from '@/api/monitor-types'

const props = defineProps<{
  open: boolean
  laneId: number
  plateNumber: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: [result: { success: boolean; message: string; gateOpened: boolean | null }]
}>()

const formState = reactive({
  reason: '' as ReleaseReason | '',
  remark: '',
})

const releasing = ref(false)
const releaseResult = ref<{ success: boolean; message: string; gateOpened: boolean | null } | null>(null)

// 弹窗打开时重置状态
watch(
  () => props.open,
  (newVal) => {
    if (newVal) {
      formState.reason = ''
      formState.remark = ''
      releaseResult.value = null
    }
  },
)

/** 确认放行 */
async function handleConfirm() {
  if (!formState.reason) {
    message.warning('请选择放行原因')
    return
  }

  releasing.value = true
  releaseResult.value = null

  try {
    const reasonText = getReasonText(formState.reason as ReleaseReason, formState.remark)
    const result = await manualOpenGate(props.laneId, reasonText)

    const success = result.gateOpened === true
    const resultMsg = success
      ? `${props.plateNumber || '车辆'} 人工放行成功`
      : `开闸失败: ${result.gateResult || result.resultMessage || '未知错误'}`

    releaseResult.value = { success, message: resultMsg, gateOpened: success }
    emit('success', { success, message: resultMsg, gateOpened: success })

    if (success) {
      message.success(resultMsg)
    } else {
      message.warning(resultMsg)
    }
  } catch (e: any) {
    const errMsg = e?.message || '人工放行请求失败'
    releaseResult.value = { success: false, message: errMsg, gateOpened: false }
    emit('success', { success: false, message: errMsg, gateOpened: false })
    message.error(errMsg)
  } finally {
    releasing.value = false
  }
}

/** 重试开闸 */
function handleRetry() {
  releaseResult.value = null
  handleConfirm()
}

/** 取消 */
function handleCancel() {
  emit('update:open', false)
}

/** 构建放行原因文本 */
function getReasonText(reason: ReleaseReason, remark: string): string {
  const label = RELEASE_REASON_OPTIONS.find((r) => r.value === reason)?.label || reason
  if (remark) {
    return `${label}: ${remark}`
  }
  return label
}
</script>
