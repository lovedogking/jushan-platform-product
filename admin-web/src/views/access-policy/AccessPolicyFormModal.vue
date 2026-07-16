<template>
  <a-modal
    v-model:open="open"
    :title="modalTitle"
    :confirm-loading="formLoading"
    width="560px"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" :rules="formRules">
      <a-form-item label="所属停车场" name="parkingLotId">
        <a-select v-model:value="formData.parkingLotId" placeholder="请选择停车场" :disabled="isEditing">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="名单类型" name="policyType">
        <a-radio-group v-model:value="formData.policyType" :disabled="isEditing">
          <a-radio value="BLACKLIST">黑名单</a-radio>
          <a-radio value="VIP">白名单</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item label="车牌号" name="policyKey">
        <a-input
          v-model:value="formData.policyKey"
          placeholder="如：京A12345"
          :disabled="isEditing"
          @blur="formData.policyKey = formData.policyKey.toUpperCase()"
        />
      </a-form-item>
      <a-form-item label="策略值">
        <a-input v-model:value="formData.policyValue" placeholder="如有效期、触发类型等配置（JSON 或文本）" />
      </a-form-item>
      <a-form-item label="说明">
        <a-input v-model:value="formData.description" placeholder="可选：备注说明" />
      </a-form-item>
      <a-form-item label="排序">
        <a-input-number v-model:value="formData.sortOrder" :min="0" style="width: 100%" placeholder="数字越小越靠前" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  POLICY_TYPE_OPTIONS,
  type AccessPolicyVO,
} from '@/api/access-policy'

const props = defineProps<{
  open: boolean
  isEditing: boolean
  editingRecord: AccessPolicyVO | null
  parkingLotOptions: { id: number; name: string }[]
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'submit', data: Record<string, any>): void
}>()

const open = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const modalTitle = computed(() => props.isEditing ? '编辑名单' : '新增名单')
const formLoading = ref(false)

const formData = reactive({
  parkingLotId: undefined as number | undefined,
  policyType: 'BLACKLIST' as string,
  policyKey: '',
  policyValue: '',
  description: '',
  sortOrder: 0,
})

const formRules: Record<string, any> = {
  parkingLotId: [{ required: true, message: '请选择停车场', trigger: 'change' }],
  policyType: [{ required: true, message: '请选择名单类型', trigger: 'change' }],
  policyKey: [
    { required: true, message: '请输入车牌号', trigger: 'blur' },
    { max: 50, message: '车牌号最多50个字符', trigger: 'blur' },
  ],
}

// 编辑时回填数据
watch(() => props.editingRecord, (record) => {
  if (record && props.isEditing) {
    formData.parkingLotId = record.parkingLotId
    formData.policyType = record.policyType
    formData.policyKey = record.policyKey
    formData.policyValue = record.policyValue || ''
    formData.description = record.description || ''
    formData.sortOrder = record.sortOrder || 0
  } else {
    resetForm()
  }
})

function resetForm() {
  formData.parkingLotId = undefined
  formData.policyType = 'BLACKLIST'
  formData.policyKey = ''
  formData.policyValue = ''
  formData.description = ''
  formData.sortOrder = 0
}

function handleCancel() {
  open.value = false
}

function handleSubmit() {
  if (!formData.parkingLotId) {
    message.warning('请选择停车场')
    return
  }
  if (!formData.policyType) {
    message.warning('请选择名单类型')
    return
  }
  if (!formData.policyKey.trim()) {
    message.warning('请输入车牌号')
    return
  }

  formLoading.value = true
  try {
    const payload: Record<string, any> = {
      parkingLotId: formData.parkingLotId,
      policyType: formData.policyType,
      policyKey: formData.policyKey.trim().toUpperCase(),
      policyValue: formData.policyValue.trim(),
      description: formData.description.trim(),
      sortOrder: formData.sortOrder || 0,
    }

    emit('submit', payload)
    open.value = false
  } finally {
    formLoading.value = false
  }
}
</script>
