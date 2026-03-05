import { beforeEach, describe, expect, it, vi } from 'vitest'

import { httpGet, httpPost } from '@/api/http'
import { applyOrderRefundV2, listSellerRefundsV2, payOrderV2 } from '@/api/orderV2'

vi.mock('@/api/http', () => ({
  httpGet: vi.fn(),
  httpPost: vi.fn(),
}))

describe('orderV2 api', () => {
  beforeEach(() => {
    vi.mocked(httpGet).mockReset()
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

  it('should post refund apply payload to buyer refund api', async () => {
    vi.mocked(httpPost).mockResolvedValue({} as never)

    await applyOrderRefundV2('O202603060001', { reason: '买家申请退款' })

    expect(httpPost).toHaveBeenCalledTimes(1)
    expect(httpPost).toHaveBeenCalledWith('/api/v2/order/orders/O202603060001/refunds', {
      reason: '买家申请退款',
    })
  })

  it('should query seller refund list with status and page params', async () => {
    vi.mocked(httpGet).mockResolvedValue({} as never)

    await listSellerRefundsV2({ status: 'REQUESTED', page: 2, size: 5 })

    expect(httpGet).toHaveBeenCalledTimes(1)
    expect(httpGet).toHaveBeenCalledWith('/api/v2/order/seller/refunds', {
      params: {
        status: 'REQUESTED',
        page: 2,
        size: 5,
      },
    })
  })
})
