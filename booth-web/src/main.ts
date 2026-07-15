import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import 'ant-design-vue/dist/reset.css'
import './styles/index.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

app.mount('#app')

// 注册 Service Worker（PWA 离线缓存）
// 使用动态 import 避免阻塞首屏渲染
if ('serviceWorker' in navigator) {
  import('virtual:pwa-register')
    .then(({ registerSW }) => {
      registerSW({
        onNeedRefresh() {
          // 有新版本可用时自动更新
          console.info('[PWA] 新版本可用，即将更新...')
        },
        onOfflineReady() {
          console.info('[PWA] 应用已可离线使用')
        },
        onRegistered(registration: ServiceWorkerRegistration | undefined) {
          if (registration) {
            console.info('[PWA] Service Worker 注册成功:', registration.scope)
            // 每小时检查一次更新
            setInterval(() => {
              registration.update().catch(() => {
                // 更新检查失败，静默忽略
              })
            }, 60 * 60 * 1000)
          }
        },
        onRegisterError(error: Error) {
          console.warn('[PWA] Service Worker 注册失败:', error.message)
        },
      })
    })
    .catch(() => {
      // virtual:pwa-register 仅在 vite-plugin-pwa 启用时可用
      console.debug('[PWA] PWA 插件未启用，跳过 Service Worker 注册')
    })
}
