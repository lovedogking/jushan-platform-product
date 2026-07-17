<template>
  <a-modal
    v-model:open="open"
    :title="modalTitle"
    :confirm-loading="formLoading"
    width="640px"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" :rules="formRules">
      <a-form-item label="所属停车场" name="parkingLotId">
        <a-select v-model:value="formData.parkingLotId" placeholder="请选择停车场" :disabled="isEditing">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="规则名称" name="name">
        <a-input v-model:value="formData.name" placeholder="如：商场标准收费" />
      </a-form-item>
      <a-form-item label="规则描述">
        <a-textarea v-model:value="formData.description" placeholder="选填" :rows="2" />
      </a-form-item>
      <a-form-item label="计费类型" name="ruleType">
        <a-select v-model:value="formData.ruleType" placeholder="请选择计费类型" :disabled="isEditing">
          <a-select-option v-for="opt in RULE_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item label="生效方式" name="effectType">
        <a-select v-model:value="formData.effectType" placeholder="请选择生效方式">
          <a-select-option v-for="opt in EFFECT_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item v-if="formData.effectType === 'SCHEDULED'" label="定时生效时间" name="effectTime">
        <a-date-picker
          v-model:value="formData.effectTime"
          show-time
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="选择定时生效时间"
          style="width: 100%"
        />
      </a-form-item>

      <!-- 按时计费字段 -->
      <template v-if="formData.ruleType === 'HOURLY'">
        <a-form-item label="免费时长(分钟)">
          <a-input-number v-model:value="formData.freeMinutes" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="首时段时长(分钟)">
          <a-input-number v-model:value="formData.firstPeriod" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="首时段金额(元)">
          <a-input-number v-model:value="formData.firstAmount" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item label="续费单位时长(分钟)">
          <a-input-number v-model:value="formData.unitPeriod" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="续费单位金额(元)">
          <a-input-number v-model:value="formData.unitAmount" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
      </template>

      <!-- 按次计费字段 -->
      <template v-if="formData.ruleType === 'FIXED'">
        <a-form-item label="免费时长(分钟)">
          <a-input-number v-model:value="formData.freeMinutes" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="固定金额(元)">
          <a-input-number v-model:value="formData.firstAmount" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
      </template>

      <!-- 封顶配置（按时计费时显示） -->
      <template v-if="formData.ruleType === 'HOURLY'">
        <a-divider>封顶配置</a-divider>
        <a-form-item label="单日封顶(元)">
          <a-input-number v-model:value="formData.dailyCap" :min="0" :precision="2" style="width: 100%" placeholder="0 表示不封顶" />
        </a-form-item>
        <a-form-item label="最大金额(元)">
          <a-input-number v-model:value="formData.maxAmount" :min="0" :precision="2" style="width: 100%" placeholder="0 表示不封顶" />
        </a-form-item>
      </template>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  RULE_TYPE_OPTIONS,
  EFFECT_TYPE_OPTIONS,
  type BillingRuleVO,
} from '@/api/billing-rule'

const props = defineProps<{
  open: boolean
  isEditing: boolean
  editingRecord: BillingRuleVO | null
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

const modalTitle = computed(() => props.isEditing ? '编辑收费规则' : '新增收费规则')
const formLoading = ref(false)

// 分为单位转为元显示，元转为分发送给后端
function centsToYuan(cents: number | undefined | null): number | undefined {
  if (cents === undefined || cents === null) return undefined
  return cents / 100
}

function yuanToCents(yuan: number | undefined | null): number {
  if (yuan === undefined || yuan === null) return 0
  return Math.round(yuan * 100)
}

const formData = reactive({
  parkingLotId: undefined as number | undefined,
  name: '',
  description: '',
  ruleType: 'HOURLY' as string,
  effectType: 'IMMEDIATE' as string,
  effectTime: null as any,
  freeMinutes: 0,
  firstPeriod: 60,
  firstAmount: undefined as number | undefined,
  unitPeriod: 60,
  unitAmount: undefined as number | undefined,
  dailyCap: undefined as number | undefined,
  maxAmount: undefined as number | undefined,
})

const formRules: Record<string, any> = {
  parkingLotId: [{ required: true, message: '请选择所属停车场', trigger: 'change' }],
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  ruleType: [{ required: true, message: '请选择计费类型', trigger: 'change' }],
  effectType: [{ required: true, message: '请选择生效方式', trigger: 'change' }],
}

// 编辑时回填数据
watch(() => props.editingRecord, (record) => {
  if (record && props.isEditing) {
    formData.parkingLotId = record.parkingLotId
    formData.name = record.name || ''
    formData.description = record.description || ''
    formData.ruleType = record.ruleType || 'HOURLY'
    formData.effectType = record.effectType || 'IMMEDIATE'
    formData.effectTime = record.effectTime ? dayjs(record.effectTime) : null
    formData.freeMinutes = record.freeMinutes || 0
    formData.firstPeriod = record.firstPeriod || 0
    formData.firstAmount = centsToYuan(record.firstAmount)
    formData.unitPeriod = record.unitPeriod || 0
    formData.unitAmount = centsToYuan(record.unitAmount)
    formData.dailyCap = centsToYuan(record.dailyCap)
    formData.maxAmount = centsToYuan(record.maxAmount)
  } else {
    resetForm()
  }
})

function resetForm() {
  formData.parkingLotId = undefined
  formData.name = ''
  formData.description = ''
  formData.ruleType = 'HOURLY'
  formData.effectType = 'IMMEDIATE'
  formData.effectTime = null
  formData.freeMinutes = 0
  formData.firstPeriod = 60
  formData.firstAmount = undefined
  formData.unitPeriod = 60
  formData.unitAmount = undefined
  formData.dailyCap = undefined
  formData.maxAmount = undefined
}

function handleCancel() {
  open.value = false
}

function handleSubmit() {
  if (!formData.name.trim()) {
    message.warning('请输入规则名称')
    return
  }
  if (!formData.parkingLotId) {
    message.warning('请选择所属停车场')
    return
  }

  formLoading.value = true
  try {
    const payload: Record<string, any> = {
      parkingLotId: formData.parkingLotId,
      name: formData.name.trim(),
      description: formData.description.trim() || undefined,
      ruleType: formData.ruleType,
      effectType: formData.effectType,
      freeMinutes: formData.freeMinutes,
    }

    if (formData.effectType === 'SCHEDULED' && formData.effectTime) {
      payload.effectTime = formData.effectTime.format('YYYY-MM-DDTHH:mm:ss')
    }

    if (formData.ruleType === 'HOURLY' || formData.ruleType === 'FIXED') {
      payload.firstPeriod = formData.firstPeriod
      payload.firstAmount = yuanToCents(formData.firstAmount)
      payload.unitPeriod = formData.unitPeriod
      payload.unitAmount = yuanToCents(formData.unitAmount)
    }

    if (formData.ruleType === 'HOURLY') {
      payload.dailyCap = yuanToCents(formData.dailyCap)
      payload.maxAmount = yuanToCents(formData.maxAmount)
    }

    emit('submit', payload)
    open.value = false
  } finally {
    formLoading.value = false
  }
}
</script>
