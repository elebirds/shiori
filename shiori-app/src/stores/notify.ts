import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

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
}

const WS_BASE_URL = import.meta.env.VITE_NOTIFY_WS_BASE_URL || 'ws://localhost:8090/ws'
const MAX_RECONNECT_DELAY_MS = 15000

export const useNotifyStore = defineStore('notify', () => {
  const connected = ref(false)
  const messages = ref<NotifyMessage[]>([])
  const lastError = ref<string>('')

  const unreadCount = computed(() => messages.value.filter((item) => !item.read).length)

  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectAttempt = 0
  let currentUserId = ''
  let manualClosed = false

  function connect(userId: string): void {
    if (!userId) {
      return
    }

    currentUserId = userId
    manualClosed = false
    clearReconnectTimer()

    if (socket) {
      socket.close()
      socket = null
    }

    const separator = WS_BASE_URL.includes('?') ? '&' : '?'
    const url = `${WS_BASE_URL}${separator}userId=${encodeURIComponent(userId)}`
    socket = new WebSocket(url)

    socket.onopen = () => {
      connected.value = true
      reconnectAttempt = 0
      lastError.value = ''
    }

    socket.onmessage = (event) => {
      try {
        const envelope = JSON.parse(event.data) as NotifyEnvelope
        if (!envelope.eventId || !envelope.type) {
          return
        }

        const duplicated = messages.value.some((item) => item.id === envelope.eventId)
        if (duplicated) {
          return
        }

        messages.value.unshift({
          id: envelope.eventId,
          type: envelope.type,
          aggregateId: envelope.aggregateId,
          createdAt: envelope.createdAt,
          payload: envelope.payload,
          receivedAt: Date.now(),
          read: false,
        })
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
    currentUserId = ''
    if (socket) {
      socket.close()
      socket = null
    }
  }

  function scheduleReconnect(): void {
    if (!currentUserId) {
      return
    }

    const delay = Math.min(1000 * 2 ** reconnectAttempt, MAX_RECONNECT_DELAY_MS)
    reconnectAttempt += 1

    clearReconnectTimer()
    reconnectTimer = setTimeout(() => {
      connect(currentUserId)
    }, delay)
  }

  function clearReconnectTimer(): void {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function markAllRead(): void {
    messages.value = messages.value.map((item) => ({ ...item, read: true }))
  }

  function markRead(messageId: string): void {
    messages.value = messages.value.map((item) => {
      if (item.id === messageId) {
        return { ...item, read: true }
      }
      return item
    })
  }

  function clearMessages(): void {
    messages.value = []
  }

  return {
    connected,
    lastError,
    unreadCount,
    messages,
    connect,
    disconnect,
    markAllRead,
    markRead,
    clearMessages,
  }
})
