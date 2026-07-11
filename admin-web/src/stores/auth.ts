import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types'
import { login as loginApi, logout as logoutApi, getUserInfo } from '@/api/auth'
import router from '@/router'

const TOKEN_KEY = 'parking_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await loginApi({ username, password })
      token.value = res.token
      localStorage.setItem(TOKEN_KEY, res.token)
      await fetchUserInfo()
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
      // ignore
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

  return { token, userInfo, loading, isLoggedIn, username, nickname, login, logout, fetchUserInfo, init }
})
