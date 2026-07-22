<template>
  <a-modal
    :open="open"
    title="手动抓拍（模拟识别 · 测试）"
    :confirm-loading="submitting"
    :mask-closable="false"
    ok-text="触发识别"
    width="440px"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-alert
      type="info"
      show-icon
      message="走真实识别业务管线：事件落库 + 入/出场处理 + 余位刷新 + 自动开闸 + WS 推送"
      style="margin-bottom: 16px"
    />

    <a-form layout="vertical">
      <a-form-item v-if="cameraOptions.length > 0" label="抓拍相机">
        <a-select v-model:value="selectedDeviceId" :options="cameraOptions" />
      </a-form-item>
      <a-alert
        v-else
        type="warning"
        show-icon
        message="该车道未绑定相机，无法触发识别"
        style="margin-bottom: 16px"
      />

      <a-form-item label="车牌号" required>
        <a-input
          v-model:value="plateNumber"
          placeholder="请输入车牌号"
          :maxlength="8"
          style="text-transform: uppercase"
        >
          <template #addonAfter>
            <a @click="regeneratePlate">随机</a>
          </template>
        </a-input>
      </a-form-item>

      <a-form-item label="识别方向">
        <a-radio-group v-model:value="direction" option-type="button" button-style="solid">
          <a-radio-button value="ENTRY">入场识别</a-radio-button>
          <a-radio-button value="EXIT">出场识别</a-radio-button>
        </a-radio-group>
        <div style="margin-top: 6px; color: #9ca3af; font-size: 12px;">
          入场：生成在场记录并刷新余位；出场：进入收费/放行流程
        </div>
      </a-form-item>

      <a-form-item label="抓拍图 URL（可选）">
        <a-input
          v-model:value="imagePath"
          placeholder="留空则事件列表显示「无抓拍图」占位"
          allow-clear
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { triggerMockRecognition } from '@/api/mock'
import type { LaneCamera } from '@/api/monitor-types'

/**
 * 手动抓拍（模拟识别）弹窗。
 * 仅用于本地/测试环境联调：调用 /api/v1/internal/mock/recognition-event，
 * 后端仅在 local/test Profile 下注册该接口，生产环境不可用。
 */
const props = defineProps<{
  open: boolean
  /** 车道 ID（展示用） */
  laneId: number | null
  /** 车道名称 */
  laneName?: string
  /** 车道方向 */
  laneDirection?: string
  /** 车道兜底设备（无多相机配置时） */
  deviceId?: number
  /** 车道相机列表 */
  cameras?: LaneCamera[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: []
}>()

const submitting = ref(false)
const selectedDeviceId = ref<number>()
const plateNumber = ref('')
const direction = ref<'ENTRY' | 'EXIT'>('ENTRY')
const imagePath = ref('')

const PLATE_PROVINCES = ['京', '沪', '粤', '浙', '苏', '川', '鄂', '湘', '鲁', '闽']
const PLATE_LETTERS = 'ABCDEFGHJKLMNPQRSTUVWXYZ'
const PLATE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ0123456789'

function randomChar(pool: string): string {
  return pool[Math.floor(Math.random() * pool.length)]
}

/** 生成随机蓝牌车牌，如 粤B7K3P9 */
function generatePlate(): string {
  const province = PLATE_PROVINCES[Math.floor(Math.random() * PLATE_PROVINCES.length)]
  let plate = province + randomChar(PLATE_LETTERS)
  for (let i = 0; i < 5; i++) plate += randomChar(PLATE_CHARS)
  return plate
}

function regeneratePlate() {
  plateNumber.value = generatePlate()
}

const cameraOptions = computed(() => {
  const cams = props.cameras ?? []
  if (cams.length > 0) {
    return cams.map((c) => ({
      value: c.deviceId,
      label: `${c.name || `相机${c.deviceId}`}（${c.role === 'PRIMARY' ? '主' : '备'}·${c.direction === 'EXIT' ? '出' : '入'}${c.online ? '' : '·离线'}）`,
    }))
  }
  return props.deviceId != null
    ? [{ value: props.deviceId, label: `车道相机（设备 ${props.deviceId}）` }]
    : []
})

/** 弹窗打开时按车道/相机信息初始化默认值 */
watch(
  () => props.open,
  (open) => {
    if (!open) return
    const cams = props.cameras ?? []
    const active = cams.find((c) => c.isActive) ?? cams.find((c) => c.role === 'PRIMARY') ?? cams[0]
    selectedDeviceId.value = active?.deviceId ?? props.deviceId
    const camDir = active?.direction
    direction.value =
      camDir === 'EXIT' || camDir === 'ENTRY'
        ? camDir
        : props.laneDirection === 'EXIT'
          ? 'EXIT'
          : 'ENTRY'
    imagePath.value = ''
    if (!plateNumber.value) regeneratePlate()
  },
)

async function handleSubmit() {
  if (selectedDeviceId.value == null) {
    message.error('该车道未绑定相机设备')
    return
  }
  const plate = plateNumber.value.trim().toUpperCase()
  if (!plate) {
    message.error('请输入车牌号')
    return
  }
  submitting.value = true
  try {
    await triggerMockRecognition({
      deviceId: selectedDeviceId.value,
      plateNumber: plate,
      direction: direction.value,
      confidence: 99,
      imagePath: imagePath.value.trim() || undefined,
    })
    message.success(
      `已触发${direction.value === 'ENTRY' ? '入场' : '出场'}识别：${plate}，请关注事件列表与余位变化`,
    )
    emit('success')
    // 测试场景常需连续触发：保留弹窗并自动换下一张随机车牌
    regeneratePlate()
  } catch {
    // request 拦截器已统一提示错误
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  emit('update:open', false)
}
</script>
