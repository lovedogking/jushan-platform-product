<template>
  <div class="order-page">
    <!-- 筛选栏 -->
    <div class="query-bar">
      <a-space wrap>
        <a-input v-model:value="query.orderNo" placeholder="订单号" allow-clear style="width: 180px" @press-enter="handleQuery" />
        <a-input v-model:value="query.plateNumber" placeholder="车牌号" allow-clear style="width: 140px" @press-enter="handleQuery" />
        <a-select
          v-model:value="query.parkingLotId"
          placeholder="车场"
          allow-clear
          show-search
          :filter-option="filterLotOption"
          style="width: 180px"
          :options="lotOptions"
        />
        <a-range-picker
          v-model:value="query.dateRange"
          :show-time="{ format: 'HH:mm' }"
          format="YYYY-MM-DD HH:mm"
          style="width: 340px"
        />
        <a-select v-model:value="query.status" placeholder="状态" allow-clear style="width: 130px" :options="statusOptions" />
        <a-select v-model:value="query.orderType" placeholder="订单类型" allow-clear style="width: 130px" :options="orderTypeOptions" />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </a-space>
      <a-button type="primary" @click="handleExport">
        <template #icon><DownloadOutlined /></template>
        导出 Excel
      </a-button>
    </div>

    <!-- 订单列表 -->
    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">
            {{ record.statusLabel || ORDER_STATUS_MAP[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-if="column.key === 'orderType'">
          <a-tag>{{ record.orderTypeLabel || ORDER_TYPE_MAP[record.orderType] || record.orderType }}</a-tag>
        </template>
        <template v-if="column.key === 'amount'">
          <span>¥ {{ formatYuan(record.payableAmount) }}</span>
          <template v-if="record.paidAmount && record.paidAmount > 0">
            <br /><span class="text-paid">实付 ¥ {{ formatYuan(record.paidAmount) }}</span>
          </template>
        </template>
        <template v-if="column.key === 'time'">
          <span>{{ formatDateTime(record.createdAt) }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="openDetail(record as OrderAdminVO)">查看详情</a>
            <a-divider type="vertical" />
            <a-popconfirm
              v-if="canClose(record.status)"
              title="确定手动关闭此订单？关闭后订单状态将变为已取消。"
              @confirm="handleClose(record as OrderAdminVO)"
            >
              <a class="text-danger">关闭</a>
            </a-popconfirm>
            <span v-else class="text-disabled">—</span>
          </a-space>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="暂无订单数据" />
      </template>
    </a-table>

    <!-- 详情弹窗 -->
    <a-modal v-model:open="detailOpen" title="订单详情" width="600px" :footer="null">
      <a-descriptions :column="2" bordered v-if="detailRecord">
        <a-descriptions-item label="订单号" :span="2">{{ detailRecord.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="订单类型">{{ detailRecord.orderTypeLabel || ORDER_TYPE_MAP[detailRecord.orderType] }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="statusColor(detailRecord.status)">
            {{ detailRecord.statusLabel || ORDER_STATUS_MAP[detailRecord.status] }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="车牌号">{{ detailRecord.plateNumber }}</a-descriptions-item>
        <a-descriptions-item label="车场">{{ detailRecord.parkingLotName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="应收金额">¥ {{ formatYuan(detailRecord.payableAmount) }}</a-descriptions-item>
        <a-descriptions-item label="实付金额">¥ {{ formatYuan(detailRecord.paidAmount) }}</a-descriptions-item>
        <a-descriptions-item label="优惠金额">¥ {{ formatYuan(detailRecord.discountAmount) }}</a-descriptions-item>
        <a-descriptions-item label="积分抵扣">¥ {{ formatYuan(detailRecord.pointsDiscount) }}</a-descriptions-item>
        <a-descriptions-item label="支付方式">{{ detailRecord.payChannelLabel || (detailRecord.payChannel ? PAY_CHANNEL_MAP[detailRecord.payChannel] : undefined) || '—' }}</a-descriptions-item>
        <a-descriptions-item label="支付时间">{{ formatDateTime(detailRecord.payTime) || '—' }}</a-descriptions-item>
        <a-descriptions-item label="入场时间">{{ formatDateTime(detailRecord.entryTime) || '—' }}</a-descriptions-item>
        <a-descriptions-item label="出场时间">{{ formatDateTime(detailRecord.exitTime) || '—' }}</a-descriptions-item>
        <a-descriptions-item label="停车时长">{{ formatDuration(detailRecord.parkingDurationMinutes) || '—' }}</a-descriptions-item>
        <a-descriptions-item label="操作人">{{ detailRecord.operatorName || '—' }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ formatDateTime(detailRecord.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ formatDateTime(detailRecord.updatedAt) || '—' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'

import {
  getOrderPage,
  closeOrder,
  exportOrders,
  ORDER_STATUS_MAP,
  ORDER_TYPE_MAP,
  PAY_CHANNEL_MAP,
  type OrderAdminVO,
} from '@/api/order'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

// ==================== 选项 ====================
const statusOptions = Object.entries(ORDER_STATUS_MAP).map(([value, label]) => ({ value, label }))
const orderTypeOptions = Object.entries(ORDER_TYPE_MAP).map(([value, label]) => ({ value, label }))

function statusColor(status: string) {
  switch (status) {
    case 'PENDING_PAY': return 'orange'
    case 'PAYING': return 'processing'
    case 'PAID': return 'green'
    case 'COMPLETED': return 'blue'
    case 'CANCELLED': return 'default'
    case 'PAY_FAILED': return 'red'
    case 'REFUNDING': return 'warning'
    case 'REFUNDED': return 'default'
    default: return 'default'
  }
}

function canClose(status: string) {
  return status === 'PENDING_PAY' || status === 'PAYING'
}

// ==================== 表格列 ====================
const columns = [
  { title: '订单号', dataIndex: 'orderNo', key: 'orderNo', width: 180 },
  { title: '车牌', dataIndex: 'plateNumber', key: 'plateNumber', width: 120 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'parkingLotName', width: 180 },
  { title: '金额', key: 'amount', width: 140 },
  { title: '状态', key: 'status', width: 100 },
  { title: '类型', key: 'orderType', width: 110 },
  { title: '时间', key: 'time', width: 160 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

// ==================== 查询 ====================
const query = reactive({
  orderNo: '',
  plateNumber: '',
  parkingLotId: undefined as number | undefined,
  dateRange: undefined as [Dayjs, Dayjs] | undefined,
  status: undefined as string | undefined,
  orderType: undefined as string | undefined,
})

const loading = ref(false)
const dataSource = ref<OrderAdminVO[]>([])
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
    const params: any = {
      page: pagination.current,
      size: pagination.pageSize,
    }
    if (query.orderNo?.trim()) params.orderNo = query.orderNo.trim()
    if (query.plateNumber?.trim()) params.plateNumber = query.plateNumber.trim()
    if (query.parkingLotId) params.parkingLotId = query.parkingLotId
    if (query.status) params.status = query.status
    if (query.orderType) params.orderType = query.orderType
    if (query.dateRange && query.dateRange.length === 2) {
      params.startTime = query.dateRange[0]?.format('YYYY-MM-DDTHH:mm:ss')
      params.endTime = query.dateRange[1]?.format('YYYY-MM-DDTHH:mm:ss')
    }
    const res = await getOrderPage(params)
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
  query.orderNo = ''
  query.plateNumber = ''
  query.parkingLotId = undefined
  query.dateRange = undefined
  query.status = undefined
  query.orderType = undefined
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
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

// ==================== 详情 ====================
const detailOpen = ref(false)
const detailRecord = ref<OrderAdminVO | null>(null)

function openDetail(record: OrderAdminVO) {
  detailRecord.value = record
  detailOpen.value = true
}

// ==================== 关闭订单 ====================
async function handleClose(record: OrderAdminVO) {
  try {
    await closeOrder(record.id)
    message.success('订单已关闭')
    fetchData()
  } catch {
    // 错误由拦截器处理
  }
}

// ==================== 导出 ====================
async function handleExport() {
  try {
    const params: any = {}
    if (query.orderNo?.trim()) params.orderNo = query.orderNo.trim()
    if (query.plateNumber?.trim()) params.plateNumber = query.plateNumber.trim()
    if (query.parkingLotId) params.parkingLotId = query.parkingLotId
    if (query.status) params.status = query.status
    if (query.orderType) params.orderType = query.orderType
    if (query.dateRange && query.dateRange.length === 2) {
      params.startTime = query.dateRange[0]?.format('YYYY-MM-DDTHH:mm:ss')
      params.endTime = query.dateRange[1]?.format('YYYY-MM-DDTHH:mm:ss')
    }

    const blob = await exportOrders(params)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const fileName = `订单导出_${dayjs().format('YYYYMMDD_HHmmss')}.csv`
    link.setAttribute('download', fileName)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (err: any) {
    // 如果后端返回 JSON 错误（超过限制），尝试解析
    if (err?.response?.data instanceof Blob) {
      const text = await err.response.data.text()
      try {
        const json = JSON.parse(text)
        if (json.message) {
          message.warning(json.message)
          return
        }
      } catch {
        // 不是 JSON，忽略
      }
    }
    message.error('导出失败')
  }
}

// ==================== 工具函数 ====================
function formatYuan(cents?: number) {
  if (cents == null) return '0.00'
  return (cents / 100).toFixed(2)
}

function formatDateTime(dt?: string) {
  if (!dt) return ''
  return dayjs(dt).format('YYYY-MM-DD HH:mm:ss')
}

function formatDuration(minutes?: number) {
  if (minutes == null || minutes <= 0) return ''
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  if (hours > 0) {
    return `${hours}小时${mins}分钟`
  }
  return `${mins}分钟`
}

// ==================== 初始化 ====================
onMounted(() => {
  fetchData()
  loadLotOptions()
})
</script>

<style lang="scss" scoped>
.order-page {
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

.text-paid {
  color: #52c41a;
  font-size: 12px;
}

.text-danger {
  color: #dc2626;
}

.text-disabled {
  color: rgba(0, 0, 0, 0.25);
}
</style>
