import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNotifyStore } from '@/stores/notify'

vi.mock('@/api/chat', () => ({
  getChatSummary: vi.fn(async () => ({ unreadConversationCount: 0, unreadMessageCount: 0 })),
  issueChatTicket: vi.fn(async () => ({ ticket: 't', expireAt: '', buyerId: 1001, sellerId: 2002, listingId: 101, jti: 'j' })),
  startConversation: vi.fn(async () => ({ conversationId: 11, listingId: 101, buyerId: 1001, sellerId: 2002, jti: 'j', expireAt: '' })),
  listConversations: vi.fn(async () => ({
    items: [{ conversationId: 11, listingId: 101, buyerId: 1001, sellerId: 2002, hasUnread: false, updatedAt: new Date().toISOString() }],
    hasMore: false,
    nextCursor: 0,
  })),
  listMessages: vi.fn(async () => ({ items: [], hasMore: false, nextBefore: 0 })),
  readConversation: vi.fn(async () => ({ conversationId: 11, lastReadMsgId: 0 })),
}))

vi.mock('@/api/auth', async () => {
  const original = await vi.importActual<typeof import('@/api/auth')>('@/api/auth')
  return {
    ...original,
    getUserProfilesByUserIds: vi.fn(async () => [
      { userId: 2002, userNo: 'U2', username: 'seller', nickname: 'Seller', avatarUrl: '', gender: 1, age: 24, bio: '' },
    ]),
  }
})

vi.mock('@/api/productV2', async () => {
  const original = await vi.importActual<typeof import('@/api/productV2')>('@/api/productV2')
  return {
    ...original,
    getProductDetailV2: vi.fn(async () => ({
      productId: 101,
      productNo: 'P101',
      ownerUserId: 2002,
      title: '教材A',
      description: '',
      detailHtml: '',
      status: 'ON_SALE',
      categoryCode: 'TEXTBOOK',
      conditionLevel: 'GOOD',
      tradeMode: 'MEETUP',
      campusCode: 'A',
      skus: [],
    })),
  }
})

describe('chat store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const storage = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => {
        storage.set(key, value)
      },
      removeItem: (key: string) => {
        storage.delete(key)
      },
      clear: () => {
        storage.clear()
      },
    })
  })

  it('should send message and update by send_ack frame', async () => {
    const authStore = useAuthStore()
    authStore.setSession({
      accessToken: 'token',
      refreshToken: 'refresh',
      user: { userId: 1001, userNo: 'U1', username: 'buyer', roles: ['USER'] },
    })
    const notifyStore = useNotifyStore()
    let frameListener: ((frame: Record<string, unknown>) => void) | null = null
    notifyStore.registerFrameListener = vi.fn((listener) => {
      frameListener = listener
      return () => {}
    })
    notifyStore.sendFrame = vi.fn(() => true)

    const chatStore = useChatStore()
    await chatStore.bootstrap()
    await chatStore.openConversation(11)
    await chatStore.sendMessage('hello')

    expect(chatStore.activeMessages).toHaveLength(1)
    const clientMsgId = chatStore.activeMessages[0].clientMsgId
    frameListener?.({
      type: 'send_ack',
      conversationId: 11,
      clientMsgId,
      messageId: 55,
      createdAt: new Date().toISOString(),
      deduplicated: false,
    })

    expect(chatStore.activeMessages[0].messageId).toBe(55)
    expect(chatStore.activeMessages[0].status).toBe('sent')
  })

  it('should merge incoming chat_message and bump unread count', async () => {
    const authStore = useAuthStore()
    authStore.setSession({
      accessToken: 'token',
      refreshToken: 'refresh',
      user: { userId: 1001, userNo: 'U1', username: 'buyer', roles: ['USER'] },
    })
    const notifyStore = useNotifyStore()
    let frameListener: ((frame: Record<string, unknown>) => void) | null = null
    notifyStore.registerFrameListener = vi.fn((listener) => {
      frameListener = listener
      return () => {}
    })

    const chatStore = useChatStore()
    await chatStore.bootstrap()
    frameListener?.({
      type: 'chat_message',
      conversationId: 11,
      messageId: 66,
      senderId: 2002,
      receiverId: 1001,
      clientMsgId: 's-1',
      content: 'hello buyer',
      createdAt: new Date().toISOString(),
    })

    expect(chatStore.messagesByConversation[11]).toHaveLength(1)
    expect(chatStore.chatUnreadMessageCount).toBe(1)
  })

  it('should keep optimistic messages in send order', async () => {
    const authStore = useAuthStore()
    authStore.setSession({
      accessToken: 'token',
      refreshToken: 'refresh',
      user: { userId: 1001, userNo: 'U1', username: 'buyer', roles: ['USER'] },
    })
    const notifyStore = useNotifyStore()
    notifyStore.registerFrameListener = vi.fn(() => () => {})
    notifyStore.sendFrame = vi.fn(() => true)

    const chatStore = useChatStore()
    await chatStore.bootstrap()
    await chatStore.openConversation(11)
    await chatStore.sendMessage('第一条')
    await new Promise((resolve) => setTimeout(resolve, 2))
    await chatStore.sendMessage('第二条')

    expect(chatStore.activeMessages.map((item) => item.content)).toEqual(['第一条', '第二条'])
  })
})
