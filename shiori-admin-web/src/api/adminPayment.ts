import { httpGet, httpPost } from '@/api/http'

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

export interface AdminWalletLedgerItemResponse {
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

export interface AdminWalletLedgerPageResponse {
  total: number
  page: number
  size: number
  items: AdminWalletLedgerItemResponse[]
}

export interface AdminWalletLedgerQuery {
  userId?: number
  bizType?: string
  bizNo?: string
  changeType?: string
  createdFrom?: string
  createdTo?: string
  page?: number
  size?: number
}

export interface ReconcileIssueResponse {
  issueNo: string
  issueType: string
  bizNo?: string
  severity: string
  status: string
  detailJson?: string
  createdAt: string
  updatedAt: string
}

export interface ReconcileIssuePageResponse {
  total: number
  page: number
  size: number
  items: ReconcileIssueResponse[]
}

export interface ReconcileIssueQuery {
  status?: string
  issueType?: string
  page?: number
  size?: number
}

export interface UpdateReconcileIssueStatusRequest {
  fromStatus: string
  toStatus: string
}

export function createAdminCdkBatch(payload: CreateCdkBatchRequest): Promise<CreateCdkBatchResponse> {
  return httpPost<CreateCdkBatchResponse>('/api/v2/admin/payments/cdks/batches', payload)
}

export function listAdminWalletLedgers(query: AdminWalletLedgerQuery): Promise<AdminWalletLedgerPageResponse> {
  return httpGet<AdminWalletLedgerPageResponse>('/api/v2/admin/payments/wallet-ledgers', { params: query })
}

export function listAdminReconcileIssues(query: ReconcileIssueQuery): Promise<ReconcileIssuePageResponse> {
  return httpGet<ReconcileIssuePageResponse>('/api/v2/admin/payments/reconcile/issues', { params: query })
}

export function updateAdminReconcileIssueStatus(issueNo: string, payload: UpdateReconcileIssueStatusRequest): Promise<void> {
  return httpPost<void>(`/api/v2/admin/payments/reconcile/issues/${issueNo}/status`, payload)
}
