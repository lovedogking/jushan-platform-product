<template>
  <div class="page-container">
    <!-- 头部 -->
    <div class="header">
      <h1 class="title">飓山智慧停车</h1>
      <p class="subtitle">输入车牌号，查询停车费用</p>
    </div>

    <!-- 车牌输入区 -->
    <div class="plate-input-area" @click="showKeyboard = true">
      <template v-if="currentPlate">
        <div class="plate-preview">
          <span
            v-for="(c, i) in currentPlate.split('')"
            :key="i"
            class="plate-preview-char"
            :class="{ 'is-province': i === 0 }"
          >{{ c }}</span>
        </div>
      </template>
      <template v-else>
        <div class="plate-placeholder">
          <van-icon name="photograph" size="22" />
          <span>点击输入车牌号</span>
        </div>
      </template>
    </div>

    <!-- 加载中 -->
    <van-loading v-if="loading" class="loading-center" />

    <!-- 错误提示 -->
    <van-empty
      v-else-if="errorMsg"
      :description="errorMsg"
      image="error"
    />

    <!-- 空结果 -->
    <van-empty
      v-else-if="searched && feeList.length === 0"
      description="未找到该车牌的在场记录"
      image="search"
    />

    <!-- 单条记录：大卡片 -->
    <div v-if="feeList.length === 1" class="fee-card">
      <div class="card-header">
        <span class="park-name">{{ feeList[0].parkName }}</span>
        <span class="plate-number">{{ feeList[0].plate }}</span>
      </div>
      <div class="card-body">
        <div class="info-row">
          <span class="label">入场时间</span>
          <span class="value">{{ formatTime(feeList[0].entryTime) }}</span>
        </div>
        <div class="info-row">
          <span class="label">停车时长</span>
          <span class="value">{{ formatDuration(feeList[0].durationMinutes) }}</span>
        </div>
      </div>
      <div class="card-footer">
        <div>
          <div class="fee-label">应付金额</div>
          <div class="fee-amount">¥{{ feeList[0].feeYuan }}</div>
        </div>
        <van-button
          type="primary"
          round
          size="normal"
          :disabled="!feeList[0].payable"
          @click="goPay(feeList[0].orderId)"
        >
          立即支付
        </van-button>
      </div>
    </div>

    <!-- 多条记录：列表 -->
    <div v-if="feeList.length > 1" class="fee-list">
      <div
        v-for="item in feeList"
        :key="item.orderId"
        class="fee-card"
      >
        <div class="card-header">
          <span class="park-name">{{ item.parkName }}</span>
          <span class="plate-number">{{ item.plate }}</span>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="label">入场时间</span>
            <span class="value">{{ formatTime(item.entryTime) }}</span>
          </div>
          <div class="info-row">
            <span class="label">停车时长</span>
            <span class="value">{{ formatDuration(item.durationMinutes) }}</span>
          </div>
        </div>
        <div class="card-footer">
          <div>
            <div class="fee-label">应付金额</div>
            <div class="fee-amount">¥{{ item.feeYuan }}</div>
          </div>
          <van-button
            type="primary"
            round
            size="small"
            :disabled="!item.payable"
            @click="goPay(item.orderId)"
          >
            立即支付
          </van-button>
        </div>
      </div>
    </div>

    <!-- 未搜索时的提示 -->
    <div v-if="!searched && !loading" class="hint-section">
      <van-divider>支持的车牌格式</van-divider>
      <div class="hint-text">
        普通蓝牌 7 位：京A12345<br />
        新能源绿牌 8 位：京AD12345<br />
        点击上方输入框唤起专用键盘
      </div>
    </div>

    <!-- 车牌键盘 -->
    <PlateKeyboard
      v-if="showKeyboard"
      @confirm="onPlateConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { queryFeeByPlate } from '@/api/fee'
import { useAppStore } from '@/stores/app'
import PlateKeyboard from '@/components/PlateKeyboard.vue'
import type { H5FeeVO } from '@/api/types'

const router = useRouter()
const appStore = useAppStore()

const currentPlate = ref(appStore.recentPlate || '')
const showKeyboard = ref(false)
const feeList = ref<H5FeeVO[]>([])
const loading = ref(false)
const errorMsg = ref('')
const searched = ref(false)

function onPlateConfirm(plate: string) {
  currentPlate.value = plate
  showKeyboard.value = false
  appStore.setRecentPlate(plate)
  fetchFee(plate)
}

async function fetchFee(plate: string) {
  loading.value = true
  errorMsg.value = ''
  feeList.value = []
  searched.value = true

  try {
    const result = await queryFeeByPlate(plate)
    feeList.value = result
  } catch (e: any) {
    errorMsg.value = e?.message || '查询失败，请重试'
  } finally {
    loading.value = false
  }
}

function goPay(orderId: number) {
  router.push(`/pay/${orderId}`)
}

function formatTime(time: string): string {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

function formatDuration(minutes: number): string {
  if (!minutes || minutes <= 0) return '不足1分钟'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0) {
    return `${h}小时${m > 0 ? m + '分钟' : ''}`
  }
  return `${m}分钟`
}
</script>

<style scoped>
.header {
  text-align: center;
  padding: 24px 0 16px;
}

.title {
  font-size: 24px;
  font-weight: 700;
  color: #1989fa;
  margin: 0;
}

.subtitle {
  font-size: 14px;
  color: #969799;
  margin-top: 8px;
}

/* ---- 车牌输入区 ---- */
.plate-input-area {
  margin: 0 0 16px;
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.plate-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #969799;
  font-size: 15px;
}

.plate-preview {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  justify-content: center;
}

.plate-preview-char {
  width: 32px;
  height: 44px;
  border-radius: 4px;
  border: 1.5px solid #dcdee0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #323233;
  font-family: 'Courier New', monospace;
  background: #f7f8fa;
}

.plate-preview-char.is-province {
  color: #1989fa;
}

/* ---- 其他 ---- */
.loading-center {
  display: flex;
  justify-content: center;
  padding: 40px 0;
}

.hint-section {
  margin-top: 40px;
}

.hint-text {
  text-align: center;
  font-size: 13px;
  color: #969799;
  line-height: 1.8;
}

.fee-list {
  display: flex;
  flex-direction: column;
}
</style>
