<template>
  <div class="order-page">
    <!-- 筛选栏 -->
    <div class="query-bar">
      <a-space wrap class="filter-group">
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
      </a-space>
      <a-space class="action-group">
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
        <a-button type="primary" @click="handleExport">
          <template #icon><DownloadOutlined /></template>
          导出 Excel
        </a-button>
      </a-space>
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
            <template v-if="canClose(record.status)">
              <a-divider type="vertical" />
              <a-popconfirm
                title="确定手动关闭此订单？关闭后订单状态将变为已取消。"
                @confirm="handleClose(record as OrderAdminVO)"
              >
                <a class="text-danger">关闭</a>
              </a-popconfirm>
            </template>
            <template v-if="canRefund(record.status)">
              <a-divider type="vertical" />
              <a class="text-warning" @click="openRefund(record as OrderAdminVO)">退款</a>
            </template>
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
        <a-descriptions-item label="重算关联" :span="2">
          <template v-if="detailRecord?.recalcSourceOrderNo && detailRecord?.recalcSourceOrderId">
            <a @click="openDetailById(detailRecord.recalcSourceOrderId)">
              查看原订单：{{ detailRecord.recalcSourceOrderNo }}
            </a>
          </template>
          <template v-else>—</template>
        </a-descriptions-item>
      </a-descriptions>

      <div v-if="detailRecord && detailRecord.refundReason" class="detail-section">
        <div class="section-title">退款信息</div>
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="退款原因">{{ detailRecord.refundReason }}</a-descriptions-item>
          <a-descriptions-item label="退款时间">{{ formatDateTime(detailRecord.refundTime) || '—' }}</a-descriptions-item>
          <a-descriptions-item label="退款操作人">{{ detailRecord.refundOperatorName || '—' }}</a-descriptions-item>
        </a-descriptions>
      </div>

      <div class="detail-section">
        <div class="section-title">状态流转日志</div>
        <a-spin :spinning="logsLoading">
          <a-timeline v-if="statusLogs.length">
            <a-timeline-item v-for="(item, idx) in statusLogs" :key="idx" :color="statusColor(item.toStatus) === 'default' ? 'gray' : undefined">
              <div>
                <a-tag :color="statusColor(item.toStatus)">{{ item.toStatusLabel }}</a-tag>
                <span v-if="item.fromStatusLabel" class="log-from">（由 {{ item.fromStatusLabel }}）</span>
              </div>
              <div class="log-meta">
                {{ formatDateTime(item.createdAt) }} · {{ item.triggerSourceLabel }}
                <span v-if="item.operatorName"> · {{ item.operatorName }}</span>
              </div>
              <div v-if="item.remark" class="log-remark">{{ item.remark }}</div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无流转记录" />
        </a-spin>
      </div>
    </a-modal>

    <!-- 退款弹窗 -->
    <a-modal
      v-model:open="refundOpen"
      title="订单退款"
      :confirm-loading="refundSubmitting"
      @ok="submitRefund"
    >
      <a-alert
        type="warning"
        show-icon
        message="仅已支付订单可退款；本期为模拟退款（无真实资金流动）。"
        style="margin-bottom: 12px"
      />
      <a-form layout="vertical">
        <a-form-item label="退款原因" required>
          <a-textarea
            v-model:value="refundReason"
            :rows="3"
            :maxlength="200"
            show-count
            placeholder="请填写退款原因（必填）"
          />
        </a-form-item>
      </a-form>
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
  getOrderDetail,
  closeOrder,
  refundOrder,
  getOrderStatusLogs,
  exportOrders,
  ORDER_STATUS_MAP,
  ORDER_TYPE_MAP,
  PAY_CHANNEL_MAP,
  type OrderAdminVO,
  type OrderStatusLogVO,
} from '@/api/order'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

// ==================== 选项 ====================
const statusOptions = Object.entries(ORDER_STATUS_MAP).map(([value, label]) => ({ value, label }))
const orderTypeOptions = Object.entries(ORDER_TYPE_MAP).map(([value, label]) => ({ value, label }))

function statusColor(status: string) {
  switch (status) {
    case 'PRE_ORDER': return 'purple'
    case 'PENDING_PAY': return 'orange'
    case 'PAYING': return 'processing'
    case 'PAID': return 'green'
    case 'COMPLETED': return 'blue'
    case 'CANCELLED': return 'default'
    case 'PAY_FAILED': return 'red'
    case 'ARREARS': return 'volcano'
    case 'REFUNDING': return 'warning'
    case 'REFUNDED': return 'default'
    default: return 'default'
  }
}

function canClose(status: string) {
  return status === 'PENDING_PAY' || status === 'PAYING'
}

function canRefund(status: string) {
  return status === 'PAID'
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
const statusLogs = ref<OrderStatusLogVO[]>([])
const logsLoading = ref(false)

async function openDetail(record: OrderAdminVO) {
  detailRecord.value = record
  detailOpen.value = true
  statusLogs.value = []
  logsLoading.value = true
  try {
    statusLogs.value = await getOrderStatusLogs(record.id)
  } catch {
    // 错误由拦截器处理
  } finally {
    logsLoading.value = false
  }
}

/** 通过订单 ID 打开详情弹窗（用于重算关联跳转） */
async function openDetailById(id: number) {
  try {
    const detail = await getOrderDetail(id)
    detailRecord.value = detail
    detailOpen.value = true
    statusLogs.value = []
    logsLoading.value = true
    try {
      statusLogs.value = await getOrderStatusLogs(id)
    } catch {
      // 错误由拦截器处理
    } finally {
      logsLoading.value = false
    }
  } catch {
    // 错误由拦截器处理
  }
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

// ==================== 退款 ====================
const refundOpen = ref(false)
const refundReason = ref('')
const refundTarget = ref<OrderAdminVO | null>(null)
const refundSubmitting = ref(false)

function openRefund(record: OrderAdminVO) {
  refundTarget.value = record
  refundReason.value = ''
  refundOpen.value = true
}

async function submitRefund() {
  if (!refundReason.value.trim()) {
    message.warning('请填写退款原因')
    return
  }
  if (!refundTarget.value) return
  refundSubmitting.value = true
  try {
    await refundOrder(refundTarget.value.id, refundReason.value.trim())
    message.success('退款成功')
    refundOpen.value = false
    fetchData()
  } catch {
    // 错误由拦截器处理
  } finally {
    refundSubmitting.value = false
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
    const fileName = `订单导出_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`
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
  align-items: flex-start;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
  gap: 12px;
}

.action-group {
  flex-shrink: 0;
  margin-left: auto;
}

.text-paid {
  color: #52c41a;
  font-size: 12px;
}

.text-danger {
  color: #dc2626;
}

.text-warning {
  color: #d46b08;
}

.text-disabled {
  color: rgba(0, 0, 0, 0.25);
}

.detail-section {
  margin-top: $spacing-lg;
}

.section-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.log-from {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  margin-left: 4px;
}

.log-meta {
  color: rgba(0, 0, 0, 0.45);
  font-size: 12px;
  margin-top: 2px;
}

.log-remark {
  color: rgba(0, 0, 0, 0.65);
  font-size: 12px;
  margin-top: 2px;
}
</style>
