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

      <a-form-item label="是否计费">
        <a-switch v-model:checked="formState.isCharge" :disabled="releasing" />
        <span style="margin-left: 8px; color: #6b7280; font-size: 12px;">
          {{ formState.isCharge ? '计费放行' : '免费放行' }}
        </span>
      </a-form-item>

      <a-form-item v-if="formState.isCharge" label="计费金额（元）">
        <a-input-number
          v-model:value="formState.amountYuan"
          :min="0"
          :precision="2"
          :disabled="releasing"
          placeholder="请输入收费金额"
          style="width: 100%"
        >
          <template #addonAfter>元</template>
        </a-input-number>
      </a-form-item>

      <a-form-item label="放行原因" required>
        <a-textarea
          v-model:value="formState.remark"
          placeholder="必填：请填写放行原因"
          :rows="2"
          :maxlength="200"
          :disabled="releasing"
        />
      </a-form-item>
    </a-form>

    <!-- 开闸结果 -->
    <template v-if="releaseResult">
      <a-result
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
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { manualOpenGate } from '@/api/charge'

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
  remark: '',
  isCharge: false,
  amountYuan: 0,
})

const releasing = ref(false)
const releaseResult = ref<{ success: boolean; message: string; gateOpened: boolean | null } | null>(null)

// 弹窗打开时重置状态
watch(
  () => props.open,
  (newVal) => {
    if (newVal) {
      formState.remark = ''
      formState.isCharge = false
      formState.amountYuan = 0
      releaseResult.value = null
    }
  },
)

/** 确认放行 */
async function handleConfirm() {
  releasing.value = true
  releaseResult.value = null

  // 放行原因必填
  if (!formState.remark.trim()) {
    message.warning('请填写放行原因')
    releasing.value = false
    return
  }

  // 计费模式：金额必填且 > 0
  if (formState.isCharge && formState.amountYuan <= 0) {
    message.warning('计费放行请填写收费金额')
    releasing.value = false
    return
  }

  try {
    const feeCents = formState.isCharge
      ? Math.round(formState.amountYuan * 100)
      : 0
    const reasonText = formState.remark.trim() || '岗亭人工放行'
    const result = await manualOpenGate(
      props.laneId,
      reasonText,
      {
        isCharge: formState.isCharge,
        feeCents,
        plateNumber: props.plateNumber || undefined,
      },
    )

    const success = result.gateDeviceAck === true
    const resultMsg = success
      ? '开闸成功'
      : `开闸失败: ${result.gateResult || result.resultMessage || '未知错误'}`

    releaseResult.value = { success, message: resultMsg, gateOpened: success }
    emit('success', { success, message: resultMsg, gateOpened: success })
  } catch (e: any) {
    const errMsg = e?.message || '开闸请求失败'
    releaseResult.value = { success: false, message: errMsg, gateOpened: false }
    emit('success', { success: false, message: errMsg, gateOpened: false })
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
</script>
