import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types'
import { login as loginApi, logout as logoutApi, getUserInfo } from '@/api/auth'
import router from '@/router'

const TOKEN_KEY = 'jushan_access_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const displayName = computed(() => userInfo.value?.displayName || userInfo.value?.username || '')

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await loginApi({ username, password })
      // 登录成功后从 data 中提取 token 和用户信息
      token.value = res.accessToken
      localStorage.setItem(TOKEN_KEY, res.accessToken)
      userInfo.value = res.user
      return res
    } finally {
      loading.value = false
    }
  }

  async function fetchUserInfo() {
    if (!token.value) return null
    try {
      const info = await getUserInfo()
      userInfo.value = info
      return info
    } catch {
      logout()
      return null
    }
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 即使后端退出接口失败，也应清理前端登录态
    } finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem(TOKEN_KEY)
      router.push('/login')
    }
  }

  async function init() {
    if (token.value) {
      await fetchUserInfo()
    }
  }

  return { token, userInfo, loading, isLoggedIn, username, displayName, login, logout, fetchUserInfo, init }
})
