<template>
  <a-modal
    :open="open"
    :title="isEntry ? '人工入场' : '人工放行'"
    :confirm-loading="releasing"
    :mask-closable="false"
    :width="640"
    :ok-text="isEntry ? '确认入场' : '确认放行'"
    cancel-text="取消"
    @ok="handleConfirm"
    @cancel="handleCancel"
  >
    <div style="display:flex;gap:16px">
      <!-- 左侧：输入区 -->
      <div style="flex:1;min-width:0">
        <a-form layout="vertical">
          <a-form-item label="车牌号">
            <a-input
              ref="plateInputRef"
              v-model:value="editablePlate"
              :placeholder="isEntry ? '请输入车牌号' : '请输入车牌号（无牌车可留空）'"
              :maxlength="8"
              style="text-transform: uppercase; font-size: 18px; font-weight: 600; letter-spacing: 2px; font-family: monospace"
              :disabled="releasing"
              allow-clear
              @change="onPlateChange"
              @focus="showKeyboard = true"
            >
              <template #suffix>
                <a-button size="small" type="text" @click.stop="showKeyboard = !showKeyboard" style="font-size:18px;padding:0 4px">⌨</a-button>
              </template>
            </a-input>
            <!-- 小键盘 -->
            <div v-if="showKeyboard" class="plate-keyboard" @mousedown.prevent>
              <div class="kb-row">
                <span v-for="c in kbProvinces" :key="c" class="kb-key kb-province" @click="appendChar(c)">{{ c }}</span>
              </div>
              <div class="kb-row">
                <span v-for="c in kbLetters" :key="c" class="kb-key kb-letter" @click="appendChar(c)">{{ c }}</span>
              </div>
              <div class="kb-row">
                <span v-for="c in kbNumbers" :key="c" class="kb-key kb-number" @click="appendChar(c)">{{ c }}</span>
              </div>
              <div class="kb-row kb-actions">
                <span class="kb-key kb-delete" @click="deleteChar">删除</span>
                <span class="kb-key kb-clear" @click="clearPlate">清空</span>
                <span class="kb-key" style="min-width:40px" @click="showKeyboard = false">收起</span>
              </div>
            </div>
          </a-form-item>

          <!-- 出口：计费选项 -->
          <template v-if="!isEntry">
            <a-form-item label="收费方式">
              <a-radio-group v-model:value="formState.isCharge" :disabled="releasing" button-style="solid" size="small">
                <a-radio-button :value="false">免费放行</a-radio-button>
                <a-radio-button :value="true">计费放行</a-radio-button>
              </a-radio-group>
            </a-form-item>
            <a-form-item v-if="formState.isCharge" label="收费金额">
              <a-input-number v-model:value="formState.amountYuan" :min="0" :precision="2" :disabled="releasing" placeholder="请输入收费金额" style="width: 100%">
                <template #addonAfter>元</template>
              </a-input-number>
            </a-form-item>
          </template>

          <a-form-item label="原因" required>
            <a-textarea v-model:value="formState.remark" placeholder="必填：请填写原因" :rows="2" :maxlength="200" :disabled="releasing" />
          </a-form-item>
        </a-form>
      </div>

      <!-- 右侧：信息展示 -->
      <div style="width:200px;flex-shrink:0">
        <PlateTag v-if="editablePlate" :plate-number="editablePlate" :plate-color="formState.plateColor" size="large" style="margin-bottom:12px" />
        <a-tag v-else style="margin-bottom:12px">未输入车牌</a-tag>

        <div style="margin-bottom:12px">
          <div style="font-size:12px;color:#999;margin-bottom:2px">车牌颜色</div>
          <a-tag :color="plateColorHex">{{ plateColorLabel }}</a-tag>
        </div>

        <div style="margin-bottom:12px">
          <div style="font-size:12px;color:#999;margin-bottom:2px">车辆类型</div>
          <a-tag v-if="autoVehicleType" :color="autoTypeColor">{{ autoVehicleType }}</a-tag>
          <a-tag v-else color="orange">临时车</a-tag>
          <div v-if="autoVehicleType" style="font-size:11px;color:#52c41a;margin-top:2px">已自动匹配</div>
          <div v-else style="font-size:11px;color:#999;margin-top:2px">未匹配，默认临时车</div>
        </div>

        <div>
          <div style="font-size:12px;color:#999;margin-bottom:4px">抓拍照片</div>
          <div v-if="formState.entryImage" style="margin-bottom:8px">
            <a-image :src="formState.entryImage" :width="200" style="border-radius:4px;border:1px solid #d1d5db" />
          </div>
          <div v-else style="width:200px;height:120px;background:#f5f5f5;border:1px dashed #d9d9d9;border-radius:4px;display:flex;align-items:center;justify-content:center;margin-bottom:8px">
            <span style="color:#ccc;font-size:12px">未抓拍</span>
          </div>
          <a-button :loading="capturing" :disabled="releasing" @click="handleCapture" size="small" block>
            <CameraOutlined /> {{ formState.entryImage ? '重新抓拍' : '抓拍' }}
          </a-button>
        </div>
      </div>
    </div>

    <template v-if="releaseResult">
      <a-result
        :status="releaseResult.success ? 'success' : 'error'"
        :title="releaseResult.success ? '开闸成功' : '开闸失败'"
        :sub-title="releaseResult.message"
      >
        <template #extra>
          <a-space>
            <a-button v-if="!releaseResult.gateOpened" type="primary" danger @click="handleRetry">重新开闸</a-button>
            <a-button @click="handleCancel">关闭</a-button>
          </a-space>
        </template>
      </a-result>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { CameraOutlined } from '@ant-design/icons-vue'
import { manualOpenGate, captureImage } from '@/api/charge'
import { queryVehicle } from '@/api/vehicle'
import PlateTag from '@/components/PlateTag.vue'
import type { CaptureImageResult } from '@/api/monitor-types'

const props = defineProps<{
  open: boolean
  laneId: number
  plateNumber: string
  direction?: number
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: [result: { success: boolean; message: string; gateOpened: boolean | null }]
}>()

const isEntry = computed(() => props.direction === 1)
const plateInputRef = ref<any>(null)

// ========== 小键盘 ==========
const showKeyboard = ref(false)
const kbProvinces = '京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁'.split('')
const kbLetters = 'ABCDEFGHJKLMNPQRSTUVWXYZ'.split('')  // no I,O (易混淆)
const kbNumbers = '0123456789'.split('')

function appendChar(c: string) {
  if (editablePlate.value.length >= 8) return
  editablePlate.value += c
  onPlateChange()
}
function deleteChar() {
  editablePlate.value = editablePlate.value.slice(0, -1)
  onPlateChange()
}
function clearPlate() {
  editablePlate.value = ''
  onPlateChange()
}

// ========== 表单 ==========
const formState = reactive({
  remark: '',
  isCharge: false,
  amountYuan: 0,
  entryImage: '',
  plateColor: 'BLUE' as string,
  vehicleType: 'TEMP' as string,
})

const editablePlate = ref('')
const releasing = ref(false)
const capturing = ref(false)
const captureResult = ref<CaptureImageResult | null>(null)
const releaseResult = ref<{ success: boolean; message: string; gateOpened: boolean | null } | null>(null)
const autoVehicleType = ref('')

// ========== 自动识别 ==========

function detectPlateColor(plate: string): string {
  if (!plate || plate.length < 2) return 'BLUE'
  const p = plate.toUpperCase()
  if (/[军警]/.test(p)) return 'WHITE'
  if (/[使领]/.test(p)) return 'BLACK'
  if (p.length >= 8) return 'GREEN'
  return 'BLUE'
}

function onPlateChange() {
  console.log('onPlateChange:', editablePlate.value, 'len:', editablePlate.value.length)
  formState.plateColor = detectPlateColor(editablePlate.value)
  autoVehicleType.value = ''
  // 输入超过6位开始查询车辆类型
  if (editablePlate.value.length >= 6) {
    console.log('trigger debounce query')
    debouncedQueryType(editablePlate.value)
  }
}

let queryTimer: ReturnType<typeof setTimeout> | null = null
function debouncedQueryType(plate: string) {
  console.log('debouncedQueryType:', plate)
  if (queryTimer) clearTimeout(queryTimer)
  queryTimer = setTimeout(() => queryVehicleType(plate), 500)
}

async function queryVehicleType(plate: string) {
  if (!plate || plate.length < 6) {
    // 输入不完整时恢复默认临时车
    if (autoVehicleType.value) {
      formState.vehicleType = 'TEMP'
      autoVehicleType.value = ''
    }
    return
  }
  try {
    const v = await queryVehicle(plate)
    console.log('queryVehicle result:', plate, v)
    if (v && v.vehicleType) {
      const vt = v.vehicleType.toUpperCase()
      // 只有明确的类型才自动匹配
      const known = ['MONTHLY', 'VIP', 'FREE', 'PREPAID', 'FIXED', 'BLACKLIST']
      if (known.includes(vt)) {
        formState.vehicleType = vt
        const labels: Record<string, string> = {
          MONTHLY: '月租车', VIP: 'VIP车', FREE: '免费车', PREPAID: '储值车', FIXED: '固定车', BLACKLIST: '黑名单'
        }
        autoVehicleType.value = labels[vt] || vt
        return
      }
    }
    // 没匹配到, 默认临时车
    formState.vehicleType = 'TEMP'
    autoVehicleType.value = ''
  } catch (e) {
    console.warn('查询车辆类型失败:', e)
    formState.vehicleType = 'TEMP'
    autoVehicleType.value = ''
  }
}

const plateColorLabel = computed(() => {
  const m: Record<string, string> = { BLUE: '蓝牌', GREEN: '绿牌', YELLOW: '黄牌', BLACK: '黑牌', WHITE: '白牌' }
  return m[formState.plateColor] || '蓝牌'
})
const plateColorHex = computed(() => {
  const m: Record<string, string> = { BLUE: 'blue', GREEN: 'green', YELLOW: 'gold', BLACK: '#1f2937', WHITE: 'default' }
  return m[formState.plateColor] || 'blue'
})

const autoTypeColor = computed(() => {
  const vt = formState.vehicleType.toUpperCase()
  if (vt === 'VIP') return 'gold'
  if (vt === 'MONTHLY') return 'green'
  if (vt === 'FREE') return 'green'
  if (vt === 'PREPAID') return 'cyan'
  if (vt === 'FIXED') return 'blue'
  if (vt === 'BLACKLIST') return 'red'
  return 'orange'
})

// ========== 生命周期 ==========

watch(() => props.open, (newVal) => {
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
    autoVehicleType.value = ''
    showKeyboard.value = false
    if (editablePlate.value) {
      formState.plateColor = detectPlateColor(editablePlate.value)
      // 弹窗打开时立即查询车辆类型
      debouncedQueryType(editablePlate.value)
    }
    handleCapture()
  }
})

// ========== 操作 ==========

async function handleCapture() {
  capturing.value = true; captureResult.value = null
  try {
    const result = await captureImage(props.laneId, props.direction ?? 1)
    captureResult.value = { success: true, message: 'OK' }
    if (result.imageUrl) formState.entryImage = result.imageUrl
  } catch (e: any) {
    captureResult.value = { success: false, message: e?.message || '抓拍失败' }
  } finally { capturing.value = false }
}

async function handleConfirm() {
  releasing.value = true; releaseResult.value = null
  if (!formState.remark.trim()) { message.warning('请填写原因'); releasing.value = false; return }
  if (!isEntry.value && formState.isCharge && formState.amountYuan <= 0) {
    message.warning('计费放行请填写收费金额'); releasing.value = false; return
  }

  try {
    const feeCents = formState.isCharge ? Math.round(formState.amountYuan * 100) : 0
    const reasonText = formState.remark.trim()
    const plate = editablePlate.value.trim().toUpperCase()
    const result = await manualOpenGate(props.laneId, reasonText, {
      isCharge: formState.isCharge,
      feeCents,
      plateNumber: plate || undefined,
      entryImage: formState.entryImage || undefined,
      direction: props.direction,
      plateColor: formState.plateColor,
      vehicleType: formState.vehicleType,
    })

    const success = result.gateDeviceAck === true
    releaseResult.value = {
      success,
      message: success ? '开闸成功' : (result.gateResult || '开闸失败'),
      gateOpened: result.gateOpened ?? null,
    }
    if (success) {
      emit('success', { success: true, message: '开闸成功', gateOpened: true })
    }
  } catch (e: any) {
    releaseResult.value = { success: false, message: e?.message || '操作失败', gateOpened: false }
  } finally { releasing.value = false }
}

function handleRetry() { releaseResult.value = null; handleConfirm() }
function handleCancel() { if (!releasing.value) emit('update:open', false) }
</script>

<style scoped>
.plate-keyboard {
  margin-top: 8px;
  background: #f5f5f5;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 8px;
  user-select: none;
}
.kb-row {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.kb-key {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 36px;
  padding: 0 4px;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 15px;
  cursor: pointer;
  transition: all .15s;
}
.kb-key:hover { background: #e6f7ff; border-color: #1890ff; color: #1890ff; }
.kb-key:active { background: #bae7ff; }
.kb-province { font-size: 13px; min-width: 28px; padding: 0 2px; }
.kb-letter { font-weight: 600; }
.kb-number { color: #666; }
.kb-actions { justify-content: flex-end; margin-top: 4px; }
.kb-delete { background: #fff7e6; border-color: #ffa940; color: #d46b08; min-width: 60px; }
.kb-delete:hover { background: #ffe7ba; }
.kb-clear { background: #fff2f0; border-color: #ff7875; color: #cf1322; min-width: 60px; }
.kb-clear:hover { background: #ffd8d2; }
</style>
