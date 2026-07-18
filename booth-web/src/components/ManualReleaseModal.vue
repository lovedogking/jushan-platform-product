<template>
  <a-modal
    :open="open"
    :title="batch ? '批量人工放行' : '人工放行'"
    :confirm-loading="releasing"
    :mask-closable="false"
    :width="batch ? 560 : undefined"
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <!-- 单通道模式：显示放行车辆 + 是否计费 + 金额 + 原因 -->
    <template v-if="!batch">
      <a-form :model="formState" layout="vertical">
        <a-form-item label="放行车辆">
          <a-input :value="plateNumber" disabled />
        </a-form-item>

        <a-form-item label="是否计费">
          <a-switch v-model:checked="formState.isCharge" :disabled="releasing" />
          <span style="margin-left: 8px; color: #6b7280; font-size: 12px;">
            {{ formState.isCharge ? '计费放行' : '免费放行' }}
          </span>
        </a-form-item>

        <a-form-item v-if="formState.isCharge" label="计费金额（元）">
          <a-input-number
            v-model:value="formState.amountYuan"
            :min="0"
            :precision="2"
            :disabled="releasing"
            placeholder="请输入收费金额"
            style="width: 100%"
          >
            <template #addonAfter>元</template>
          </a-input-number>
        </a-form-item>

        <a-form-item label="放行原因" required>
          <a-textarea
            v-model:value="formState.remark"
            placeholder="必填：请填写放行原因"
            :rows="2"
            :maxlength="200"
            :disabled="releasing"
          />
        </a-form-item>
      </a-form>
    </template>

    <!-- 批量模式：多通道选择 -->
    <template v-else>
      <a-form :model="formState" layout="vertical">
        <a-form-item label="选择通道" required>
          <a-checkbox-group v-model:value="selectedLaneIds" :disabled="releasing">
            <a-row :gutter="[8, 8]">
              <a-col
                v-for="lane in batchLanes"
                :key="lane.id"
                :span="12"
              >
                <a-checkbox :value="lane.id" :disabled="!lane.hasDevice">
                  <span>{{ lane.name }}</span>
                  <a-tag
                    size="small"
                    :color="lane.direction === 'EXIT' ? 'orange' : 'blue'"
                    style="margin-left: 4px"
                  >
                    {{ lane.direction === 'ENTRY' ? '入口' : lane.direction === 'EXIT' ? '出口' : '混合' }}
                  </a-tag>
                  <a-tag v-if="!lane.hasDevice" size="small" color="default">无设备</a-tag>
                </a-checkbox>
              </a-col>
            </a-row>
          </a-checkbox-group>
        </a-form-item>

        <a-form-item label="备注">
          <a-textarea
            v-model:value="formState.remark"
            placeholder="选填：放行备注说明"
            :rows="2"
            :maxlength="200"
            :disabled="releasing"
          />
        </a-form-item>
      </a-form>
    </template>

    <!-- 开闸结果 -->
    <template v-if="releaseResult">
      <!-- 单通道结果 -->
      <a-result
        v-if="!batch"
        :status="releaseResult.success ? 'success' : 'error'"
        :title="releaseResult.success ? '开闸成功' : '开闸失败'"
        :sub-title="releaseResult.message"
      >
        <template #extra>
          <a-space>
            <a-button v-if="!releaseResult.gateOpened" type="primary" danger @click="handleRetry">
              重新开闸
            </a-button>
            <a-button @click="handleCancel">关闭</a-button>
          </a-space>
        </template>
      </a-result>

      <!-- 批量结果 -->
      <div v-else class="batch-result">
        <a-alert
          :type="batchResult.successCount > 0 && batchResult.failedCount === 0 ? 'success' : batchResult.failedCount > 0 ? 'warning' : 'error'"
          :message="`操作完成：成功 ${batchResult.successCount} / 失败 ${batchResult.failedCount}`"
          style="margin-bottom: 12px"
        />
        <a-table
          v-if="batchResult.success.length > 0"
          :data-source="batchResult.success"
          :columns="batchResultColumns"
          :pagination="false"
          size="small"
          row-key="deviceId"
        >
          <template #bodyCell="{ column }">
            <template v-if="column.key === 'status'">
              <a-tag color="success">成功</a-tag>
            </template>
          </template>
        </a-table>
        <a-table
          v-if="batchResult.failed.length > 0"
          :data-source="batchResult.failed"
          :columns="batchResultColumns"
          :pagination="false"
          size="small"
          row-key="deviceId"
          style="margin-top: 8px"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag color="error">失败</a-tag>
            </template>
            <template v-if="column.key === 'message'">
              <span class="text-danger">{{ record.message }}</span>
            </template>
          </template>
        </a-table>
        <div style="text-align: center; margin-top: 16px">
          <a-button @click="handleCancel">关闭</a-button>
        </div>
      </div>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { manualOpenGate, manualOpenGateBatch } from '@/api/charge'

/** 批量模式下的车道选项 */
export interface BatchLaneOption {
  id: number
  name: string
  direction: string
  deviceId?: number
  hasDevice: boolean
}

const props = defineProps<{
  open: boolean
  laneId: number
  plateNumber: string
  batch?: boolean
  batchLanes?: BatchLaneOption[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: [result: { success: boolean; message: string; gateOpened: boolean | null }]
}>()

const formState = reactive({
  remark: '',
  isCharge: false,
  amountYuan: 0,
})

const releasing = ref(false)
const releaseResult = ref<{ success: boolean; message: string; gateOpened: boolean | null } | null>(null)
const selectedLaneIds = ref<number[]>([])

// 批量结果
const batchResult = reactive({
  success: [] as { deviceId: number; success: boolean; message: string }[],
  failed: [] as { deviceId: number; success: boolean; message: string }[],
  successCount: 0,
  failedCount: 0,
})

const batchResultColumns = [
  { title: '设备 ID', dataIndex: 'deviceId', key: 'deviceId', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '消息', dataIndex: 'message', key: 'message' },
]

// 弹窗打开时重置状态
watch(
  () => props.open,
  (newVal) => {
    if (newVal) {
      formState.remark = ''
      formState.isCharge = false
      formState.amountYuan = 0
      releaseResult.value = null
      selectedLaneIds.value = []
      batchResult.success = []
      batchResult.failed = []
      batchResult.successCount = 0
      batchResult.failedCount = 0
    }
  },
)

/** 确认放行 */
async function handleConfirm() {

  if (props.batch && selectedLaneIds.value.length === 0) {
    message.warning('请选择至少一个通道')
    return
  }

  releasing.value = true
  releaseResult.value = null

  // 单通道模式：放行原因必填
  if (!props.batch && !formState.remark.trim()) {
    message.warning('请填写放行原因')
    releasing.value = false
    return
  }

  // 单通道计费模式：金额必填且 > 0
  if (!props.batch && formState.isCharge && formState.amountYuan <= 0) {
    message.warning('计费放行请填写收费金额')
    releasing.value = false
    return
  }

  if (props.batch) {
    // 批量模式
    const reasonText = getReasonText(formState.remark)

    // 将选中的 laneId 解析为 deviceIds
    const deviceIds: number[] = []
    for (const laneId of selectedLaneIds.value) {
      const lane = props.batchLanes?.find((l) => l.id === laneId)
      if (lane && lane.deviceId) {
        deviceIds.push(lane.deviceId)
      }
    }

    if (deviceIds.length === 0) {
      message.warning('所选通道无可控设备')
      releasing.value = false
      return
    }

    try {
      const result = await manualOpenGateBatch({
        deviceIds,
        reason: reasonText,
      })
      batchResult.success = result.success || []
      batchResult.failed = result.failed || []
      batchResult.successCount = result.successCount || 0
      batchResult.failedCount = result.failedCount || 0
      releaseResult.value = {
        success: result.failedCount === 0,
        message: `成功 ${result.successCount} / 失败 ${result.failedCount}`,
        gateOpened: result.failedCount === 0 ? true : null,
      }
    } catch (e: any) {
      batchResult.success = []
      batchResult.failed = [{ deviceId: 0, success: false, message: e?.message || '批量开闸请求失败' }]
      batchResult.successCount = 0
      batchResult.failedCount = 1
      releaseResult.value = { success: false, message: e?.message || '批量开闸请求失败', gateOpened: false }
    } finally {
      releasing.value = false
    }
  } else {
    // 单通道模式
    try {
      const feeCents = formState.isCharge
        ? Math.round(formState.amountYuan * 100)
        : 0
      const reasonText = getReasonText(formState.remark)
      const result = await manualOpenGate(
        props.laneId,
        reasonText,
        {
          isCharge: formState.isCharge,
          feeCents,
          plateNumber: props.plateNumber || undefined,
        },
      )

      const success = result.gateDeviceAck === true
      const resultMsg = success
        ? '开闸成功'
        : `开闸失败: ${result.gateResult || result.resultMessage || '未知错误'}`

      releaseResult.value = { success, message: resultMsg, gateOpened: success }
      emit('success', { success, message: resultMsg, gateOpened: success })
    } catch (e: any) {
      const errMsg = e?.message || '开闸请求失败'
      releaseResult.value = { success: false, message: errMsg, gateOpened: false }
      emit('success', { success: false, message: errMsg, gateOpened: false })
    } finally {
      releasing.value = false
    }
  }
}

/** 重试开闸 */
function handleRetry() {
  releaseResult.value = null
  handleConfirm()
}

/** 取消 */
function handleCancel() {
  emit('update:open', false)
}

/** 构建放行原因文本 */
function getReasonText(remark: string): string {
  return remark.trim() || '岗亭人工放行'
}
</script>

<style lang="scss" scoped>
.text-danger {
  color: #ff4d4f;
}

.batch-result {
  max-height: 400px;
  overflow-y: auto;
}
</style>
