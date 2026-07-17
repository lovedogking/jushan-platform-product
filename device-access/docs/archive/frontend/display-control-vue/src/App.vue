<script setup lang="ts">
import { ref } from 'vue'
import GlobalConfig from './components/GlobalConfig.vue'
import DisplayTextPanel from './components/DisplayTextPanel.vue'
import SaveDisplayPanel from './components/SaveDisplayPanel.vue'
import PeripheralPanel from './components/PeripheralPanel.vue'
import ConfigPanel from './components/ConfigPanel.vue'
import VoicePanel from './components/VoicePanel.vue'
import EnhancedPanel from './components/EnhancedPanel.vue'
import GateControlPanel from './components/GateControlPanel.vue'

const deviceId = ref('917e2298-8ddf3e46')
const apiBase = ref('/api/v1')
const apiKey = ref('test-key-123')

const getHeaders = () => ({
  'Content-Type': 'application/json',
  'X-API-Key': apiKey.value,
})
</script>

<template>
  <div class="app">
    <div class="header">
      <h1>Device Access 显示屏控制面板</h1>
      <p>支持信路通 / 臻识双品牌设备 | 科发 DC199 屏卡协议</p>
    </div>

    <GlobalConfig
      v-model:deviceId="deviceId"
      v-model:apiBase="apiBase"
      v-model:apiKey="apiKey"
    />

    <!-- 道闸控制 -->
    <div class="section-header">
      <h3>道闸控制</h3>
    </div>
    <div class="panel-grid">
      <GateControlPanel
        :deviceId="deviceId"
        :apiBase="apiBase"
        :getHeaders="getHeaders"
      />
    </div>

    <!-- 显示控制 -->
    <div class="section-header">
      <h3>显示控制</h3>
    </div>
    <div class="panel-grid">
      <DisplayTextPanel
        :deviceId="deviceId"
        :apiBase="apiBase"
        :getHeaders="getHeaders"
      />
      <SaveDisplayPanel
        :deviceId="deviceId"
        :apiBase="apiBase"
        :getHeaders="getHeaders"
      />
      <EnhancedPanel
        :deviceId="deviceId"
        :apiBase="apiBase"
        :getHeaders="getHeaders"
      />
      <PeripheralPanel
        :deviceId="deviceId"
        :apiBase="apiBase"
        :getHeaders="getHeaders"
      />
    </div>

    <!-- 设备配置 -->
    <div class="section-header">
      <h3>设备配置</h3>
    </div>
    <div class="panel-grid">
      <ConfigPanel
        :deviceId="deviceId"
        :apiBase="apiBase"
        :getHeaders="getHeaders"
      />
      <VoicePanel
        :deviceId="deviceId"
        :apiBase="apiBase"
        :getHeaders="getHeaders"
      />
    </div>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background: #f5f7fa;
  min-height: 100vh;
  padding: 20px;
}

.app {
  max-width: 1400px;
  margin: 0 auto;
}

.header {
  text-align: center;
  margin-bottom: 30px;
  padding: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  color: white;
  box-shadow: 0 10px 40px rgba(102, 126, 234, 0.3);
}

.header h1 {
  font-size: 28px;
  margin-bottom: 8px;
}

.header p {
  opacity: 0.9;
  font-size: 14px;
}

.section-header {
  margin: 30px 0 15px;
  padding: 10px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  color: white;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.2);
}

.section-header h3 {
  font-size: 16px;
  font-weight: 600;
}

.panel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 20px;
}

@media (max-width: 768px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
