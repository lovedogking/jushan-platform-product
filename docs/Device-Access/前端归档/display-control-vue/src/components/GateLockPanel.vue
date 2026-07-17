<script setup lang="ts">
import { ref } from 'vue'
import { useApi } from '../composables/useApi'

const props = defineProps<{
  deviceId: string
  apiBase: string
  getHeaders: () => Record<string, string>
}>()

const { lockGate, unlockGate } = useApi(props)

const loading = ref(false)
const result = ref<{ message: string; type: 'success' | 'error' | 'info' } | null>(null)

async function doLock() {
  loading.value = true
  result.value = null
  const success = await lockGate()
  loading.value = false
  result.value = success
    ? { message: '锁定开闸成功', type: 'success' }
    : { message: '锁定开闸失败', type: 'error' }
}

async function doUnlock() {
  loading.value = true
  result.value = null
  const success = await unlockGate()
  loading.value = false
  result.value = success
    ? { message: '解除锁定并关闸成功', type: 'success' }
    : { message: '解除锁定并关闸失败', type: 'error' }
}
</script>

<template>
  <div class="panel">
    <h2><span class="icon" style="background:#ffebee">L</span> 道闸锁定控制 <span class="tag tag-lock">v0.4</span></h2>
    <p style="font-size:12px;color:#999;margin-bottom:12px">通过持续吸合继电器锁定道闸状态</p>

    <div class="lock-section">
      <h3>锁定操作</h3>
      <div class="button-group">
        <button class="btn-lock" @click="doLock()" :disabled="loading">锁定开闸</button>
      </div>
    </div>

    <div class="lock-section">
      <h3>解除锁定</h3>
      <div class="button-group">
        <button class="btn-unlock" @click="doUnlock()" :disabled="loading">解除锁定并关闸</button>
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
.lock-section {
  margin-bottom: 16px;
}
.lock-section h3 {
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
  font-weight: 500;
}
.button-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
button {
  padding: 10px 16px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 120px;
  justify-content: center;
}
button:hover:not(:disabled) {
  transform: translateY(-1px);
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.btn-lock {
  background: linear-gradient(135deg, #ff5252 0%, #d32f2f 100%);
  color: white;
}
.btn-lock:hover:not(:disabled) {
  box-shadow: 0 4px 15px rgba(255, 82, 82, 0.4);
}
.btn-unlock {
  background: linear-gradient(135deg, #66bb6a 0%, #388e3c 100%);
  color: white;
}
.btn-unlock:hover:not(:disabled) {
  box-shadow: 0 4px 15px rgba(102, 187, 106, 0.4);
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
.tag-lock { background: #ffebee; color: #c62828; }
</style>
