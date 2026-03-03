import { afterEach, describe, expect, it } from 'vitest'

import { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY, clearTokenPair, setTokenPair } from '@/api/http'
import { ApiBizError, unwrapResult } from '@/types/result'

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
