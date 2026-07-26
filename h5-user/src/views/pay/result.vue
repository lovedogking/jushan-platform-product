<template>
  <div class="page-container">
    <!-- 加载中 -->
    <van-loading v-if="loading" class="loading-center" />

    <!-- 支付成功 -->
    <div v-else-if="isSuccess" class="result-page">
      <div class="result-icon success-icon">
        <van-icon name="checked" size="48" color="#fff" />
      </div>
      <h2 class="result-title">支付成功</h2>
      <p class="result-desc">订单号：{{ result?.orderNo || '-' }}</p>

      <div class="result-detail">
        <div class="detail-row">
          <span class="label">车牌号</span>
          <span class="value">{{ result?.plate || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="label">车场</span>
          <span class="value">{{ result?.parkName || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="label">支付金额</span>
          <span class="value amount">¥{{ result?.payableAmountYuan || '0.00' }}</span>
        </div>
      </div>

      <div class="result-action">
        <van-button type="primary" round block @click="$router.replace('/')">
          返回首页
        </van-button>
      </div>
    </div>

    <!-- 支付失败 -->
    <div v-else class="result-page">
      <div class="result-icon fail-icon">
        <van-icon name="close" size="48" color="#fff" />
      </div>
      <h2 class="result-title">支付失败</h2>
      <p class="result-desc">{{ errorMsg || '支付未完成' }}</p>

      <div class="result-action fail-actions">
        <van-button type="primary" round block @click="$router.back()">
          重新支付
        </van-button>
        <van-button type="default" round block @click="$router.replace('/')">
          返回首页
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { queryPayStatus } from '@/api/pay'
import type { H5PayResultVO } from '@/api/types'

const route = useRoute()

const result = ref<H5PayResultVO | null>(null)
const loading = ref(true)
const verifyError = ref('')

const urlStatus = (route.query.status as string) || ''
const urlOrderId = Number(route.query.orderId)

const isSuccess = computed(() => {
  if (result.value) {
    return result.value.status === 'PAID' || result.value.status === 'COMPLETED'
  }
  return urlStatus === 'success'
})

const errorMsg = computed(() => {
  return verifyError.value || '支付未完成'
})

onMounted(async () => {
  if (!urlOrderId) {
    loading.value = false
    verifyError.value = '订单ID无效'
    return
  }

  try {
    const data = await queryPayStatus(urlOrderId)
    result.value = data
  } catch (e: any) {
    verifyError.value = e?.message || '查询支付状态失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.loading-center {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.result-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 60px;
}

.result-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.success-icon {
  background: #07c160;
}

.fail-icon {
  background: #ee0a24;
}

.result-title {
  font-size: 22px;
  font-weight: 600;
  color: #323233;
  margin: 0 0 8px;
}

.result-desc {
  font-size: 14px;
  color: #969799;
  margin: 0 0 24px;
}

.result-detail {
  width: 100%;
  max-width: 320px;
  text-align: left;
  padding: 16px;
  background: #f7f8fa;
  border-radius: 8px;
  margin-bottom: 32px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 14px;
}

.detail-row .label {
  color: #969799;
}

.detail-row .value {
  color: #323233;
}

.detail-row .amount {
  color: #ee0a24;
  font-weight: 600;
}

.result-action {
  width: 100%;
  max-width: 320px;
}

.fail-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
