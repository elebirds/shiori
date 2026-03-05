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

export function getWalletBalance(): Promise<WalletBalanceResponse> {
  return httpGet<WalletBalanceResponse>('/api/v2/payment/wallet/balance')
}

export function redeemCdk(payload: RedeemCdkRequest): Promise<RedeemCdkResponse> {
  return httpPost<RedeemCdkResponse>('/api/v2/payment/cdks/redeem', payload)
}
