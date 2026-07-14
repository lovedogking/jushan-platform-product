<template>
  <div class="time-segment-editor">
    <div v-for="(segment, index) in segments" :key="index" class="segment-row">
      <a-input v-model:value="segment.segmentName" placeholder="时段名称" style="width: 100px" />
      <a-time-picker v-model:value="segment.startTime" placeholder="开始" format="HH:mm" style="width: 90px" />
      <span>~</span>
      <a-time-picker v-model:value="segment.endTime" placeholder="结束" format="HH:mm" style="width: 90px" />
      <a-input-number v-model:value="segment.unitMinutes" :min="1" placeholder="单位(分)" style="width: 80px" />
      <a-input-number v-model:value="segment.unitPrice" :min="0" :precision="2" placeholder="单价(元)" style="width: 100px" />
      <a-input-number v-model:value="segment.capAmount" :min="0" :precision="2" placeholder="封顶(元)" style="width: 100px" />
      <a-button type="link" danger @click="removeSegment(index)">
        <DeleteOutlined />
      </a-button>
    </div>
    <a-button type="dashed" block @click="addSegment">
      <PlusOutlined />添加时段
    </a-button>
    <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

const props = defineProps<{
  value: Record<string, any>[]
}>()

const emit = defineEmits<{
  (e: 'update:value', value: Record<string, any>[]): void
}>()

const segments = ref<Record<string, any>[]>(props.value && props.value.length > 0 ? [...props.value] : [defaultSegment()])
const errorMsg = ref('')

watch(segments, (val) => {
  emit('update:value', val)
  validate()
}, { deep: true })

function defaultSegment() {
  return {
    segmentName: '',
    startTime: null,
    endTime: null,
    unitMinutes: 60,
    unitPrice: undefined,
    capAmount: undefined,
    sortOrder: 0,
  }
}

function addSegment() {
  segments.value.push(defaultSegment())
}

function removeSegment(index: number) {
  if (segments.value.length <= 1) {
    return
  }
  segments.value.splice(index, 1)
}

function validate() {
  errorMsg.value = ''
  const list = segments.value
  for (let i = 0; i < list.length; i++) {
    const s = list[i]
    if (!s.startTime || !s.endTime) {
      errorMsg.value = `时段${i + 1}的时间未填写完整`
      return false
    }
    if (!s.unitPrice || s.unitPrice <= 0) {
      errorMsg.value = `时段${i + 1}的单价必须大于0`
      return false
    }
  }

  // 校验重叠
  for (let i = 0; i < list.length; i++) {
    for (let j = i + 1; j < list.length; j++) {
      const a = list[i]
      const b = list[j]
      const aStart = timeToMinutes(a.startTime)
      const aEnd = timeToMinutes(a.endTime)
      const bStart = timeToMinutes(b.startTime)
      const bEnd = timeToMinutes(b.endTime)
      if (aStart < bEnd && bStart < aEnd) {
        errorMsg.value = `时段${i + 1}与时段${j + 1}存在重叠`
        return false
      }
    }
  }

  return true
}

function timeToMinutes(val: any): number {
  if (!val) return 0
  if (typeof val === 'string') {
    const [h, m] = val.split(':').map(Number)
    return h * 60 + m
  }
  // dayjs object
  if (val.hour !== undefined) {
    return val.hour() * 60 + val.minute()
  }
  return 0
}
</script>

<style lang="scss" scoped>
.time-segment-editor {
  .segment-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
  .error-msg {
    color: #dc2626;
    font-size: 12px;
    margin-top: 8px;
  }
}
</style>
