import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type NavItem = {
  key: string
  label: string
}

export type MenuItem = {
  key: string
  label: string
  icon?: string
  path: string
}

// 一级导航（T10 最小占位，后续任务逐步激活）
export const NAV_ITEMS: NavItem[] = [
  { key: 'overview', label: '运营总览' },
  { key: 'parking', label: '车场运营' },
  { key: 'deviceOps', label: '设备运维' },
  { key: 'billing', label: '收费与支付' },
  { key: 'platform', label: '平台管理' },
  { key: 'settings', label: '系统配置' },
]

// 二级菜单（T10 最小占位，后续任务按模块补充）
export const MENU_MAP: Record<string, MenuItem[]> = {
  overview: [
    { key: 'dashboard', label: '平台驾驶舱', icon: 'DashboardOutlined', path: '/dashboard' },
  ],
  parking: [],
  deviceOps: [],
  billing: [],
  platform: [],
  settings: [],
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const activeNav = ref('overview')
  const activeMenu = ref('dashboard')

  const currentMenus = computed(() => MENU_MAP[activeNav.value] || [])

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setActiveNav(key: string) {
    activeNav.value = key
    const menus = MENU_MAP[key]
    if (menus && menus.length > 0) {
      activeMenu.value = menus[0].key
    }
  }

  function setActiveMenu(key: string) {
    activeMenu.value = key
  }

  function setNavByPath(path: string) {
    for (const [navKey, menus] of Object.entries(MENU_MAP)) {
      const matched = menus.find((menu) => menu.path === path)
      if (matched) {
        activeNav.value = navKey
        activeMenu.value = matched.key
        return
      }
    }
  }

  return { sidebarCollapsed, activeNav, activeMenu, currentMenus, toggleSidebar, setActiveNav, setActiveMenu, setNavByPath }
})
