import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
  },
  // 岗亭工作区（GB: 岗亭管理员）
  {
    path: '/booth',
    name: 'BoothLayout',
    component: () => import('@/layout/index.vue'),
    redirect: '/booth/monitor',
    meta: { title: '岗亭工作区', roles: ['booth'] },
    children: [
      {
        path: 'monitor',
        name: 'BoothMonitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: '实时监控' },
      },
    ],
  },
  // 车场运营区（SA/OP: 超管 + 租户管理员）
  {
    path: '/operation',
    name: 'OperationLayout',
    component: () => import('@/layout/OperationLayout.vue'),
    redirect: '/operation/dashboard',
    meta: { title: '车场运营区', roles: ['platform', 'tenant'] },
    children: [
      {
        path: 'dashboard',
        name: 'OperationDashboard',
        component: () => import('@/views/operation/Dashboard.vue'),
        meta: { title: '运营概览' },
      },
      {
        path: 'analytics',
        name: 'OperationAnalytics',
        component: () => import('@/views/operation/Analytics.vue'),
        meta: { title: '运营数据' },
      },
      // SA-01/02/03 已移至平台管理区（V1.4 权限矩阵修正）
      {
        path: 'vehicles',
        name: 'Vehicles',
        component: () => import('@/views/operation/Vehicles.vue'),
        meta: { title: '固定车管理' },
      },
      {
        path: 'access-records',
        name: 'AccessRecords',
        component: () => import('@/views/operation/AccessRecords.vue'),
        meta: { title: '通行记录' },
      },
    ],
  },
  // 平台管理区（AD/SA: 超管专属 — V1.4 车场/车道/设备管理也在此区域）
  {
    path: '/admin',
    name: 'AdminLayout',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/admin/accounts',
    meta: { title: '平台管理区', roles: ['platform'] },
    children: [
      {
        path: 'accounts',
        name: 'AdminAccounts',
        component: () => import('@/views/admin/Accounts.vue'),
        meta: { title: '账号管理' },
      },
      {
        path: 'parking',
        name: 'AdminParking',
        component: () => import('@/views/operation/ParkingManage.vue'),
        meta: { title: '车场管理' },
      },
    ],
  },
  // 兼容旧路径 /monitor 重定向
  {
    path: '/monitor',
    redirect: '/booth/monitor',
  },
  {
    path: '/',
    redirect: '/booth/monitor',
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
  routes,
})

const TOKEN_KEY = 'jushan_access_token'
const ROLES_KEY = 'jushan_roles'

const WHITE_LIST = ['/login']

/** 从 sessionStorage 获取用户角色列表 */
function getUserRoles(): string[] {
  try {
    const raw = sessionStorage.getItem(ROLES_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

/** 检查用户是否有权限访问目标路由 */
function checkRoleAccess(targetRoles?: string[]): boolean {
  if (!targetRoles || targetRoles.length === 0) return true
  const userRoles = getUserRoles()
  return targetRoles.some(r => userRoles.includes(r))
}

/** 根据角色获取默认跳转路径 */
function getDefaultRoute(): string {
  const roles = getUserRoles()
  if (roles.includes('platform')) return '/admin/accounts'
  if (roles.includes('tenant')) return '/operation/dashboard'
  if (roles.includes('booth')) return '/booth/monitor'
  return '/login'
}

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta?.title || '飓山智慧停车'} - 飓山智慧停车`

  if (WHITE_LIST.includes(to.path)) {
    const token = sessionStorage.getItem(TOKEN_KEY)
    if (token && to.path === '/login') {
      next(getDefaultRoute())
    } else {
      next()
    }
    return
  }

  const token = sessionStorage.getItem(TOKEN_KEY)
  if (!token) {
    next(`/login?redirect=${to.path}`)
    return
  }

  // 角色鉴权
  const targetRoles = to.meta?.roles as string[] | undefined
  if (!checkRoleAccess(targetRoles)) {
    next(getDefaultRoute())
    return
  }

  next()
})

export default router
