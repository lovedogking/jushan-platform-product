<template>
  <span class="plate-tag" :class="plateClass" :style="plateStyle">{{ displayPlate }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  plateNumber: string
  plateColor?: string
  size?: 'default' | 'small'
}>(), { size: 'default' })

const displayPlate = computed(() => {
  if (!props.plateNumber) return '--'
  return props.plateNumber.toUpperCase().replace(/\?/g, '')
})

function detectColor(plate: string): string {
  const clean = plate.replace(/\?/g, '').toUpperCase()
  // 军警白牌
  if (/^[军警WJ]/.test(clean)) return 'WHITE'
  // 使领馆黑牌
  if (/^[使领]/.test(clean)) return 'BLACK'
  // 新能源绿牌：8位
  if (clean.length >= 8) return 'GREEN'
  // 默认蓝牌（黄牌需显式 plateColor 区分）
  return 'BLUE'
}

const color = computed(() => {
  if (props.plateColor) return props.plateColor.toUpperCase()
  return detectColor(props.plateNumber || '')
})

const plateClass = computed(() => {
  switch (color.value) {
    case 'GREEN': return 'plate-green'
    case 'YELLOW': return 'plate-yellow'
    case 'BLACK': return 'plate-black'
    case 'WHITE': return 'plate-white'
    default: return 'plate-blue'
  }
})

const plateStyle = computed(() => ({
  fontSize: props.size === 'small' ? '12px' : '14px',
  padding: props.size === 'small' ? '1px 6px' : '2px 8px',
  lineHeight: props.size === 'small' ? '18px' : '22px',
}))
</script>

<style scoped>
.plate-tag {
  display: inline-block;
  font-weight: 700;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  border-radius: 4px;
  letter-spacing: 1px;
  white-space: nowrap;
}
.plate-blue { background: #1e6fff; color: #fff; border: 1px solid #1558d6; }
.plate-green { background: linear-gradient(135deg, #10b981, #059669); color: #fff; border: 1px solid #047857; }
.plate-yellow { background: #f59e0b; color: #111827; border: 1px solid #d97706; }
.plate-black { background: #1f2937; color: #f9fafb; border: 1px solid #111827; }
.plate-white { background: #f9fafb; color: #111827; border: 1px solid #d1d5db; }
</style>
