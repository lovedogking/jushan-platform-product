<template>
  <div class="holiday-rule-editor">
    <div v-for="(rule, index) in rules" :key="index" class="rule-row">
      <a-select v-model:value="rule.holidayType" placeholder="类型" style="width: 100px">
        <a-select-option value="LEGAL">法定节假日</a-select-option>
        <a-select-option value="NON_LEGAL">非法定节假日</a-select-option>
      </a-select>
      <a-date-picker v-model:value="rule.startDate" placeholder="开始日期" style="width: 130px" />
      <span>~</span>
      <a-date-picker v-model:value="rule.endDate" placeholder="结束日期" style="width: 130px" />
      <a-input-number v-model:value="rule.unitPrice" :min="0" :precision="2" placeholder="单价(元)" style="width: 100px" />
      <a-button type="link" danger @click="removeRule(index)">
        <DeleteOutlined />
      </a-button>
    </div>
    <a-button type="dashed" block @click="addRule">
      <PlusOutlined />添加节假日规则
    </a-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  value: Record<string, any>[]
}>()

const emit = defineEmits<{
  (e: 'update:value', value: Record<string, any>[]): void
}>()

const rules = ref<Record<string, any>[]>(props.value && props.value.length > 0 ? [...props.value] : [defaultRule()])

watch(rules, (val) => {
  emit('update:value', val)
}, { deep: true })

function defaultRule() {
  return {
    holidayType: 'LEGAL',
    startDate: null,
    endDate: null,
    unitPrice: undefined,
  }
}

function addRule() {
  rules.value.push(defaultRule())
}

function removeRule(index: number) {
  if (rules.value.length <= 1) {
    return
  }
  rules.value.splice(index, 1)
}
</script>

<style lang="scss" scoped>
.holiday-rule-editor {
  .rule-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
}
</style>
