<template>
  <div class="remote-gate-page">
    <div class="page-header">
      <h2>远程开闸</h2>
      <span class="page-desc">选择车场和通道，输入原因后远程开启道闸</span>
    </div>

    <a-card :bordered="false" class="gate-card">
      <a-form :model="form" layout="vertical" autocomplete="off">
        <!-- 车场选择 -->
        <a-form-item label="选择车场" required>
          <a-select
            v-model:value="form.parkingLotId"
            placeholder="请选择车场"
            :options="parkingLotOptions"
            :loading="lotLoading"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 360px"
            @change="(value: any) => handleLotChange(value)"
          />
        </a-form-item>

        <!-- 通道选择（仅出口通道） -->
        <a-form-item label="选择通道" required>
          <a-select
            v-model:value="form.laneId"
            placeholder="请先选择车场"
            :options="laneOptions"
            :loading="laneLoading"
            :disabled="!form.parkingLotId"
            allow-clear
            show-search
            option-filter-prop="label"
            style="width: 360px"
          />
        </a-form-item>

        <!-- 开闸原因 -->
        <a-form-item label="开闸原因" required>
          <a-textarea
            v-model:value="form.reason"
            placeholder="请输入开闸原因（必填）"
            :maxlength="200"
            :rows="3"
            style="width: 360px"
            show-count
          />
        </a-form-item>

        <!-- 提交按钮 -->
        <a-form-item>
          <a-popconfirm
            title="确认远程开闸？"
            description="开闸后道闸将立即抬起，请确认已通知岗亭端做好准备。"
            @confirm="handleOpenGate"
          >
            <a-button
              type="primary"
              size="large"
              :loading="submitting"
              :disabled="!canSubmit"
              v-permission="'device:remote:open'"
            >
              <template #icon><ThunderboltOutlined /></template>
              确认开闸
            </a-button>
          </a-popconfirm>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 开闸结果模态框 -->
    <a-modal
      v-model:open="resultModalOpen"
      title="开闸结果"
      :footer="null"
      width="480px"
    >
      <a-result
        :status="resultSuccess ? 'success' : 'error'"
        :title="resultSuccess ? '开闸成功' : '开闸失败'"
        :sub-title="resultMessage"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { ThunderboltOutlined } from '@ant-design/icons-vue'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'
import { getParkingLanes, type ParkingLaneVO } from '@/api/parking-lane'
import { openRemoteGate, type CommandResult } from '@/api/remote-gate'

/** 表单数据 */
const form = ref({
  parkingLotId: undefined as number | undefined,
  laneId: undefined as number | undefined,
  reason: '',
})

// ========== 车场 ==========
const parkingLotOptions = ref<{ label: string; value: number }[]>([])
const lotLoading = ref(false)

async function loadParkingLots() {
  lotLoading.value = true
  try {
    const res = await getParkingLots({ page: 1, size: 100, status: 1 })
    parkingLotOptions.value = (res.records || []).map((lot: ParkingLotVO) => ({
      label: lot.name,
      value: lot.id,
    }))
  } catch {
    // 统一拦截器已处理
  } finally {
    lotLoading.value = false
  }
}

// ========== 通道 ==========
const laneOptions = ref<{ label: string; value: number }[]>([])
const laneLoading = ref(false)

async function loadLanes(lotId: number) {
  laneLoading.value = true
  laneOptions.value = []
  try {
    const res = await getParkingLanes({
      lotId,
      type: 2, // 仅出口通道
      status: 1, // 仅启用
      page: 1,
      size: 100,
    })
    laneOptions.value = (res.records || []).map((lane: ParkingLaneVO) => ({
      label: `${lane.name}${lane.laneNo ? `（${lane.laneNo}）` : ''}`,
      value: lane.id,
    }))
  } catch {
    // 统一拦截器已处理
  } finally {
    laneLoading.value = false
  }
}

function handleLotChange(value: any) {
  const lotId = value !== undefined ? Number(value) : undefined
  form.value.laneId = undefined
  if (lotId) {
    loadLanes(lotId)
  }
}

// ========== 提交 ==========
const submitting = ref(false)
const resultModalOpen = ref(false)
const resultSuccess = ref(false)
const resultMessage = ref('')

const canSubmit = computed(() => {
  return form.value.parkingLotId && form.value.laneId && form.value.reason.trim().length > 0
})

async function handleOpenGate() {
  if (!canSubmit.value) {
    message.warning('请完整填写开闸信息')
    return
  }

  submitting.value = true
  resultModalOpen.value = false

  try {
    const result: CommandResult = await openRemoteGate({
      parkingLotId: form.value.parkingLotId!,
      laneId: form.value.laneId!,
      reason: form.value.reason.trim(),
    })
    resultSuccess.value = result.success
    resultMessage.value = result.success
      ? '道闸已成功开启，岗亭端将收到弹窗通知。'
      : `开闸失败: ${result.message || '未知错误'}`
    resultModalOpen.value = true
    if (result.success) {
      message.success(`开闸成功: ${result.message || ''}`)
    } else {
      message.error(`开闸失败: ${result.message || '未知错误'}`)
    }
  } catch (e: any) {
    resultSuccess.value = false
    resultMessage.value = e?.message || '开闸请求失败，请稍后重试'
    resultModalOpen.value = true
    // 统一拦截器已显示错误消息
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadParkingLots()
})
</script>

<style scoped>
.remote-gate-page {
  padding: 0;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
}

.page-desc {
  color: #888;
  font-size: 14px;
}

.gate-card {
  max-width: 600px;
  border-radius: 8px;
}
</style>
