import { httpDelete, httpGet, httpPost, httpPut } from '@/api/http'

export interface ChatReportItem {
  id: number
  reporterUserId: number
  targetUserId: number
  conversationId: number
  messageId?: number
  reason: string
  status: string
  remark?: string
  handledBy?: number
  handledAt?: string
  createdAt: string
  updatedAt: string
}

export interface ChatBlockItem {
  blockerUserId: number
  targetUserId: number
  createdAt: string
}

export interface ForbiddenWordItem {
  id: number
  word: string
  matchType: string
  policy: string
  mask: string
  status: string
  createdBy?: number
  updatedBy?: number
  createdAt: string
  updatedAt: string
}

export interface ListReportsParams {
  page?: number
  size?: number
  status?: string
}

export interface ListBlocksParams {
  page?: number
  size?: number
  blockerUserId?: number
  targetUserId?: number
}

export interface UpsertForbiddenWordPayload {
  word: string
  matchType?: 'EXACT' | 'KEYWORD'
  policy?: 'MASK' | 'REJECT'
  mask?: string
  status?: 'ACTIVE' | 'DISABLED'
}

export async function listChatReports(params: ListReportsParams = {}): Promise<{
  items: ChatReportItem[]
  total: number
  page: number
  size: number
}> {
  return httpGet('/api/admin/chat/reports', { params })
}

export async function handleChatReport(
  reportId: number,
  payload: {
    status: 'PENDING' | 'RESOLVED' | 'REJECTED' | 'IGNORED'
    remark?: string
  },
): Promise<{ reportId: number; status: string }> {
  return httpPost(`/api/admin/chat/reports/${reportId}/handle`, payload)
}

export async function listChatBlocks(params: ListBlocksParams = {}): Promise<{
  items: ChatBlockItem[]
  total: number
  page: number
  size: number
}> {
  return httpGet('/api/admin/chat/blocks', { params })
}

export async function listForbiddenWords(params?: { includeDisabled?: boolean }): Promise<{ items: ForbiddenWordItem[] }> {
  return httpGet('/api/admin/chat/forbidden-words', { params })
}

export async function createForbiddenWord(payload: UpsertForbiddenWordPayload): Promise<ForbiddenWordItem> {
  return httpPost('/api/admin/chat/forbidden-words', payload)
}

export async function updateForbiddenWord(ruleId: number, payload: UpsertForbiddenWordPayload): Promise<ForbiddenWordItem> {
  return httpPut(`/api/admin/chat/forbidden-words/${ruleId}`, payload)
}

export async function deleteForbiddenWord(ruleId: number): Promise<{ ruleId: number }> {
  return httpDelete(`/api/admin/chat/forbidden-words/${ruleId}`)
}
