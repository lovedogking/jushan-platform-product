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
        <!-- 网络状态指示器 -->
        <a-tag :color="networkTagColor" class="network-tag">
          <span class="status-dot" :class="networkDotClass" />
          {{ networkLabel }}
          <span v-if="syncStatus === 'syncing'" class="sync-spin">⟳</span>
        </a-tag>
        <!-- 离线队列待处理数 -->
        <a-badge v-if="pendingCount > 0" :count="pendingCount" :overflow-count="99">
          <a-tag color="orange">待同步</a-tag>
        </a-badge>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ReloadOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { logout as logoutApi } from '@/api/auth'
import { useNetworkStatus } from '@/composables/useNetworkStatus'

const TOKEN_KEY = 'jushan_access_token'

const router = useRouter()
const wsConnected = ref(false)
const loggingOut = ref(false)
const currentTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))

// 网络状态
const { online, syncStatus, pendingCount } = useNetworkStatus()

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  timer = setInterval(() => {
    currentTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

/** 网络状态 Tag 颜色 */
const networkTagColor = computed(() => {
  if (syncStatus.value === 'syncing') return 'processing'
  if (!online.value) return 'error'
  return 'success'
})

/** 网络状态圆点样式 */
const networkDotClass = computed(() => {
  if (syncStatus.value === 'syncing') return 'syncing'
  if (!online.value) return 'offline'
  return 'online'
})

/** 网络状态文字 */
const networkLabel = computed(() => {
  if (syncStatus.value === 'syncing') return '同步中'
  if (!online.value) return '离线'
  return '在线'
})

function handleRefresh() {
  router.go(0)
}

async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await logoutApi()
  } catch {
    // 即使远程 logout 失败，也清理本地状态
  } finally {
    sessionStorage.removeItem(TOKEN_KEY)
    router.push('/login')
  }
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
  &.syncing { background: #f59e0b; }
}

.network-tag {
  cursor: default;
}

.sync-spin {
  display: inline-block;
  animation: sync-spin 1s linear infinite;
  margin-left: 2px;
}

@keyframes sync-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
