<template>
  <div class="page-container">
    <!-- 加载中 -->
    <van-loading v-if="loading" class="loading-center" />

    <!-- 错误 -->
    <van-empty
      v-else-if="errorMsg"
      :description="errorMsg"
      image="error"
    >
      <van-button type="primary" round @click="$router.push('/')">
        返回首页
      </van-button>
    </van-empty>

    <!-- 支付确认 -->
    <template v-else-if="payResult">
      <div class="confirm-section">
        <div class="confirm-header">
          <van-icon name="description" size="24" color="#1989fa" />
          <span class="confirm-title">支付确认</span>
        </div>
      </div>

      <div class="fee-card">
        <div class="card-header">
          <span class="park-name">{{ payResult.parkName }}</span>
          <span class="plate-number">{{ payResult.plate }}</span>
        </div>
        <div class="card-body">
          <div class="info-row">
            <span class="label">订单编号</span>
            <span class="value">{{ payResult.orderNo }}</span>
          </div>
          <div class="info-row">
            <span class="label">订单状态</span>
            <van-tag type="warning" size="medium">待支付</van-tag>
          </div>
        </div>
        <div class="card-footer">
          <div>
            <div class="fee-label">应付金额</div>
            <div class="fee-amount">¥{{ payResult.payableAmountYuan }}</div>
          </div>
        </div>
      </div>

      <!-- 支付按钮 -->
      <div class="pay-actions">
        <van-button
          type="primary"
          round
          block
          size="large"
          :loading="paying"
          @click="onConfirmPay"
        >
          确认支付 ¥{{ payResult.payableAmountYuan }}
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { preparePay } from '@/api/pay'
import type { H5PayResultVO } from '@/api/types'

const route = useRoute()
const router = useRouter()

const payResult = ref<H5PayResultVO | null>(null)
const loading = ref(true)
const paying = ref(false)
const errorMsg = ref('')

onMounted(async () => {
  const orderId = Number(route.params.orderId)
  if (!orderId) {
    errorMsg.value = '订单ID无效'
    loading.value = false
    return
  }

  try {
    const result = await preparePay(orderId)
    payResult.value = result
  } catch (e: any) {
    errorMsg.value = e?.message || '获取支付信息失败'
  } finally {
    loading.value = false
  }
})

function onConfirmPay() {
  if (!payResult.value) return

  paying.value = true

  // 根据 mock 字段决定跳转目标
  if (payResult.value.mock) {
    // 模拟支付模式：通过路由 state 传递支付数据
    router.push({
      path: `/mock-pay/${payResult.value.orderId}`,
      state: {
        payData: JSON.parse(JSON.stringify(payResult.value)),
      },
    })
  } else {
    // 真实支付模式：跳转 P云 pay_url（后续实现）
    showToast('真实支付暂未接入')
    paying.value = false
  }
}
</script>

<style scoped>
.loading-center {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confirm-title {
  font-size: 18px;
  font-weight: 600;
  color: #323233;
}

.pay-actions {
  margin-top: 24px;
}
</style>
