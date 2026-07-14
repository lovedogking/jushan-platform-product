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
      // Sprint 3：费用试算
      {
        path: 'fee-calculator',
        name: 'FeeCalculator',
        component: () => import('@/views/billing-rule/FeeCalculator.vue'),
        meta: { title: '费用试算', icon: 'CalculatorOutlined' },
      },
      // Sprint 1：公司管理
      {
        path: 'companies',
        name: 'Companies',
        component: () => import('@/views/company/CompanyManage.vue'),
        meta: { title: '公司管理', icon: 'ApartmentOutlined', permission: 'company:view', cache: true },
      },
      // Sprint 1：管理员账号
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
      // FIX-11：代理模式
      {
        path: 'proxy',
        name: 'Proxy',
        component: () => import('@/views/proxy/index.vue'),
        meta: { title: '代理管理', icon: 'SwapOutlined' },
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
