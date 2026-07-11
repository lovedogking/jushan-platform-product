<template>
  <a-layout class="booth-layout">
    <!-- 顶部栏 -->
    <a-layout-header class="booth-header">
      <div class="header-left">
        <span class="logo-text">飓山停车 · 岗亭端</span>
        <a-tag color="green" v-if="wsConnected">
          <span class="status-dot online" /> 已连接
        </a-tag>
        <a-tag color="red" v-else>
          <span class="status-dot offline" /> 未连接
        </a-tag>
      </div>
      <div class="header-center">
        <span class="current-time">{{ currentTime }}</span>
      </div>
      <div class="header-right">
        <a-button type="text" class="header-btn" @click="handleRefresh">
          <template #icon><ReloadOutlined /></template>
        </a-button>
        <a-button type="text" class="header-btn" @click="handleLogout">
          <template #icon><LogoutOutlined /></template>
          退出
        </a-button>
      </div>
    </a-layout-header>

    <!-- 内容区 -->
    <a-layout-content class="booth-content">
      <router-view />
    </a-layout-content>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ReloadOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'

const router = useRouter()
const wsConnected = ref(false)
const currentTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

function handleRefresh() {
  router.go(0)
}

function handleLogout() {
  localStorage.removeItem('jushan_access_token')
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.booth-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.booth-header {
  height: $header-height;
  background: linear-gradient(90deg, #0f172a 0%, #102a43 56%, #0b2a4a 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .logo-text {
      color: #fff;
      font-size: 15px;
      font-weight: 700;
    }
  }

  .header-center {
    .current-time {
      color: rgba(255, 255, 255, 0.9);
      font-size: 16px;
      font-variant-numeric: tabular-nums;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 4px;

    .header-btn {
      color: rgba(255, 255, 255, 0.85);
      &:hover { color: #fff; background: rgba(255, 255, 255, 0.1); }
    }
  }
}

.booth-content {
  flex: 1;
  overflow: auto;
  padding: $spacing-lg;
  background: $bg-color;
}

.status-dot {
  display: inline-block;
  width: 6px; height: 6px;
  border-radius: 50%;
  &.online { background: $success-color; }
  &.offline { background: $error-color; }
}
</style>
