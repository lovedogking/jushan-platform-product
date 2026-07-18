<template>
  <div class="vehicle-page">
    <!-- 查询区 + 操作区 -->
    <div class="query-bar">
      <a-space>
        <a-input
          v-model:value="queryPlate"
          placeholder="请输入车牌号"
          allow-clear
          style="width: 200px"
          @press-enter="handleQuery"
        />
        <a-select
          v-model:value="queryType"
          placeholder="车辆类型"
          allow-clear
          style="width: 160px"
          :options="typeOptions"
        />
        <a-select
          v-model:value="queryStatus"
          placeholder="状态"
          allow-clear
          style="width: 130px"
          :options="statusOptions"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </a-space>
      <a-button type="default" @click="handleGoBlacklist">
        <template #icon><SafetyOutlined /></template>
        黑白名单
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
        <!-- 车辆类型 -->
        <template v-if="column.key === 'vehicleType'">
          <a-tag :color="typeColor(record.vehicleType)">{{ typeLabel(record.vehicleType) }}</a-tag>
        </template>

        <!-- 状态 -->
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ACTIVE' ? 'green' : (record.status === 'EXPIRED' ? 'orange' : 'red')">
            {{ statusLabel(record.status) }}
          </a-tag>
        </template>

        <!-- 有效期 -->
        <template v-if="column.key === 'validEndDate'">
          <span>{{ record.validEndDate || '—' }}</span>
        </template>

        <!-- 操作列 -->
        <template v-if="column.key === 'action'">
          <a-space>
            <PermissionButton value="vehicle:renew">
              <a @click="openRenew(record)">续费</a>
            </PermissionButton>
          </a-space>
        </template>
      </template>

      <template #emptyText>
        <a-empty description="暂无车辆数据" />
      </template>
    </a-table>

    <!-- 续费弹窗 -->
    <a-modal
      v-model:open="renewOpen"
      title="月卡 / 固定车续费"
      :confirm-loading="renewLoading"
      :after-close="resetRenewForm"
      @ok="handleRenewOk"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="车牌号">
          <span>{{ renewForm.plateNumber }}</span>
        </a-form-item>
        <a-form-item label="车辆类型">
          <span>{{ typeLabel(renewForm.vehicleType) }}</span>
        </a-form-item>
        <a-form-item label="当前有效期">
          <span>{{ renewForm.currentEndDate || '无（从未设置）' }}</span>
        </a-form-item>
        <a-form-item label="续费月数" required>
          <a-input-number
            v-model:value="renewForm.renewalMonths"
            :min="1"
            :max="120"
            style="width: 100%"
            @change="handleMonthsOrPriceChange"
          />
        </a-form-item>
        <a-form-item label="月租金(元)" required>
          <a-input-number
            v-model:value="renewForm.monthlyPrice"
            :min="0"
            :precision="2"
            style="width: 100%"
            @change="handleMonthsOrPriceChange"
          />
        </a-form-item>
        <a-form-item label="支付方式" required>
          <a-select v-model:value="renewForm.payChannel" :options="payChannelOptions" />
        </a-form-item>
        <a-form-item label="续费后有效期">
          <span v-if="preview">{{ preview.newEndDate }}</span>
          <span v-else class="muted">输入续费月数后自动计算</span>
        </a-form-item>
        <a-form-item label="续费金额(元)">
          <span v-if="renewAmountYuan != null">{{ renewAmountYuan.toFixed(2) }}</span>
          <span v-else class="muted">—</span>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button @click="renewOpen = false">取消</a-button>
        <a-button type="primary" :loading="renewLoading" @click="handleRenewOk">确认续费并支付</a-button>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, SafetyOutlined } from '@ant-design/icons-vue'
import {
  getVehiclePage,
  previewRenewal,
  renewVehicle,
  confirmRenewal,
  VEHICLE_TYPE_MAP,
  VEHICLE_STATUS_MAP,
  PAY_CHANNEL_MAP,
  type VehicleVO,
  type RenewalPreviewVO,
} from '@/api/vehicle'
import PermissionButton from '@/components/PermissionButton.vue'

const router = useRouter()

type VehicleTableRow = VehicleVO

const typeOptions = Object.entries(VEHICLE_TYPE_MAP).map(([value, label]) => ({ value, label }))
const statusOptions = Object.entries(VEHICLE_STATUS_MAP).map(([value, label]) => ({ value, label }))
const payChannelOptions = Object.entries(PAY_CHANNEL_MAP).map(([value, label]) => ({ value, label }))

function typeLabel(type?: string) {
  return type ? (VEHICLE_TYPE_MAP[type] || type) : '—'
}
function statusLabel(status?: string) {
  return status ? (VEHICLE_STATUS_MAP[status] || status) : '—'
}
function typeColor(type?: string) {
  switch (type) {
    case 'MONTHLY':
    case 'PREPAID':
      return 'blue'
    case 'VIP':
    case 'SUPER':
      return 'purple'
    case 'FREE':
      return 'green'
    case 'BLACKLIST':
      return 'red'
    default:
      return 'default'
  }
}

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 130 },
  { title: '车辆类型', key: 'vehicleType', width: 140 },
  { title: '车主', dataIndex: 'ownerName', key: 'ownerName', width: 110 },
  { title: '停车场ID', dataIndex: 'parkingLotId', key: 'parkingLotId', width: 110 },
  { title: '有效期至', key: 'validEndDate', width: 130 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<VehicleTableRow[]>([])
const queryPlate = ref('')
const queryType = ref<string | undefined>(undefined)
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
    const res = await getVehiclePage({
      current: pagination.current,
      size: pagination.pageSize,
      plateNumber: queryPlate.value?.trim() || undefined,
      vehicleType: queryType.value,
      status: queryStatus.value,
    })
    dataSource.value = (res.records || []) as VehicleTableRow[]
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
  queryType.value = undefined
  queryStatus.value = undefined
  pagination.current = 1
  fetchData()
}
function handleGoBlacklist() {
  router.push('/vehicle-list')
}
function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

// ============ 续费弹窗 ============
const renewOpen = ref(false)
const renewLoading = ref(false)
const preview = ref<RenewalPreviewVO | null>(null)
const renewForm = reactive({
  vehicleId: 0,
  plateNumber: '',
  vehicleType: '',
  currentEndDate: '',
  renewalMonths: 1,
  monthlyPrice: 0,
  payChannel: 'PYUN',
})

const renewAmountYuan = computed(() => {
  if (renewForm.renewalMonths && renewForm.monthlyPrice != null) {
    return (renewForm.monthlyPrice * renewForm.renewalMonths)
  }
  return null
})

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function openRenew(record: any) {
  Object.assign(renewForm, {
    vehicleId: record.id,
    plateNumber: record.plateNumber,
    vehicleType: record.vehicleType,
    currentEndDate: record.validEndDate || '',
    renewalMonths: 1,
    monthlyPrice: 0,
    payChannel: 'PYUN',
  })
  preview.value = null
  renewOpen.value = true
  loadPreview()
}

async function loadPreview() {
  if (!renewForm.vehicleId || !renewForm.renewalMonths) {
    preview.value = null
    return
  }
  try {
    preview.value = await previewRenewal(renewForm.vehicleId, renewForm.renewalMonths)
  } catch {
    preview.value = null
  }
}

function handleMonthsOrPriceChange() {
  loadPreview()
}

async function handleRenewOk() {
  if (renewForm.renewalMonths == null || renewForm.renewalMonths < 1) {
    message.warning('请输入有效的续费月数')
    return
  }
  if (renewForm.monthlyPrice == null || renewForm.monthlyPrice < 0) {
    message.warning('请输入有效的月租金')
    return
  }
  const amountCents = Math.round(renewForm.monthlyPrice * 100 * renewForm.renewalMonths)
  renewLoading.value = true
  try {
    // 1. 发起续费，创建 MONTH_RENEW 订单（PENDING_PAY）
    const order = await renewVehicle(renewForm.vehicleId, {
      renewalMonths: renewForm.renewalMonths,
      payChannel: renewForm.payChannel,
      amountCents,
    })
    // 2. 确认支付成功，触发生效（延长有效期 / 回写在场车辆类型 / 记录审计）
    const result = await confirmRenewal(order.orderId)
    const newEnd = result.newValidEndDate || preview.value?.newEndDate || ''
    message.success(`续费成功，有效期已延长至 ${newEnd}`)
    renewOpen.value = false
    fetchData()
  } catch {
    // 错误由统一拦截器处理
  } finally {
    renewLoading.value = false
  }
}

function resetRenewForm() {
  preview.value = null
  Object.assign(renewForm, {
    vehicleId: 0,
    plateNumber: '',
    vehicleType: '',
    currentEndDate: '',
    renewalMonths: 1,
    monthlyPrice: 0,
    payChannel: 'PYUN',
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.vehicle-page {
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

.muted {
  color: rgba(0, 0, 0, 0.45);
}
</style>
