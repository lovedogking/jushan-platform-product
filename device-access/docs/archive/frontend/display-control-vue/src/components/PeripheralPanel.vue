<script setup lang="ts">
import { ref } from 'vue'
import { useApi } from '../composables/useApi'

const props = defineProps<{
  deviceId: string
  apiBase: string
  getHeaders: () => Record<string, string>
}>()

const { apiPost } = useApi(props)

const displayMode = ref('TWO_LINE')
const loading = ref(false)
const result = ref<{ message: string; type: 'success' | 'error' | 'info' } | null>(null)

async function controlPeripheral(action: string) {
  loading.value = true
  result.value = null
  const res = await apiPost('/peripheral/display', { action })
  loading.value = false
  result.value = res.success
    ? { message: '操作成功: ' + (res.message || ''), type: 'success' }
    : { message: '操作失败: ' + (res.message || '未知错误'), type: 'error' }
}

async function setDisplayMode() {
  loading.value = true
  result.value = null
  const res = await apiPost('/peripheral/display', { action: 'SET_MODE', mode: displayMode.value })
  loading.value = false
  result.value = res.success
    ? { message: '操作成功: ' + (res.message || ''), type: 'success' }
    : { message: '操作失败: ' + (res.message || '未知错误'), type: 'error' }
}
</script>

<template>
  <div class="panel">
    <h2><span class="icon" style="background:#e8f5e9">P</span> 外围设备控制 <span class="tag tag-v0-3">v0.3</span></h2>
    <div class="form-group">
      <label>显示屏开关</label>
      <div class="button-group">
        <button class="btn-success" @click="controlPeripheral('ENABLE')" :disabled="loading">启用显示屏</button>
        <button class="btn-danger" @click="controlPeripheral('DISABLE')" :disabled="loading">关闭显示屏</button>
      </div>
    </div>
    <div class="divider"></div>
    <div class="form-group">
      <label>显示模式</label>
      <select v-model="displayMode">
        <option value="TWO_LINE">TWO_LINE - 竖屏模式（2行，每行32px）</option>
        <option value="FOUR_LINE">FOUR_LINE - 横屏模式（4行，每行16px）</option>
      </select>
    </div>
    <div class="button-group">
      <button class="btn-primary" @click="setDisplayMode" :disabled="loading">设置模式</button>
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
select {
  width: 100%;
  padding: 10px 14px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  font-size: 14px;
  transition: all 0.3s;
  background: #fafafa;
}
select:focus {
  outline: none;
  border-color: #667eea;
  background: white;
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
.btn-success {
  background: #4caf50;
  color: white;
}
.btn-success:hover:not(:disabled) {
  background: #43a047;
  box-shadow: 0 4px 15px rgba(76, 175, 80, 0.4);
}
.btn-danger {
  background: #f44336;
  color: white;
}
.btn-danger:hover:not(:disabled) {
  background: #e53935;
  box-shadow: 0 4px 15px rgba(244, 67, 54, 0.4);
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
.tag-v0-3 { background: #e8f5e9; color: #2e7d32; }
</style>
