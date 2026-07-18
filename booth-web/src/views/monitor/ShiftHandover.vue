<template>
  <div class="shift-handover">
    <!-- 当前班次信息 -->
    <a-card v-if="currentShift" title="当前班次信息" :bordered="false" class="shift-card">
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="班次类型">{{ shiftTypeLabel }}</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ formattedStartTime }}</a-descriptions-item>
        <a-descriptions-item label="入场车辆">{{ currentShift.entryCount ?? 0 }} 辆</a-descriptions-item>
        <a-descriptions-item label="出场车辆">{{ currentShift.exitCount ?? 0 }} 辆</a-descriptions-item>
        <a-descriptions-item label="系统应收">
          <span class="fee-highlight">¥{{ formatMoney(currentShift.feeAmount) }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="现金实收">
          <span class="fee-highlight">¥{{ formatMoney(currentShift.cashAmount ?? currentShift.feeAmount) }}</span>
          <a-button type="link" size="small" @click="showAdjustModal = true" :disabled="adjusting">校正</a-button>
        </a-descriptions-item>
        <a-descriptions-item label="差额">
          <span :class="{ 'text-danger': (currentShift.onlineAmount ?? 0) !== 0 }">
            ¥{{ formatMoney(currentShift.onlineAmount ?? 0) }}
          </span>
        </a-descriptions-item>
        <a-descriptions-item label="欠费订单">{{ currentShift.arrearsCount ?? 0 }} 笔</a-descriptions-item>
        <a-descriptions-item label="异常处理">{{ currentShift.exceptionCount ?? 0 }} 次</a-descriptions-item>
        <a-descriptions-item label="交接订单">{{ currentShift.handoverOrderCount ?? 0 }} 笔</a-descriptions-item>
      </a-descriptions>

      <div class="shift-actions">
        <a-space>
          <a-button type="primary" :loading="closingShift" @click="handleShiftConfirm">
            交班确认
          </a-button>
        </a-space>
      </div>
    </a-card>

    <!-- 无当前班次：显示开班 -->
    <a-card v-else title="尚未开班" :bordered="false" class="shift-card">
      <a-form layout="vertical">
        <a-form-item label="班次类型" required>
          <a-select v-model:value="newShiftType" placeholder="选择班次类型" style="width: 200px">
            <a-select-option value="MORNING">早班</a-select-option>
            <a-select-option value="AFTERNOON">中班</a-select-option>
            <a-select-option value="NIGHT">晚班</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="startingShift" @click="handleStartShift">
            开班
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 历史交接班记录 -->
    <a-card title="历史交接班记录" :bordered="false" class="shift-card" style="margin-top: 16px">
      <a-table
        :data-source="historyRecords"
        :columns="historyColumns"
        :pagination="historyPagination"
        :loading="loadingHistory"
        row-key="id"
        size="small"
        @change="handleHistoryPageChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'feeAmount'">
            ¥{{ formatMoney(record.feeAmount) }}
          </template>
          <template v-if="column.key === 'cashAmount'">
            ¥{{ formatMoney(record.cashAmount) }}
          </template>
          <template v-if="column.key === 'onlineAmount'">
            ¥{{ formatMoney(record.onlineAmount) }}
          </template>
          <template v-if="column.key === 'handoverStatus'">
            <a-tag :color="record.handoverStatus === 'OPEN' ? 'processing' : 'default'">
              {{ record.handoverStatus === 'OPEN' ? '进行中' : '已交班' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 校正实收弹窗 -->
    <a-modal
      v-model:open="showAdjustModal"
      title="校正实收金额"
      @ok="handleAdjustConfirm"
      :confirm-loading="adjusting"
    >
      <a-form layout="vertical">
        <a-form-item label="系统应收">
          <a-input :value="'¥' + formatMoney(currentShift?.feeAmount ?? 0)" disabled />
        </a-form-item>
        <a-form-item label="校正后实收（元）" required>
          <a-input-number
            v-model:value="adjustedCashAmount"
            :min="0"
            :precision="2"
            style="width: 100%"
            :disabled="adjusting"
          />
        </a-form-item>
        <a-form-item label="校正原因">
          <a-textarea
            v-model:value="adjustedReason"
            placeholder="请填写校正原因（可选）"
            :rows="2"
            :maxlength="200"
            :disabled="adjusting"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 交班确认弹窗 -->
    <a-modal
      v-model:open="showConfirmModal"
      title="交班确认"
      @ok="doCloseShift"
      :confirm-loading="closingShift"
      ok-text="确认交班"
    >
      <a-descriptions :column="1" size="small" bordered>
        <a-descriptions-item label="班次">{{ shiftTypeLabel }}</a-descriptions-item>
        <a-descriptions-item label="入场">{{ currentShift?.entryCount ?? 0 }} 辆</a-descriptions-item>
        <a-descriptions-item label="出场">{{ currentShift?.exitCount ?? 0 }} 辆</a-descriptions-item>
        <a-descriptions-item label="系统应收">¥{{ formatMoney(currentShift?.feeAmount ?? 0) }}</a-descriptions-item>
        <a-descriptions-item label="现金实收">¥{{ formatMoney(currentShift?.cashAmount ?? currentShift?.feeAmount ?? 0) }}</a-descriptions-item>
        <a-descriptions-item label="欠费订单">{{ currentShift?.arrearsCount ?? 0 }} 笔</a-descriptions-item>
      </a-descriptions>

      <a-alert
        v-if="(currentShift?.arrearsOrders?.length ?? 0) > 0"
        type="warning"
        message="以下欠费订单将交接至下一班次"
        style="margin-top: 12px"
      />
      <a-table
        v-if="(currentShift?.arrearsOrders?.length ?? 0) > 0"
        :data-source="currentShift?.arrearsOrders ?? []"
        :columns="arrearsColumns"
        :pagination="false"
        size="small"
        row-key="orderId"
        style="margin-top: 8px"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  getCurrentShift,
  startShift,
  closeShift,
  getShiftHistory,
  type ShiftRecordVO,
} from '@/api/shift'

const props = defineProps<{
  parkingLotId: number
}>()

const currentShift = ref<ShiftRecordVO | null>(null)
const historyRecords = ref<ShiftRecordVO[]>([])
const loadingHistory = ref(false)
const startingShift = ref(false)
const closingShift = ref(false)
const adjusting = ref(false)

const newShiftType = ref('MORNING')
const showAdjustModal = ref(false)
const showConfirmModal = ref(false)
const adjustedCashAmount = ref(0)
const adjustedReason = ref('')

const historyPagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
})

const historyColumns = [
  { title: '班次', dataIndex: 'shiftType', key: 'shiftType', width: 80 },
  { title: '操作员', dataIndex: 'operatorName', key: 'operatorName', width: 100 },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
  { title: '应收', key: 'feeAmount', width: 100 },
  { title: '实收', key: 'cashAmount', width: 100 },
  { title: '差额', key: 'onlineAmount', width: 100 },
  { title: '状态', key: 'handoverStatus', width: 80 },
]

const arrearsColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber' },
  { title: '欠费金额（分）', dataIndex: 'feeCents', key: 'feeCents' },
  { title: '产生时间', dataIndex: 'createdAt', key: 'createdAt' },
]

const shiftTypeLabel = computed(() => {
  const map: Record<string, string> = { MORNING: '早班', AFTERNOON: '中班', NIGHT: '晚班' }
  return map[currentShift.value?.shiftType ?? ''] || '-'
})

const formattedStartTime = computed(() => {
  const t = currentShift.value?.startTime
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
})

function formatMoney(value: number | null | undefined): string {
  if (value == null) return '0.00'
  return value.toFixed(2)
}

async function loadCurrentShift() {
  try {
    currentShift.value = await getCurrentShift()
    if (currentShift.value) {
      adjustedCashAmount.value = currentShift.value.cashAmount ?? currentShift.value.feeAmount ?? 0
    }
  } catch {
    currentShift.value = null
  }
}

async function loadHistory() {
  loadingHistory.value = true
  try {
    const result = await getShiftHistory({
      current: historyPagination.value.current,
      size: historyPagination.value.pageSize,
      parkingLotId: props.parkingLotId,
    })
    historyRecords.value = result.records || []
    historyPagination.value.total = result.total || 0
  } catch {
    historyRecords.value = []
  } finally {
    loadingHistory.value = false
  }
}

function handleHistoryPageChange(pag: { current: number; pageSize: number }) {
  historyPagination.value.current = pag.current
  historyPagination.value.pageSize = pag.pageSize
  loadHistory()
}

async function handleStartShift() {
  startingShift.value = true
  try {
    await startShift({
      parkingLotId: props.parkingLotId,
      shiftType: newShiftType.value,
      operatorName: '',
    })
    message.success('开班成功')
    await loadCurrentShift()
    await loadHistory()
  } catch (e: any) {
    message.error(e?.message || '开班失败')
  } finally {
    startingShift.value = false
  }
}

function handleShiftConfirm() {
  // 先刷新当前班次数据
  loadCurrentShift().then(() => {
    showConfirmModal.value = true
  })
}

async function doCloseShift() {
  closingShift.value = true
  try {
    const payload: any = { shiftId: currentShift.value!.id }
    // 如果用户校正过实收金额
    if (showAdjustModal.value && adjustedCashAmount.value !== (currentShift.value?.feeAmount ?? 0)) {
      payload.confirmedCashAmount = adjustedCashAmount.value
      payload.adjustReason = adjustedReason.value
    }
    await closeShift(payload)
    message.success('交班成功')
    showConfirmModal.value = false
    showAdjustModal.value = false
    await loadCurrentShift()
    await loadHistory()
  } catch (e: any) {
    message.error(e?.message || '交班失败')
  } finally {
    closingShift.value = false
  }
}

async function handleAdjustConfirm() {
  adjusting.value = true
  try {
    if (!currentShift.value) return
    currentShift.value.cashAmount = adjustedCashAmount.value
    currentShift.value.adjustReason = adjustedReason.value
    currentShift.value.onlineAmount = (currentShift.value.feeAmount ?? 0) - adjustedCashAmount.value
    message.success('实收金额已校正')
    showAdjustModal.value = false
  } finally {
    adjusting.value = false
  }
}

onMounted(() => {
  loadCurrentShift()
  loadHistory()
})

watch(() => props.parkingLotId, () => {
  loadCurrentShift()
  loadHistory()
})
</script>

<style lang="scss" scoped>
.shift-handover {
  padding: 8px;
}

.shift-card {
  margin-bottom: 16px;
}

.shift-actions {
  margin-top: 16px;
  text-align: center;
}

.fee-highlight {
  font-weight: 700;
  font-size: 16px;
  color: #1f2937;
}

.text-danger {
  color: #ff4d4f;
  font-weight: 600;
}
</style>
