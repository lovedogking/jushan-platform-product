import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/home/index.vue'),
    meta: { title: '停车缴费' },
  },
  {
    path: '/pay/:orderId',
    name: 'PayConfirm',
    component: () => import('@/views/pay/confirm.vue'),
    meta: { title: '支付确认' },
  },
  {
    path: '/mock-pay/:orderId',
    name: 'MockPay',
    component: () => import('@/views/pay/mock.vue'),
    meta: { title: '模拟支付' },
  },
  {
    path: '/pay/result',
    name: 'PayResult',
    component: () => import('@/views/pay/result.vue'),
    meta: { title: '支付结果' },
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

router.beforeEach((to) => {
  document.title = `${to.meta?.title || '飓山智慧停车'} - 飓山智慧停车`
})

export default router
