<template>
  <div class="access-records-page">
    <div class="page-header">
      <h3>通行记录</h3>
    </div>

    <a-card class="filter-bar">
      <a-form layout="inline" :model="filters">
        <a-form-item label="车牌号">
          <a-input v-model:value="filters.plateNumber" placeholder="请输入车牌号" allow-clear style="width: 140px" />
        </a-form-item>
        <a-form-item label="车辆类型">
          <a-select v-model:value="filters.vehicleType" placeholder="全部" allow-clear style="width: 120px">
            <a-select-option value="MONTHLY">月租车</a-select-option>
            <a-select-option value="FIXED">固定车</a-select-option>
            <a-select-option value="VIP">VIP车</a-select-option>
            <a-select-option value="FREE">免费车</a-select-option>
            <a-select-option value="PREPAID">储值车</a-select-option>
            <a-select-option value="TEMP">临时车</a-select-option>
            <a-select-option value="BLACKLIST">黑名单</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="filters.status" placeholder="全部" allow-clear style="width: 120px">
            <a-select-option value="IN">已入场</a-select-option>
            <a-select-option value="OUT">已出场</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="入场车道">
          <a-select v-model:value="filters.laneId" placeholder="全部" allow-clear style="width: 160px" :options="laneFilterOptions" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="table-card">
      <a-table
        :columns="columns"
        :data-source="records"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        size="small"
        :scroll="{ x: 1050 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'plateNumber'">
            <PlateTag :plate-number="record.plateNumber" size="small" />
          </template>
          <template v-if="column.key === 'vehicleType'">
            <a-tag :color="vehicleTypeColor(record.vehicleType)" size="small">
              {{ vehicleTypeLabel(record.vehicleType) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag v-if="record.status === 'IN'" color="processing">已入场</a-tag>
            <a-tag v-else-if="record.status === 'OUT'" color="default">已出场</a-tag>
            <template v-else>{{ record.status || '--' }}</template>
          </template>
          <template v-if="column.key === 'entryTime'">
            {{ record.entryTime ? dayjs(record.entryTime).format('YYYY-MM-DD HH:mm:ss') : '--' }}
          </template>
          <template v-if="column.key === 'exitTime'">
            {{ record.exitTime ? dayjs(record.exitTime).format('YYYY-MM-DD HH:mm:ss') : '--' }}
          </template>
          <template v-if="column.key === 'duration'">
            {{ formatDuration(record.durationMinutes) }}
          </template>
          <template v-if="column.key === 'entryLane'">
            {{ getLaneName(record.laneId) }}
          </template>
          <template v-if="column.key === 'exitLane'">
            {{ record.exitLaneId ? getLaneName(record.exitLaneId) : '--' }}
          </template>
          <template v-if="column.key === 'entryImage'">
            <a-image v-if="record.entryImage" :src="record.entryImage" :width="44" />
            <span v-else style="color:#ccc;font-size:11px">--</span>
          </template>
          <template v-if="column.key === 'exitImage'">
            <a-image v-if="record.exitImage" :src="record.exitImage" :width="44" />
            <span v-else style="color:#ccc;font-size:11px">--</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import dayjs from 'dayjs'
import { getParkingSessions } from '@/api/vehicle-query'
import type { ParkingSessionVO } from '@/api/monitor-types'
import PlateTag from '@/components/PlateTag.vue'

const loading = ref(false)
const records = ref<ParkingSessionVO[]>([])

const filters = reactive({
  plateNumber: '',
  vehicleType: undefined as string | undefined,
  status: undefined as string | undefined,
  laneId: undefined as number | undefined,
})

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
})

const columns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 120 },
  { title: '车辆类型', key: 'vehicleType', width: 90 },
  { title: '订单状态', key: 'status', width: 80 },
  { title: '入场时间', key: 'entryTime', width: 160 },
  { title: '入口车道', key: 'entryLane', width: 100 },
  { title: '入场图片', key: 'entryImage', width: 70 },
  { title: '出场时间', key: 'exitTime', width: 160 },
  { title: '出场车道', key: 'exitLane', width: 100 },
  { title: '出场图片', key: 'exitImage', width: 70 },
  { title: '停车时长', key: 'duration', width: 90 },
]

// 车道名称映射
const laneNames = ref<Record<number, string>>({})
const laneFilterOptions = ref<{ value: number; label: string }[]>([])

async function loadLaneNames() {
  try {
    const lots = await getBoothParkingLots()
    const map: Record<number, string> = {}
    const opts: { value: number; label: string }[] = []
    for (const lot of lots) {
      // 每个车场有自己的车道列表，需要单独获取
      try {
        const { getSnapshot } = await import('@/api/monitor')
        const snapshot = await getSnapshot(lot.id)
        for (const lane of snapshot.lanes || []) {
          map[lane.id] = lane.name || `车道${lane.id}`
          opts.push({ value: lane.id, label: lane.name || `车道${lane.id}` })
        }
      } catch { /* 跳过无权限的车场 */ }
    }
    laneNames.value = map
    laneFilterOptions.value = opts
  } catch { /* 静默 */ }
}

function getLaneName(laneId: number): string {
  return laneNames.value[laneId] || `车道${laneId}`
}

function vehicleTypeLabel(type: string): string {
  if (!type) return '临时车'
  const t = type.toUpperCase()
  const map: Record<string, string> = {
    MONTHLY: '月租车', MONTHLY_PASS: '月租车',
    VIP: 'VIP车',
    FIXED: '固定车', FIXED_SPACE: '固定车', WHITE: '固定车', WHITELIST: '固定车',
    FREE: '免费车',
    PREPAID: '储值车',
    BLACKLIST: '黑名单',
    VISITOR: '访客车',
    TEMP: '临时车', TEMPORARY: '临时车',
  }
  return map[t] || '临时车'
}

function vehicleTypeColor(type: string): string {
  if (!type) return 'orange'
  const t = type.toUpperCase()
  if (t === 'VIP') return 'gold'
  if (t === 'BLACKLIST') return 'red'
  if (t === 'PREPAID') return 'cyan'
  if (t === 'FIXED' || t === 'FIXED_SPACE' || t === 'WHITE' || t === 'WHITELIST') return 'blue'
  if (t === 'MONTHLY' || t === 'MONTHLY_PASS' || t === 'FREE') return 'green'
  return 'orange'
}

function isFixedVehicle(type: string): boolean {
  if (!type) return false
  const t = type.toUpperCase()
  return t === 'WHITE' || t === 'FIXED' || t === 'FIXED_SPACE' || t === 'MONTHLY' || t === 'MONTHLY_PASS' || t === 'WHITELIST'
}

function formatDuration(minutes: number): string {
  if (minutes == null || minutes < 0) return '--'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}时${m}分` : `${m}分`
}

async function loadData() {
  loading.value = true
  try {
    const result = await getParkingSessions({
      current: pagination.current,
      size: pagination.pageSize,
      plateNumber: filters.plateNumber || undefined,
      status: filters.status || undefined,
    })
    let list = result.records || []
    // 客户端筛选：车辆类型和入场车道
    if (filters.vehicleType) {
      if (filters.vehicleType === 'WHITE') {
        list = list.filter(r => isFixedVehicle(r.vehicleType))
      } else {
        list = list.filter(r => !isFixedVehicle(r.vehicleType))
      }
    }
    if (filters.laneId) {
      list = list.filter(r => r.laneId === filters.laneId || r.exitLaneId === filters.laneId)
    }
    records.value = list
    pagination.total = result.total || 0
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadData()
}

function handleReset() {
  filters.plateNumber = ''
  filters.vehicleType = undefined
  filters.status = undefined
  filters.laneId = undefined
  pagination.current = 1
  loadData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadData()
}

onMounted(() => {
  loadLaneNames()
  loadData()
})
</script>

<style lang="scss" scoped>
.access-records-page {
  .page-header { margin-bottom: 16px; h3 { margin: 0; } }
  .filter-bar { margin-bottom: 16px; }
  .table-card { overflow: auto; }
}
</style>
