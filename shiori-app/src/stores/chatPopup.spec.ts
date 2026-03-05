import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useChatPopupStore } from '@/stores/chatPopup'

describe('chat popup store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should auto dismiss popup after timeout', () => {
    const store = useChatPopupStore()
    store.enqueue({
      conversationId: 11,
      senderId: 2002,
      content: 'hello',
      createdAt: '2026-03-05T00:00:00.000Z',
      peerNickname: 'Seller',
      peerAvatarUrl: '',
    })
    expect(store.items).toHaveLength(1)

    vi.advanceTimersByTime(7_999)
    expect(store.items).toHaveLength(1)

    vi.advanceTimersByTime(1)
    expect(store.items).toHaveLength(0)
  })

  it('should merge same conversation in merge window and refresh timer', () => {
    const store = useChatPopupStore()
    store.enqueue({
      conversationId: 11,
      senderId: 2002,
      content: 'first',
      createdAt: '2026-03-05T00:00:00.000Z',
      peerNickname: 'Seller',
      peerAvatarUrl: '',
    })
    vi.advanceTimersByTime(3_000)
    store.enqueue({
      conversationId: 11,
      senderId: 2002,
      content: 'second',
      createdAt: '2026-03-05T00:00:03.000Z',
      peerNickname: 'Seller',
      peerAvatarUrl: '',
    })

    expect(store.items).toHaveLength(1)
    expect(store.items[0].unreadCount).toBe(2)
    expect(store.items[0].content).toBe('second')

    vi.advanceTimersByTime(7_999)
    expect(store.items).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(store.items).toHaveLength(0)
  })

  it('should keep max 3 popups and drop oldest overflow item', () => {
    const store = useChatPopupStore()
    for (let i = 1; i <= 4; i += 1) {
      store.enqueue({
        conversationId: i,
        senderId: 2000 + i,
        content: `msg-${i}`,
        createdAt: `2026-03-05T00:00:0${i}.000Z`,
        peerNickname: `User-${i}`,
        peerAvatarUrl: '',
      })
      vi.advanceTimersByTime(1)
    }
    expect(store.items).toHaveLength(3)
    expect(store.items.map((item) => item.conversationId)).toEqual([4, 3, 2])
  })

  it('should dismiss by id and conversation and clear all', () => {
    const store = useChatPopupStore()
    store.enqueue({
      conversationId: 11,
      senderId: 2002,
      content: 'a',
      createdAt: '2026-03-05T00:00:00.000Z',
      peerNickname: 'A',
      peerAvatarUrl: '',
    })
    store.enqueue({
      conversationId: 12,
      senderId: 2003,
      content: 'b',
      createdAt: '2026-03-05T00:00:01.000Z',
      peerNickname: 'B',
      peerAvatarUrl: '',
    })
    const firstId = store.items[0].id
    store.dismiss(firstId)
    expect(store.items).toHaveLength(1)

    store.dismissConversation(11)
    expect(store.items).toHaveLength(0)

    store.enqueue({
      conversationId: 13,
      senderId: 2004,
      content: 'c',
      createdAt: '2026-03-05T00:00:02.000Z',
      peerNickname: 'C',
      peerAvatarUrl: '',
    })
    store.clearAll()
    expect(store.items).toHaveLength(0)
  })
})
