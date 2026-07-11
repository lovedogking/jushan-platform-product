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

  /**
   * 写入 Token 前校验 accessToken 是非空字符串。
   * 不满足时抛错，不写 localStorage，不进入已登录状态。
   */
  function validateAndSetToken(accessToken: unknown): string {
    if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
      throw new Error('服务端返回的 accessToken 无效')
    }
    const trimmed = accessToken.trim()
    token.value = trimmed
    localStorage.setItem(TOKEN_KEY, trimmed)
    return trimmed
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await loginApi({ username, password })
      // 登录成功后从 data 中提取 token 和用户信息
      validateAndSetToken(res.accessToken)
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
      clearLocalAuth()
      return null
    }
  }

  /**
   * 退出登录：调用远程 logout，但无论成败都清理本地状态。
   */
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

  /**
   * 仅清理本地认证状态（不调用远程 logout）。
   * 用于 HTTP 401 场景，避免递归 401。
   */
  function clearLocalAuth() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  async function init() {
    if (token.value) {
      await fetchUserInfo()
    }
  }

  return {
    token,
    userInfo,
    loading,
    isLoggedIn,
    username,
    displayName,
    login,
    logout,
    fetchUserInfo,
    clearLocalAuth,
    init,
  }
})
