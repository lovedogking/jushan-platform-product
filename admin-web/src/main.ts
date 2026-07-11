import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import 'ant-design-vue/dist/reset.css'
import './styles/index.scss'
import { useAuthStore } from './stores'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

// 初始化认证状态（页面刷新时恢复登录态）
useAuthStore().init()

app.mount('#app')
