<script setup lang="ts">
import { ref } from 'vue'
import { useApi } from '../composables/useApi'

const props = defineProps<{
  deviceId: string
  apiBase: string
  getHeaders: () => Record<string, string>
}>()

const { apiPost } = useApi(props)

const content = ref('智能停车\n欢迎光临')
const direction = ref('HORIZONTAL')
const loading = ref(false)
const result = ref<{ message: string; type: 'success' | 'error' | 'info' } | null>(null)

const quickTexts = [
  { label: '默认欢迎', text: '智能停车\n欢迎光临' },
  { label: '余位显示', text: 'P1停车场\n余位: 100' },
  { label: '禁止停车', text: '请勿泊车' },
]

async function saveDisplay() {
  if (!content.value) {
    result.value = { message: '请输入保存内容', type: 'error' }
    return
  }
  loading.value = true
  result.value = null
  const res = await apiPost('/display/save', { content: content.value, direction: direction.value })
  loading.value = false
  result.value = res.success
    ? { message: '操作成功: ' + (res.message || ''), type: 'success' }
    : { message: '操作失败: ' + (res.message || '未知错误'), type: 'error' }
}
</script>

<template>
  <div class="panel">
    <h2><span class="icon" style="background:#e8f5e9">S</span> 保存显示内容 <span class="tag tag-v0-3">v0.3</span></h2>
    <p style="font-size:12px;color:#999;margin-bottom:12px">写入 Flash 持久化存储，掉电不丢失。控制卡空闲时自动循环显示。</p>
    <div class="form-group">
      <label>保存内容（支持 \n 换行）</label>
      <textarea v-model="content" placeholder="请输入要保存的内容"></textarea>
      <div class="quick-buttons">
        <button
          v-for="item in quickTexts"
          :key="item.label"
          class="quick-btn"
          @click="content = item.text"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
    <div class="form-group" style="margin-top:12px">
      <label>布局方向</label>
      <select v-model="direction">
        <option value="HORIZONTAL">横向排列</option>
        <option value="VERTICAL">竖向排列（每字一行）</option>
      </select>
    </div>
    <div class="button-group">
      <button class="btn-success" @click="saveDisplay" :disabled="loading">保存到设备</button>
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
textarea, select {
  width: 100%;
  padding: 10px 14px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  font-size: 14px;
  transition: all 0.3s;
  background: #fafafa;
}
textarea:focus, select:focus {
  outline: none;
  border-color: #667eea;
  background: white;
}
textarea {
  resize: vertical;
  min-height: 80px;
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
.btn-success {
  background: #4caf50;
  color: white;
}
.btn-success:hover:not(:disabled) {
  background: #43a047;
  box-shadow: 0 4px 15px rgba(76, 175, 80, 0.4);
}
.quick-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}
.quick-btn {
  padding: 6px 12px;
  background: #f5f5f5;
  border: 1px solid #e0e0e0;
  border-radius: 16px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.3s;
}
.quick-btn:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
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
