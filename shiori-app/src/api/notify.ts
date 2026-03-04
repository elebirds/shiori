import { httpGet, httpPost } from '@/api/http'

export interface NotifyEventItem {
  eventId: string
  type: string
  aggregateId: string
  createdAt: string
  payload: Record<string, unknown>
  read: boolean
  readAt?: string
}

export interface NotifyEventsResponse {
  afterEventId: string
  limit: number
  items: NotifyEventItem[]
  nextEventId: string
  hasMore: boolean
}

export interface NotifySummaryResponse {
  unreadCount: number
}

export interface NotifyMarkReadResponse {
  eventId: string
  marked: boolean
}

export interface NotifyReadAllResponse {
  affected: number
}

export function listNotifyEvents(params?: {
  afterEventId?: string
  limit?: number
}): Promise<NotifyEventsResponse> {
  return httpGet('/api/notify/events', { params })
}

export function markNotifyEventRead(eventId: string): Promise<NotifyMarkReadResponse> {
  return httpPost(`/api/notify/events/${eventId}/read`)
}

export function markAllNotifyEventsRead(): Promise<NotifyReadAllResponse> {
  return httpPost('/api/notify/events/read-all')
}

export function getNotifySummary(): Promise<NotifySummaryResponse> {
  return httpGet('/api/notify/summary')
}

