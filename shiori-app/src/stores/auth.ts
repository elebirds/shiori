import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  ACCESS_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  clearTokenPair,
  getAccessToken,
  getRefreshToken,
  setTokenPair,
} from '@/api/http'
import {
  changePassword,
  getMyProfile,
  login,
  logout,
  refresh,
  register,
  uploadMyAvatar as uploadMyAvatarApi,
  updateMyProfile as updateMyProfileApi,
  type AuthUserInfo,
  type AvatarUploadResponse,
  type ChangePasswordRequest,
  type LoginRequest,
  type RegisterRequest,
  type UpdateProfileRequest,
  type UserProfile,
} from '@/api/auth'

const USER_STORAGE_KEY = 'shiori_auth_user'

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

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(getAccessToken())
  const refreshToken = ref<string | null>(getRefreshToken())
  const user = ref<AuthUserInfo | null>(readUserFromStorage())
  const profile = ref<UserProfile | null>(null)

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value?.userId))

  function setSession(payload: {
    accessToken: string
    refreshToken: string
    user: AuthUserInfo
  }): void {
    accessToken.value = payload.accessToken
    refreshToken.value = payload.refreshToken
    user.value = payload.user
    setTokenPair(payload.accessToken, payload.refreshToken)
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(payload.user))
  }

  function syncTokensFromStorage(): void {
    accessToken.value = getAccessToken()
    refreshToken.value = getRefreshToken()
  }

  function clearAuth(): void {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    profile.value = null
    clearTokenPair()
    localStorage.removeItem(USER_STORAGE_KEY)
  }

  function setMustChangePassword(required: boolean): void {
    if (!user.value) {
      return
    }
    user.value = {
      ...user.value,
      mustChangePassword: required,
    }
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user.value))
  }

  async function loginWithPassword(payload: LoginRequest): Promise<void> {
    const tokenPair = await login(payload)
    setSession({
      accessToken: tokenPair.accessToken,
      refreshToken: tokenPair.refreshToken,
      user: tokenPair.user,
    })
  }

  async function registerAccount(payload: RegisterRequest): Promise<void> {
    await register(payload)
  }

  async function refreshSession(): Promise<void> {
    syncTokensFromStorage()

    if (!refreshToken.value) {
      clearAuth()
      return
    }

    const tokenPair = await refresh({ refreshToken: refreshToken.value })
    setSession({
      accessToken: tokenPair.accessToken,
      refreshToken: tokenPair.refreshToken,
      user: tokenPair.user,
    })
  }

  async function logoutSession(): Promise<void> {
    syncTokensFromStorage()
    const currentRefreshToken = refreshToken.value
    try {
      if (currentRefreshToken) {
        await logout({ refreshToken: currentRefreshToken })
      }
    } finally {
      clearAuth()
    }
  }

  async function fetchMyProfile(): Promise<UserProfile> {
    const data = await getMyProfile()
    profile.value = data
    return data
  }

  async function updateMyProfile(payload: UpdateProfileRequest): Promise<UserProfile> {
    const data = await updateMyProfileApi(payload)
    profile.value = data
    return data
  }

  async function uploadMyAvatar(file: File): Promise<AvatarUploadResponse> {
    const data = await uploadMyAvatarApi(file)
    if (profile.value) {
      profile.value = {
        ...profile.value,
        avatarUrl: data.avatarUrl,
      }
    }
    return data
  }

  async function changeMyPassword(payload: ChangePasswordRequest): Promise<void> {
    await changePassword(payload)
    setMustChangePassword(false)
  }

  return {
    accessToken,
    refreshToken,
    user,
    profile,
    isAuthenticated,
    setSession,
    clearAuth,
    loginWithPassword,
    registerAccount,
    refreshSession,
    logoutSession,
    fetchMyProfile,
    updateMyProfile,
    uploadMyAvatar,
    changeMyPassword,
    setMustChangePassword,
    syncTokensFromStorage,
    userStorageKey: USER_STORAGE_KEY,
    accessTokenKey: ACCESS_TOKEN_KEY,
    refreshTokenKey: REFRESH_TOKEN_KEY,
  }
})
