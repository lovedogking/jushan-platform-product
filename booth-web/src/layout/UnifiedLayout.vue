<template>
  <a-layout class="unified-layout">
    <!-- 侧边菜单（booth 单角色时隐藏，给岗亭页留全屏空间） -->
    <a-layout-sider v-if="showSider" v-model:collapsed="collapsed" collapsible width="220">
      <div class="logo">{{ collapsed ? '飓山' : '飓山智慧停车' }}</div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        theme="dark"
        mode="inline"
        @click="handleMenuClick"
      >
        <template v-for="item in visibleMenus" :key="item.key">
          <a-sub-menu v-if="item.children" :key="item.key">
            <template #icon><component :is="item.icon" /></template>
            <template #title>{{ item.title }}</template>
            <a-menu-item v-for="child in item.children" :key="child.path">
              {{ child.title }}
            </a-menu-item>
          </a-sub-menu>
          <a-menu-item v-else :key="item.path">
            <template #icon><component :is="item.icon" /></template>
            <span>{{ item.title }}</span>
          </a-menu-item>
        </template>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <!-- 顶栏 -->
      <a-layout-header class="unified-header">
        <div class="header-left">
          <span v-if="!showSider" class="logo-text">飓山智慧停车</span>
          <span class="area-title">{{ areaTitle }}</span>
          <!-- 网络状态指示器 -->
          <a-tag :color="networkTagColor" class="network-tag">
            <span class="status-dot" :class="networkDotClass" />
            {{ networkLabel }}
            <span v-if="syncStatus === 'syncing'" class="sync-spin">⟳</span>
          </a-tag>
          <a-badge v-if="pendingCount > 0" :count="pendingCount" :overflow-count="99">
            <a-tag color="orange">待同步</a-tag>
          </a-badge>
        </div>
        <div class="header-right">
          <span class="current-time">{{ currentTime }}</span>
          <span class="username">{{ displayName }}</span>
          <a-button type="text" class="header-btn" @click="handleLogout">
            <template #icon><LogoutOutlined /></template>
            退出
          </a-button>
        </div>
      </a-layout-header>

      <!-- 内容区：岗亭页全幅，其余白底卡片 -->
      <a-layout-content :class="isBoothRoute ? 'booth-content' : 'main-content'">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, type Component } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  UserOutlined, HomeOutlined, FileTextOutlined, BarChartOutlined,
  MonitorOutlined, LogoutOutlined,
} from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { logout as logoutApi } from '@/api/auth'
import { useNetworkStatus } from '@/composables/useNetworkStatus'

const ROLES_KEY = 'jushan_roles'
const USER_KEY = 'jushan_user'

interface MenuChild {
  title: string
  path: string
  roles: string[]
}

interface MenuItem {
  key: string
  title: string
  icon: Component
  roles: string[]
  path?: string
  children?: MenuChild[]
}

/** 统一菜单结构（按角色过滤） */
const MENUS: MenuItem[] = [
  { key: 'analytics', title: '运营数据', icon: BarChartOutlined, path: '/operation/analytics', roles: ['platform', 'tenant'] },
  {
    key: 'parking', title: '车场管理', icon: HomeOutlined, roles: ['platform', 'tenant'],
    children: [
      { title: '车场信息', path: '/admin/parking', roles: ['platform', 'tenant'] },
      { title: '固定车管理', path: '/operation/vehicles', roles: ['platform', 'tenant'] },
    ],
  },
  { key: 'accounts', title: '账号管理', icon: UserOutlined, path: '/admin/accounts', roles: ['platform'] },
  {
    key: 'records', title: '记录查询', icon: FileTextOutlined, roles: ['platform', 'tenant'],
    children: [
      { title: '通行记录', path: '/operation/access-records', roles: ['platform', 'tenant'] },
    ],
  },
  { key: 'booth', title: '岗亭工作区', icon: MonitorOutlined, path: '/booth/monitor', roles: ['platform', 'tenant', 'booth'] },
]

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const selectedKeys = ref<string[]>([route.path])
const openKeys = ref<string[]>([])
const currentTime = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))

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

function getUserRoles(): string[] {
  try {
    const raw = sessionStorage.getItem(ROLES_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

const userRoles = computed<string[]>(() => getUserRoles())

/** 仅 booth 角色时隐藏侧边栏 */
const showSider = computed(() => {
  const roles = userRoles.value
  return roles.includes('platform') || roles.includes('tenant')
})

function hasAnyRole(required: string[]): boolean {
  return required.some(r => userRoles.value.includes(r))
}

/** 按角色过滤后的可见菜单 */
const visibleMenus = computed<MenuItem[]>(() => {
  return MENUS
    .filter(m => hasAnyRole(m.roles))
    .map(m => {
      if (!m.children) return m
      const children = m.children.filter(c => hasAnyRole(c.roles))
      return { ...m, children }
    })
    .filter(m => !m.children || m.children.length > 0)
})

const isBoothRoute = computed(() => route.path.startsWith('/booth'))

const areaTitle = computed(() => {
  if (route.path.startsWith('/admin')) return '平台管理区'
  if (route.path.startsWith('/operation')) return '车场运营区'
  if (route.path.startsWith('/booth')) return '岗亭工作区'
  return ''
})

const displayName = computed(() => {
  try {
    const raw = sessionStorage.getItem(USER_KEY)
    if (!raw) return ''
    const user = JSON.parse(raw)
    return user.displayName || user.username || ''
  } catch {
    return ''
  }
})

/** 展开当前路由所属的子菜单 */
function syncOpenKeys() {
  if (collapsed.value) {
    openKeys.value = []
    return
  }
  const parent = visibleMenus.value.find(m => m.children?.some(c => c.path === route.path))
  openKeys.value = parent ? [parent.key] : []
}

watch(() => route.path, (path) => {
  selectedKeys.value = [path]
  syncOpenKeys()
}, { immediate: true })

watch(collapsed, () => syncOpenKeys())

const networkTagColor = computed(() => {
  if (syncStatus.value === 'syncing') return 'processing'
  if (!online.value) return 'error'
  return 'success'
})

const networkDotClass = computed(() => {
  if (syncStatus.value === 'syncing') return 'syncing'
  if (!online.value) return 'offline'
  return 'online'
})

const networkLabel = computed(() => {
  if (syncStatus.value === 'syncing') return '同步中'
  if (!online.value) return '离线'
  return '在线'
})

function handleMenuClick({ key }: { key: string }) {
  router.push(key)
}

async function handleLogout() {
  try { await logoutApi() } catch { /* 即使远程 logout 失败，也清理本地状态 */ }
  sessionStorage.clear()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.unified-layout { height: 100vh; }

.logo {
  color: #fff;
  padding: 16px;
  font-size: 16px;
  font-weight: 700;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
}

.unified-header {
  height: $header-height;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .logo-text {
      font-size: 15px;
      font-weight: 700;
      color: $text-color;
    }

    .area-title {
      font-size: 16px;
      font-weight: 600;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .current-time {
      color: $text-color-secondary;
      font-size: 13px;
      font-variant-numeric: tabular-nums;
    }

    .username {
      font-weight: 600;
    }
  }
}

.main-content {
  margin: 16px;
  padding: 16px;
  background: #fff;
  border-radius: 4px;
  overflow: auto;
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

.network-tag { cursor: default; }

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
