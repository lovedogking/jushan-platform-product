<template>
  <a-modal
    :open="open"
    title="调整剩余车位"
    :confirm-loading="submitting"
    :mask-closable="false"
    ok-text="确认调整"
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <a-form :model="formState" layout="vertical">
      <a-form-item label="当前剩余车位">
        <a-input-number :value="currentRemaining" :min="0" disabled style="width:100%" />
      </a-form-item>

      <a-form-item label="目标剩余车位">
        <a-input-number
          v-model:value="formState.targetValue"
          :min="0"
          :max="totalSpaces"
          :disabled="submitting"
          placeholder="输入目标剩余车位数"
          style="width:100%"
        />
      </a-form-item>

      <a-form-item label="修改原因" required>
        <a-textarea
          v-model:value="formState.reason"
          placeholder="必填：请填写修改原因"
          :rows="2"
          :maxlength="255"
          :disabled="submitting"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { adjustSpaces } from '@/api/monitor'

const props = defineProps<{
  open: boolean
  parkingLotId: number
  currentRemaining: number
  totalSpaces: number
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: []
}>()

const formState = reactive({
  targetValue: 0,
  reason: '',
})

const submitting = ref(false)

watch(() => props.open, (newVal) => {
  if (newVal) {
    formState.targetValue = props.currentRemaining
    formState.reason = ''
  }
})

async function handleConfirm() {
  if (!formState.reason.trim()) { message.warning('请填写修改原因'); return }
  if (formState.targetValue < 0) { message.warning('剩余车位不能为负数'); return }
  if (formState.targetValue > props.totalSpaces) {
    message.warning(`剩余车位不能超过总车位（${props.totalSpaces}）`)
    return
  }

  submitting.value = true
  try {
    await adjustSpaces({
      mode: 'SET',
      value: formState.targetValue,
      parkingLotId: props.parkingLotId,
      reason: formState.reason.trim(),
    })
    message.success('余位调整成功')
    emit('success')
    emit('update:open', false)
  } catch (e: any) {
    message.error(e?.message || '余位调整失败')
  } finally { submitting.value = false }
}

function handleCancel() { emit('update:open', false) }
</script>
