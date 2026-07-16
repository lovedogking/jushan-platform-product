<template>
  <div class="fixed-space-page">
    <!-- 标签页：全部 / 到期预警 -->
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane key="all" tab="全部绑定">
        <!-- 查询区 -->
        <div class="query-bar">
          <a-space>
            <a-input v-model:value="querySpaceNo" placeholder="车位号" allow-clear style="width: 140px" @press-enter="handleQuery" />
            <a-input v-model:value="queryPlate" placeholder="车牌号" allow-clear style="width: 140px" @press-enter="handleQuery" />
            <a-select v-model:value="queryLotId" placeholder="车场" allow-clear style="width: 160px" :options="lotOptions" />
            <a-select v-model:value="queryStatus" placeholder="状态" allow-clear style="width: 110px" :options="statusOptions" />
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
            绑定车位
          </a-button>
        </div>

        <!-- 列表 -->
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
            <template v-if="column.key === 'validEnd'">
              <span :class="{ 'text-warning': isNearExpiry(record as any) }">{{ record.validEnd || '—' }}</span>
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a @click="openRenew(record as any)">续期</a>
                <a-divider type="vertical" />
                <a-popconfirm title="确定注销此固定车位？注销后该车辆按临停计费。" @confirm="handleCancel(record as any)">
                  <a class="text-danger">注销</a>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
          <template #emptyText>
            <a-empty description="暂无固定车位绑定数据" />
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="expiring" tab="到期预警">
        <div class="query-bar">
          <span class="expiring-hint">以下固定车位绑定将在 7 天内到期，共 {{ expiringTotal }} 条</span>
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
            <template v-if="column.key === 'validEnd'">
              <span class="text-warning">{{ record.validEnd }}</span>
            </template>
            <template v-if="column.key === 'action'">
              <a @click="openRenew(record as any)">立即续期</a>
            </template>
          </template>
          <template #emptyText>
            <a-empty description="暂无即将到期的固定车位" />
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- 绑定弹窗 -->
    <a-modal v-model:open="createOpen" title="绑定固定车位" :confirm-loading="createLoading" width="600px" @ok="handleCreateOk" @cancel="resetCreateForm">
      <a-form ref="createFormRef" :model="createForm" :rules="createRules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="车场" name="parkingLotId">
          <a-select
            v-model:value="createForm.parkingLotId"
            placeholder="请选择车场"
            show-search
            :filter-option="filterLotOption"
            :options="lotOptions"
            style="width: 280px"
            @change="loadZoneOptions"
          />
        </a-form-item>
        <a-form-item label="区域" name="zoneId">
          <a-select
            v-model:value="createForm.zoneId"
            placeholder="请选择区域（可选）"
            allow-clear
            :options="zoneOptions"
            style="width: 280px"
          />
        </a-form-item>
        <a-form-item label="车位号" name="spaceNo">
          <a-input v-model:value="createForm.spaceNo" placeholder="例：A-001" style="width: 200px" />
        </a-form-item>
        <a-form-item label="车牌号" name="plateNumber">
          <a-input v-model:value="createForm.plateNumber" placeholder="例：京A12345" style="width: 200px" />
        </a-form-item>
        <a-form-item label="有效期起" name="validStart">
          <a-date-picker v-model:value="createForm.validStart" value-format="YYYY-MM-DD" style="width: 200px" />
        </a-form-item>
        <a-form-item label="有效期止" name="validEnd">
          <a-date-picker v-model:value="createForm.validEnd" value-format="YYYY-MM-DD" style="width: 200px" />
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="createForm.remark" :rows="2" style="width: 280px" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 续期弹窗 -->
    <a-modal v-model:open="renewOpen" title="固定车位续期" :confirm-loading="renewLoading" width="500px" @ok="handleRenewOk" @cancel="resetRenewForm">
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="车位号">
          <span>{{ renewForm.spaceNo }}</span>
        </a-form-item>
        <a-form-item label="车牌号">
          <span>{{ renewForm.plateNumber }}</span>
        </a-form-item>
        <a-form-item label="当前有效期止">
          <span>{{ renewForm.currentEndDate || '—' }}</span>
        </a-form-item>
        <a-form-item label="新有效期止" required>
          <a-date-picker v-model:value="renewForm.newValidEnd" value-format="YYYY-MM-DD" style="width: 200px" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

import {
  getFixedSpacePage,
  getExpiringSpaces,
  createFixedSpace,
  renewFixedSpace,
  cancelFixedSpace,
  FIXED_SPACE_STATUS_MAP,
  type FixedSpaceVO,
  type FixedSpaceCreateRequest,
  type FixedSpaceRenewRequest,
} from '@/api/fixed-space'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'
import { getParkingZonesByLotId, type ParkingZoneVO } from '@/api/parking-zone'

// ==================== 状态选项 ====================
const statusOptions = Object.entries(FIXED_SPACE_STATUS_MAP).map(([value, label]) => ({
  value: Number(value),
  label,
}))

function statusLabel(status?: number) {
  return status != null ? (FIXED_SPACE_STATUS_MAP[status] || String(status)) : '—'
}
function statusColor(status?: number) {
  switch (status) {
    case 1: return 'green'
    case 2: return 'red'
    case 3: return 'default'
    default: return 'default'
  }
}
function isNearExpiry(record: FixedSpaceVO) {
  if (!record.validEnd || record.status !== 1) return false
  return dayjs(record.validEnd).diff(dayjs(), 'day') <= 7
}

// ==================== 表格列定义 ====================
const allColumns = [
  { title: '车位号', dataIndex: 'spaceNo', key: 'spaceNo', width: 110 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 160 },
  { title: '区域', dataIndex: 'zoneName', key: 'zoneName', width: 120 },
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 130 },
  { title: '有效期起', dataIndex: 'validStart', key: 'validStart', width: 120 },
  { title: '有效期止', key: 'validEnd', width: 120 },
  { title: '状态', key: 'status', width: 80 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

const expiringColumns = [
  { title: '车位号', dataIndex: 'spaceNo', key: 'spaceNo', width: 110 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 160 },
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 130 },
  { title: '有效期止', key: 'validEnd', width: 130 },
  { title: '状态', key: 'status', width: 80 },
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

// ==================== 全部列表 ====================
const loading = ref(false)
const dataSource = ref<FixedSpaceVO[]>([])
const querySpaceNo = ref('')
const queryPlate = ref('')
const queryLotId = ref<number | undefined>(undefined)
const queryStatus = ref<number | undefined>(undefined)

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
    const res = await getFixedSpacePage({
      page: pagination.current,
      size: pagination.pageSize,
      spaceNo: querySpaceNo.value?.trim() || undefined,
      plateNumber: queryPlate.value?.trim() || undefined,
      parkingLotId: queryLotId.value,
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
  querySpaceNo.value = ''
  queryPlate.value = ''
  queryLotId.value = undefined
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
const expiringDataSource = ref<FixedSpaceVO[]>([])
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
    const res = await getExpiringSpaces({
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
    // 静默失败
  }
}
function filterLotOption(input: string, option: { value: number; label: string } | undefined) {
  return option?.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

// ==================== 区域选项（跟随车场变化） ====================
const zoneOptions = ref<{ value: number; label: string }[]>([])
async function loadZoneOptions(lotId: any) {
  const id = Number(lotId)
  zoneOptions.value = []
  if (!id) return
  try {
    const res = await getParkingZonesByLotId(id)
    zoneOptions.value = (res || []).map((z: ParkingZoneVO) => ({
      value: z.id,
      label: z.name,
    }))
  } catch {
    // 静默失败
  }
}

// ==================== 绑定 ====================
const createOpen = ref(false)
const createLoading = ref(false)
const createFormRef = ref()
const createForm = reactive({
  parkingLotId: undefined as number | undefined,
  zoneId: undefined as number | undefined,
  spaceNo: '',
  plateNumber: '',
  validStart: undefined as string | undefined,
  validEnd: undefined as string | undefined,
  remark: '',
})

const createRules: Record<string, any[]> = {
  parkingLotId: [{ required: true, message: '请选择车场', type: 'number' }],
  spaceNo: [{ required: true, message: '请输入车位号' }],
  plateNumber: [{ required: true, message: '请输入车牌号' }],
  validStart: [{ required: true, message: '请选择有效期起' }],
  validEnd: [{ required: true, message: '请选择有效期止' }],
}

function openCreate() {
  resetCreateForm()
  createOpen.value = true
}

function resetCreateForm() {
  createFormRef.value?.resetFields()
  Object.assign(createForm, {
    parkingLotId: undefined,
    zoneId: undefined,
    spaceNo: '',
    plateNumber: '',
    validStart: undefined,
    validEnd: undefined,
    remark: '',
  })
  zoneOptions.value = []
}

async function handleCreateOk() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }
  createLoading.value = true
  try {
    const data: FixedSpaceCreateRequest = {
      parkingLotId: createForm.parkingLotId!,
      zoneId: createForm.zoneId || undefined,
      spaceNo: createForm.spaceNo.trim(),
      plateNumber: createForm.plateNumber.toUpperCase(),
      validStart: createForm.validStart!,
      validEnd: createForm.validEnd!,
      remark: createForm.remark || undefined,
    }
    await createFixedSpace(data)
    message.success('固定车位绑定成功')
    createOpen.value = false
    fetchData()
  } catch {
    // 错误由统一拦截器处理
  } finally {
    createLoading.value = false
  }
}

// ==================== 续期 ====================
const renewOpen = ref(false)
const renewLoading = ref(false)
const renewForm = reactive({
  id: 0,
  spaceNo: '',
  plateNumber: '',
  currentEndDate: '',
  newValidEnd: undefined as string | undefined,
})

function openRenew(record: FixedSpaceVO) {
  Object.assign(renewForm, {
    id: record.id,
    spaceNo: record.spaceNo,
    plateNumber: record.plateNumber,
    currentEndDate: record.validEnd || '',
    newValidEnd: undefined,
  })
  renewOpen.value = true
}

function resetRenewForm() {
  Object.assign(renewForm, {
    id: 0,
    spaceNo: '',
    plateNumber: '',
    currentEndDate: '',
    newValidEnd: undefined,
  })
}

async function handleRenewOk() {
  if (!renewForm.newValidEnd) {
    message.warning('请选择新有效期止')
    return
  }
  if (renewForm.currentEndDate && dayjs(renewForm.newValidEnd).isBefore(dayjs(renewForm.currentEndDate))) {
    message.warning('新有效期止不能早于当前有效期止')
    return
  }
  renewLoading.value = true
  try {
    const data: FixedSpaceRenewRequest = {
      newValidEnd: renewForm.newValidEnd,
    }
    await renewFixedSpace(renewForm.id, data)
    message.success('续期成功')
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

// ==================== 注销 ====================
async function handleCancel(record: FixedSpaceVO) {
  try {
    await cancelFixedSpace(record.id)
    message.success('固定车位已注销')
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
.fixed-space-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
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
