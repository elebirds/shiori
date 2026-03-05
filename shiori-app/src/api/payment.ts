import { httpGet, httpPost } from '@/api/http'

export interface WalletBalanceResponse {
  availableBalanceCent: number
  frozenBalanceCent: number
  totalBalanceCent: number
}

export interface RedeemCdkRequest {
  code: string
}

export interface RedeemCdkResponse {
  redeemAmountCent: number
  availableBalanceCent: number
  frozenBalanceCent: number
}

export interface WalletLedgerItemResponse {
  id: number
  userId: number
  bizType: string
  bizNo: string
  changeType: string
  deltaAvailableCent: number
  deltaFrozenCent: number
  availableAfterCent: number
  frozenAfterCent: number
  remark?: string
  createdAt: string
}

export interface WalletLedgerPageResponse {
  total: number
  page: number
  size: number
  items: WalletLedgerItemResponse[]
}

export interface WalletLedgerQuery {
  bizType?: string
  bizNo?: string
  changeType?: string
  createdFrom?: string
  createdTo?: string
  page?: number
  size?: number
}

export function getWalletBalance(): Promise<WalletBalanceResponse> {
  return httpGet<WalletBalanceResponse>('/api/v2/payment/wallet/balance')
}

export function redeemCdk(payload: RedeemCdkRequest): Promise<RedeemCdkResponse> {
  return httpPost<RedeemCdkResponse>('/api/v2/payment/cdks/redeem', payload)
}

export function listWalletLedger(query: WalletLedgerQuery): Promise<WalletLedgerPageResponse> {
  return httpGet<WalletLedgerPageResponse>('/api/v2/payment/wallet/ledger', { params: query })
}
