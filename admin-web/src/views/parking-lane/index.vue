<template>
  <div class="lane-page">
    <div class="query-bar">
      <a-space>
        <a-select v-model:value="queryParkingLotId" placeholder="停车场" allow-clear style="width: 180px" @change="handleQuery">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 120px" @change="handleQuery">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="ENABLED">已启用</a-select-option>
          <a-select-option value="DISABLED">已停用</a-select-option>
        </a-select>
        <a-select v-model:value="queryDirection" placeholder="方向" allow-clear style="width: 100px" @change="handleQuery">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="ENTRY">入口</a-select-option>
          <a-select-option value="EXIT">出口</a-select-option>
          <a-select-option value="MIXED">混合</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery"><template #icon><SearchOutlined /></template>查询</a-button>
        <a-button @click="handleReset"><template #icon><ReloadOutlined /></template>重置</a-button>
      </a-space>
      <a-button type="primary" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增车道
      </a-button>
    </div>

    <a-table :columns="columns" :data-source="dataSource" :loading="loading" :pagination="pagination" row-key="id" @change="handleTableChange">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'direction'">
          <a-tag :color="record.direction === 'ENTRY' ? 'blue' : record.direction === 'EXIT' ? 'orange' : 'purple'">
            {{ record.direction === 'ENTRY' ? '入口' : record.direction === 'EXIT' ? '出口' : '混合' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'red'">
            {{ record.status === 'ENABLED' ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'isKeyLane'">
          {{ record.isKeyLane === 1 ? '是' : '否' }}
        </template>
        <template v-if="column.key === 'devices'">
          <a-tag v-for="d in record.devices" :key="d.id" style="margin: 2px">{{ d.name }}</a-tag>
          <span v-if="!record.devices?.length" style="color: #999">未绑定</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a v-if="record.status === 'DISABLED'" @click="handleToggleStatus(record, 'ENABLED')">启用</a>
            <a v-else style="color: #dc2626" @click="handleToggleStatus(record, 'DISABLED')">停用</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="formModalOpen" :title="formModalTitle" :confirm-loading="formLoading" width="560px" @ok="handleFormSubmit">
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="所属停车场" required>
          <a-select v-model:value="formData.parkingLotId" placeholder="请选择停车场" :disabled="isEditing">
            <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="车道名称" required>
          <a-input v-model:value="formData.name" placeholder="如：入口1号车道" />
        </a-form-item>
        <a-form-item label="车道编码" required>
          <a-input v-model:value="formData.code" placeholder="如：L01" />
        </a-form-item>
        <a-form-item label="方向" required>
          <a-select v-model:value="formData.direction" placeholder="请选择方向">
            <a-select-option value="ENTRY">入口</a-select-option>
            <a-select-option value="EXIT">出口</a-select-option>
            <a-select-option value="MIXED">混合</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="是否关键车道">
          <a-switch v-model:checked="formData.isKeyLaneChecked" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="formData.description" placeholder="备注（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { getLanes, createLane, updateLane, updateLaneStatus, type ParkingLaneVO } from '@/api/parking-lane'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '车道名称', dataIndex: 'name', key: 'name', width: 140 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 90 },
  { title: '方向', key: 'direction', width: 80 },
  { title: '状态', key: 'status', width: 80 },
  { title: '关键车道', key: 'isKeyLane', width: 80 },
  { title: '绑定设备', key: 'devices', width: 200 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<ParkingLaneVO[]>([])
const queryStatus = ref('')
const queryDirection = ref('')
const queryParkingLotId = ref<number | undefined>(undefined)
const parkingLotOptions = ref<ParkingLotVO[]>([])

const pagination = reactive({
  current: 1, pageSize: 10, total: 0,
  showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条`,
})

const formModalOpen = ref(false)
const formLoading = ref(false)
const formModalTitle = ref('')
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive({
  parkingLotId: undefined as number | undefined,
  name: '', code: '', direction: 'ENTRY',
  isKeyLaneChecked: false, description: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getLanes({
      page: pagination.current, size: pagination.pageSize,
      parkingLotId: queryParkingLotId.value,
      status: queryStatus.value || undefined,
      direction: queryDirection.value || undefined,
    })
    dataSource.value = res.records
    pagination.total = res.total
  } finally { loading.value = false }
}

async function loadParkingLots() {
  const res = await getParkingLots({ page: 1, size: 100 })
  parkingLotOptions.value = res.records.filter(l => l.status === 'ENABLED')
}

function handleQuery() { pagination.current = 1; fetchData() }
function handleReset() { queryStatus.value = ''; queryDirection.value = ''; queryParkingLotId.value = undefined; pagination.current = 1; fetchData() }
function handleTableChange(pag: any) { pagination.current = pag.current; pagination.pageSize = pag.pageSize; fetchData() }

function handleCreate() {
  isEditing.value = false; editingId.value = null
  formModalTitle.value = '新增车道'
  formData.parkingLotId = queryParkingLotId.value || undefined
  formData.name = ''; formData.code = ''; formData.direction = 'ENTRY'
  formData.isKeyLaneChecked = false; formData.description = ''
  formModalOpen.value = true
}

function handleEdit(record: any) {
  isEditing.value = true; editingId.value = record.id
  formModalTitle.value = `编辑车道 — ${record.name}`
  formData.parkingLotId = record.parkingLotId
  formData.name = record.name; formData.code = record.code
  formData.direction = record.direction
  formData.isKeyLaneChecked = record.isKeyLane === 1
  formData.description = record.description || ''
  formModalOpen.value = true
}

async function handleFormSubmit() {
  if (!formData.parkingLotId) { message.warning('请选择停车场'); return }
  if (!formData.name.trim()) { message.warning('请输入车道名称'); return }
  if (!formData.code.trim()) { message.warning('请输入车道编码'); return }
  formLoading.value = true
  try {
    if (isEditing.value && editingId.value) {
      await updateLane(editingId.value, {
        name: formData.name.trim(), code: formData.code.trim(),
        direction: formData.direction,
        isKeyLane: formData.isKeyLaneChecked,
        description: formData.description.trim(),
      })
      message.success('更新成功')
    } else {
      await createLane({
        parkingLotId: formData.parkingLotId!,
        name: formData.name.trim(), code: formData.code.trim(),
        direction: formData.direction,
        isKeyLane: formData.isKeyLaneChecked,
        description: formData.description.trim(),
      })
      message.success('创建成功')
    }
    formModalOpen.value = false; fetchData()
  } catch { /* */ } finally { formLoading.value = false }
}

function handleToggleStatus(record: any, action: string) {
  updateLaneStatus(record.id, action).then(() => {
    message.success(action === 'ENABLED' ? '已启用' : '已停用')
    fetchData()
  }).catch(() => {})
}

onMounted(() => { loadParkingLots(); fetchData() })
</script>

<style lang="scss" scoped>
.lane-page { background: #fff; border-radius: $border-radius-base; padding: $spacing-lg; }
.query-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: $spacing-lg; }
</style>
