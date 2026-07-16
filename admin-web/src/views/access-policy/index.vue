<template>
  <div class="access-policy-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select
          v-model:value="queryLotId"
          placeholder="选择停车场"
          allow-clear
          style="width: 180px"
          @change="handleQuery"
        >
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select
          v-model:value="queryPolicyType"
          placeholder="名单类型"
          allow-clear
          style="width: 130px"
          @change="handleQuery"
        >
          <a-select-option v-for="opt in POLICY_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
        <a-input
          v-model:value="queryPlate"
          placeholder="车牌号"
          allow-clear
          style="width: 160px"
          @press-enter="handleQuery"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <a-button type="primary" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增名单
      </a-button>
    </div>

    <!-- 表格区 -->
    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'policyType'">
          <a-tag :color="policyTypeColor(record.policyType)">
            {{ policyTypeText(record.policyType) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">
            {{ statusText(record.status) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'parkingLot'">
          <span>{{ parkingLotName(record.parkingLotId) }}</span>
        </template>
        <template v-if="column.key === 'policyValue'">
          <span class="policy-value">{{ record.policyValue || '-' }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a-divider type="vertical" />
            <a-popconfirm title="确认删除该名单记录？" @confirm="handleDelete(record)">
              <a style="color: #dc2626">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <AccessPolicyFormModal
      v-model:open="formModalOpen"
      :is-editing="isEditing"
      :editing-record="editingRecord"
      :parking-lot-options="parkingLotOptions"
      @submit="handleFormSubmit"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import AccessPolicyFormModal from './AccessPolicyFormModal.vue'
import {
  getAccessPolicies,
  createAccessPolicy,
  updateAccessPolicy,
  deleteAccessPolicy,
  POLICY_TYPE_OPTIONS,
  policyTypeText,
  policyTypeColor,
  statusText,
  statusColor,
  type AccessPolicyVO,
} from '@/api/access-policy'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '车牌号', dataIndex: 'policyKey', key: 'policyKey', width: 140 },
  { title: '名单类型', key: 'policyType', width: 100 },
  { title: '停车场', key: 'parkingLot', width: 160 },
  { title: '策略值', key: 'policyValue', width: 200 },
  { title: '说明', dataIndex: 'description', key: 'description', width: 180, ellipsis: true },
  { title: '状态', key: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<AccessPolicyVO[]>([])
const queryLotId = ref<number | undefined>(undefined)
const queryPolicyType = ref<string | undefined>(undefined)
const queryPlate = ref<string>('')
const parkingLotOptions = ref<ParkingLotVO[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 新增/编辑弹窗
const formModalOpen = ref(false)
const isEditing = ref(false)
const editingRecord = ref<AccessPolicyVO | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      current: pagination.current,
      size: pagination.pageSize,
    }
    if (queryLotId.value !== undefined) {
      params.parkingLotId = queryLotId.value
    }
    if (queryPolicyType.value) {
      params.policyType = queryPolicyType.value
    }
    const res = await getAccessPolicies(params)
    // 前端车牌号过滤
    let records = res.records
    if (queryPlate.value.trim()) {
      const q = queryPlate.value.trim().toUpperCase()
      records = records.filter(r => r.policyKey.toUpperCase().includes(q))
    }
    dataSource.value = records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function loadParkingLots() {
  try {
    const res = await getParkingLots({ page: 1, size: 100 })
    parkingLotOptions.value = res.records
  } catch {
    // ignore
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryLotId.value = undefined
  queryPolicyType.value = undefined
  queryPlate.value = ''
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

function handleCreate() {
  isEditing.value = false
  editingRecord.value = null
  formModalOpen.value = true
}

function handleEdit(record: any) {
  const r = record as AccessPolicyVO
  isEditing.value = true
  editingRecord.value = r
  formModalOpen.value = true
}

function parkingLotName(lotId: number) {
  const lot = parkingLotOptions.value.find(l => l.id === lotId)
  return lot?.name || `车场#${lotId}`
}

async function handleFormSubmit(data: Record<string, any>) {
  try {
    if (isEditing.value && editingRecord.value) {
      await updateAccessPolicy(editingRecord.value.id, data)
      message.success('更新成功')
    } else {
      await createAccessPolicy(data as any)
      message.success('创建成功')
    }
    formModalOpen.value = false
    fetchData()
  } catch (err: any) {
    // 互斥错误：后端返回同一车牌已存在时展示明确提示
    const msg = err?.message || ''
    if (msg.includes('已存在') || msg.includes('互斥') || msg.includes('同时存在') || msg.includes('黑名单') || msg.includes('白名单')) {
      message.error(msg)
    }
    // 其他错误由 request 拦截器统一处理
  }
}

async function handleDelete(record: any) {
  const r = record as AccessPolicyVO
  try {
    await deleteAccessPolicy(r.id)
    message.success('删除成功')
    fetchData()
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadParkingLots()
  fetchData()
})
</script>

<style lang="scss" scoped>
.access-policy-page {
  background: #fff;
  border-radius: $border-radius-base;
  padding: $spacing-lg;
}

.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.policy-value {
  font-size: 12px;
  color: #666;
}
</style>
