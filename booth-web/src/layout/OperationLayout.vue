<template>
  <a-layout class="operation-layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible width="220">
      <div class="logo">飓山停车 · 车场运营</div>
      <a-menu v-model:selectedKeys="selectedKeys" theme="dark" mode="inline" @click="handleMenuClick">
        <a-menu-item key="/operation/dashboard"><template #icon><DashboardOutlined /></template><span>运营概览</span></a-menu-item>
        <a-menu-item key="/operation/analytics"><template #icon><BarChartOutlined /></template><span>运营数据</span></a-menu-item>
        <!-- SA-01/02/03 已移至平台管理区（V1.4） -->
        <a-menu-item key="/operation/vehicles"><template #icon><CarOutlined /></template><span>固定车管理 (OP-01)</span></a-menu-item>
        <a-menu-item key="/operation/access-records"><template #icon><FileTextOutlined /></template><span>通行记录 (OP-04)</span></a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="operation-header">
        <span class="header-title">车场运营区</span>
        <a-button type="text" @click="handleLogout"><template #icon><LogoutOutlined /></template>退出</a-button>
      </a-layout-header>
      <a-layout-content class="operation-content"><router-view /></a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { DashboardOutlined, CarOutlined, FileTextOutlined, BarChartOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { logout as logoutApi } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const selectedKeys = ref([route.path])

watch(() => route.path, (path) => { selectedKeys.value = [path] })
function handleMenuClick({ key }: { key: string }) { router.push(key) }
async function handleLogout() {
  try { await logoutApi() } catch { /* ignore */ }
  sessionStorage.clear(); router.push('/login')
}
</script>

<style lang="scss" scoped>
.operation-layout { height: 100vh; }
.logo { color: #fff; padding: 16px; font-size: 16px; font-weight: 700; text-align: center; }
.operation-header { background: #fff; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; border-bottom: 1px solid #f0f0f0; }
.header-title { font-size: 16px; font-weight: 600; }
.operation-content { margin: 16px; padding: 16px; background: #fff; border-radius: 4px; overflow: auto; }
</style>
