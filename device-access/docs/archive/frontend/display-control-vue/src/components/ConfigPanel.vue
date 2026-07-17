<script setup lang="ts">
import { ref } from 'vue'
import { useApi } from '../composables/useApi'

const props = defineProps<{
  deviceId: string
  apiBase: string
  getHeaders: () => Record<string, string>
}>()

const { apiPost } = useApi(props)

const volume = ref(50)
const brightness = ref(80)
const loading = ref(false)
const result = ref<{ message: string; type: 'success' | 'error' | 'info' } | null>(null)

async function configDisplay(configType: string, intValue?: number) {
  const body: Record<string, any> = { configType }
  if (configType === 'VOLUME') {
    body.intValue = volume.value
  } else if (configType === 'BRIGHTNESS') {
    body.intValue = brightness.value
  } else if (configType === 'DIRECTION') {
    body.intValue = intValue
  }
  loading.value = true
  result.value = null
  const res = await apiPost('/display/config', body)
  loading.value = false
  result.value = res.success
    ? { message: '操作成功: ' + (res.message || ''), type: 'success' }
    : { message: '操作失败: ' + (res.message || '未知错误'), type: 'error' }
}
</script>

<template>
  <div class="panel">
    <h2><span class="icon" style="background:#fff3e0">C</span> 显示屏配置 <span class="tag tag-v0-4">v0.4</span></h2>

    <div class="slider-group">
      <div class="slider-header">
        <label>音量控制 (0x0D)</label>
        <span class="slider-value">{{ volume }}%</span>
      </div>
      <input type="range" v-model="volume" min="0" max="100">
      <div class="button-group" style="margin-top:8px">
        <button class="btn-primary" @click="configDisplay('VOLUME')" :disabled="loading">设置音量</button>
      </div>
    </div>

    <div class="divider"></div>

    <div class="slider-group">
      <div class="slider-header">
        <label>亮度控制 (0x0C)</label>
        <span class="slider-value">{{ brightness }}%</span>
      </div>
      <input type="range" v-model="brightness" min="10" max="100">
      <div class="button-group" style="margin-top:8px">
        <button class="btn-primary" @click="configDisplay('BRIGHTNESS')" :disabled="loading">设置亮度</button>
      </div>
    </div>

    <div class="divider"></div>

    <div class="form-group">
      <label>显示方向 (0x19)</label>
      <div class="button-group">
        <button class="btn-secondary" @click="configDisplay('DIRECTION', 0)" :disabled="loading">正常方向</button>
        <button class="btn-secondary" @click="configDisplay('DIRECTION', 1)" :disabled="loading">旋转180度</button>
      </div>
    </div>

    <div class="divider"></div>

    <div class="form-group">
      <label>时间同步 (0x05)</label>
      <p style="font-size:12px;color:#999;margin-bottom:8px">自动同步服务器当前时间到屏卡 RTC</p>
      <div class="button-group">
        <button class="btn-primary" @click="configDisplay('TIME_SYNC')" :disabled="loading">同步时间</button>
      </div>
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
.slider-group {
  margin-bottom: 16px;
}
.slider-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.slider-value {
  background: #667eea;
  color: white;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  min-width: 40px;
  text-align: center;
}
input[type="range"] {
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: #e0e0e0;
  outline: none;
  -webkit-appearance: none;
}
input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #667eea;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(102, 126, 234, 0.4);
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
.btn-secondary {
  background: #f5f5f5;
  color: #555;
  border: 1px solid #ddd;
}
.btn-secondary:hover:not(:disabled) {
  background: #e0e0e0;
}
.divider {
  height: 1px;
  background: #e8e8e8;
  margin: 16px 0;
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
</style>
