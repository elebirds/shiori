import { beforeEach, describe, expect, it, vi } from 'vitest'

import { httpPost } from '@/api/http'
import { createAdminCdkBatch } from '@/api/adminPayment'

vi.mock('@/api/http', () => ({
  httpGet: vi.fn(),
  httpPost: vi.fn(),
  httpPut: vi.fn(),
  httpDelete: vi.fn(),
}))

describe('admin payment api', () => {
  beforeEach(() => {
    vi.mocked(httpPost).mockReset()
  })

  it('should create cdk batch with expected payload', async () => {
    vi.mocked(httpPost).mockResolvedValue({} as never)

    await createAdminCdkBatch({
      quantity: 20,
      amountCent: 1200,
      expireAt: '2026-03-30T12:00:00',
    })

    expect(httpPost).toHaveBeenCalledTimes(1)
    expect(httpPost).toHaveBeenCalledWith('/api/v2/admin/payments/cdks/batches', {
      quantity: 20,
      amountCent: 1200,
      expireAt: '2026-03-30T12:00:00',
    })
  })
})
