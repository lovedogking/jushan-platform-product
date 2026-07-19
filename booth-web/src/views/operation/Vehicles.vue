<template>
  <div class="page">
    <div class="toolbar">
      <h3>固定车管理 (OP-01)</h3>
      <a-space>
        <!-- platform：下拉选场；tenant 单场：锁定显示场名 -->
        <template v-if="isTenantOnly && lotOptions.length === 1">
          <a-tag color="blue" style="font-size:14px;padding:4px 12px">{{ lotOptions[0]!.name }}</a-tag>
        </template>
        <a-select
          v-else
          v-model:value="selectedLotId" placeholder="选择车场" style="width:200px" @change="fetchData" :loading="lotLoading"
        >
          <a-select-option v-for="lot in lotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-input-search v-model:value="plateFilter" placeholder="搜索车牌" @search="fetchData" allow-clear style="width:160px" />
        <a-upload :before-upload="handleImport" accept=".xlsx" :show-upload-list="false">
          <a-button><template #icon><UploadOutlined /></template>Excel 导入</a-button>
        </a-upload>
      </a-space>
    </div>
    <a-table :columns="cols" :data-source="data" :loading="loading" :pagination="pag" @change="onPage" row-key="id" size="middle">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'listType'"><a-tag color="green">白名单</a-tag></template>
        <template v-if="column.key === 'actions'">
          <a-popconfirm title="确定删除？" @confirm="handleDelete(record.id)">
            <a style="color:red">删除</a>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-modal v-model:visible="importVisible" title="导入结果" @ok="importVisible = false" cancel-button-props="{ style: { display: 'none' } }" width="600px">
      <a-descriptions bordered size="small" :column="3">
        <a-descriptions-item label="总数">{{ importResult.total }}</a-descriptions-item>
        <a-descriptions-item label="成功">{{ importResult.successCount }}</a-descriptions-item>
        <a-descriptions-item label="失败">{{ importResult.failCount }}</a-descriptions-item>
      </a-descriptions>
      <a-table v-if="importResult.errors?.length" :columns="errCols" :data-source="importResult.errors" size="small" :pagination="false" style="margin-top:12px" row-key="plate" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import { getVehicleList, deleteVehicle, importVehicles, getParkingLots, type VehicleListVO } from '@/api/parking-manage'
import { getBoothParkingLots } from '@/api/parking-lot'

const ROLES_KEY = 'jushan_roles'

function getUserRoles(): string[] {
  try {
    const raw = sessionStorage.getItem(ROLES_KEY)
    return raw ? JSON.parse(raw) : []
  } catch { return [] }
}

const isPlatform = computed(() => getUserRoles().includes('platform'))
const isTenantOnly = computed(() => !getUserRoles().includes('platform') && getUserRoles().includes('tenant'))

const loading = ref(false); const plateFilter = ref(''); const lotLoading = ref(false)
const selectedLotId = ref<number | undefined>(undefined)
const lotOptions = ref<{ id: number; name: string }[]>([])
const data = ref<VehicleListVO[]>([])
const pag = reactive({ current: 1, pageSize: 10, total: 0 })

const importVisible = ref(false)
const importResult = reactive<{ total: number; successCount: number; failCount: number; errors: any[] }>({ total: 0, successCount: 0, failCount: 0, errors: [] })

const cols = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plate' },
  { title: '类型', key: 'listType', width: 80 },
  { title: '所属车场', dataIndex: 'parkingLotName', key: 'lot' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'time', width: 170 },
  { title: '操作', key: 'actions', width: 80 },
]
const errCols = [
  { title: '行号', dataIndex: 'row', key: 'row', width: 60 },
  { title: '车牌', dataIndex: 'plate', key: 'plate' },
  { title: '原因', dataIndex: 'reason', key: 'reason' },
]

async function fetchData() {
  loading.value = true
  try {
    const res = await getVehicleList({
      page: pag.current, size: pag.pageSize,
      plateNumber: plateFilter.value || undefined,
      listType: 'WHITE',
      parkingLotId: selectedLotId.value,
    })
    data.value = res.records; pag.total = res.total
  } finally { loading.value = false }
}
async function handleDelete(id: number) {
  try { await deleteVehicle(id); message.success('已删除'); fetchData() } catch (e: any) { message.error(e.message) }
}
async function handleImport(file: File) {
  const lotId = selectedLotId.value
  if (!lotId) { message.warning('请先选择车场'); return false }
  try {
    const res = await importVehicles(file, lotId)
    Object.assign(importResult, res)
    importVisible.value = true; fetchData()
  } catch (e: any) { message.error(e.message || '导入失败') }
  return false // prevent default upload
}
async function loadLotOptions() {
  lotLoading.value = true
  try {
    if (isPlatform.value) {
      // platform 用户：加载全部车场
      const res = await getParkingLots({ page: 1, size: 1000 })
      lotOptions.value = (res.records || []).map((l: any) => ({ id: l.id, name: l.name }))
    } else {
      // tenant/booth 用户：只加载授权车场
      const lots = await getBoothParkingLots()
      lotOptions.value = (lots || []).map((l: any) => ({ id: l.id, name: l.name }))
    }
    // 默认选中第一个车场
    if (lotOptions.value.length > 0 && !selectedLotId.value) {
      selectedLotId.value = lotOptions.value[0]!.id
      fetchData()
    }
  } catch { /* */ } finally { lotLoading.value = false }
}
function onPage(p: { current: number; pageSize: number }) { pag.current = p.current; pag.pageSize = p.pageSize; fetchData() }
onMounted(() => loadLotOptions())
</script>

<style lang="scss" scoped>
.page { .toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; h3 { margin: 0; } } }
</style>
