<template>
  <a-modal
    :open="open"
    title="人工放行"
    :confirm-loading="releasing"
    :mask-closable="false"
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <a-form :model="formState" layout="vertical">
      <a-form-item label="放行车辆">
        <a-input
          v-model:value="editablePlate"
          placeholder="请输入车牌号（无牌车可留空）"
          :maxlength="8"
          style="text-transform: uppercase"
          :disabled="releasing"
          allow-clear
        />
      </a-form-item>

      <a-form-item label="车牌颜色">
        <a-radio-group v-model:value="formState.plateColor" :disabled="releasing">
          <a-radio-button value="BLUE">蓝牌</a-radio-button>
          <a-radio-button value="GREEN">绿牌</a-radio-button>
          <a-radio-button value="YELLOW">黄牌</a-radio-button>
          <a-radio-button value="BLACK">黑牌</a-radio-button>
          <a-radio-button value="WHITE">白牌</a-radio-button>
        </a-radio-group>
      </a-form-item>

      <a-form-item label="车辆类型">
        <a-radio-group v-model:value="formState.vehicleType" :disabled="releasing">
          <a-radio-button value="TEMP">临时车</a-radio-button>
          <a-radio-button value="MONTHLY">月租车</a-radio-button>
          <a-radio-button value="PREPAID">储值车</a-radio-button>
          <a-radio-button value="FREE">免费车</a-radio-button>
        </a-radio-group>
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

      <a-form-item label="入场抓拍">
        <a-space>
          <a-button
            :loading="capturing"
            :disabled="releasing"
            @click="handleCapture"
          >
            <template #icon>
              <CameraOutlined />
            </template>
            抓拍
          </a-button>
          <span v-if="captureResult?.success" style="color: #52c41a; font-size: 12px;">抓拍成功</span>
          <span v-else-if="captureResult" style="color: #ff4d4f; font-size: 12px;">{{ captureResult.message || '抓拍失败' }}</span>
        </a-space>
        <div v-if="formState.entryImage" style="margin-top: 8px;">
          <a-image
            :src="formState.entryImage"
            :width="240"
            :height="180"
            style="object-fit: cover; border-radius: 4px; border: 1px solid #d1d5db;"
            alt="抓拍预览"
          />
        </div>
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

    <!-- 开闸结果 -->
    <template v-if="releaseResult">
      <a-result
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
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CameraOutlined } from '@ant-design/icons-vue'
import { manualOpenGate, captureImage } from '@/api/charge'
import type { CaptureImageResult } from '@/api/monitor-types'

const props = defineProps<{
  open: boolean
  laneId: number
  plateNumber: string
  /** 识别方向：1=入口, 2=出口（双向车道用于选择正确相机抓拍） */
  direction?: number
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: [result: { success: boolean; message: string; gateOpened: boolean | null }]
}>()

const formState = reactive({
  remark: '',
  isCharge: false,
  amountYuan: 0,
  entryImage: '',
  plateColor: 'BLUE' as string,
  vehicleType: 'TEMP' as string,
})

/** 放行车辆（可编辑，弹窗打开时用 prop 初始化） */
const editablePlate = ref('')

const releasing = ref(false)
const capturing = ref(false)
const captureResult = ref<CaptureImageResult | null>(null)
const releaseResult = ref<{ success: boolean; message: string; gateOpened: boolean | null } | null>(null)

// 弹窗打开时重置状态并自动抓拍
watch(
  () => props.open,
  (newVal) => {
    if (newVal) {
      formState.remark = ''
      formState.isCharge = false
      formState.amountYuan = 0
      formState.entryImage = ''
      formState.plateColor = 'BLUE'
      formState.vehicleType = 'TEMP'
      editablePlate.value = props.plateNumber || ''
      captureResult.value = null
      releaseResult.value = null
      // 自动触发抓拍
      handleCapture()
    }
  },
)

/** 确认放行 */
async function handleConfirm() {
  releasing.value = true
  releaseResult.value = null

  // 放行原因必填
  if (!formState.remark.trim()) {
    message.warning('请填写放行原因')
    releasing.value = false
    return
  }

  // 计费模式：金额必填且 > 0
  if (formState.isCharge && formState.amountYuan <= 0) {
    message.warning('计费放行请填写收费金额')
    releasing.value = false
    return
  }

  try {
    const feeCents = formState.isCharge
      ? Math.round(formState.amountYuan * 100)
      : 0
    const reasonText = formState.remark.trim() || '岗亭人工放行'
    const plate = editablePlate.value.trim().toUpperCase()
    const result = await manualOpenGate(
      props.laneId,
      reasonText,
      {
        isCharge: formState.isCharge,
        feeCents,
        plateNumber: plate || undefined,
        entryImage: formState.entryImage || undefined,
        direction: props.direction,
        plateColor: formState.plateColor || undefined,
        vehicleType: formState.vehicleType || undefined,
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

/** 手动抓拍 */
async function handleCapture() {
  capturing.value = true
  captureResult.value = null
  try {
    const result = await captureImage(props.laneId, props.direction)
    captureResult.value = result
    if (result.success && result.imageUrl) {
      formState.entryImage = result.imageUrl
      message.success('抓拍成功')
    } else {
      message.warning(result.message || '抓拍失败')
    }
  } catch (e: any) {
    const errMsg = e?.message || '抓拍请求失败'
    captureResult.value = { success: false, message: errMsg }
    message.error(errMsg)
  } finally {
    capturing.value = false
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
</script>
