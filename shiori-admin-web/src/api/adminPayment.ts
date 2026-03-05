import { httpPost } from '@/api/http'

export interface CreateCdkBatchRequest {
  quantity: number
  amountCent: number
  expireAt?: string
}

export interface CdkItemResponse {
  code: string
  codeMask: string
}

export interface CreateCdkBatchResponse {
  batchNo: string
  quantity: number
  amountCent: number
  expireAt?: string
  codes: CdkItemResponse[]
}

export function createAdminCdkBatch(payload: CreateCdkBatchRequest): Promise<CreateCdkBatchResponse> {
  return httpPost<CreateCdkBatchResponse>('/api/v2/admin/payments/cdks/batches', payload)
}
