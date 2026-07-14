import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types'
import { login as loginApi, logout as logoutApi, getUserInfo, refreshToken as refreshTokenApi } from '@/api/auth'
import router from '@/router'

const TOKEN_KEY = 'jushan_access_token'
const REFRESH_TOKEN_KEY = 'jushan_refresh_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<Set<string>>(new Set())
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const displayName = computed(() => userInfo.value?.realName || userInfo.value?.username || '')
  const level = computed(() => userInfo.value?.level || 0)
  const tenantId = computed(() => userInfo.value?.tenantId)

  /**
   * 是否有指定权限（支持单个权限码或权限码数组，满足任意一个即可）
   */
  function hasPermission(value: string | string[]) {
    const required = Array.isArray(value) ? value : [value]
    // 拥有 * 表示超级权限
    if (permissions.value.has('*')) {
      return true
    }
    return required.some(p => permissions.value.has(p))
  }

  /**
   * 写入 Token。
   */
  function setToken(accessToken: string, refresh: string) {
    token.value = accessToken
    refreshToken.value = refresh
    localStorage.setItem(TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refresh)
  }

  /**
   * 设置用户信息和权限。
   */
  function setUserInfo(info: UserInfo, perms: string[]) {
    userInfo.value = info
    permissions.value = new Set(perms || [])
  }

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await loginApi({ username, password })
      setToken(res.token, res.refreshToken)
      setUserInfo(res.userInfo, res.permissions)
      return res
    } finally {
      loading.value = false
    }
  }

  /**
   * 使用 refreshToken 刷新 accessToken。
   */
  async function doRefreshToken() {
    if (!refreshToken.value) {
      throw new Error('无 refreshToken')
    }
    const res = await refreshTokenApi(refreshToken.value)
    setToken(res.token, res.refreshToken)
    if (res.userInfo) {
      setUserInfo(res.userInfo, res.permissions)
    }
    return res
  }

  async function fetchUserInfo() {
    if (!token.value) return null
    try {
      const info = await getUserInfo()
      userInfo.value = info
      // 从 JWT Token 中恢复权限（避免页面刷新后权限丢失）
      restorePermissionsFromToken()
      return info
    } catch {
      clearLocalAuth()
      return null
    }
  }

  /**
   * 从 JWT Token 的 payload 中解码并恢复权限。
   * JWT 格式：header.payload.signature，payload 为 Base64Url 编码的 JSON。
   */
  function restorePermissionsFromToken() {
    try {
      const parts = token.value.split('.')
      if (parts.length !== 3) return
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
      const perms = payload.permissions
      if (perms && typeof perms === 'string' && perms.length > 0) {
        permissions.value = new Set(perms.split(',').map((p: string) => p.trim()).filter((p: string) => p.length > 0))
      }
    } catch {
      // JWT 解析失败，保持现有权限状态
    }
  }

  /**
   * 退出登录：调用远程 logout，无论成败都清理本地状态。
   */
  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 即使后端退出接口失败，也应清理前端登录态
    } finally {
      clearLocalAuth()
      router.push('/login')
    }
  }

  /**
   * 仅清理本地认证状态（不调用远程 logout）。
   * 用于 HTTP 401 场景，避免递归 401。
   */
  function clearLocalAuth() {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    permissions.value = new Set()
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }

  async function init() {
    if (token.value) {
      await fetchUserInfo()
    }
  }

  return {
    token,
    refreshToken,
    userInfo,
    permissions,
    loading,
    isLoggedIn,
    username,
    displayName,
    level,
    tenantId,
    hasPermission,
    setToken,
    setUserInfo,
    login,
    logout,
    doRefreshToken,
    fetchUserInfo,
    clearLocalAuth,
    init,
  }
})
