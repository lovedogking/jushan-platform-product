<template>
  <div class="report-page">
    <div class="page-header">
      <span class="page-title">车流量报表</span>
    </div>

    <!-- 筛选栏 -->
    <div class="query-bar">
      <a-space wrap>
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

    <!-- 汇总卡片 -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">总入场</div>
          <div class="stat-value">{{ data.totalEntry }}</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">总出场</div>
          <div class="stat-value">{{ data.totalExit }}</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">峰值小时</div>
          <div class="stat-value">{{ data.peakHour }}:00</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">峰值流量</div>
          <div class="stat-value">{{ data.peakCount }}</div>
        </a-card>
      </a-col>
    </a-row>

    <a-spin :spinning="loading">
      <!-- 按天趋势柱状图 -->
      <a-card title="每日车流量趋势" :bordered="false" style="margin-bottom: 16px">
        <div class="chart-container">
          <div class="bar-chart">
            <div
              v-for="item in data.dailyStats"
              :key="item.date"
              class="bar-item"
              :title="`${item.date} 入场${item.entryCount} 出场${item.exitCount}`"
            >
              <div class="bar-wrapper">
                <div
                  class="bar bar-entry"
                  :style="{ height: barPct(item.entryCount) + '%' }"
                />
                <div
                  class="bar bar-exit"
                  :style="{ height: barPct(item.exitCount) + '%' }"
                />
              </div>
              <div class="bar-label">{{ item.date.slice(5) }}</div>
            </div>
          </div>
        </div>
        <div class="chart-legend">
          <span class="legend-item"><span class="dot dot-entry" /> 入场</span>
          <span class="legend-item"><span class="dot dot-exit" /> 出场</span>
        </div>
      </a-card>

      <!-- 按小时分布 -->
      <a-card title="时段分布（跨天合并）" :bordered="false">
        <div class="chart-container">
          <div class="hour-chart">
            <div
              v-for="item in data.hourlyStats"
              :key="item.hour"
              class="hour-row"
            >
              <span class="hour-label">{{ String(item.hour).padStart(2, '0') }}:00</span>
              <div class="hour-bar-track">
                <div
                  class="hour-bar"
                  :style="{ width: hourBarPct(item.total) + '%' }"
                >
                  <span class="hour-count">{{ item.total }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </a-card>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import {
  getTrafficReport,
  exportTrafficReport,
  type TrafficReportVO,
} from '@/api/report'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const loading = ref(false)
const data = reactive<TrafficReportVO>({
  totalEntry: 0,
  totalExit: 0,
  peakHour: 0,
  peakCount: 0,
  dailyStats: [],
  hourlyStats: [],
})

const query = reactive({
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

function filterLotOption(input: string, option: { value: number; label: string } | undefined) {
  return option?.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

function getDefaultDateRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(30, 'day').startOf('day'), dayjs().endOf('day')]
}

const maxDaily = computed(() => {
  let max = 1
  for (const d of data.dailyStats) {
    max = Math.max(max, d.entryCount, d.exitCount)
  }
  return max
})

const maxHourly = computed(() => {
  let max = 1
  for (const h of data.hourlyStats) {
    max = Math.max(max, h.total)
  }
  return max
})

function barPct(val: number): number {
  return (val / maxDaily.value) * 100
}

function hourBarPct(val: number): number {
  return Math.max(2, (val / maxHourly.value) * 100)
}

async function loadData() {
  loading.value = true
  try {
    const range = query.dateRange || getDefaultDateRange()
    const res = await getTrafficReport({
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      lotId: query.lotId,
    })
    Object.assign(data, res)
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
    const blob = await exportTrafficReport({
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      lotId: query.lotId,
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `车流量报表_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch {
    message.error('导出失败')
  }
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

.chart-container {
  min-height: 140px;
}
.bar-chart {
  display: flex;
  align-items: flex-end;
  height: 180px;
  gap: 2px;
  padding: 0 4px;
  overflow-x: auto;
}
.bar-item {
  flex: none;
  width: 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.bar-wrapper {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 1px;
}
.bar {
  width: 100%;
  border-radius: 2px 2px 0 0;
  min-height: 2px;
}
.bar-entry { background: #52c41a; }
.bar-exit { background: #1677ff; }
.bar-label {
  font-size: 9px;
  color: #999;
  margin-top: 4px;
  transform: rotate(-45deg);
  transform-origin: top left;
  white-space: nowrap;
}
.chart-legend {
  text-align: center;
  margin-top: 8px;
}
.legend-item {
  font-size: 12px;
  color: #999;
  margin: 0 8px;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}
.dot-entry { background: #52c41a; }
.dot-exit { background: #1677ff; }

.hour-chart {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hour-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hour-label {
  width: 42px;
  font-size: 12px;
  color: #666;
  text-align: right;
}
.hour-bar-track {
  flex: 1;
  background: #f0f0f0;
  border-radius: 4px;
  height: 24px;
  overflow: hidden;
}
.hour-bar {
  height: 100%;
  background: linear-gradient(90deg, #1677ff, #4096ff);
  border-radius: 4px;
  display: flex;
  align-items: center;
  min-width: 30px;
  transition: width 0.3s;
}
.hour-count {
  font-size: 11px;
  color: #fff;
  padding-left: 6px;
  white-space: nowrap;
}
</style>
