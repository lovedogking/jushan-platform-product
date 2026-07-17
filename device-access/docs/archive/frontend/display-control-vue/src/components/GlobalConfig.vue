<script setup lang="ts">
import { ref } from 'vue'

const deviceId = defineModel<string>('deviceId')
const apiBase = defineModel<string>('apiBase')
const apiKey = defineModel<string>('apiKey')
</script>

<template>
  <div class="global-config">
    <h2>全局配置</h2>
    <div class="config-grid">
      <div class="form-group">
        <label>设备ID</label>
        <input type="text" v-model="deviceId" placeholder="请输入设备ID">
      </div>
      <div class="form-group">
        <label>API 地址</label>
        <input type="text" v-model="apiBase" placeholder="http://localhost:8082/api/v1">
      </div>
      <div class="form-group">
        <label>API Key</label>
        <input type="text" v-model="apiKey" placeholder="请输入API Key">
      </div>
    </div>
  </div>
</template>

<style scoped>
.global-config {
  background: white;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
}

.global-config h2 {
  font-size: 18px;
  color: #333;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 16px;
}

.form-group {
  margin-bottom: 0;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  color: #555;
  font-weight: 500;
  font-size: 13px;
}

input[type="text"] {
  width: 100%;
  padding: 10px 14px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  font-size: 14px;
  transition: all 0.3s;
  background: #fafafa;
}

input[type="text"]:focus {
  outline: none;
  border-color: #667eea;
  background: white;
}

@media (max-width: 768px) {
  .config-grid {
    grid-template-columns: 1fr;
  }
}
</style>
