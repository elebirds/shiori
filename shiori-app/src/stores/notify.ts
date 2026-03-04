import { ref } from 'vue'
import { defineStore } from 'pinia'

import { getAccessToken } from '@/api/http'
import {
  getNotifySummary,
  listNotifyEvents,
  markAllNotifyEventsRead,
  markNotifyEventRead,
  type NotifyEventItem,
} from '@/api/notify'

export interface NotifyEnvelope {
  eventId: string
  type: string
  aggregateId: string
  createdAt: string
  payload: Record<string, unknown>
}

export interface NotifyMessage {
  id: string
  type: string
  aggregateId: string
  createdAt: string
  payload: Record<string, unknown>
  receivedAt: number
  read: boolean
  readAt?: string
}

const WS_BASE_URL = import.meta.env.VITE_NOTIFY_WS_BASE_URL || 'ws://localhost:8090/ws'
const MAX_RECONNECT_DELAY_MS = 15000
const DEFAULT_SYNC_LIMIT = 100

export const useNotifyStore = defineStore('notify', () => {
  const connected = ref(false)
  const messages = ref<NotifyMessage[]>([])
  const unreadCount = ref(0)
  const lastError = ref('')

  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempt = 0
  let currentAccessToken = ''
  let manualClosed = false

  function connect(accessToken?: string): void {
    const token = (accessToken || getAccessToken() || '').trim()
    if (!token) {
      return
    }

    currentAccessToken = token
    manualClosed = false
    clearReconnectTimer()

    if (socket) {
      socket.close()
      socket = null
    }

    const wsURL = buildWSURL(token, latestEventID())
    socket = new WebSocket(wsURL)

    socket.onopen = () => {
      connected.value = true
      reconnectAttempt = 0
      lastError.value = ''
      void syncFromServer()
    }

    socket.onmessage = (evt) => {
      try {
        const envelope = JSON.parse(evt.data) as NotifyEnvelope
        if (!envelope.eventId || !envelope.type) {
          return
        }
        if (hasMessage(envelope.eventId)) {
          return
        }

        messages.value.unshift({
          id: envelope.eventId,
          type: envelope.type,
          aggregateId: envelope.aggregateId,
          createdAt: envelope.createdAt,
          payload: envelope.payload || {},
          receivedAt: Date.now(),
          read: false,
        })
        unreadCount.value += 1
      } catch {
        lastError.value = '通知消息解析失败'
      }
    }

    socket.onerror = () => {
      lastError.value = '通知连接出现异常'
    }

    socket.onclose = () => {
      connected.value = false
      socket = null
      if (!manualClosed) {
        scheduleReconnect()
      }
    }
  }

  function disconnect(): void {
    manualClosed = true
    clearReconnectTimer()
    connected.value = false
    currentAccessToken = ''
    if (socket) {
      socket.close()
      socket = null
    }
  }

  async function syncFromServer(): Promise<void> {
    if (!currentAccessToken) {
      return
    }

    try {
      const [eventsResp, summaryResp] = await Promise.all([
        listNotifyEvents({ limit: DEFAULT_SYNC_LIMIT }),
        getNotifySummary(),
      ])

      mergeMessages(eventsResp.items)
      unreadCount.value = summaryResp.unreadCount
      lastError.value = ''
    } catch (error) {
      lastError.value = error instanceof Error ? error.message : '通知同步失败'
    }
  }

  async function markAllRead(): Promise<void> {
    try {
      const resp = await markAllNotifyEventsRead()
      if (resp.affected > 0) {
        messages.value = messages.value.map((item) => ({
          ...item,
          read: true,
          readAt: item.readAt || new Date().toISOString(),
        }))
      }
      unreadCount.value = Math.max(unreadCount.value - resp.affected, 0)
    } catch (error) {
      lastError.value = error instanceof Error ? error.message : '全部已读失败'
    }
  }

  async function markRead(messageID: string): Promise<void> {
    if (!messageID) {
      return
    }
    try {
      await markNotifyEventRead(messageID)
      let changed = false
      messages.value = messages.value.map((item) => {
        if (item.id !== messageID || item.read) {
          return item
        }
        changed = true
        return {
          ...item,
          read: true,
          readAt: new Date().toISOString(),
        }
      })
      if (changed) {
        unreadCount.value = Math.max(unreadCount.value - 1, 0)
      }
    } catch (error) {
      lastError.value = error instanceof Error ? error.message : '标记已读失败'
    }
  }

  function clearMessages(): void {
    messages.value = []
    unreadCount.value = 0
  }

  function scheduleReconnect(): void {
    if (!currentAccessToken) {
      return
    }

    const delay = Math.min(1000 * 2 ** reconnectAttempt, MAX_RECONNECT_DELAY_MS)
    reconnectAttempt += 1

    clearReconnectTimer()
    reconnectTimer = setTimeout(() => {
      connect(currentAccessToken)
    }, delay)
  }

  function clearReconnectTimer(): void {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function hasMessage(eventID: string): boolean {
    return messages.value.some((item) => item.id === eventID)
  }

  function latestEventID(): string {
    return messages.value[0]?.id || ''
  }

  function mergeMessages(items: NotifyEventItem[]): void {
    if (!items || items.length === 0) {
      return
    }

    const merged = new Map<string, NotifyMessage>()
    for (const item of messages.value) {
      merged.set(item.id, item)
    }
    for (const item of items) {
      merged.set(item.eventId, {
        id: item.eventId,
        type: item.type,
        aggregateId: item.aggregateId,
        createdAt: item.createdAt,
        payload: item.payload || {},
        receivedAt: Date.now(),
        read: item.read,
        readAt: item.readAt,
      })
    }

    messages.value = Array.from(merged.values()).sort((a, b) => {
      const tsA = Date.parse(a.createdAt) || a.receivedAt
      const tsB = Date.parse(b.createdAt) || b.receivedAt
      return tsB - tsA
    })
  }

  function buildWSURL(accessToken: string, lastEventID: string): string {
    const base = resolveWSBaseURL(WS_BASE_URL)
    const separator = base.includes('?') ? '&' : '?'
    let url = `${base}${separator}accessToken=${encodeURIComponent(accessToken)}`
    if (lastEventID) {
      url += `&lastEventId=${encodeURIComponent(lastEventID)}`
    }
    return url
  }

  return {
    connected,
    lastError,
    unreadCount,
    messages,
    connect,
    disconnect,
    syncFromServer,
    markAllRead,
    markRead,
    clearMessages,
  }
})

function resolveWSBaseURL(rawBaseURL: string): string {
  const trimmed = rawBaseURL.trim()
  if (trimmed.startsWith('ws://') || trimmed.startsWith('wss://')) {
    return trimmed
  }
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  if (trimmed.startsWith('/')) {
    return `${proto}//${window.location.host}${trimmed}`
  }
  return `${proto}//${window.location.host}/${trimmed}`
}
