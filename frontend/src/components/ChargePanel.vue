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
            <PlateTag :plate-number="store.currentChargeInfo.plateNumber" :plate-color="store.currentChargeInfo.plateColor" />
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
            <!-- Phase 3: 扫码支付暂不启用
            <a-radio-button value="WECHAT">微信</a-radio-button>
            <a-radio-button value="ALIPAY">支付宝</a-radio-button>
            -->
          </a-radio-group>

          <!-- Phase 3: 扫码支付暂不启用
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
          -->
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
                v-if="hasFeeReducePermission"
                size="large"
                block
                :disabled="submitting || feeReductionReducing"
                @click="openFeeReduction"
              >
                <template #icon><DollarOutlined /></template>
                费用减免
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

    <!-- 费用减免弹窗 -->
    <a-modal
      v-model:open="showFeeReductionModal"
      title="费用减免"
      :confirm-loading="feeReductionReducing"
      @ok="handleFeeReductionConfirm"
    >
      <a-form layout="vertical">
        <a-form-item label="原应收金额">
          <a-input :value="currentFeeDisplay" disabled />
        </a-form-item>
        <a-form-item label="减免金额（元）" required>
          <a-input-number
            v-model:value="feeReductionAmountYuan"
            :min="0"
            :max="currentFeeYuan"
            :precision="2"
            style="width: 100%"
            placeholder="请输入减免金额"
          />
        </a-form-item>
        <a-form-item label="减免原因" required>
          <a-textarea
            v-model:value="feeReductionReason"
            :maxlength="200"
            :rows="3"
            placeholder="请输入减免原因（最多200字）"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 人工放行弹窗 -->
    <ManualReleaseModal
      v-model:open="manualReleaseVisible"
      :lane-id="store.currentChargeInfo?.laneId ?? 0"
      :plate-number="store.currentChargeInfo?.plateNumber ?? ''"
      :direction="2"
      @success="handleManualReleaseSuccess"
    />
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  CheckOutlined,
  ToolOutlined,
  ScanOutlined,
  DollarOutlined,
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useMonitorStore } from '@/stores/monitor'
import { manualOpenGate, submitFeeReduction } from '@/api/charge'
import ManualReleaseModal from './ManualReleaseModal.vue'
import PlateTag from '@/components/PlateTag.vue'
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
  const t = type.toUpperCase()
  const map: Record<string, string> = {
    MONTHLY: '月租车', MONTHLY_PASS: '月租车',
    VIP: 'VIP车',
    FIXED: '固定车', FIXED_SPACE: '固定车', WHITE: '固定车', WHITELIST: '固定车',
    FREE: '免费车',
    PREPAID: '储值车',
    BLACKLIST: '黑名单',
    VISITOR: '访客车',
    TEMP: '临时车', TEMPORARY: '临时车',
  }
  return map[t] || t
})

const vehicleTypeColor = computed(() => {
  const type: string = store.currentChargeInfo?.vehicleType || ''
  const t = type.toUpperCase()
  if (t === 'VIP') return 'gold'
  if (t === 'BLACKLIST') return 'red'
  if (t === 'PREPAID') return 'cyan'
  if (t === 'FIXED' || t === 'FIXED_SPACE' || t === 'WHITE' || t === 'WHITELIST') return 'blue'
  if (t === 'MONTHLY' || t === 'MONTHLY_PASS' || t === 'FREE') return 'green'
  if (t === 'TEMP' || t === 'TEMPORARY') return 'orange'
  return 'default'
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
  if (feeReductionReducing.value) return false
  return true
})

// ========== 费用减免状态与计算属性 ==========

const showFeeReductionModal = ref(false)
const feeReductionReducing = ref(false)
const feeReductionAmountYuan = ref(0)
const feeReductionReason = ref('')

const currentFeeYuan = computed(() => (store.currentChargeInfo?.feeCents ?? 0) / 100)
const currentFeeDisplay = computed(() => `¥ ${currentFeeYuan.value.toFixed(2)}`)
const hasFeeReducePermission = computed(() => {
  try {
    const permsJson = sessionStorage.getItem('jushan_permissions')
    if (permsJson) {
      const perms: string[] = JSON.parse(permsJson)
      return perms.includes('fee:reduce')
    }
  } catch { /* ignore */ }
  return false
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

/** 确认收费（模拟成功，直接开闸放行） */
async function handleConfirmCharge() {
  const info = store.currentChargeInfo
  if (!info) return

  submitting.value = true
  try {
    const feeDisplay = `¥${(info.feeCents / 100).toFixed(2)}`
    const reason = `收费放行: ${paymentMethodLabel.value} ${feeDisplay}`
    const gateResult = await manualOpenGate(info.laneId, reason, { direction: 2 })

    const success = gateResult.gateOpened === true
    store.setChargeResult({
      success,
      message: success
        ? `${info.plateNumber} 已收费 ${feeDisplay}，已放行`
        : `收费成功但开闸失败: ${gateResult.gateResult || gateResult.resultMessage || '请人工处理'}`,
      gateOpened: gateResult.gateOpened === true,
    })

    if (success) {
      message.success(`${info.plateNumber} 收费成功，已放行`)
      store.refreshAllDevices().catch(() => {})
    } else {
      message.warning(`${info.plateNumber} 收费成功，但开闸失败，请人工处理`)
    }
  } catch (e: any) {
    const errMsg = e?.message || '操作失败'
    store.setChargeResult({ success: false, message: errMsg, gateOpened: false })
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
  showFeeReductionModal.value = false
  feeReductionAmountYuan.value = 0
  feeReductionReason.value = ''
}

// ========== 费用减免方法 ==========

/** 打开费用减免弹窗 */
function openFeeReduction() {
  feeReductionAmountYuan.value = 0
  feeReductionReason.value = ''
  showFeeReductionModal.value = true
}

/** 确认费用减免（验证并触发） */
async function handleFeeReductionConfirm() {
  if (!feeReductionReason.value.trim()) {
    message.warning('请输入减免原因')
    return
  }
  if (feeReductionAmountYuan.value <= 0) {
    message.warning('请输入减免金额')
    return
  }
  if (feeReductionAmountYuan.value > currentFeeYuan.value) {
    message.warning('减免金额不能超过应收金额')
    return
  }

  const info = store.currentChargeInfo
  if (!info) return

  const originalFeeCents = info.feeCents
  const reductionCents = Math.round(feeReductionAmountYuan.value * 100)
  const reducedFeeCents = originalFeeCents - reductionCents

  // 大额减免二次确认（原金额 > 50000 分 = 500 元）
  if (originalFeeCents > 50000) {
    Modal.confirm({
      title: '大额减免确认',
      content: `原应收金额为 ¥${(originalFeeCents / 100).toFixed(2)}，减免 ¥${feeReductionAmountYuan.value.toFixed(2)} 后实际收取 ¥${(reducedFeeCents / 100).toFixed(2)}，确认执行？`,
      okText: '确认减免',
      cancelText: '取消',
      okType: 'danger',
      onOk: () => doExecuteFeeReduction(info, originalFeeCents, reductionCents, reducedFeeCents),
    })
    return
  }

  await doExecuteFeeReduction(info, originalFeeCents, reductionCents, reducedFeeCents)
}

/** 执行费用减免 API 调用 */
async function doExecuteFeeReduction(
  info: { sessionId: number; feeCents: number; feeAmount: number },
  originalFeeCents: number,
  reductionCents: number,
  reducedFeeCents: number,
) {
  feeReductionReducing.value = true
  try {
    const result = await submitFeeReduction({
      sessionId: info.sessionId,
      originalFeeCents,
      reducedFeeCents,
      reductionCents,
      reason: feeReductionReason.value.trim(),
    })
    // 更新 store 中的费用信息，使"确认收费"使用减免后金额
    const chargeInfo = store.currentChargeInfo
    if (chargeInfo) {
      chargeInfo.feeCents = result.reducedFeeCents
      chargeInfo.feeAmount = result.reducedFeeCents / 100
    }
    showFeeReductionModal.value = false
    message.success('费用减免成功')
  } catch (e: any) {
    message.error(e?.message || '费用减免失败')
  } finally {
    feeReductionReducing.value = false
  }
}

/** 关闭面板 */
function handleClose() {
  if (submitting.value || feeReductionReducing.value) {
    message.warning('正在处理中，请稍候')
    return
  }
  store.hideChargePanel()
  paymentMethod.value = 'CASH'
  authCode.value = ''
  showFeeReductionModal.value = false
  feeReductionAmountYuan.value = 0
  feeReductionReason.value = ''
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
