<template>
  <div class="analytics-page">
    <!-- 筛选栏 -->
    <a-card size="small" style="margin-bottom: 16px">
      <a-row :gutter="16" align="middle">
        <a-col :span="6">
          <a-select
            v-model:value="query.lotId"
            :options="lotOptions"
            placeholder="选择停车场"
            style="width: 100%"
            @change="fetchData"
          />
        </a-col>
        <a-col :span="10">
          <a-radio-group v-model:value="query.period" button-style="solid" @change="onPeriodChange">
            <a-radio-button value="today">今日</a-radio-button>
            <a-radio-button value="month">本月</a-radio-button>
            <a-radio-button value="year">今年</a-radio-button>
            <a-radio-button value="custom">自定义</a-radio-button>
          </a-radio-group>
        </a-col>
        <a-col :span="8" v-if="query.period === 'custom'">
          <a-range-picker v-model:value="customRange" @change="onCustomRangeChange" style="width: 100%" />
        </a-col>
      </a-row>
    </a-card>

    <!-- KPI 卡片 -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="4">
        <a-statistic title="入场总数" :value="data?.entryCount ?? '-'" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="出场总数" :value="data?.exitCount ?? '-'" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="当前在场" :value="data?.currentInCount ?? '-'" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="实收总额（元）" :value="formatAmount(data?.revenue?.totalPaid)" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="固定车营收（元）" :value="formatAmount(data?.revenue?.fixedCarRevenue)" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="其他营收（元）" :value="formatAmount(data?.revenue?.otherRevenue)" />
      </a-col>
    </a-row>

    <!-- 图表区 -->
    <a-row :gutter="16">
      <a-col :span="14">
        <a-card title="车流量趋势" size="small">
          <div ref="trendChartRef" style="height: 320px" />
        </a-card>
      </a-col>
      <a-col :span="10">
        <a-card title="入场方式分布" size="small">
          <div ref="pieChartRef" style="height: 320px" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import * as echarts from 'echarts'
import { getAnalyticsOverview, getParkingLots, type AnalyticsOverviewVO, type ParkingLotVO } from '@/api/parking-manage'

const data = ref<AnalyticsOverviewVO | null>(null)

/** 金额格式化：千分位 + 2 位小数 */
function formatAmount(v?: number | null): string {
  if (v === null || v === undefined) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 车场下拉
const lotOptions = ref<{ value: number | string; label: string }[]>([])
async function loadLotOptions() {
  const res = await getParkingLots({ page: 1, size: 200 })
  const options: { value: number | string; label: string }[] = [{ value: '', label: '全部车场' }]
  res.records.forEach((lot: ParkingLotVO) => options.push({ value: lot.id, label: lot.name }))
  lotOptions.value = options
}

const query = reactive({
  lotId: '' as number | string,
  period: 'today' as string,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
})

const customRange = ref<[Dayjs, Dayjs] | null>(null)

function onPeriodChange() {
  if (query.period !== 'custom') {
    customRange.value = null
    query.startDate = undefined
    query.endDate = undefined
    fetchData()
  }
}

function onCustomRangeChange() {
  if (customRange.value) {
    query.startDate = customRange.value[0].format('YYYY-MM-DD')
    query.endDate = customRange.value[1].format('YYYY-MM-DD')
    fetchData()
  }
}

// ECharts refs
const trendChartRef = ref<HTMLElement | null>(null)
const pieChartRef = ref<HTMLElement | null>(null)
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

async function fetchData() {
  try {
    data.value = await getAnalyticsOverview({
      lotId: (query.lotId as number) || undefined,
      period: query.period as any,
      startDate: query.startDate,
      endDate: query.endDate,
    })
    await nextTick()
    renderTrendChart()
    renderPieChart()
  } catch {
    message.error('加载数据失败')
  }
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const d = data.value
  if (!d) return
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['入场', '出场'] },
    xAxis: { type: 'category', data: d.trendData.map((t: any) => t.time) },
    yAxis: { type: 'value' },
    series: [
      { name: '入场', type: 'line', data: d.trendData.map((t: any) => t.entry), smooth: true, color: '#1890ff' },
      { name: '出场', type: 'line', data: d.trendData.map((t: any) => t.exit), smooth: true, color: '#52c41a' },
    ],
  }, true)
}

function renderPieChart() {
  if (!pieChartRef.value) return
  if (!pieChart) pieChart = echarts.init(pieChartRef.value)
  const d = data.value
  if (!d) return
  const stats = d.entryTriggerStats || {}
  const pieData = [
    { name: '白名单自动', value: stats.whitelist_auto || 0 },
    { name: '人工放行', value: stats.manual_open || 0 },
    { name: '常开时段', value: stats.always_open_period || 0 },
    { name: '手动补录', value: stats.manual_entry || 0 },
  ].filter(item => item.value > 0)
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: pieData,
      label: { formatter: '{b}\n{d}%' },
      color: ['#1890ff', '#faad14', '#52c41a', '#bfbfbf'],
    }],
  }, true)
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  await loadLotOptions()
  await fetchData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})
</script>

<style lang="scss" scoped>
.analytics-page { }
</style>
