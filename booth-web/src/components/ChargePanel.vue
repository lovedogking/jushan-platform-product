<template>
  <a-drawer
    :open="store.chargePanelVisible"
    title="收费面板"
    placement="right"
    :width="480"
    :closable="true"
    :mask-closable="false"
    @close="handleClose"
  >
    <!-- 加载中 -->
    <a-spin :spinning="store.chargeLoading" tip="查询收费信息...">
      <!-- 收费结果（已完成时） -->
      <a-result
        v-if="store.chargeResult"
        :status="store.chargeResult.success ? 'success' : 'error'"
        :title="store.chargeResult.success ? '已放行' : '操作失败'"
        :sub-title="store.chargeResult.message"
      >
        <template #extra>
          <a-space>
            <a-button
              v-if="!store.chargeResult.gateOpened"
              type="primary"
              danger
              @click="handleManualRelease"
            >
              人工放行
            </a-button>
            <a-button @click="handleReset">继续收费</a-button>
            <a-button type="primary" @click="handleClose">关闭</a-button>
          </a-space>
        </template>
      </a-result>

      <!-- 收费表单 -->
      <template v-else-if="store.currentChargeInfo">
        <!-- 车辆信息卡片 -->
        <a-card :bordered="false" size="small" class="vehicle-card">
          <div class="vehicle-plate">
            <span class="plate-text">{{ store.currentChargeInfo.plateNumber }}</span>
            <a-tag v-if="store.currentChargeInfo.plateColor" :color="plateColorHex">
              {{ plateColorLabel }}
            </a-tag>
          </div>
          <a-descriptions :column="1" size="small" class="vehicle-detail">
            <a-descriptions-item label="车辆类型">
              <a-tag :color="vehicleTypeColor">{{ vehicleTypeLabel }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="入场时间">
              {{ formattedEntryTime }}
            </a-descriptions-item>
            <a-descriptions-item label="停车时长">
              {{ formattedDuration }}
            </a-descriptions-item>
            <a-descriptions-item label="应收金额">
              <span class="fee-amount">{{ formattedFee }}</span>
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <!-- 支付方式选择 -->
        <a-card :bordered="false" size="small" title="支付方式" class="payment-card">
          <a-radio-group
            v-model:value="paymentMethod"
            :disabled="submitting"
            button-style="solid"
            size="large"
            class="payment-methods"
          >
            <a-radio-button value="CASH">现金</a-radio-button>
            <a-radio-button value="WECHAT">微信</a-radio-button>
            <a-radio-button value="ALIPAY">支付宝</a-radio-button>
          </a-radio-group>

          <!-- 扫码支付输入框 -->
          <div v-if="paymentMethod === 'WECHAT' || paymentMethod === 'ALIPAY'" class="scan-input-area">
            <a-input
              ref="scanInputRef"
              v-model:value="authCode"
              placeholder="请扫描车主付款码"
              size="large"
              :disabled="submitting"
              @keydown="handleScanKeydown"
              @press-enter="handleConfirmCharge"
            >
              <template #prefix>
                <ScanOutlined />
              </template>
            </a-input>
            <div class="scan-hint">
              支持扫码枪自动输入，或手动输入付款码后按回车
            </div>
          </div>
        </a-card>

        <!-- 操作按钮 -->
        <div class="charge-actions">
          <a-space direction="vertical" style="width: 100%">
            <a-button
              type="primary"
              size="large"
              block
              :loading="submitting"
              :disabled="!canConfirmCharge"
              @click="handleConfirmCharge"
            >
              <template #icon><CheckOutlined /></template>
              确认收费（{{ paymentMethodLabel }}）
            </a-button>
            <a-button
              size="large"
              block
              :disabled="submitting"
              @click="handleFreeRelease"
            >
              <template #icon><ThunderboltOutlined /></template>
              免费放行
            </a-button>
            <a-button
              type="dashed"
              size="large"
              block
              danger
              :disabled="submitting"
              @click="handleManualRelease"
            >
              <template #icon><ToolOutlined /></template>
              人工放行
            </a-button>
          </a-space>
        </div>
      </template>

      <!-- 无收费信息 -->
      <a-empty v-else description="无收费信息" />
    </a-spin>

    <!-- 人工放行弹窗 -->
    <ManualReleaseModal
      v-model:open="manualReleaseVisible"
      :lane-id="store.currentChargeInfo?.laneId ?? 0"
      :plate-number="store.currentChargeInfo?.plateNumber ?? ''"
      @success="handleManualReleaseSuccess"
    />
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  CheckOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  ScanOutlined,
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useMonitorStore } from '@/stores/monitor'
import { submitCharge, manualOpenGate } from '@/api/charge'
import ManualReleaseModal from './ManualReleaseModal.vue'
import type { PaymentMethod } from '@/api/monitor-types'

const store = useMonitorStore()

const paymentMethod = ref<PaymentMethod>('CASH')
const authCode = ref('')
const submitting = ref(false)
const manualReleaseVisible = ref(false)
const scanInputRef = ref<InstanceType<typeof HTMLInputElement> | null>(null)

// 扫码枪检测：记录按键时间戳，连续快速输入 + Enter 判断为扫码枪
let lastKeyTime = 0
const SCAN_THRESHOLD_MS = 80 // 扫码枪两次按键间隔通常 < 30ms

function handleScanKeydown(_e: KeyboardEvent) {
  const now = Date.now()
  lastKeyTime = now
}

// ========== 格式化方法 ==========

const formattedEntryTime = computed(() => {
  const t = store.currentChargeInfo?.entryTime
  if (!t) return '-'
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
})

const formattedDuration = computed(() => {
  const minutes = store.currentChargeInfo?.durationMinutes
  if (minutes == null || minutes < 0) return '-'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0) return `${h} 小时 ${m} 分钟`
  return `${m} 分钟`
})

const formattedFee = computed(() => {
  const feeCents = store.currentChargeInfo?.feeCents
  if (feeCents == null) return '-'
  return `¥ ${(feeCents / 100).toFixed(2)}`
})

const plateColorLabel = computed(() => {
  const color = store.currentChargeInfo?.plateColor
  if (!color) return ''
  const map: Record<string, string> = {
    BLUE: '蓝牌', GREEN: '绿牌', YELLOW: '黄牌',
    WHITE: '白牌', BLACK: '黑牌',
  }
  return map[color] || color
})

const plateColorHex = computed(() => {
  const color: string = store.currentChargeInfo?.plateColor || ''
  const map: Record<string, string> = {
    BLUE: 'blue', GREEN: 'green', YELLOW: 'gold',
    WHITE: 'default', BLACK: '#1f2937',
  }
  return map[color] || 'default'
})

const vehicleTypeLabel = computed(() => {
  const type: string = store.currentChargeInfo?.vehicleType || ''
  if (!type) return '未知'
  const map: Record<string, string> = {
    MONTHLY: '月卡车', VIP: 'VIP车', FIXED: '固定车',
    TEMPORARY: '临时车', VISITOR: '访客车',
  }
  return map[type] || type
})

const vehicleTypeColor = computed(() => {
  const type: string = store.currentChargeInfo?.vehicleType || ''
  const map: Record<string, string> = {
    MONTHLY: 'green', VIP: 'gold', FIXED: 'blue',
    TEMPORARY: 'default', VISITOR: 'orange',
  }
  return map[type] || 'default'
})

const paymentMethodLabel = computed(() => {
  const map: Record<string, string> = {
    CASH: '现金', WECHAT: '微信扫码', ALIPAY: '支付宝扫码',
  }
  return map[paymentMethod.value] || paymentMethod.value
})

const canConfirmCharge = computed(() => {
  if (!store.currentChargeInfo) return false
  if (submitting.value) return false
  // 扫码支付需要输入付款码
  if (paymentMethod.value === 'WECHAT' || paymentMethod.value === 'ALIPAY') {
    return authCode.value.trim().length > 0
  }
  return true
})

// 当切换到扫码支付时自动聚焦输入框
watch(paymentMethod, async (newVal) => {
  if (newVal === 'WECHAT' || newVal === 'ALIPAY') {
    await nextTick()
    // 聚焦到扫码输入框
    const el = document.querySelector('.scan-input-area input') as HTMLInputElement
    el?.focus()
  }
})

// ========== 操作方法 ==========

/** 确认收费 */
async function handleConfirmCharge() {
  const info = store.currentChargeInfo
  if (!info) return

  submitting.value = true
  try {
    // 1. 提交收费（调用出场接口）
    const feeAmount = info.feeAmount
    await submitCharge({
      sessionId: info.sessionId,
      exitLaneId: info.laneId,
      feeAmount,
      paidAmount: feeAmount,
      paymentMethod: paymentMethod.value,
      authCode: authCode.value || undefined,
    })

    // 2. 收费成功后自动开闸
    const reason = `收费放行: ${paymentMethodLabel.value}`
    const gateResult = await manualOpenGate(info.laneId, reason)

    const success = gateResult.gateOpened === true

    store.setChargeResult({
      success,
      message: success
        ? `${info.plateNumber} 已收费 ¥${(info.feeCents / 100).toFixed(2)}，已放行`
        : `收费成功但开闸失败: ${gateResult.gateResult || gateResult.resultMessage || '请人工处理'}`,
      gateOpened: gateResult.gateOpened === true,
    })

    if (success) {
      message.success(`${info.plateNumber} 收费成功，已放行`)
      // 刷新车道状态
      store.refreshAllDevices().catch(() => {})
    } else {
      message.warning(`${info.plateNumber} 收费成功，但开闸失败，请人工处理`)
    }
  } catch (e: any) {
    const errMsg = e?.message || '收费失败'
    store.setChargeResult({
      success: false,
      message: errMsg,
      gateOpened: false,
    })
    message.error(errMsg)
  } finally {
    submitting.value = false
  }
}

/** 免费放行 */
async function handleFreeRelease() {
  const info = store.currentChargeInfo
  if (!info) return

  submitting.value = true
  try {
    // 免费放行：提交 0 元收费
    await submitCharge({
      sessionId: info.sessionId,
      exitLaneId: info.laneId,
      feeAmount: 0,
      paidAmount: 0,
      paymentMethod: 'FREE',
      remark: '免费放行',
    })

    const gateResult = await manualOpenGate(info.laneId, `免费放行: ${info.plateNumber}`)
    const success = gateResult.gateOpened === true

    store.setChargeResult({
      success,
      message: success
        ? `${info.plateNumber} 已免费放行`
        : `免费放行完成但开闸失败: ${gateResult.gateResult || '请人工处理'}`,
      gateOpened: gateResult.gateOpened === true,
    })

    if (success) {
      message.success(`${info.plateNumber} 免费放行成功`)
      store.refreshAllDevices().catch(() => {})
    } else {
      message.warning('免费放行完成但开闸失败，请人工处理')
    }
  } catch (e: any) {
    const errMsg = e?.message || '免费放行失败'
    store.setChargeResult({
      success: false,
      message: errMsg,
      gateOpened: false,
    })
    message.error(errMsg)
  } finally {
    submitting.value = false
  }
}

/** 人工放行 */
function handleManualRelease() {
  manualReleaseVisible.value = true
}

/** 人工放行成功回调 */
function handleManualReleaseSuccess(result: { success: boolean; message: string; gateOpened: boolean | null }) {
  store.setChargeResult(result)

  // 如果是从收费面板触发的人工放行，自动刷新
  if (result.success) {
    store.refreshAllDevices().catch(() => {})
  }
}

/** 重置收费面板（清空结果，可以再次收费） */
function handleReset() {
  store.setChargeResult(null as any)
  paymentMethod.value = 'CASH'
  authCode.value = ''
}

/** 关闭面板 */
function handleClose() {
  if (submitting.value) {
    message.warning('正在处理中，请稍候')
    return
  }
  store.hideChargePanel()
  paymentMethod.value = 'CASH'
  authCode.value = ''
}
</script>

<style lang="scss" scoped>
.vehicle-card {
  margin-bottom: $spacing-md;

  .vehicle-plate {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: $spacing-md;
  }

  .plate-text {
    font-size: 28px;
    font-weight: 700;
    color: $text-color;
    letter-spacing: 1px;
    font-family: 'Courier New', monospace;
  }

  .vehicle-detail {
    margin-top: $spacing-sm;
  }

  .fee-amount {
    font-size: 24px;
    font-weight: 700;
    color: $error-color;
  }
}

.payment-card {
  margin-bottom: $spacing-lg;

  .payment-methods {
    display: flex;
    gap: $spacing-sm;
    margin-bottom: $spacing-md;
  }

  .scan-input-area {
    margin-top: $spacing-md;

    .scan-hint {
      margin-top: $spacing-xs;
      font-size: 12px;
      color: $text-color-secondary;
    }
  }
}

.charge-actions {
  padding: $spacing-md 0;
}
</style>
