<template>
  <div class="fee-rule-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select v-model:value="queryLotId" placeholder="选择停车场" allow-clear style="width: 180px" @change="handleQuery">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select v-model:value="queryBillingMode" placeholder="计费模式" allow-clear style="width: 140px" @change="handleQuery">
          <a-select-option v-for="opt in BILLING_MODE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 130px" @change="handleQuery">
          <a-select-option v-for="opt in FEE_RULE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <a-space>
        <a-button type="primary" @click="handleCreate">
          <template #icon><PlusOutlined /></template>新增规则
        </a-button>
        <a-button @click="handleGoCalculator">
          <template #icon><CalculatorOutlined /></template>费用试算
        </a-button>
      </a-space>
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
        <template v-if="column.key === 'billingMode'">
          <a-tag :color="billingModeColor(record.billingMode)">
            {{ billingModeText(record.billingMode) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'red'">
            {{ record.status === 1 ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'priceInfo'">
          <span class="price-summary">{{ priceSummary(record) }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleCopy(record)">复制</a>
            <a @click="handleEdit(record)">编辑</a>
            <a-divider type="vertical" />
            <a v-if="record.status !== 1" @click="handleToggleStatus(record, 1)">启用</a>
            <a v-else style="color: #dc2626" @click="handleToggleStatus(record, 2)">禁用</a>
            <a-divider type="vertical" />
            <a-popconfirm title="确认删除该收费规则？" @confirm="handleDelete(record)">
              <a style="color: #dc2626">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <FeeRuleFormModal
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
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined, CalculatorOutlined } from '@ant-design/icons-vue'
import FeeRuleFormModal from './FeeRuleFormModal.vue'
import {
  getFeeRules,
  createFeeRule,
  updateFeeRule,
  deleteFeeRule,
  copyFeeRule,
  updateFeeRuleStatus,
  BILLING_MODE_OPTIONS,
  FEE_RULE_STATUS_OPTIONS,
  billingModeText,
  billingModeColor,
  type FeeRuleVO,
} from '@/api/fee-rule'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '规则名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '计费模式', key: 'billingMode', width: 110 },
  { title: '价格信息', key: 'priceInfo', width: 200 },
  { title: '免费时长(分)', dataIndex: 'freeMinutes', key: 'freeMinutes', width: 110 },
  { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80 },
  { title: '状态', key: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 240, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<FeeRuleVO[]>([])
const queryLotId = ref<number | undefined>(undefined)
const queryBillingMode = ref<number | undefined>(undefined)
const queryStatus = ref<number | undefined>(undefined)
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
const editingRecord = ref<FeeRuleVO | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getFeeRules({
      current: pagination.current,
      size: pagination.pageSize,
      lotId: queryLotId.value,
      billingMode: queryBillingMode.value,
      status: queryStatus.value,
    })
    dataSource.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function loadParkingLots() {
  try {
    const res = await getParkingLots({ current: 1, size: 100 })
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
  queryBillingMode.value = undefined
  queryStatus.value = undefined
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
  const r = record as FeeRuleVO
  isEditing.value = true
  editingRecord.value = r
  formModalOpen.value = true
}

async function handleCopy(record: any) {
  const r = record as FeeRuleVO
  try {
    await copyFeeRule(r.id)
    message.success('复制成功')
    fetchData()
  } catch {
    // ignore
  }
}

async function handleFormSubmit(data: Record<string, any>) {
  try {
    if (isEditing.value && editingRecord.value) {
      await updateFeeRule(editingRecord.value.id, data)
      message.success('更新成功')
    } else {
      await createFeeRule(data)
      message.success('创建成功')
    }
    formModalOpen.value = false
    fetchData()
  } catch {
    // ignore
  }
}

function handleToggleStatus(record: any, status: number) {
  const r = record as FeeRuleVO
  const actionText = status === 1 ? '启用' : '禁用'
  updateFeeRuleStatus(r.id, status).then(() => {
    message.success(`已${actionText}`)
    fetchData()
  }).catch(() => {})
}

async function handleDelete(record: any) {
  const r = record as FeeRuleVO
  try {
    await deleteFeeRule(r.id)
    message.success('删除成功')
    fetchData()
  } catch {
    // ignore
  }
}

function handleGoCalculator() {
  router.push('/fee-calculator')
}

function priceSummary(record: any) {
  const r = record as FeeRuleVO
  const mode = r.billingMode
  if (mode === 1) {
    const first = r.firstPeriodPrice || '0'
    const sub = r.subsequentPrice || '0'
    return `首${first}元 + 续${sub}元/${r.unitMinutes}分`
  }
  if (mode === 2) {
    return `固定${r.firstPeriodPrice || '0'}元/次`
  }
  if (mode === 3) {
    return `阶梯: 首${r.firstPeriodPrice || '0'}元 + 续${r.subsequentPrice || '0'}元`
  }
  if (mode === 4) {
    return '分时段计费'
  }
  return '-'
}

onMounted(() => {
  loadParkingLots()
  fetchData()
})
</script>

<style lang="scss" scoped>
.fee-rule-page {
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

.price-summary {
  font-size: 12px;
  color: #666;
}
</style>
