<template>
  <a-modal
    v-model:open="open"
    title="修改收费标准"
    width="480px"
    :confirm-loading="loading"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <a-spin :spinning="!feeRule && open">
      <a-form
        v-if="feeRule"
        :model="form"
        :label-col="{ span: 8 }"
        :wrapper-col="{ span: 16 }"
        layout="horizontal"
      >
        <a-form-item label="规则名称">
          <a-input v-model:value="form.name" placeholder="如：临时车标准收费" />
        </a-form-item>

        <a-form-item label="免费时长（分钟）">
          <a-input-number v-model:value="form.freeMinutes" :min="0" :max="120" style="width: 100%" />
        </a-form-item>

        <a-form-item label="计费单位（分钟）">
          <a-input-number v-model:value="form.unitMinutes" :min="1" :max="1440" style="width: 100%" />
        </a-form-item>

        <a-form-item label="首时段价格（元）">
          <a-input-number v-model:value="form.firstPeriodPrice" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>

        <a-form-item label="后续单价（元）">
          <a-input-number v-model:value="form.subsequentPrice" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>

        <a-form-item label="24小时封顶（元）">
          <a-input-number v-model:value="form.dailyCap" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>

        <a-alert
          message="提示"
          description="修改后的收费标准仅对当前车道生效，不影响其他车场或区域。"
          type="info"
          show-icon
          style="margin-top: 12px"
        />
      </a-form>

      <div v-else style="text-align: center; padding: 32px 0">
        <a-empty description="未加载到收费规则" />
      </div>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'

interface Props {
  open: boolean
  laneId: number
  feeRule: any
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  save: [data: any]
}>()

const loading = ref(false)

const open = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val),
})

const form = ref({
  name: '',
  freeMinutes: 15,
  unitMinutes: 30,
  firstPeriodPrice: 5,
  subsequentPrice: 2,
  dailyCap: 50,
})

// 当 feeRule 加载后，填充表单
watch(
  () => props.feeRule,
  (rule) => {
    if (rule) {
      form.value = {
        name: rule.name || '',
        freeMinutes: rule.freeMinutes ?? 15,
        unitMinutes: rule.unitMinutes ?? 30,
        firstPeriodPrice: rule.firstPeriodPrice ?? 5,
        subsequentPrice: rule.subsequentPrice ?? 2,
        dailyCap: rule.dailyCap ?? 50,
      }
    }
  },
  { immediate: true },
)

function handleOk() {
  if (!form.value.name.trim()) {
    return
  }
  loading.value = true
  emit('save', {
    name: form.value.name,
    freeMinutes: form.value.freeMinutes,
    unitMinutes: form.value.unitMinutes,
    firstPeriodPrice: form.value.firstPeriodPrice,
    subsequentPrice: form.value.subsequentPrice,
    dailyCap: form.value.dailyCap,
  })
  loading.value = false
}

function handleCancel() {
  open.value = false
}
</script>
