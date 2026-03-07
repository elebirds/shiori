import { AxiosHeaders } from 'axios'
import { afterEach, beforeAll, describe, expect, it } from 'vitest'

import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, apiClient, clearTokenPair, setTokenPair } from '@/api/http'
import { ApiBizError, unwrapResult } from '@/types/result'

function ensureLocalStorageApi(): void {
  const target = globalThis.localStorage as Partial<Storage> | undefined
  const isValid =
    target &&
    typeof target.getItem === 'function' &&
    typeof target.setItem === 'function' &&
    typeof target.removeItem === 'function' &&
    typeof target.clear === 'function'

  if (isValid) {
    return
  }

  const memory = new Map<string, string>()
  const mockStorage: Storage = {
    get length() {
      return memory.size
    },
    clear() {
      memory.clear()
    },
    getItem(key: string) {
      return memory.has(key) ? memory.get(key) || null : null
    },
    key(index: number) {
      return Array.from(memory.keys())[index] ?? null
    },
    removeItem(key: string) {
      memory.delete(key)
    },
    setItem(key: string, value: string) {
      memory.set(String(key), String(value))
    },
  }

  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: mockStorage,
  })
}

beforeAll(() => {
  ensureLocalStorageApi()
})

afterEach(() => {
  clearTokenPair()
})

describe('http token helpers', () => {
  it('should persist token pair to localStorage', () => {
    setTokenPair('access-1', 'refresh-1')

    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBe('access-1')
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBe('refresh-1')
  })

  it('should clear token pair from localStorage', () => {
    setTokenPair('access-2', 'refresh-2')
    clearTokenPair()

    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull()
    expect(localStorage.getItem(REFRESH_TOKEN_KEY)).toBeNull()
  })

  it('should remove stale authorization header when access token is missing', async () => {
    setTokenPair('access-3', 'refresh-3')
    await apiClient.get('/api/ping-auth', {
      adapter: async (config) => ({
        data: { code: 0, message: 'ok', data: null, timestamp: Date.now() },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }),
    })

    clearTokenPair()
    let requestHeaders = AxiosHeaders.from({})
    await apiClient.get('/api/ping-public', {
      headers: { Authorization: 'Bearer stale-token' },
      adapter: async (config) => {
        requestHeaders = AxiosHeaders.from(config.headers)
        return {
          data: { code: 0, message: 'ok', data: null, timestamp: Date.now() },
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        }
      },
    })

    expect(requestHeaders.has('Authorization')).toBe(false)
  })
})

describe('result unwrap', () => {
  it('should return payload data when code is 0', () => {
    const data = unwrapResult<{ id: number }>({
      code: 0,
      message: '成功',
      data: { id: 1001 },
      timestamp: Date.now(),
    })

    expect(data.id).toBe(1001)
  })

  it('should throw ApiBizError for non-zero business code', () => {
    expect(() =>
      unwrapResult({
        code: 30001,
        message: '用户不存在',
        data: null,
        timestamp: Date.now(),
      }),
    ).toThrowError(ApiBizError)
  })

  it('should throw ApiBizError for malformed payload', () => {
    expect(() => unwrapResult('invalid-payload')).toThrowError(ApiBizError)
  })
})
