import { beforeEach, describe, expect, it, vi } from 'vitest'

import { httpPost } from '@/api/http'
import { payOrderV2 } from '@/api/orderV2'

vi.mock('@/api/http', () => ({
  httpGet: vi.fn(),
  httpPost: vi.fn(),
}))

describe('orderV2 api', () => {
  beforeEach(() => {
    vi.mocked(httpPost).mockReset()
  })

  it('should call v2 pay api without request body and with idempotency key', async () => {
    vi.mocked(httpPost).mockResolvedValue({} as never)

    await payOrderV2('O202603060001', 'idem-v2-pay-1')

    expect(httpPost).toHaveBeenCalledTimes(1)
    expect(httpPost).toHaveBeenCalledWith('/api/v2/order/orders/O202603060001/pay', undefined, {
      headers: {
        'Idempotency-Key': 'idem-v2-pay-1',
      },
    })
  })
})
