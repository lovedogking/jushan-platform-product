<template>
  <div class="map-location-picker">
    <a-input
      v-model:value="localAddress"
      placeholder="搜索地址或点击地图选点"
      allow-clear
      @press-enter="onSearch"
    >
      <template #prefix><SearchOutlined /></template>
    </a-input>
    <a-button type="primary" size="small" style="margin-top: 8px" @click="onSearch">
      搜索并回填
    </a-button>
    <div class="map-placeholder">
      <div class="map-info">
        <EnvironmentOutlined />
        <div>
          <p>地图选点（开发阶段占位）</p>
          <p>点击搜索按钮将模拟回填地址、省市区、经纬度</p>
        </div>
      </div>
    </div>
    <div v-if="localLng && localLat" class="selected-info">
      <a-tag color="blue">经度: {{ localLng }}</a-tag>
      <a-tag color="blue">纬度: {{ localLat }}</a-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { SearchOutlined, EnvironmentOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

const props = defineProps<{
  address?: string
  longitude?: string
  latitude?: string
}>()

const emit = defineEmits<{
  (e: 'change', data: { address: string; province: string; city: string; district: string; longitude: string; latitude: string }): void
}>()

const localAddress = ref(props.address || '')
const localLng = ref(props.longitude || '')
const localLat = ref(props.latitude || '')

watch(() => props.address, (v) => { if (v) localAddress.value = v })
watch(() => props.longitude, (v) => { if (v) localLng.value = v })
watch(() => props.latitude, (v) => { if (v) localLat.value = v })

function onSearch() {
  const addr = localAddress.value.trim()
  if (!addr) {
    message.warning('请输入地址')
    return
  }
  // 开发阶段模拟回填：从地址中提取省市区信息
  const result = parseAddress(addr)
  localLng.value = result.longitude
  localLat.value = result.latitude
  emit('change', {
    address: result.address,
    province: result.province,
    city: result.city,
    district: result.district,
    longitude: result.longitude,
    latitude: result.latitude,
  })
  message.success('地址已回填（开发阶段模拟）')
}

function parseAddress(addr: string) {
  // 简单模拟：如果地址包含"省/市/区"则提取
  let province = ''
  let city = ''
  let district = ''
  let remaining = addr

  const provinceMatch = addr.match(/^([^省]+省)/)
  if (provinceMatch) {
    province = provinceMatch[1]
    remaining = addr.slice(province.length)
  }

  const cityMatch = remaining.match(/^([^市]+市)/)
  if (cityMatch) {
    city = cityMatch[1]
    remaining = remaining.slice(city.length)
  }

  const districtMatch = remaining.match(/^([^区]+区)/)
  if (districtMatch) {
    district = districtMatch[1]
    remaining = remaining.slice(district.length)
  }

  // 模拟经纬度（基于地址长度生成伪随机数）
  const seed = addr.split('').reduce((a, b) => a + b.charCodeAt(0), 0)
  const lng = (121.47 + (seed % 100) / 1000).toFixed(6)
  const lat = (31.23 + (seed % 100) / 1000).toFixed(6)

  return {
    province,
    city,
    district,
    address: remaining || addr,
    longitude: lng,
    latitude: lat,
  }
}
</script>

<style lang="scss" scoped>
.map-location-picker {
  .map-placeholder {
    margin-top: 8px;
    height: 180px;
    background: #f5f7fa;
    border: 1px dashed #d9d9d9;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    .map-info {
      text-align: center;
      color: #999;
      p { margin: 4px 0; }
    }
  }
  .selected-info {
    margin-top: 8px;
  }
}
</style>
