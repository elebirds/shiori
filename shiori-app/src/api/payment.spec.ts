import { beforeEach, describe, expect, it, vi } from 'vitest'

import { httpGet, httpPost } from '@/api/http'
import { getWalletBalance, redeemCdk } from '@/api/payment'

vi.mock('@/api/http', () => ({
  httpGet: vi.fn(),
  httpPost: vi.fn(),
}))

describe('payment api', () => {
  beforeEach(() => {
    vi.mocked(httpGet).mockReset()
    vi.mocked(httpPost).mockReset()
  })

  it('should request wallet balance from payment service', async () => {
    vi.mocked(httpGet).mockResolvedValue({} as never)

    await getWalletBalance()

    expect(httpGet).toHaveBeenCalledTimes(1)
    expect(httpGet).toHaveBeenCalledWith('/api/v2/payment/wallet/balance')
  })

  it('should send redeem payload to cdk api', async () => {
    vi.mocked(httpPost).mockResolvedValue({} as never)

    await redeemCdk({ code: 'CDK-TEST-001' })

    expect(httpPost).toHaveBeenCalledTimes(1)
    expect(httpPost).toHaveBeenCalledWith('/api/v2/payment/cdks/redeem', { code: 'CDK-TEST-001' })
  })
})
