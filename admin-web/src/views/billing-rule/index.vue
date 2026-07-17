<template>
  <div class="billing-rule-page">
    <a-alert
      message="本期使用旧 billing_rule 计费体系，新 fee_rule 体系冻结为二期候选。"
      type="info"
      show-icon
      closable
      style="margin-bottom: 16px"
    />

    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select v-model:value="queryParkingLotId" placeholder="选择停车场" allow-clear style="width: 180px" @change="handleQuery">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 130px" @change="handleQuery">
          <a-select-option v-for="opt in RULE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
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
        <template v-if="column.key === 'ruleType'">
          <a-tag :color="ruleTypeColor(record.ruleType)">
            {{ record.ruleTypeDesc || record.ruleType }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'red'">
            {{ record.statusDesc || (record.status === 'ENABLED' ? '启用' : '禁用') }}
          </a-tag>
        </template>
        <template v-if="column.key === 'configSummary'">
          <span class="config-summary">{{ record.configSummary || '-' }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a-divider type="vertical" />
            <a-popconfirm title="确认删除该收费规则？" @confirm="handleDelete(record)">
              <a style="color: #dc2626">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <BillingRuleFormModal
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
import BillingRuleFormModal from './BillingRuleFormModal.vue'
import {
  getBillingRules,
  createBillingRule,
  updateBillingRule,
  RULE_STATUS_OPTIONS,
  RULE_TYPE_OPTIONS,
  type BillingRuleVO,
} from '@/api/billing-rule'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '规则名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '所属车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 150 },
  { title: '计费类型', key: 'ruleType', width: 110 },
  { title: '收费概要', key: 'configSummary', width: 220 },
  { title: '状态', key: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 160, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<BillingRuleVO[]>([])
const queryParkingLotId = ref<number | undefined>(undefined)
const queryStatus = ref<string | undefined>(undefined)
const parkingLotOptions = ref<ParkingLotVO[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const formModalOpen = ref(false)
const isEditing = ref(false)
const editingRecord = ref<BillingRuleVO | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getBillingRules({
      page: pagination.current,
      size: pagination.pageSize,
      parkingLotId: queryParkingLotId.value,
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
  queryParkingLotId.value = undefined
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
  isEditing.value = true
  editingRecord.value = record as BillingRuleVO
  formModalOpen.value = true
}

async function handleFormSubmit(data: Record<string, any>) {
  try {
    if (isEditing.value && editingRecord.value) {
      await updateBillingRule(editingRecord.value.id, data)
      message.success('更新成功')
    } else {
      await createBillingRule(data as any)
      message.success('创建成功')
    }
    formModalOpen.value = false
    fetchData()
  } catch {
    // ignore
  }
}

async function handleDelete(record: any) {
  message.info('本期旧 billing_rule 体系暂不支持删除，请使用状态禁用替代')
}

function ruleTypeColor(ruleType: string) {
  const map: Record<string, string> = { HOURLY: 'blue', FIXED: 'green', NO_FEE: 'default' }
  return map[ruleType] || 'default'
}

onMounted(() => {
  loadParkingLots()
  fetchData()
})
</script>

<style lang="scss" scoped>
.billing-rule-page {
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

.config-summary {
  font-size: 12px;
  color: #666;
}
</style>
