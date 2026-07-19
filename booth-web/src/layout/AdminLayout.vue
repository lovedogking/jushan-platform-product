<template>
  <a-layout class="admin-layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible width="220">
      <div class="logo">飓山停车 · 平台管理</div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        theme="dark"
        mode="inline"
        @click="handleMenuClick"
      >
        <a-menu-item key="/admin/accounts">
          <template #icon><UserOutlined /></template>
          <span>账号管理 (AD-01)</span>
        </a-menu-item>
        <a-menu-item key="/admin/parking">
          <template #icon><HomeOutlined /></template>
          <span>车场管理 (SA-01)</span>
        </a-menu-item>
	      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="admin-header">
        <span class="header-title">平台管理区</span>
        <a-button type="text" @click="handleLogout">
          <template #icon><LogoutOutlined /></template>
          退出
        </a-button>
      </a-layout-header>
      <a-layout-content class="admin-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { UserOutlined, HomeOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { logout as logoutApi } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const selectedKeys = ref([route.path])

watch(() => route.path, (path) => {
  selectedKeys.value = [path]
})

function handleMenuClick({ key }: { key: string }) {
  router.push(key)
}

async function handleLogout() {
  try { await logoutApi() } catch { /* ignore */ }
  sessionStorage.clear()
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.admin-layout { height: 100vh; }
.logo { color: #fff; padding: 16px; font-size: 16px; font-weight: 700; text-align: center; }
.admin-header { background: #fff; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; border-bottom: 1px solid #f0f0f0; }
.header-title { font-size: 16px; font-weight: 600; }
.admin-content { margin: 16px; padding: 16px; background: #fff; border-radius: 4px; overflow: auto; }
</style>
