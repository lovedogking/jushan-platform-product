import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue'),
    meta: { title: '租户注册' },
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '平台驾驶舱', icon: 'DashboardOutlined', cache: true },
      },
      // FIX-11：租户管理
      {
        path: 'tenants',
        name: 'Tenants',
        component: () => import('@/views/tenant/index.vue'),
        meta: { title: '租户管理', icon: 'TeamOutlined' },
      },
      // FIX-11：停车场管理
      {
        path: 'parking-lots',
        name: 'ParkingLots',
        component: () => import('@/views/parking/ParkingLotManage.vue'),
        meta: { title: '停车场管理', icon: 'CarOutlined' },
      },
      // Sprint 2：区域管理
      {
        path: 'parking-zones',
        name: 'ParkingZones',
        component: () => import('@/views/parking/ParkingZoneManage.vue'),
        meta: { title: '区域管理', icon: 'AppstoreOutlined' },
      },
      // FIX-11：通道管理
      {
        path: 'parking-lanes',
        name: 'ParkingLanes',
        component: () => import('@/views/parking/ParkingLaneManage.vue'),
        meta: { title: '通道管理', icon: 'BranchesOutlined' },
      },
      // FIX-11：设备管理
      {
        path: 'devices',
        name: 'Devices',
        component: () => import('@/views/device/index.vue'),
        meta: { title: '设备管理', icon: 'ToolOutlined' },
      },
      // Phase 1 B2：远程开闸
      {
        path: 'remote-gate',
        name: 'RemoteGate',
        component: () => import('@/views/remote-gate/index.vue'),
        meta: { title: '远程开闸', icon: 'ThunderboltOutlined', permission: 'device:remote:open', cache: true },
      },
      // Phase 2 D3：手动开闸记录
      {
        path: 'manual-gate-records',
        name: 'ManualGateRecords',
        component: () => import('@/views/manual-gate-record/index.vue'),
        meta: { title: '开闸记录', icon: 'HistoryOutlined', permission: 'device:audit', cache: true },
      },
      // FIX-11：员工管理
      {
        path: 'employees',
        name: 'Employees',
        component: () => import('@/views/employee/index.vue'),
        meta: { title: '员工管理', icon: 'UserOutlined' },
      },
      // FIX-11：审计日志
      {
        path: 'audit-logs',
        name: 'AuditLogs',
        component: () => import('@/views/audit-log/index.vue'),
        meta: { title: '审计日志', icon: 'FileTextOutlined' },
      },
      // T34：收费规则管理
      {
        path: 'billing-rules',
        name: 'BillingRules',
        component: () => import('@/views/billing-rule/index.vue'),
        meta: { title: '收费规则', icon: 'DollarOutlined', cache: true },
      },
      // Sprint 3：费用试算（二期恢复）
      {
        path: 'fee-calculator',
        name: 'FeeCalculator',
        component: () => import('@/views/billing-rule/FeeCalculator.vue'),
        meta: { title: '费用试算（二期恢复）', icon: 'CalculatorOutlined' },
      },
      // Sprint 1：公司管理
      {
        path: 'companies',
        name: 'Companies',
        component: () => import('@/views/company/CompanyManage.vue'),
        meta: { title: '公司管理', icon: 'ApartmentOutlined', permission: 'company:view', cache: true },
      },
      // 车辆管理（含月卡/固定车续费）
      {
        path: 'vehicles',
        name: 'Vehicles',
        component: () => import('@/views/vehicle/VehicleManage.vue'),
        meta: { title: '车辆管理', icon: 'CarOutlined', permission: 'vehicle:view', cache: true },
      },
      // Phase 1 A1：月卡独立管理
      {
        path: 'monthly-passes',
        name: 'MonthlyPasses',
        component: () => import('@/views/monthly-pass/index.vue'),
        meta: { title: '月卡管理', icon: 'IdcardOutlined', permission: 'monthly:manage', cache: true },
      },
      // Phase 1 A2：固定车位管理
      {
        path: 'fixed-spaces',
        name: 'FixedSpaces',
        component: () => import('@/views/fixed-space/index.vue'),
        meta: { title: '固定车位', icon: 'CarOutlined', permission: 'fixed:manage', cache: true },
      },
      // Phase 2 D1：通行记录管理
      {
        path: 'parking-records',
        name: 'ParkingRecords',
        component: () => import('@/views/parking-record/index.vue'),
        meta: { title: '通行记录', icon: 'FileTextOutlined', permission: 'record:view', cache: true },
      },
      // B1：订单中心管理
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/order/index.vue'),
        meta: { title: '订单管理', icon: 'FileTextOutlined', permission: 'order:manage', cache: true },
      },
      {
        path: 'admin-accounts',
        name: 'AdminAccounts',
        component: () => import('@/views/account/AdminAccountManage.vue'),
        meta: { title: '账号管理', icon: 'UserOutlined', permission: 'account:view', cache: true },
      },
      // Sprint 1：自定义角色
      {
        path: 'custom-roles',
        name: 'CustomRoles',
        component: () => import('@/views/account/CustomRoleManage.vue'),
        meta: { title: '角色管理', icon: 'IdcardOutlined', permission: 'role:view', cache: true },
      },
      // 黑白名单管理（任务包 6-2：替换为 vehicle-list 新页面）
      {
        path: 'vehicle-list',
        name: 'VehicleList',
        component: () => import('@/views/vehicle-list/VehicleListManage.vue'),
        meta: { title: '黑白名单', icon: 'SafetyOutlined', permission: 'vehicle-list:view', cache: true },
      },
      // Phase 1 A3：系统参数管理
      {
        path: 'system-params',
        name: 'SystemParams',
        component: () => import('@/views/system-param/index.vue'),
        meta: { title: '系统参数', icon: 'SettingOutlined', permission: 'system:param:manage', cache: true },
      },
      // Phase 2 D2：异常记录管理
      {
        path: 'exception-records',
        name: 'ExceptionRecords',
        component: () => import('@/views/exception-record/index.vue'),
        meta: { title: '异常记录', icon: 'ExclamationCircleOutlined', permission: 'exception:view', cache: true },
      },
      // 包6-1：收入报表
      {
        path: 'report-revenue',
        name: 'ReportRevenue',
        component: () => import('@/views/report/RevenueReport.vue'),
        meta: { title: '收入报表', icon: 'DollarCircleOutlined', permission: 'dashboard:view', cache: true },
      },
      // 包6-1：车流量报表
      {
        path: 'report-traffic',
        name: 'ReportTraffic',
        component: () => import('@/views/report/TrafficReport.vue'),
        meta: { title: '车流量报表', icon: 'BarChartOutlined', permission: 'dashboard:view', cache: true },
      },

    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '403' },
  },
  {
    path: '/500',
    name: 'ServerError',
    component: () => import('@/views/error/500.vue'),
    meta: { title: '500' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
})

const WHITE_LIST = ['/login', '/register']

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()
  document.title = `${to.meta?.title || '智慧停车'} - 飓山智慧停车运营平台`

  if (WHITE_LIST.includes(to.path)) {
    if (authStore.isLoggedIn && to.path === '/login') {
      next('/dashboard')
    } else {
      next()
    }
    return
  }

  if (!authStore.isLoggedIn) {
    next(`/login?redirect=${to.path}`)
    return
  }

  if (!authStore.userInfo) {
    await authStore.fetchUserInfo()
    if (!authStore.isLoggedIn) {
      next(`/login?redirect=${to.path}`)
      return
    }
  }

  // 校验路由权限
  const requiredPermission = to.meta?.permission as string | undefined
  if (requiredPermission && !authStore.hasPermission(requiredPermission)) {
    next('/403')
    return
  }

  next()
})

export default router
