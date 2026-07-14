<template>
  <a-modal
    v-model:open="open"
    :title="modalTitle"
    :confirm-loading="formLoading"
    width="720px"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }" :rules="formRules">
      <!-- 基础信息 -->
      <a-form-item label="所属停车场" name="lotId">
        <a-select v-model:value="formData.lotId" placeholder="请选择停车场" :disabled="isEditing">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="适用区域">
        <a-select v-model:value="formData.zoneId" placeholder="车场通用（不选）" allow-clear :disabled="isEditing">
          <a-select-option v-for="zone in zoneOptions" :key="zone.id" :value="zone.id">{{ zone.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="规则名称" name="name">
        <a-input v-model:value="formData.name" placeholder="如：商场标准收费" />
      </a-form-item>
      <a-form-item label="计费模式" name="billingMode">
        <a-select v-model:value="formData.billingMode" placeholder="请选择计费模式" :disabled="isEditing">
          <a-select-option v-for="opt in BILLING_MODE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
      </a-form-item>

      <!-- 按时计费字段 -->
      <template v-if="formData.billingMode === 1">
        <a-form-item label="免费时长(分钟)">
          <a-input-number v-model:value="formData.freeMinutes" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="计费单位(分钟)">
          <a-input-number v-model:value="formData.unitMinutes" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="首时段价格(元)">
          <a-input-number v-model:value="formData.firstPeriodPrice" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item label="后续单价(元)">
          <a-input-number v-model:value="formData.subsequentPrice" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
      </template>

      <!-- 按次计费字段 -->
      <template v-if="formData.billingMode === 2">
        <a-form-item label="固定价格(元)">
          <a-input-number v-model:value="formData.firstPeriodPrice" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
      </template>

      <!-- 阶梯计费字段 -->
      <template v-if="formData.billingMode === 3">
        <a-form-item label="免费时长(分钟)">
          <a-input-number v-model:value="formData.freeMinutes" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="计费单位(分钟)">
          <a-input-number v-model:value="formData.unitMinutes" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="第一阶梯价格(元)">
          <a-input-number v-model:value="formData.firstPeriodPrice" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item label="后续阶梯单价(元)">
          <a-input-number v-model:value="formData.subsequentPrice" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
      </template>

      <!-- 分时段计费字段 -->
      <template v-if="formData.billingMode === 4">
        <a-form-item label="免费时长(分钟)">
          <a-input-number v-model:value="formData.freeMinutes" :min="0" style="width: 100%" />
        </a-form-item>
        <a-form-item label="时段配置">
          <TimeSegmentEditor v-model:value="formData.timeSegments" />
        </a-form-item>
      </template>

      <!-- 通用封顶配置 -->
      <a-divider>封顶配置</a-divider>
      <a-form-item label="24小时封顶(元)">
        <a-input-number v-model:value="formData.dailyCap" :min="0" :precision="2" style="width: 100%" placeholder="不封顶则不填" />
      </a-form-item>
      <a-form-item label="夜间封顶(元)">
        <a-input-number v-model:value="formData.nightCap" :min="0" :precision="2" style="width: 100%" placeholder="不封顶则不填" />
      </a-form-item>

      <!-- 生效配置 -->
      <a-divider>生效配置</a-divider>
      <a-form-item label="优先级">
        <a-input-number v-model:value="formData.priority" :min="0" style="width: 100%" placeholder="数字越大优先级越高" />
      </a-form-item>
      <a-form-item label="生效开始">
        <a-date-picker v-model:value="formData.effectiveStart" show-time style="width: 100%" placeholder="立即生效则不填" />
      </a-form-item>
      <a-form-item label="生效结束">
        <a-date-picker v-model:value="formData.effectiveEnd" show-time style="width: 100%" placeholder="永久生效则不填" />
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="formData.status" placeholder="请选择状态">
          <a-select-option v-for="opt in FEE_RULE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import TimeSegmentEditor from '@/components/TimeSegmentEditor.vue'
import {
  BILLING_MODE_OPTIONS,
  FEE_RULE_STATUS_OPTIONS,
  type FeeRuleVO,
} from '@/api/fee-rule'
import { getParkingZonesByLotId, type ParkingZoneVO } from '@/api/parking-zone'

const props = defineProps<{
  open: boolean
  isEditing: boolean
  editingRecord: FeeRuleVO | null
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
const zoneOptions = ref<ParkingZoneVO[]>([])

const formData = reactive({
  lotId: undefined as number | undefined,
  zoneId: undefined as number | undefined,
  name: '',
  billingMode: 1 as number,
  freeMinutes: 15,
  unitMinutes: 60,
  firstPeriodPrice: undefined as number | undefined,
  subsequentPrice: undefined as number | undefined,
  dailyCap: undefined as number | undefined,
  nightCap: undefined as number | undefined,
  priority: 0,
  status: 1,
  effectiveStart: null as any,
  effectiveEnd: null as any,
  timeSegments: [] as Record<string, any>[],
})

const formRules: Record<string, any> = {
  lotId: [{ required: true, message: '请选择所属停车场', trigger: 'change' }],
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  billingMode: [{ required: true, message: '请选择计费模式', trigger: 'change' }],
}

// 加载区域选项
watch(() => formData.lotId, async (lotId) => {
  if (lotId) {
    try {
      const res = await getParkingZonesByLotId(lotId)
      zoneOptions.value = res
    } catch {
      zoneOptions.value = []
    }
  } else {
    zoneOptions.value = []
  }
})

// 编辑时回填数据
watch(() => props.editingRecord, (record) => {
  if (record && props.isEditing) {
    formData.lotId = record.lotId
    formData.zoneId = record.zoneId || undefined
    formData.name = record.name
    formData.billingMode = record.billingMode
    formData.freeMinutes = record.freeMinutes || 0
    formData.unitMinutes = record.unitMinutes || 60
    formData.firstPeriodPrice = record.firstPeriodPrice ? parseFloat(record.firstPeriodPrice) : undefined
    formData.subsequentPrice = record.subsequentPrice ? parseFloat(record.subsequentPrice) : undefined
    formData.dailyCap = record.dailyCap ? parseFloat(record.dailyCap) : undefined
    formData.nightCap = record.nightCap ? parseFloat(record.nightCap) : undefined
    formData.priority = record.priority || 0
    formData.status = record.status || 1
    formData.effectiveStart = record.effectiveStart ? dayjs(record.effectiveStart) : null
    formData.effectiveEnd = record.effectiveEnd ? dayjs(record.effectiveEnd) : null
    formData.timeSegments = record.timeSegments
      ? record.timeSegments.map(s => ({
          segmentName: s.segmentName,
          startTime: s.startTime,
          endTime: s.endTime,
          unitMinutes: s.unitMinutes,
          unitPrice: s.unitPrice ? parseFloat(s.unitPrice) : undefined,
          capAmount: s.capAmount ? parseFloat(s.capAmount) : undefined,
          sortOrder: s.sortOrder,
        }))
      : []
  } else {
    resetForm()
  }
})

function resetForm() {
  formData.lotId = undefined
  formData.zoneId = undefined
  formData.name = ''
  formData.billingMode = 1
  formData.freeMinutes = 15
  formData.unitMinutes = 60
  formData.firstPeriodPrice = undefined
  formData.subsequentPrice = undefined
  formData.dailyCap = undefined
  formData.nightCap = undefined
  formData.priority = 0
  formData.status = 1
  formData.effectiveStart = null
  formData.effectiveEnd = null
  formData.timeSegments = []
}

function handleCancel() {
  open.value = false
}

function handleSubmit() {
  if (!formData.name.trim()) {
    message.warning('请输入规则名称')
    return
  }
  if (!formData.lotId) {
    message.warning('请选择所属停车场')
    return
  }

  formLoading.value = true
  try {
    const payload: Record<string, any> = {
      lotId: formData.lotId,
      zoneId: formData.zoneId,
      name: formData.name.trim(),
      billingMode: formData.billingMode,
      freeMinutes: formData.freeMinutes,
      unitMinutes: formData.unitMinutes,
      firstPeriodPrice: formData.firstPeriodPrice,
      subsequentPrice: formData.subsequentPrice,
      dailyCap: formData.dailyCap,
      nightCap: formData.nightCap,
      priority: formData.priority,
      status: formData.status,
      effectiveStart: formData.effectiveStart ? formData.effectiveStart.format('YYYY-MM-DD HH:mm:ss') : null,
      effectiveEnd: formData.effectiveEnd ? formData.effectiveEnd.format('YYYY-MM-DD HH:mm:ss') : null,
    }

    // 分时段模式添加时段配置
    if (formData.billingMode === 4 && formData.timeSegments.length > 0) {
      payload.timeSegments = formData.timeSegments.map((s, idx) => ({
        segmentName: s.segmentName || `时段${idx + 1}`,
        startTime: s.startTime,
        endTime: s.endTime,
        unitMinutes: s.unitMinutes || 60,
        unitPrice: s.unitPrice,
        capAmount: s.capAmount,
        sortOrder: s.sortOrder || idx,
      }))
    }

    emit('submit', payload)
    open.value = false
  } finally {
    formLoading.value = false
  }
}
</script>
