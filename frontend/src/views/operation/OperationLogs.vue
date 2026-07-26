<template>
  <div class="operation-logs-page">
    <div class="page-header">
      <h3>操作日志</h3>
      <a-space>
        <a-select v-model:value="filterType" placeholder="全部类型" style="width:130px" allow-clear @change="fetchData">
          <a-select-option value="OPEN_GATE">开闸</a-select-option>
          <a-select-option value="MANUAL_RELEASE">人工放行</a-select-option>
          <a-select-option value="CLOSE_GATE">关闸</a-select-option>
        </a-select>
        <a-input-search v-model:value="filterPlate" placeholder="搜索车牌" @search="fetchData" allow-clear style="width:140px" />
      </a-space>
    </div>

    <a-table :columns="cols" :data-source="data" :loading="loading" :pagination="pag" @change="onPage" row-key="id" size="middle">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'operationType'">
          <a-tag :color="typeColor(record.operationType)">{{ typeLabel(record.operationType) }}</a-tag>
        </template>
        <template v-if="column.key === 'plateNumber'">
          <PlateTag v-if="record.plateNumber" :plate-number="record.plateNumber" size="small" />
          <span v-else>--</span>
        </template>
        <template v-if="column.key === 'entryImage'">
          <a-image v-if="record.entryImage" :src="record.entryImage" :width="60" :preview="{ mask: '查看' }" style="border-radius:4px" />
          <span v-else class="text-muted">--</span>
        </template>
        <template v-if="column.key === 'exitImage'">
          <a-image v-if="record.exitImage" :src="record.exitImage" :width="60" :preview="{ mask: '查看' }" style="border-radius:4px" />
          <span v-else class="text-muted">--</span>
        </template>
        <template v-if="column.key === 'feeCents'">
          {{ record.feeCents ? (record.feeCents / 100).toFixed(2) + ' 元' : '--' }}
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getOperationLogs, type GateOperationLogVO } from '@/api/operation-log'
import PlateTag from '@/components/PlateTag.vue'

const loading = ref(false)
const filterType = ref<string | undefined>(undefined)
const filterPlate = ref('')
const data = ref<GateOperationLogVO[]>([])
const pag = reactive({ current: 1, pageSize: 20, total: 0 })

const cols = [
  { title: '操作时间', dataIndex: 'operationTime', key: 'time', width: 170 },
  { title: '类型', key: 'operationType', width: 80 },
  { title: '车牌号', key: 'plateNumber', width: 110 },
  { title: '车道', dataIndex: 'laneName', key: 'lane', width: 100 },
  { title: '方向', key: 'direction', width: 60 },
  { title: '操作员', dataIndex: 'operatorName', key: 'operator', width: 80 },
  { title: '金额', key: 'feeCents', width: 80 },
  { title: '抓拍照片', key: 'entryImage', width: 80 },
  { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
]

function typeLabel(t: string) {
  const m: Record<string, string> = { OPEN_GATE: '开闸', MANUAL_RELEASE: '人工放行', CLOSE_GATE: '关闸' }
  return m[t] || t
}
function typeColor(t: string) {
  const m: Record<string, string> = { OPEN_GATE: 'blue', MANUAL_RELEASE: 'orange', CLOSE_GATE: 'default' }
  return m[t] || 'default'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getOperationLogs({
      current: pag.current,
      size: pag.pageSize,
      operationType: filterType.value,
      plateNumber: filterPlate.value || undefined,
    })
    data.value = (res.records || []).map(r => ({
      ...r,
      direction: r.direction === 'ENTRY' ? '入场' : r.direction === 'EXIT' ? '出场' : '',
    }))
    pag.total = res.total
  } finally { loading.value = false }
}

function onPage(p: { current: number; pageSize: number }) { pag.current = p.current; pag.pageSize = p.pageSize; fetchData() }

onMounted(() => fetchData())
</script>

<style lang="scss" scoped>
.operation-logs-page { height: 100%; display: flex; flex-direction: column; }
.text-muted { color: #bfbfbf; }
</style>
