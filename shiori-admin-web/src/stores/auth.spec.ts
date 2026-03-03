import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn(),
}))

afterEach(() => {
  localStorage.clear()
  vi.resetAllMocks()
})

describe('admin auth store', () => {
  it('should reject non-admin login', async () => {
    setActivePinia(createPinia())
    const store = useAuthStore()

    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'a',
      refreshToken: 'r',
      accessTokenExpiresIn: 900,
      refreshTokenExpiresIn: 604800,
      tokenType: 'Bearer',
      user: {
        userId: 1,
        userNo: 'U1',
        username: 'u1',
        roles: ['ROLE_USER'],
      },
    })

    await expect(store.loginWithPassword({ username: 'u1', password: '12345678' })).rejects.toThrowError()
    expect(store.isAuthenticated).toBe(false)
  })

  it('should accept admin login', async () => {
    setActivePinia(createPinia())
    const store = useAuthStore()

    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'a2',
      refreshToken: 'r2',
      accessTokenExpiresIn: 900,
      refreshTokenExpiresIn: 604800,
      tokenType: 'Bearer',
      user: {
        userId: 2,
        userNo: 'U2',
        username: 'admin',
        roles: ['ROLE_ADMIN'],
      },
    })

    await store.loginWithPassword({ username: 'admin', password: '12345678' })
    expect(store.isAuthenticated).toBe(true)
    expect(store.isAdmin).toBe(true)
  })
})
