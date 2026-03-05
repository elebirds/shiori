import { ref } from 'vue'
import { defineStore } from 'pinia'

const MERGE_WINDOW_MS = 10_000
const AUTO_DISMISS_MS = 8_000
const MAX_POPUP_ITEMS = 3

export interface ChatPopupEnqueuePayload {
  conversationId: number
  senderId: number
  content: string
  createdAt: string
  peerNickname: string
  peerAvatarUrl?: string
}

export interface ChatPopupItem {
  id: string
  conversationId: number
  senderId: number
  content: string
  createdAt: string
  peerNickname: string
  peerAvatarUrl: string
  unreadCount: number
  lastIncomingAt: number
}

export const useChatPopupStore = defineStore('chat-popup', () => {
  const items = ref<ChatPopupItem[]>([])
  const timers = new Map<string, ReturnType<typeof setTimeout>>()

  function enqueue(payload: ChatPopupEnqueuePayload): void {
    const now = Date.now()
    const targetIndex = items.value.findIndex(
      (item) => item.conversationId === payload.conversationId && now - item.lastIncomingAt <= MERGE_WINDOW_MS,
    )
    if (targetIndex >= 0) {
      const existing = items.value[targetIndex]
      const merged: ChatPopupItem = {
        ...existing,
        senderId: payload.senderId,
        content: payload.content,
        createdAt: payload.createdAt,
        peerNickname: payload.peerNickname || existing.peerNickname,
        peerAvatarUrl: payload.peerAvatarUrl || existing.peerAvatarUrl,
        unreadCount: existing.unreadCount + 1,
        lastIncomingAt: now,
      }
      const next = items.value.slice()
      next.splice(targetIndex, 1)
      next.unshift(merged)
      items.value = next
      scheduleAutoDismiss(merged.id)
      return
    }

    const newItem: ChatPopupItem = {
      id: `chat-popup-${payload.conversationId}-${now}-${Math.random().toString(36).slice(2, 8)}`,
      conversationId: payload.conversationId,
      senderId: payload.senderId,
      content: payload.content,
      createdAt: payload.createdAt,
      peerNickname: payload.peerNickname,
      peerAvatarUrl: payload.peerAvatarUrl || '',
      unreadCount: 1,
      lastIncomingAt: now,
    }
    items.value = [newItem, ...items.value]
    scheduleAutoDismiss(newItem.id)
    trimOverflow()
  }

  function dismiss(id: string): void {
    clearItemTimer(id)
    items.value = items.value.filter((item) => item.id !== id)
  }

  function dismissConversation(conversationId: number): void {
    if (!conversationId) {
      return
    }
    const targets = items.value.filter((item) => item.conversationId === conversationId)
    for (const item of targets) {
      clearItemTimer(item.id)
    }
    items.value = items.value.filter((item) => item.conversationId !== conversationId)
  }

  function clearAll(): void {
    for (const id of timers.keys()) {
      clearItemTimer(id)
    }
    items.value = []
  }

  function trimOverflow(): void {
    if (items.value.length <= MAX_POPUP_ITEMS) {
      return
    }
    const overflow = items.value.slice(MAX_POPUP_ITEMS)
    for (const item of overflow) {
      clearItemTimer(item.id)
    }
    items.value = items.value.slice(0, MAX_POPUP_ITEMS)
  }

  function scheduleAutoDismiss(id: string): void {
    clearItemTimer(id)
    const timer = setTimeout(() => {
      dismiss(id)
    }, AUTO_DISMISS_MS)
    timers.set(id, timer)
  }

  function clearItemTimer(id: string): void {
    const timer = timers.get(id)
    if (!timer) {
      return
    }
    clearTimeout(timer)
    timers.delete(id)
  }

  return {
    items,
    enqueue,
    dismiss,
    dismissConversation,
    clearAll,
  }
})
