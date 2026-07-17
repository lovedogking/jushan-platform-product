<script setup lang="ts">
import { ref } from 'vue'
import { useApi } from '../composables/useApi'

const props = defineProps<{
  deviceId: string
  apiBase: string
  getHeaders: () => Record<string, string>
}>()

const { apiPost } = useApi(props)

const content = ref('欢迎光临\n请入场停车')
const direction = ref('HORIZONTAL')
const loading = ref(false)
const result = ref<{ message: string; type: 'success' | 'error' | 'info' } | null>(null)

const quickTexts = [
  { label: '欢迎光临', text: '欢迎光临' },
  { label: '入场欢迎', text: '欢迎光临\n请入场停车' },
  { label: '出场告别', text: '一路顺风\n欢迎下次光临' },
  { label: '缴费提示', text: '请交费\n15.00元' },
]

async function displayText() {
  if (!content.value) {
    result.value = { message: '请输入显示内容', type: 'error' }
    return
  }
  loading.value = true
  result.value = null
  const res = await apiPost('/display/text', { content: content.value, direction: direction.value })
  loading.value = false
  result.value = res.success
    ? { message: '操作成功: ' + (res.message || ''), type: 'success' }
    : { message: '操作失败: ' + (res.message || '未知错误'), type: 'error' }
}
</script>

<template>
  <div class="panel panel-text">
    <h2><span class="icon">T</span> 实时显示文字 <span class="tag tag-v0-3">v0.3</span></h2>
    <div class="form-group">
      <label>显示内容（支持 \n 换行）</label>
      <textarea v-model="content" placeholder="请输入显示内容，例如：欢迎光临\n请入场停车"></textarea>
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
        <option value="HORIZONTAL">横向排列（按行展开）</option>
        <option value="VERTICAL">竖向排列（每字一行）</option>
      </select>
    </div>
    <div class="button-group">
      <button class="btn-primary" @click="displayText" :disabled="loading">实时显示</button>
    </div>
    <div v-if="loading" class="loading">
      <span class="loading-spinner"></span>发送中...
    </div>
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
  background: #e3f2fd;
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

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
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

.result.info {
  background: #e3f2fd;
  color: #1565c0;
  border: 1px solid #90caf9;
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
.tag-v0-4 { background: #fff3e0; color: #e65100; }
</style>
