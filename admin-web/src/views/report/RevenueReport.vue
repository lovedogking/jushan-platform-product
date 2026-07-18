<template>
  <div class="report-page">
    <div class="page-header">
      <span class="page-title">收入报表</span>
    </div>

    <!-- Filter bar -->
    <div class="query-bar">
      <a-space wrap>
        <a-radio-group v-model:value="query.periodType" button-style="solid" @change="handleQuery">
          <a-radio-button value="DAILY">按日</a-radio-button>
          <a-radio-button value="MONTHLY">按月</a-radio-button>
          <a-radio-button value="YEARLY">按年</a-radio-button>
        </a-radio-group>
        <a-select
          v-model:value="query.lotId"
          placeholder="全部车场"
          allow-clear
          show-search
          :filter-option="filterLotOption"
          style="width: 200px"
          :options="lotOptions"
          @change="handleQuery"
        />
        <a-range-picker
          v-model:value="query.dateRange"
          format="YYYY-MM-DD"
          style="width: 260px"
          @change="handleQuery"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button type="primary" @click="handleExport">
          <template #icon><DownloadOutlined /></template>
          导出 Excel
        </a-button>
      </a-space>
    </div>

    <!-- Summary cards -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="8">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">总收入</div>
          <div class="stat-value">¥ {{ formatYuan(data.totalRevenue) }}</div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">订单总数</div>
          <div class="stat-value">{{ data.orderCount }}</div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">平均订单金额</div>
          <div class="stat-value">¥ {{ formatYuan(data.avgOrderAmount) }}</div>
        </a-card>
      </a-col>
    </a-row>

    <!-- Detail table -->
    <a-spin :spinning="loading">
      <a-table
        :columns="columns"
        :data-source="data.periods"
        row-key="period"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'totalRevenue'">
            ¥ {{ formatYuan(record.totalRevenue) }}
          </template>
          <template v-if="column.key === 'avgAmount'">
            ¥ {{ formatYuan(record.orderCount > 0 ? Math.round(record.totalRevenue / record.orderCount) : 0) }}
          </template>
        </template>
      </a-table>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import {
  getRevenueReport,
  exportRevenueReport,
  type RevenueReportVO,
  type ReportPeriod,
} from '@/api/report'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const columns = [
  { title: '期间', dataIndex: 'period', key: 'period', width: 150 },
  { title: '收入', key: 'totalRevenue', width: 150 },
  { title: '订单数', dataIndex: 'orderCount', key: 'orderCount', width: 120 },
  { title: '平均订单金额', key: 'avgAmount', width: 150 },
]

const loading = ref(false)
const data = reactive<RevenueReportVO>({
  totalRevenue: 0,
  orderCount: 0,
  avgOrderAmount: 0,
  periods: [],
})

const query = reactive({
  periodType: 'DAILY' as ReportPeriod,
  lotId: undefined as number | undefined,
  dateRange: undefined as [Dayjs, Dayjs] | undefined,
})

const lotOptions = ref<{ value: number; label: string }[]>([])

async function loadLotOptions() {
  try {
    const res = await getParkingLots({ page: 1, size: 999 })
    lotOptions.value = (res.records || []).map((lot: ParkingLotVO) => ({
      value: lot.id,
      label: lot.name,
    }))
  } catch { /* ignore */ }
}

function filterLotOption(input: string, option: any) {
  return option?.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

function getDefaultDateRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(30, 'day').startOf('day'), dayjs().endOf('day')]
}

async function loadData() {
  loading.value = true
  try {
    const range = query.dateRange || getDefaultDateRange()
    const res = await getRevenueReport({
      periodType: query.periodType,
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      lotId: query.lotId,
    })
    data.totalRevenue = res.totalRevenue
    data.orderCount = res.orderCount
    data.avgOrderAmount = res.avgOrderAmount
    data.periods = res.periods || []
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function handleQuery() {
  loadData()
}

async function handleExport() {
  try {
    const range = query.dateRange || getDefaultDateRange()
    const blob = await exportRevenueReport({
      periodType: query.periodType,
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      lotId: query.lotId,
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `收入报表_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch {
    message.error('导出失败')
  }
}

function formatYuan(cents?: number): string {
  if (cents == null) return '0.00'
  return (cents / 100).toFixed(2)
}

onMounted(() => {
  query.dateRange = getDefaultDateRange()
  loadLotOptions()
  loadData()
})
</script>

<style lang="scss" scoped>
.report-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
}
.query-bar {
  margin-bottom: 16px;
}
.summary-card {
  border-radius: 8px;
  margin-bottom: 8px;
}
.stat-label {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #333;
}
</style>
