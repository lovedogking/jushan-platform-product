<template>
  <div class="dashboard-page">
    <div class="page-header">
      <span class="page-title">平台驾驶舱</span>
      <a-space>
        <a-tag v-if="lastUpdated" color="blue">最后更新：{{ lastUpdated }}</a-tag>
        <a-button :loading="loading" @click="fetchData">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </a-space>
    </div>

    <!-- 加载骨架 -->
    <div v-if="loading && !data">
      <a-row :gutter="16">
        <a-col :span="4" v-for="i in 6" :key="i">
          <a-card :bordered="false"><a-skeleton active :paragraph="{ rows: 2 }" /></a-card>
        </a-col>
      </a-row>
    </div>

    <!-- 错误提示 -->
    <a-result v-else-if="error" status="error" title="加载失败" :sub-title="error" />

    <!-- 仪表盘内容 -->
    <template v-else-if="data">
      <!-- 核心指标卡片 -->
      <a-row :gutter="16">
        <a-col :span="4">
          <a-card class="stat-card revenue-card" :bordered="false">
            <div class="stat-label">今日收入</div>
            <div class="stat-value">¥ {{ formatYuan(data.todayRevenue) }}</div>
            <div class="stat-sub">实时收入汇总</div>
          </a-card>
        </a-col>
        <a-col :span="4">
          <a-card class="stat-card traffic-card" :bordered="false">
            <div class="stat-label">今日车流量</div>
            <div class="stat-value">{{ data.todayTraffic.total }}</div>
            <div class="stat-sub">入场 {{ data.todayTraffic.entryCount }} / 出场 {{ data.todayTraffic.exitCount }}</div>
          </a-card>
        </a-col>
        <a-col :span="4">
          <a-card class="stat-card parked-card" :bordered="false">
            <div class="stat-label">在场车辆</div>
            <div class="stat-value">{{ data.currentParkedCount }}</div>
            <div class="stat-sub">当前在场总数</div>
          </a-card>
        </a-col>
        <a-col :span="4">
          <a-card class="stat-card space-card" :bordered="false">
            <div class="stat-label">余位数</div>
            <div class="stat-value">{{ data.remainingSpaces }}</div>
            <div class="stat-sub">剩余车位数</div>
          </a-card>
        </a-col>
        <a-col :span="4">
          <a-card class="stat-card device-card" :bordered="false">
            <div class="stat-label">设备状态</div>
            <div class="stat-value">{{ data.deviceStatus.online }} / {{ data.deviceStatus.total }}</div>
            <div class="stat-sub">
              <a-badge status="success" />在线 {{ data.deviceStatus.online }}
              <a-badge status="error" />离线 {{ data.deviceStatus.offline }}
            </div>
          </a-card>
        </a-col>
        <a-col :span="4">
          <a-card class="stat-card alert-card" :bordered="false">
            <div class="stat-label">未处理异常</div>
            <div class="stat-value" :class="{ 'text-danger': data.unhandledAlertCount > 0 }">
              {{ data.unhandledAlertCount }}
            </div>
            <div class="stat-sub">需关注处理</div>
          </a-card>
        </a-col>
      </a-row>

      <!-- 趋势图表 -->
      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :span="12">
          <a-card title="今日收入趋势" :bordered="false">
            <div class="chart-container">
              <div class="bar-chart">
                <div
                  v-for="item in data.hourlyRevenue"
                  :key="item.hour"
                  class="bar-item"
                  :title="`${item.hour}:00 - ¥${formatYuan(item.amount)}`"
                >
                  <div class="bar-wrapper">
                    <div
                      class="bar"
                      :style="{ height: barHeight(item.amount, maxRevenue) + '%' }"
                    />
                  </div>
                  <div class="bar-label">{{ item.hour }}</div>
                </div>
              </div>
              <div v-if="maxRevenue === 0" class="chart-empty">今日暂无收入数据</div>
            </div>
          </a-card>
        </a-col>
        <a-col :span="12">
          <a-card title="今日车流量趋势" :bordered="false">
            <div class="chart-container">
              <div class="bar-chart traffic-chart">
                <div
                  v-for="item in data.hourlyTraffic"
                  :key="item.hour"
                  class="bar-item"
                  :title="`${item.hour}:00 入场${item.entryCount} 出场${item.exitCount}`"
                >
                  <div class="bar-wrapper">
                    <div
                      class="bar bar-entry"
                      :style="{ height: trafficBarHeight(item.entryCount, maxTraffic) + '%' }"
                    />
                    <div
                      class="bar bar-exit"
                      :style="{ height: trafficBarHeight(item.exitCount, maxTraffic) + '%' }"
                    />
                  </div>
                  <div class="bar-label">{{ item.hour }}</div>
                </div>
              </div>
              <div v-if="maxTraffic === 0" class="chart-empty">今日暂无车流量数据</div>
              <div class="chart-legend">
                <span class="legend-item"><span class="dot dot-entry" /> 入场</span>
                <span class="legend-item"><span class="dot dot-exit" /> 出场</span>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { getDashboard, type DashboardVO } from '@/api/dashboard'

const loading = ref(false)
const data = ref<DashboardVO | null>(null)
const error = ref('')
const lastUpdated = ref('')

async function fetchData() {
  loading.value = true
  error.value = ''
  try {
    data.value = await getDashboard()
    lastUpdated.value = dayjs().format('HH:mm:ss')
  } catch (e: any) {
    error.value = e?.message || '获取仪表盘数据失败'
  } finally {
    loading.value = false
  }
}

// 图表工具
const maxRevenue = computed(() => {
  if (!data.value?.hourlyRevenue?.length) return 0
  return Math.max(...data.value.hourlyRevenue.map((i) => i.amount), 1)
})

const maxTraffic = computed(() => {
  if (!data.value?.hourlyTraffic?.length) return 0
  return Math.max(...data.value.hourlyTraffic.flatMap((i) => [i.entryCount, i.exitCount]), 1)
})

function barHeight(amount: number, max: number) {
  return (amount / max) * 100
}

function trafficBarHeight(count: number, max: number) {
  return (count / max) * 100
}

function formatYuan(cents?: number) {
  if (cents == null) return '0.00'
  return (cents / 100).toFixed(2)
}

onMounted(fetchData)
</script>

<style lang="scss" scoped>
.dashboard-page {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: $text-color;
}

// ==================== 统计卡片 ====================

.stat-card {
  border-radius: $border-radius-lg;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 13px;
  color: $text-color-secondary;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 6px;
  color: $text-color;
}

.stat-sub {
  font-size: 12px;
  color: $text-color-secondary;
}

// ==================== 柱状图 ====================

.chart-container {
  position: relative;
  min-height: 200px;
}

.bar-chart {
  display: flex;
  align-items: flex-end;
  height: 200px;
  gap: 2px;
  padding: 0 4px;
}

.bar-item {
  flex: 1;
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
  gap: 2px;
}

.bar {
  width: 60%;
  margin: 0 auto;
  background: linear-gradient(180deg, #1677ff 0%, #4096ff 100%);
  border-radius: 2px 2px 0 0;
  min-height: 2px;
  transition: height 0.3s ease;
}

.bar-entry {
  background: linear-gradient(180deg, #52c41a 0%, #73d13d 100%);
}

.bar-exit {
  background: linear-gradient(180deg, #1677ff 0%, #4096ff 100%);
}

.bar-label {
  font-size: 10px;
  color: $text-color-secondary;
  margin-top: 4px;
  text-align: center;
}

.chart-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: $text-color-secondary;
  font-size: 14px;
}

.chart-legend {
  text-align: center;
  margin-top: 8px;
}

.legend-item {
  font-size: 12px;
  color: $text-color-secondary;
  margin: 0 8px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.dot-entry {
  background: #52c41a;
}

.dot-exit {
  background: #1677ff;
}

// ==================== 响应式 ====================

@media (max-width: 1200px) {
  .stat-value {
    font-size: 22px;
  }
}

.text-danger {
  color: #ff4d4f;
}
</style>
