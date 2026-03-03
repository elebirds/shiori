import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, clearTokenPair, getAccessToken, getRefreshToken, setTokenPair } from '@/api/http'
import { login, logout, type AuthUserInfo, type LoginRequest } from '@/api/auth'
import { ApiBizError } from '@/types/result'

const USER_STORAGE_KEY = 'shiori_admin_user'

function readUserFromStorage(): AuthUserInfo | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as AuthUserInfo
  } catch {
    localStorage.removeItem(USER_STORAGE_KEY)
    return null
  }
}

function hasAdminRole(user: AuthUserInfo | null): boolean {
  if (!user?.roles?.length) {
    return false
  }
  return user.roles.some((role) => role.toUpperCase() === 'ROLE_ADMIN')
}

export const useAuthStore = defineStore('admin-auth', () => {
  const accessToken = ref<string | null>(getAccessToken())
  const refreshToken = ref<string | null>(getRefreshToken())
  const user = ref<AuthUserInfo | null>(readUserFromStorage())

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value?.userId))
  const isAdmin = computed(() => hasAdminRole(user.value))

  function setSession(payload: { accessToken: string; refreshToken: string; user: AuthUserInfo }): void {
    accessToken.value = payload.accessToken
    refreshToken.value = payload.refreshToken
    user.value = payload.user
    setTokenPair(payload.accessToken, payload.refreshToken)
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(payload.user))
  }

  function clearAuth(): void {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    clearTokenPair()
    localStorage.removeItem(USER_STORAGE_KEY)
  }

  async function loginWithPassword(payload: LoginRequest): Promise<void> {
    const tokenPair = await login(payload)
    if (!hasAdminRole(tokenPair.user)) {
      clearAuth()
      throw new ApiBizError(10004, '当前账号不是管理员，无法登录后台', 403)
    }
    setSession({
      accessToken: tokenPair.accessToken,
      refreshToken: tokenPair.refreshToken,
      user: tokenPair.user,
    })
  }

  async function logoutSession(): Promise<void> {
    const token = getRefreshToken()
    try {
      if (token) {
        await logout({ refreshToken: token })
      }
    } finally {
      clearAuth()
    }
  }

  function restore(): void {
    accessToken.value = getAccessToken()
    refreshToken.value = getRefreshToken()
    user.value = readUserFromStorage()
    if (isAuthenticated.value && !isAdmin.value) {
      clearAuth()
    }
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    isAdmin,
    loginWithPassword,
    logoutSession,
    clearAuth,
    restore,
    accessTokenKey: ACCESS_TOKEN_KEY,
    refreshTokenKey: REFRESH_TOKEN_KEY,
    userStorageKey: USER_STORAGE_KEY,
  }
})
