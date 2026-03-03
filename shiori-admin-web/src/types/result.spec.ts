import { describe, expect, it } from 'vitest'

import { ApiBizError, unwrapResult } from '@/types/result'

describe('result unwrap', () => {
  it('should unwrap success payload', () => {
    const data = unwrapResult<{ ok: boolean }>({
      code: 0,
      message: '成功',
      data: { ok: true },
      timestamp: Date.now(),
    })

    expect(data.ok).toBe(true)
  })

  it('should throw when code is non-zero', () => {
    expect(() =>
      unwrapResult({
        code: 10004,
        message: '无权限',
        data: null,
        timestamp: Date.now(),
      }),
    ).toThrowError(ApiBizError)
  })
})
