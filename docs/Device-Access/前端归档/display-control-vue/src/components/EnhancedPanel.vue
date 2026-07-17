<script setup lang="ts">
import { ref } from 'vue'
import { useApi } from '../composables/useApi'

const props = defineProps<{
  deviceId: string
  apiBase: string
  getHeaders: () => Record<string, string>
}>()

const { apiPost } = useApi(props)

const content = ref('欢迎光临')
const line1Content = ref('欢迎光临')
const line2Content = ref('')
const direction = ref('HORIZONTAL')
const font = ref('SONG_16')
const voiceId = ref('')
const voiceVariable = ref('')
const loading = ref(false)
const result = ref<{ message: string; type: 'success' | 'error' | 'info' } | null>(null)

const selectedColor = ref([255, 255, 255, 0])
const line1Color = ref([255, 0, 0, 0])
const line2Color = ref([0, 255, 0, 0])

function isSameColor(a: number[], b: number[]): boolean {
  return a.length === b.length && a.every((v, i) => v === b[i])
}
const perLineColor = ref(false)

const colors = [
  { label: '红色', value: [255, 0, 0, 0], bg: '#ff0000' },
  { label: '绿色', value: [0, 255, 0, 0], bg: '#00ff00' },
  { label: '黄色', value: [255, 255, 0, 0], bg: '#ffff00' },
  { label: '黑色', value: [0, 0, 0, 0], bg: '#000000' },
]

// DC199 双色屏硬件限制：第一行只有红色LED，第二行只有绿色LED
const line1Colors = [
  { label: '红色', value: [255, 0, 0, 0], bg: '#ff0000' },
  { label: '黑色', value: [0, 0, 0, 0], bg: '#000000' },
]

const line2Colors = [
  { label: '绿色', value: [0, 255, 0, 0], bg: '#00ff00' },
  { label: '黑色', value: [0, 0, 0, 0], bg: '#000000' },
]

const fonts = [
  { label: '宋体 16x16（默认）', value: 'SONG_16' },
  { label: 'ASCII 8x16', value: 'ASCII_8' },
  { label: 'ASCII 10x20', value: 'ASCII_10' },
  { label: 'ASCII 13x26', value: 'ASCII_13' },
  { label: '宋体 24x24', value: 'SONG_24' },
  { label: '宋体 32x32', value: 'SONG_32' },
  { label: '宋体 48x48', value: 'SONG_48' },
  { label: '宋体 64x64', value: 'SONG_64' },
]

async function displayTextEnhanced() {
  let displayContent = content.value
  if (perLineColor.value) {
    if (!line1Content.value) {
      result.value = { message: '请输入第一行内容', type: 'error' }
      return
    }
    displayContent = line1Content.value + (line2Content.value ? '\n' + line2Content.value : '')
  } else if (!content.value) {
    result.value = { message: '请输入显示内容', type: 'error' }
    return
  }
  const body: Record<string, any> = {
    content: displayContent,
    direction: direction.value,
    font: font.value,
  }
  if (perLineColor.value) {
    body.color = [line1Color.value, line2Color.value]
  } else {
    body.color = [selectedColor.value]
  }
  if (voiceId.value) body.voiceId = parseInt(voiceId.value)
  if (voiceVariable.value) body.voiceVariable = voiceVariable.value

  loading.value = true
  result.value = null
  const res = await apiPost('/display/text/enhanced', body)
  loading.value = false
  result.value = res.success
    ? { message: '操作成功: ' + (res.message || ''), type: 'success' }
    : { message: '操作失败: ' + (res.message || '未知错误'), type: 'error' }
}
</script>

<template>
  <div class="panel">
    <h2><span class="icon" style="background:#e3f2fd">E</span> 增强版显示 <span class="tag tag-v0-4">v0.4</span></h2>
    <p style="font-size:12px;color:#999;margin-bottom:12px">支持字体、颜色、语音同步的实时文字显示</p>
    <div v-if="!perLineColor" class="form-group">
      <label>显示内容</label>
      <textarea v-model="content" placeholder="请输入显示内容，用换行分隔多行"></textarea>
    </div>
    <div v-else>
      <div class="form-group">
        <label>第一行内容</label>
        <input type="text" v-model="line1Content" placeholder="第一行文字">
      </div>
      <div class="form-group">
        <label>第二行内容</label>
        <input type="text" v-model="line2Content" placeholder="第二行文字（可选）">
      </div>
    </div>
    <div class="row-2">
      <div class="form-group">
        <label>字体</label>
        <select v-model="font">
          <option v-for="f in fonts" :key="f.value" :value="f.value">{{ f.label }}</option>
        </select>
      </div>
      <div class="form-group">
        <label>布局方向</label>
        <select v-model="direction">
          <option value="HORIZONTAL">横向排列</option>
          <option value="VERTICAL">竖向排列（每字一行）</option>
        </select>
      </div>
    </div>
    <div class="form-group">
      <label>
        <input type="checkbox" v-model="perLineColor" style="margin-right:6px">
        每行独立颜色
      </label>
    </div>
    <div v-if="!perLineColor" class="form-group">
      <label>文字颜色</label>
      <div class="color-picker">
        <div
          v-for="c in colors"
          :key="c.label"
          class="color-option"
          :class="{ active: selectedColor.toString() === c.value.toString() }"
          :style="{ background: c.bg, border: '3px solid transparent' }"
          :title="c.label"
          @click="selectedColor = c.value"
        ></div>
      </div>
      <p class="color-note">DC199 屏卡支持：红、绿、黄、黑</p>
    </div>
    <div v-else class="row-2">
      <div class="form-group">
        <label>第一行颜色 <span style="font-size:11px;color:#999">（仅红/黑）</span></label>
        <div class="color-picker">
          <div
            v-for="c in line1Colors"
            :key="'l1-' + c.label"
            class="color-option"
            :class="{ active: isSameColor(line1Color, c.value) }"
            :style="{ background: c.bg, border: '3px solid transparent' }"
            :title="c.label"
            @click="line1Color = c.value"
          ></div>
        </div>
      </div>
      <div class="form-group">
        <label>第二行颜色 <span style="font-size:11px;color:#999">（仅绿/黑）</span></label>
        <div class="color-picker">
          <div
            v-for="c in line2Colors"
            :key="'l2-' + c.label"
            class="color-option"
            :class="{ active: isSameColor(line2Color, c.value) }"
            :style="{ background: c.bg, border: '3px solid transparent' }"
            :title="c.label"
            @click="line2Color = c.value"
          ></div>
        </div>
      </div>
    </div>
    <div class="row-2">
      <div class="form-group">
        <label>语音ID（可选）</label>
        <input type="number" v-model="voiceId" placeholder="不填则不播放语音" min="0">
      </div>
      <div class="form-group">
        <label>语音变量（可选）</label>
        <input type="text" v-model="voiceVariable" placeholder="如金额、车牌号">
      </div>
    </div>
    <div class="button-group">
      <button class="btn-primary" @click="displayTextEnhanced" :disabled="loading">增强显示</button>
    </div>
    <div v-if="loading" class="loading"><span class="loading-spinner"></span>发送中...</div>
    <div v-if="result" :class="['result', result.type]">{{ result.message }}</div>
  </div>
</template>

<style scoped>
.panel {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  transition: transform 0.3s, box-shadow 0.3s;
}
.panel:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(0,0,0,0.12);
}
.panel h2 {
  font-size: 18px;
  color: #333;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel h2 .icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}
.form-group {
  margin-bottom: 12px;
}
.form-group label {
  display: block;
  margin-bottom: 6px;
  color: #555;
  font-weight: 500;
  font-size: 13px;
}
textarea, select, input[type="number"], input[type="text"] {
  width: 100%;
  padding: 10px 14px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  font-size: 14px;
  transition: all 0.3s;
  background: #fafafa;
}
textarea:focus, select:focus, input:focus {
  outline: none;
  border-color: #667eea;
  background: white;
}
textarea {
  resize: vertical;
  min-height: 80px;
}
.row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.color-picker {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.color-option {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  cursor: pointer;
  border: 3px solid transparent;
  transition: all 0.3s;
}
.color-option:hover, .color-option.active {
  border-color: #667eea;
  transform: scale(1.1);
}
.color-note {
  font-size: 11px;
  color: #999;
  margin-top: 6px;
}
.button-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}
button {
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
button:hover:not(:disabled) {
  transform: translateY(-1px);
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}
.btn-primary:hover:not(:disabled) {
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}
.result {
  margin-top: 12px;
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 13px;
  word-break: break-all;
}
.result.success {
  background: #e8f5e9;
  color: #2e7d32;
  border: 1px solid #a5d6a7;
}
.result.error {
  background: #ffebee;
  color: #c62828;
  border: 1px solid #ef9a9a;
}
.loading {
  text-align: center;
  margin-top: 12px;
}
.loading-spinner {
  border: 2px solid #f3f3f3;
  border-top: 2px solid #667eea;
  border-radius: 50%;
  width: 20px;
  height: 20px;
  animation: spin 1s linear infinite;
  display: inline-block;
  vertical-align: middle;
  margin-right: 8px;
}
@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  margin-left: 8px;
}
.tag-v0-4 { background: #fff3e0; color: #e65100; }
@media (max-width: 768px) {
  .row-2 {
    grid-template-columns: 1fr;
  }
}
</style>
