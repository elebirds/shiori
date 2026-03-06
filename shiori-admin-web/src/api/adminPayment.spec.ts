import { beforeEach, describe, expect, it, vi } from 'vitest'

import { httpGet, httpPost } from '@/api/http'
import { createAdminCdkBatch, listAdminWalletLedgers, updateAdminReconcileIssueStatus } from '@/api/adminPayment'

vi.mock('@/api/http', () => ({
  httpGet: vi.fn(),
  httpPost: vi.fn(),
  httpPut: vi.fn(),
  httpDelete: vi.fn(),
}))

describe('admin payment api', () => {
  beforeEach(() => {
    vi.mocked(httpGet).mockReset()
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

  it('should query admin wallet ledgers with pagination params', async () => {
    vi.mocked(httpGet).mockResolvedValue({} as never)

    await listAdminWalletLedgers({ userId: 101, page: 2, size: 30 })

    expect(httpGet).toHaveBeenCalledTimes(1)
    expect(httpGet).toHaveBeenCalledWith('/api/v2/admin/payments/wallet-ledgers', {
      params: {
        userId: 101,
        page: 2,
        size: 30,
      },
    })
  })

  it('should update reconcile issue status', async () => {
    vi.mocked(httpPost).mockResolvedValue(undefined as never)

    await updateAdminReconcileIssueStatus('RI202603060001', {
      fromStatus: 'OPEN',
      toStatus: 'CONFIRMED',
    })

    expect(httpPost).toHaveBeenCalledTimes(1)
    expect(httpPost).toHaveBeenCalledWith('/api/v2/admin/payments/reconcile/issues/RI202603060001/status', {
      fromStatus: 'OPEN',
      toStatus: 'CONFIRMED',
    })
  })
})
