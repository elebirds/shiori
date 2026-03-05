import { httpGet, httpPost } from '@/api/http'

export interface ChatTicketResponse {
  ticket: string
  expireAt: string
  buyerId: number
  sellerId: number
  listingId: number
  jti: string
}

export interface ChatStartConversationRequest {
  chatTicket: string
}

export interface ChatStartConversationResponse {
  conversationId: number
  listingId: number
  buyerId: number
  sellerId: number
  jti: string
  expireAt: string
}

export interface ChatMessageItem {
  messageId: number
  conversationId: number
  senderId: number
  receiverId: number
  clientMsgId: string
  content: string
  createdAt: string
}

export interface ChatConversationItem {
  conversationId: number
  listingId: number
  buyerId: number
  sellerId: number
  hasUnread: boolean
  updatedAt: string
  lastMessage?: ChatMessageItem
}

export interface ChatConversationsResponse {
  items: ChatConversationItem[]
  hasMore: boolean
  nextCursor: number
}

export interface ChatMessagesResponse {
  items: ChatMessageItem[]
  hasMore: boolean
  nextBefore: number
}

export interface ChatReadResponse {
  conversationId: number
  lastReadMsgId: number
}

export interface ChatSummaryResponse {
  unreadConversationCount: number
  unreadMessageCount: number
}

export function issueChatTicket(listingId: number): Promise<ChatTicketResponse> {
  return httpPost<ChatTicketResponse>(`/api/v2/product/chat/ticket?listingId=${encodeURIComponent(String(listingId))}`)
}

export function startConversation(payload: ChatStartConversationRequest): Promise<ChatStartConversationResponse> {
  return httpPost<ChatStartConversationResponse>('/api/chat/conversations/start', payload)
}

export function listConversations(params?: {
  cursor?: number
  limit?: number
}): Promise<ChatConversationsResponse> {
  return httpGet<ChatConversationsResponse>('/api/chat/conversations', { params })
}

export function listMessages(
  conversationId: number,
  params?: {
    before?: number
    limit?: number
  },
): Promise<ChatMessagesResponse> {
  return httpGet<ChatMessagesResponse>(`/api/chat/conversations/${conversationId}/messages`, { params })
}

export function readConversation(conversationId: number, lastReadMsgId: number): Promise<ChatReadResponse> {
  return httpPost<ChatReadResponse>(`/api/chat/conversations/${conversationId}/read`, { lastReadMsgId })
}

export function getChatSummary(): Promise<ChatSummaryResponse> {
  return httpGet<ChatSummaryResponse>('/api/chat/summary')
}
