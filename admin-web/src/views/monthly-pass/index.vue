<template>
  <div class="monthly-pass-page">
    <!-- 标签页：全部月卡 / 到期预警 -->
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane key="all" tab="全部月卡">
        <!-- 查询区 -->
        <div class="query-bar">
          <a-space>
            <a-input v-model:value="queryPlate" placeholder="车牌号" allow-clear style="width: 180px" @press-enter="handleQuery" />
            <a-select v-model:value="queryStatus" placeholder="状态" allow-clear style="width: 130px" :options="statusOptions" />
            <a-button type="primary" @click="handleQuery">
              <template #icon><SearchOutlined /></template>
              查询
            </a-button>
            <a-button @click="handleReset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
          </a-space>
          <a-button type="primary" @click="openCreate">
            <template #icon><PlusOutlined /></template>
            登记月卡
          </a-button>
        </div>

        <!-- 月卡列表 -->
        <a-table
          :columns="allColumns"
          :data-source="dataSource"
          :loading="loading"
          :pagination="pagination"
          row-key="id"
          @change="handleTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">
                {{ statusLabel(record.status) }}
              </a-tag>
            </template>
            <template v-if="column.key === 'validEndDate'">
              <span :class="{ 'text-warning': isNearExpiry(record as any) }">{{ record.validEndDate || '—' }}</span>
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a @click="openRenew(record as any)">续期</a>
                <a-divider type="vertical" />
                <a-popconfirm title="确定注销此月卡？注销后该车辆按临停计费。" @confirm="handleCancel(record as any)">
                  <a class="text-danger">注销</a>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
          <template #emptyText>
            <a-empty description="暂无月卡数据" />
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="expiring" tab="到期预警">
        <div class="query-bar">
          <span class="expiring-hint">以下月卡将在 7 天内到期，共 {{ expiringTotal }} 条</span>
          <a-button @click="fetchExpiringList">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
        </div>
        <a-table
          :columns="expiringColumns"
          :data-source="expiringDataSource"
          :loading="expiringLoading"
          :pagination="expiringPagination"
          row-key="id"
          @change="handleExpiringTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag color="orange">即将到期</a-tag>
            </template>
            <template v-if="column.key === 'validEndDate'">
              <span class="text-warning">{{ record.validEndDate }}</span>
            </template>
            <template v-if="column.key === 'action'">
              <a @click="openRenew(record as any)">立即续期</a>
            </template>
          </template>
          <template #emptyText>
            <a-empty description="暂无即将到期的月卡" />
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- 登记弹窗 -->
    <a-modal v-model:open="createOpen" title="登记月卡" :confirm-loading="createLoading" width="600px" @ok="handleCreateOk" @cancel="resetCreateForm">
      <a-form ref="createFormRef" :model="createForm" :rules="createRules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="车牌号" name="plateNumber">
          <a-input v-model:value="createForm.plateNumber" placeholder="例：京A12345" style="width: 200px" />
        </a-form-item>
        <a-form-item label="所属车场" name="parkingLotId">
          <a-select
            v-model:value="createForm.parkingLotId"
            placeholder="请选择车场"
            show-search
            :filter-option="filterLotOption"
            :options="lotOptions"
            style="width: 280px"
          />
        </a-form-item>
        <a-form-item label="有效期起" name="validStartDate">
          <a-date-picker v-model:value="createForm.validStartDate" value-format="YYYY-MM-DD" style="width: 200px" />
        </a-form-item>
        <a-form-item label="有效期止" name="validEndDate">
          <a-date-picker v-model:value="createForm.validEndDate" value-format="YYYY-MM-DD" style="width: 200px" />
        </a-form-item>
        <a-form-item label="登记费用(元)">
          <a-input-number v-model:value="createForm.amountYuan" :min="0" :precision="2" style="width: 200px" />
        </a-form-item>
        <a-form-item label="车主姓名">
          <a-input v-model:value="createForm.ownerName" style="width: 200px" />
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="createForm.ownerPhone" style="width: 200px" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="createForm.remark" :rows="2" style="width: 280px" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 续期弹窗 -->
    <a-modal v-model:open="renewOpen" title="月卡续期" :confirm-loading="renewLoading" width="500px" @ok="handleRenewOk" @cancel="resetRenewForm">
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="车牌号">
          <span>{{ renewForm.plateNumber }}</span>
        </a-form-item>
        <a-form-item label="当前有效期">
          <span>{{ renewForm.currentEndDate || '—' }}</span>
        </a-form-item>
        <a-form-item label="续费月数" required>
          <a-select v-model:value="renewForm.renewalMonths" style="width: 200px">
            <a-select-option :value="1">1 个月</a-select-option>
            <a-select-option :value="3">3 个月</a-select-option>
            <a-select-option :value="6">6 个月</a-select-option>
            <a-select-option :value="12">12 个月</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="金额(元)" required>
          <a-input-number v-model:value="renewForm.amountYuan" :min="0" :precision="2" style="width: 200px" />
        </a-form-item>
        <a-form-item label="续费后有效期">
          <span>{{ computedNewEndDate }}</span>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

import {
  getMonthlyPassPage,
  getExpiringList,
  createMonthlyPass,
  renewMonthlyPass,
  cancelMonthlyPass,
  MONTHLY_STATUS_MAP,
  type MonthlyPassVO,
  type MonthlyPassCreateRequest,
  type MonthlyPassRenewRequest,
} from '@/api/monthly-pass'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

// ==================== 状态选项 ====================
const statusOptions = Object.entries(MONTHLY_STATUS_MAP).map(([value, label]) => ({ value, label }))

function statusLabel(status?: string) {
  return status ? (MONTHLY_STATUS_MAP[status] || status) : '—'
}
function statusColor(status?: string) {
  switch (status) {
    case 'ACTIVE': return 'green'
    case 'EXPIRED': return 'red'
    case 'DISABLED': return 'default'
    default: return 'default'
  }
}
function isNearExpiry(record: MonthlyPassVO) {
  if (!record.validEndDate || record.status !== 'ACTIVE') return false
  return dayjs(record.validEndDate).diff(dayjs(), 'day') <= 7
}

// ==================== 表格列定义 ====================
const allColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 130 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 180 },
  { title: '有效期起', dataIndex: 'validStartDate', key: 'validStartDate', width: 120 },
  { title: '有效期止', key: 'validEndDate', width: 120 },
  { title: '状态', key: 'status', width: 90 },
  { title: '车主', dataIndex: 'ownerName', key: 'ownerName', width: 110 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

const expiringColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 130 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 180 },
  { title: '有效期止', key: 'validEndDate', width: 130 },
  { title: '状态', key: 'status', width: 90 },
  { title: '车主', dataIndex: 'ownerName', key: 'ownerName', width: 110 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const },
]

// ==================== 标签页 ====================
const activeTab = ref('all')
function handleTabChange(key: string | number) {
  if (key === 'expiring') {
    fetchExpiringList()
  } else {
    fetchData()
  }
}

// ==================== 全部月卡 ====================
const loading = ref(false)
const dataSource = ref<MonthlyPassVO[]>([])
const queryPlate = ref('')
const queryStatus = ref<string | undefined>(undefined)

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getMonthlyPassPage({
      page: pagination.current,
      size: pagination.pageSize,
      plateNumber: queryPlate.value?.trim() || undefined,
      status: queryStatus.value,
    })
    dataSource.value = res.records || []
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}
function handleReset() {
  queryPlate.value = ''
  queryStatus.value = undefined
  pagination.current = 1
  fetchData()
}
function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

// ==================== 到期预警 ====================
const expiringLoading = ref(false)
const expiringDataSource = ref<MonthlyPassVO[]>([])
const expiringTotal = ref(0)

const expiringPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

async function fetchExpiringList() {
  expiringLoading.value = true
  try {
    const res = await getExpiringList({
      days: 7,
      page: expiringPagination.current,
      size: expiringPagination.pageSize,
    })
    expiringDataSource.value = res.records || []
    expiringTotal.value = res.total || 0
    expiringPagination.total = res.total || 0
  } finally {
    expiringLoading.value = false
  }
}

function handleExpiringTableChange(pag: any) {
  expiringPagination.current = pag.current
  expiringPagination.pageSize = pag.pageSize
  fetchExpiringList()
}

// ==================== 车场选项 ====================
const lotOptions = ref<{ value: number; label: string }[]>([])
async function loadLotOptions() {
  try {
    const res = await getParkingLots({ page: 1, size: 999 })
    lotOptions.value = (res.records || []).map((lot: ParkingLotVO) => ({
      value: lot.id,
      label: lot.name,
    }))
  } catch {
    // 静默失败，下拉为空
  }
}
function filterLotOption(input: string, option: { value: number; label: string } | undefined) {
  return option?.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

// ==================== 登记月卡 ====================
const createOpen = ref(false)
const createLoading = ref(false)
const createFormRef = ref()
const createForm = reactive({
  plateNumber: '',
  parkingLotId: undefined as number | undefined,
  validStartDate: undefined as string | undefined,
  validEndDate: undefined as string | undefined,
  amountYuan: 0,
  ownerName: '',
  ownerPhone: '',
  remark: '',
})

const createRules: Record<string, any[]> = {
  plateNumber: [{ required: true, message: '请输入车牌号' }],
  parkingLotId: [{ required: true, message: '请选择车场', type: 'number' }],
  validStartDate: [{ required: true, message: '请选择有效期起' }],
  validEndDate: [{ required: true, message: '请选择有效期止' }],
}

function openCreate() {
  resetCreateForm()
  createOpen.value = true
}

function resetCreateForm() {
  createFormRef.value?.resetFields()
  Object.assign(createForm, {
    plateNumber: '',
    parkingLotId: undefined,
    validStartDate: undefined,
    validEndDate: undefined,
    amountYuan: 0,
    ownerName: '',
    ownerPhone: '',
    remark: '',
  })
}

async function handleCreateOk() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }
  createLoading.value = true
  try {
    const data: MonthlyPassCreateRequest = {
      plateNumber: createForm.plateNumber.toUpperCase(),
      parkingLotId: createForm.parkingLotId!,
      validStartDate: createForm.validStartDate!,
      validEndDate: createForm.validEndDate!,
      amountCents: Math.round(createForm.amountYuan * 100),
      ownerName: createForm.ownerName || undefined,
      ownerPhone: createForm.ownerPhone || undefined,
      remark: createForm.remark || undefined,
    }
    await createMonthlyPass(data)
    message.success('月卡登记成功')
    createOpen.value = false
    fetchData()
  } catch {
    // 错误由统一拦截器处理
  } finally {
    createLoading.value = false
  }
}

// ==================== 月卡续期 ====================
const renewOpen = ref(false)
const renewLoading = ref(false)
const renewForm = reactive({
  id: 0,
  plateNumber: '',
  currentEndDate: '',
  renewalMonths: 1,
  amountYuan: 0,
})

const computedNewEndDate = computed(() => {
  if (!renewForm.currentEndDate || !renewForm.renewalMonths) return '—'
  return dayjs(renewForm.currentEndDate).add(renewForm.renewalMonths, 'month').format('YYYY-MM-DD')
})

function openRenew(record: MonthlyPassVO) {
  Object.assign(renewForm, {
    id: record.id,
    plateNumber: record.plateNumber,
    currentEndDate: record.validEndDate || '',
    renewalMonths: 1,
    amountYuan: 0,
  })
  renewOpen.value = true
}

function resetRenewForm() {
  Object.assign(renewForm, {
    id: 0,
    plateNumber: '',
    currentEndDate: '',
    renewalMonths: 1,
    amountYuan: 0,
  })
}

async function handleRenewOk() {
  if (!renewForm.renewalMonths || renewForm.renewalMonths < 1) {
    message.warning('请选择续费月数')
    return
  }
  if (renewForm.amountYuan == null || renewForm.amountYuan < 0) {
    message.warning('请输入续费金额')
    return
  }
  renewLoading.value = true
  try {
    const data: MonthlyPassRenewRequest = {
      renewalMonths: renewForm.renewalMonths,
      amountCents: Math.round(renewForm.amountYuan * 100),
    }
    const result = await renewMonthlyPass(renewForm.id, data)
    message.success(`续期成功，有效期已延长至 ${result.validEndDate}`)
    renewOpen.value = false
    if (activeTab.value === 'all') {
      fetchData()
    } else {
      fetchExpiringList()
    }
  } catch {
    // 错误由统一拦截器处理
  } finally {
    renewLoading.value = false
  }
}

// ==================== 月卡注销 ====================
async function handleCancel(record: MonthlyPassVO) {
  try {
    await cancelMonthlyPass(record.id)
    message.success('月卡已注销')
    fetchData()
  } catch {
    // 错误由统一拦截器处理
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  fetchData()
  loadLotOptions()
})
</script>

<style lang="scss" scoped>
.monthly-pass-page {
  background: #fff;
  border-radius: $border-radius-base;
  padding: $spacing-lg;
}

.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
  gap: 8px;
}

.expiring-hint {
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}

.text-warning {
  color: #f59e0b;
  font-weight: 500;
}

.text-danger {
  color: #dc2626;
}
</style>
