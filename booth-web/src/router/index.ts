import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '岗亭登录' },
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layout/index.vue'),
    redirect: '/monitor',
    children: [
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('@/views/monitor/index.vue'),
        meta: { title: '实时监控' },
      },
    ],
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

const WHITE_LIST = ['/login']

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta?.title || '岗亭端'} - 飓山智慧停车`

  if (WHITE_LIST.includes(to.path)) {
    const token = localStorage.getItem('booth_token')
    if (token && to.path === '/login') {
      next('/monitor')
    } else {
      next()
    }
    return
  }

  const token = localStorage.getItem('booth_token')
  if (!token) {
    next(`/login?redirect=${to.path}`)
    return
  }

  next()
})

export default router
