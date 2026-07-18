<template>
  <a-modal
    v-model:open="visible"
    :title="modalTitle"
    :confirm-loading="formLoading"
    width="560px"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
      <a-form-item label="所属停车场" name="parkingLotId" required>
        <a-select v-model:value="formData.parkingLotId" placeholder="请选择停车场" :disabled="isEditing">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="名单类型" name="listType" required>
        <a-radio-group v-model:value="formData.listType" :disabled="isEditing">
          <a-radio value="BLACK">黑名单</a-radio>
          <a-radio value="WHITE">白名单</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="车牌号" name="plateNumber" required>
        <a-input
          v-model:value="formData.plateNumber"
          placeholder="如：京A12345"
          :disabled="isEditing"
          @blur="formData.plateNumber = formData.plateNumber.toUpperCase()"
        />
      </a-form-item>
      <a-form-item v-if="formData.listType === 'BLACK'" label="黑名单触发类型" name="triggerType" required>
        <a-select v-model:value="formData.triggerType" placeholder="请选择触发类型">
          <a-select-option value="ARREARS">欠费类</a-select-option>
          <a-select-option value="MANAGEMENT">管理类</a-select-option>
          <a-select-option value="OTHER">其他类</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="有效期起">
        <a-date-picker v-model:value="formData.startDate" value-format="YYYY-MM-DD" style="width: 100%" placeholder="永久有效（可选）" />
      </a-form-item>
      <a-form-item label="有效期止">
        <a-date-picker v-model:value="formData.endDate" value-format="YYYY-MM-DD" style="width: 100%" placeholder="永久有效（可选）" />
      </a-form-item>
      <a-form-item label="备注">
        <a-textarea v-model:value="formData.remark" placeholder="可选：备注说明" :rows="2" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { VehicleListRecord } from '@/api/vehicle-list'

const props = defineProps<{
  open: boolean
  isEditing: boolean
  editingRecord: VehicleListRecord | null
  parkingLotOptions: { id: number; name: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'submit', data: Record<string, any>): void
}>()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const modalTitle = computed(() => props.isEditing ? '编辑名单' : '新增名单')
const formLoading = ref(false)

const formData = reactive({
  parkingLotId: undefined as number | undefined,
  listType: 'BLACK' as string,
  plateNumber: '',
  triggerType: undefined as string | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  remark: '',
})

watch(() => props.editingRecord, (record) => {
  if (record && props.isEditing) {
    formData.parkingLotId = record.parkingLotId
    formData.listType = record.listType
    formData.plateNumber = record.plateNumber
    formData.triggerType = record.triggerType || undefined
    formData.startDate = record.startDate
    formData.endDate = record.endDate
    formData.remark = record.remark || ''
  } else {
    resetForm()
  }
})

function resetForm() {
  formData.parkingLotId = undefined
  formData.listType = 'BLACK'
  formData.plateNumber = ''
  formData.triggerType = undefined
  formData.startDate = undefined
  formData.endDate = undefined
  formData.remark = ''
}

function handleCancel() { visible.value = false }

function handleSubmit() {
  if (!formData.parkingLotId) { message.warning('请选择停车场'); return }
  if (!formData.listType) { message.warning('请选择名单类型'); return }
  if (!formData.plateNumber.trim()) { message.warning('请输入车牌号'); return }
  if (formData.listType === 'BLACK' && !formData.triggerType) {
    message.warning('黑名单请选择触发类型'); return
  }

  formLoading.value = true
  try {
    const payload: Record<string, any> = {
      parkingLotId: formData.parkingLotId,
      listType: formData.listType,
      plateNumber: formData.plateNumber.trim().toUpperCase(),
      startDate: formData.startDate || undefined,
      endDate: formData.endDate || undefined,
      triggerType: formData.listType === 'BLACK' ? formData.triggerType : undefined,
      remark: formData.remark?.trim() || undefined,
    }
    emit('submit', payload)
    visible.value = false
  } finally {
    formLoading.value = false
  }
}
</script>
