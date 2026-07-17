<script setup lang="ts">
import { ref } from 'vue'
import { useApi } from '../composables/useApi'

const props = defineProps<{
  deviceId: string
  apiBase: string
  getHeaders: () => Record<string, string>
}>()

const { apiPost, lockGate, unlockGate } = useApi(props)

const loading = ref(false)
const result = ref<{ message: string; type: 'success' | 'error' | 'info' } | null>(null)

async function doAction(label: string, action: () => Promise<any>) {
  loading.value = true
  result.value = null
  const res = await action()
  loading.value = false
  if (res.success) {
    result.value = { message: `${label}成功: ${res.message || ''}`, type: 'success' }
  } else {
    result.value = { message: `${label}失败: ${res.message || '未知错误'}`, type: 'error' }
  }
}

function openGate() {
  doAction('开闸', () => apiPost('/gate/open', {}))
}

function closeGate() {
  doAction('关闸', () => apiPost('/gate/close', {}))
}

function doLock() {
  doAction('锁定开闸', () => lockGate())
}

function doUnlock() {
  doAction('解除锁定并关闸', () => unlockGate())
}
</script>

<template>
  <div class="panel">
    <h2><span class="icon" style="background:#ffebee">G</span> 道闸控制 <span class="tag tag-v0-4">v0.4</span></h2>
    <p style="font-size:12px;color:#999;margin-bottom:12px">开闸/关闸/锁定/解锁一体化控制</p>

    <div class="section">
      <h3>基础控制</h3>
      <div class="button-group">
        <button class="btn-open" @click="openGate" :disabled="loading">开闸</button>
        <button class="btn-close" @click="closeGate" :disabled="loading">关闸</button>
      </div>
    </div>

    <div class="divider"></div>

    <div class="section">
      <h3>锁定控制 <span style="font-size:11px;color:#999">（继电器持续吸合保持开启）</span></h3>
      <div class="button-group">
        <button class="btn-lock" @click="doLock()" :disabled="loading">锁定开闸</button>
      </div>
    </div>

    <div class="section">
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
.section {
  margin-bottom: 16px;
}
.section h3 {
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
  font-weight: 500;
}
.divider {
  height: 1px;
  background: #e8e8e8;
  margin: 16px 0;
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
.btn-open {
  background: linear-gradient(135deg, #66bb6a 0%, #388e3c 100%);
  color: white;
}
.btn-open:hover:not(:disabled) {
  box-shadow: 0 4px 15px rgba(102, 187, 106, 0.4);
}
.btn-close {
  background: linear-gradient(135deg, #ef5350 0%, #c62828 100%);
  color: white;
}
.btn-close:hover:not(:disabled) {
  box-shadow: 0 4px 15px rgba(239, 83, 80, 0.4);
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
.tag-v0-4 { background: #fff3e0; color: #e65100; }
</style>
