import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useAuthStore } from './auth'

export type NavItem = {
  key: string
  label: string
}

export type MenuItem = {
  key: string
  label: string
  icon?: string
  path: string
  permission?: string
}

// 一级导航（T10 最小占位，后续任务逐步激活）
export const NAV_ITEMS: NavItem[] = [
  { key: 'overview', label: '运营总览' },
  { key: 'parking', label: '车场运营' },
  { key: 'deviceOps', label: '设备管理' },
  { key: 'billing', label: '收费与支付' },
  { key: 'platform', label: '权限管理' },
  { key: 'settings', label: '系统配置' },
]

// 二级菜单（T10 最小占位，后续任务按模块补充）
export const MENU_MAP: Record<string, MenuItem[]> = {
  overview: [
    { key: 'dashboard', label: '平台驾驶舱', icon: 'DashboardOutlined', path: '/dashboard' },
  ],
  // FIX-11：车场运营菜单
  parking: [
    { key: 'parking-lots', label: '车场管理', icon: 'CarOutlined', path: '/parking-lots' },
    { key: 'parking-lanes', label: '通道管理', icon: 'BranchesOutlined', path: '/parking-lanes' },
    { key: 'vehicles', label: '车辆管理', icon: 'IdcardOutlined', path: '/vehicles', permission: 'vehicle:view' },
    { key: 'monthly-passes', label: '月卡管理', icon: 'IdcardOutlined', path: '/monthly-passes', permission: 'monthly:manage' },
    { key: 'fixed-spaces', label: '固定车位', icon: 'CarOutlined', path: '/fixed-spaces', permission: 'fixed:manage' },
    { key: 'parking-records', label: '通行记录', icon: 'FileTextOutlined', path: '/parking-records', permission: 'record:view' },
    { key: 'remote-gate', label: '远程开闸', icon: 'ThunderboltOutlined', path: '/remote-gate', permission: 'device:remote:open' },
  ],
  // 设备管理菜单
  deviceOps: [
    { key: 'devices', label: '设备管理', icon: 'ToolOutlined', path: '/devices' },
    { key: 'manual-gate-records', label: '开闸记录', icon: 'HistoryOutlined', path: '/manual-gate-records', permission: 'device:audit' },
    { key: 'exception-records', label: '异常记录', icon: 'ExclamationCircleOutlined', path: '/exception-records', permission: 'exception:view' },
  ],
  // 包6-1：收费与支付菜单
  billing: [
    { key: 'billing-rules', label: '收费规则', icon: 'DollarOutlined', path: '/billing-rules' },
    { key: 'orders', label: '订单管理', icon: 'FileTextOutlined', path: '/orders', permission: 'order:manage' },
    { key: 'report-revenue', label: '收入报表', icon: 'DollarCircleOutlined', path: '/report-revenue', permission: 'dashboard:view' },
    { key: 'report-traffic', label: '车流量报表', icon: 'BarChartOutlined', path: '/report-traffic', permission: 'dashboard:view' },
  ],
  // FIX-11：平台管理菜单
  platform: [
    { key: 'tenants', label: '租户管理', icon: 'TeamOutlined', path: '/tenants', permission: 'tenant:read' },
    { key: 'companies', label: '公司管理', icon: 'ApartmentOutlined', path: '/companies', permission: 'company:view' },
    { key: 'admin-accounts', label: '账号管理', icon: 'UserOutlined', path: '/admin-accounts', permission: 'account:view' },
    { key: 'custom-roles', label: '角色管理', icon: 'IdcardOutlined', path: '/custom-roles', permission: 'role:view' },

    { key: 'audit-logs', label: '审计日志', icon: 'FileTextOutlined', path: '/audit-logs' },
  ],
  settings: [
    { key: 'system-params', label: '系统参数', icon: 'SettingOutlined', path: '/system-params', permission: 'system:param:manage' },
  ],
}

export const useAppStore = defineStore('app', () => {
  const authStore = useAuthStore()
  const sidebarCollapsed = ref(false)
  const activeNav = ref('overview')
  const activeMenu = ref('dashboard')

  const currentMenus = computed(() => {
    const menus = MENU_MAP[activeNav.value] || []
    return menus.filter((menu) => !menu.permission || authStore.hasPermission(menu.permission))
  })

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setActiveNav(key: string) {
    activeNav.value = key
    const menus = currentMenus.value
    if (menus.length > 0) {
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
