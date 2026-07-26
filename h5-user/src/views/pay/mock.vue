<template>
  <div class="page-container">
    <!-- 无支付数据 -->
    <van-empty
      v-if="!payData"
      description="支付信息已过期，请重新查询"
      image="error"
    >
      <van-button type="primary" round @click="$router.push('/')">
        返回首页
      </van-button>
    </van-empty>

    <!-- 模拟支付 -->
    <template v-else>
      <div class="mock-header">
        <van-icon name="warning-o" size="36" color="#ff976a" />
        <h2 class="mock-title">模拟支付</h2>
        <p class="mock-desc">开发环境模拟支付，不会产生真实扣费</p>
      </div>

      <div class="fee-card">
        <div class="card-header">
          <span class="park-name">{{ payData.parkName }}</span>
          <span class="plate-number">{{ payData.plate }}</span>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="label">订单编号</span>
            <span class="value">{{ payData.orderNo }}</span>
          </div>
        </div>
        <div class="card-footer">
          <div>
            <div class="fee-label">应付金额</div>
            <div class="fee-amount">¥{{ payData.payableAmountYuan }}</div>
          </div>
        </div>
      </div>

      <!-- 模拟支付按钮 -->
      <div class="mock-actions">
        <van-button
          type="primary"
          round
          block
          size="large"
          :loading="submitting === 'success'"
          @click="onMockSuccess"
        >
          支付成功
        </van-button>
        <van-button
          type="default"
          round
          block
          size="large"
          :loading="submitting === 'fail'"
          @click="onMockFail"
        >
          支付失败
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { notifyPay } from '@/api/pay'
import type { H5PayResultVO } from '@/api/types'

const router = useRouter()

// 从 history.state 获取支付数据
const payData = ref<H5PayResultVO | null>(
  history.state?.payData || null,
)

const submitting = ref('')

async function onMockSuccess() {
  if (!payData.value) return
  submitting.value = 'success'

  try {
    await notifyPay(payData.value.orderId, 'success')
    router.push(`/pay/result?orderId=${payData.value.orderId}&status=success`)
  } catch (e: any) {
    showToast(e?.message || '操作失败')
  } finally {
    submitting.value = ''
  }
}

async function onMockFail() {
  if (!payData.value) return
  submitting.value = 'fail'

  try {
    await notifyPay(payData.value.orderId, 'fail')
    router.push(`/pay/result?orderId=${payData.value.orderId}&status=fail`)
  } catch (e: any) {
    showToast(e?.message || '操作失败')
  } finally {
    submitting.value = ''
  }
}
</script>

<style scoped>
.mock-header {
  text-align: center;
  padding: 40px 0 24px;
}

.mock-title {
  font-size: 20px;
  font-weight: 600;
  color: #323233;
  margin: 12px 0 8px;
}

.mock-desc {
  font-size: 13px;
  color: #ff976a;
  margin: 0;
}

.mock-actions {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
