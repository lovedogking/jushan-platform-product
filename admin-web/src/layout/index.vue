<template>
  <a-layout class="main-layout">
    <!-- 顶部导航 -->
    <a-layout-header class="main-header">
      <div class="header-left">
        <a-tooltip title="折叠菜单">
          <a-button type="text" class="header-btn collapse-btn" @click="appStore.toggleSidebar()">
            <template #icon><MenuFoldOutlined v-if="!sidebarCollapsed" /><MenuUnfoldOutlined v-else /></template>
          </a-button>
        </a-tooltip>
        <div class="logo">
          <span class="logo-text">飓山停车 SaaS</span>
        </div>
        <a-menu
          v-model:selectedKeys="selectedNavKeys"
          mode="horizontal"
          theme="dark"
          class="nav-menu"
          @click="handleNavClick"
        >
          <a-menu-item v-for="nav in NAV_ITEMS" :key="nav.key">
            {{ nav.label }}
          </a-menu-item>
        </a-menu>
      </div>
      <div class="header-right">
        <div class="brand-welcome">智慧停车运营控制台</div>
        <a-tooltip title="刷新">
          <a-button type="text" class="header-btn" @click="handleRefresh">
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </a-tooltip>
        <a-tooltip :title="isFullscreen ? '退出全屏' : '全屏'">
          <a-button type="text" class="header-btn" @click="toggleFullscreen">
            <template #icon>
              <FullscreenExitOutlined v-if="isFullscreen" />
              <FullscreenOutlined v-else />
            </template>
          </a-button>
        </a-tooltip>
        <a-dropdown>
          <div class="user-info">
            <a-avatar :size="32" class="user-avatar">
              {{ nickname?.charAt(0)?.toUpperCase() || 'U' }}
            </a-avatar>
            <span class="user-name">{{ nickname }}</span>
            <DownOutlined />
          </div>
          <template #overlay>
            <a-menu>
              <a-menu-item key="logout" @click="handleLogout">
                <LogoutOutlined /> 退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>

    <a-layout class="main-body">
      <!-- 左侧菜单 -->
      <a-layout-sider
        :width="216"
        :collapsed="sidebarCollapsed"
        :collapsed-width="48"
        class="main-sider"
        theme="light"
      >
        <a-menu
          v-model:selectedKeys="selectedMenuKeys"
          mode="inline"
          class="sider-menu"
          @click="handleMenuClick"
        >
          <a-menu-item v-for="menu in currentMenus" :key="menu.key">
            <component :is="getIcon(menu.icon)" v-if="menu.icon" />
            <span>{{ menu.label }}</span>
          </a-menu-item>
        </a-menu>
      </a-layout-sider>

      <!-- 内容区 -->
      <a-layout-content class="main-content">
        <!-- 标签页 -->
        <div class="tabs-container">
          <a-tabs
            v-model:activeKey="activeTabKey"
            type="editable-card"
            hide-add
            @change="handleTabChange"
            @edit="handleTabEdit"
          >
            <a-tab-pane
              v-for="tab in tabs"
              :key="tab.key"
              :closable="tab.closable"
            >
              <template #tab>
                <span>{{ tab.title }}</span>
              </template>
            </a-tab-pane>
          </a-tabs>
          <a-dropdown>
            <a-button type="text" size="small" class="tabs-more">
              <DownOutlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="refresh" @click="handleRefresh">
                  <ReloadOutlined /> 刷新当前
                </a-menu-item>
                <a-menu-item key="closeOther" @click="handleCloseOther">
                  <CloseOutlined /> 关闭其他
                </a-menu-item>
                <a-menu-item key="closeAll" @click="handleCloseAll">
                  <MinusCircleOutlined /> 关闭全部
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>

        <!-- 页面内容 -->
        <div class="page-wrapper">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <keep-alive :include="cachedViews">
                <component :is="Component" :key="routeKey" />
              </keep-alive>
            </transition>
          </router-view>
        </div>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  DownOutlined,
  ReloadOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LogoutOutlined,
  CloseOutlined,
  MinusCircleOutlined,
  DashboardOutlined,
  HomeOutlined,
  SwapOutlined,
  ShopOutlined,
  ApiOutlined,
  ThunderboltOutlined,
  FileTextOutlined,
  UserOutlined,
  TeamOutlined,
  DollarOutlined,
  IdcardOutlined,
  AlertOutlined,
  ApartmentOutlined,
  AuditOutlined,
  BellOutlined,
  SettingOutlined,
  WalletOutlined,
  CarOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore, useAppStore, NAV_ITEMS, MENU_MAP, useTabsStore } from '@/stores'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const appStore = useAppStore()
const tabsStore = useTabsStore()

const routeKey = ref(route.fullPath)
const isFullscreen = ref(false)

const nickname = computed(() => authStore.displayName)
const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const currentMenus = computed(() => appStore.currentMenus)
const tabs = computed(() => tabsStore.tabs)
const activeTabKey = computed({
  get: () => tabsStore.activeKey,
  set: () => {},
})

const selectedNavKeys = computed(() => [appStore.activeNav])
const selectedMenuKeys = computed(() => [appStore.activeMenu])

// keep-alive 缓存视图列表（T10 占位）
const cachedViews = ref<string[]>(['Dashboard'])

const iconMap: Record<string, any> = {
  DashboardOutlined, HomeOutlined, SwapOutlined, ShopOutlined,
  ApiOutlined, ThunderboltOutlined, CarOutlined, FileTextOutlined,
  UserOutlined, TeamOutlined, DollarOutlined, IdcardOutlined,
  AlertOutlined, ApartmentOutlined, AuditOutlined, BellOutlined,
  SettingOutlined, WalletOutlined,
}

function getIcon(icon?: string) {
  return icon ? iconMap[icon] : null
}

function handleNavClick(info: any) {
  const key = String(info.key)
  appStore.setActiveNav(key)
  const menus = MENU_MAP[key]
  if (menus && menus.length > 0) {
    router.push(menus[0].path)
  }
}

function handleMenuClick(info: any) {
  const key = String(info.key)
  appStore.setActiveMenu(key)
  const menu = currentMenus.value.find(m => m.key === key)
  if (menu) {
    router.push(menu.path)
  }
}

function handleTabChange(key: string | number) {
  router.push(String(key))
}

function handleTabEdit(key: any, action: 'add' | 'remove') {
  if (action === 'remove') {
    const newPath = tabsStore.closeTab(key)
    if (newPath) {
      router.push(newPath)
    }
  }
}

function handleRefresh() {
  routeKey.value = Date.now().toString()
}

function handleCloseOther() {
  tabsStore.closeOtherTabs(activeTabKey.value)
}

function handleCloseAll() {
  const path = tabsStore.closeAllTabs()
  router.push(path)
}

async function toggleFullscreen() {
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(() => {
  tabsStore.addTab(route)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

function handleLogout() {
  authStore.logout()
}

watch(
  () => route.path,
  (path) => {
    appStore.setNavByPath(path)
    tabsStore.addTab(route)
  },
  { immediate: true }
)
</script>

<style lang="scss" scoped>
.main-layout {
  height: 100vh;
  overflow: hidden;
}

.main-header {
  height: $header-height;
  background: linear-gradient(90deg, #0f172a 0%, #102a43 56%, #0b2a4a 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;

  .header-left {
    display: flex;
    align-items: center;

    .logo {
      display: flex;
      align-items: center;
      color: #fff;
      font-size: 16px;
      font-weight: 700;
      margin-right: 28px;
      white-space: nowrap;

      .logo-text {
        margin-left: 4px;
      }
    }

    .nav-menu {
      background: transparent;
      border-bottom: none;
      min-width: 560px;

      :deep(.ant-menu-item) {
        color: rgba(255, 255, 255, 0.85);

        &::after {
          border-bottom-color: #64a7ff !important;
        }

        &:hover {
          color: #fff;
          background: rgba(255, 255, 255, 0.1);
        }
      }

      :deep(.ant-menu-item-selected) {
        color: #fff;
        background: rgba(22, 93, 255, 0.22);
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;

    .header-btn {
      color: rgba(255, 255, 255, 0.85);

      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.1);
      }
    }

    .brand-welcome {
      color: rgba(255, 255, 255, 0.78);
      font-size: 13px;
      white-space: nowrap;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #fff;
      cursor: pointer;
      padding: 0 12px;
      height: 56px;

      &:hover {
        background: rgba(255, 255, 255, 0.1);
      }

      .user-avatar {
        background: $primary-color;
      }

      .user-name {
        font-size: 14px;
      }
    }
  }
}

.main-body {
  background: $bg-color;
}

.main-sider {
  background: #fff;
  border-right: 1px solid $border-color;
  overflow: auto;

  .sider-menu {
    border-right: none;
    height: 100%;

    :deep(.ant-menu-item) {
      margin: 4px 8px;
      border-radius: 8px;
    }

    :deep(.ant-menu-item-selected) {
      background: $primary-color-light;
      color: $primary-color;
      font-weight: 650;
    }

    :deep(.ant-menu-item:hover) {
      background: #f3f8ff;
      color: $primary-color;
    }
  }
}

.main-content {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tabs-container {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid $border-color;
  padding: 0 12px;

  :deep(.ant-tabs) {
    flex: 1;

    .ant-tabs-nav {
      margin-bottom: 0;

      &::before {
        border-bottom: none;
      }
    }

    .ant-tabs-tab {
      padding: 8px 16px;
      border-radius: 10px 10px 0 0;
    }
  }

  .tabs-more {
    margin-left: 8px;
  }
}

.page-wrapper {
  flex: 1;
  overflow: auto;
  padding: $spacing-lg;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
