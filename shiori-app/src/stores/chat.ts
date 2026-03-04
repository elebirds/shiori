import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { getUserProfilesByUserIds, type PublicUserProfile } from '@/api/auth'
import {
  getChatSummary,
  issueChatTicket,
  listConversations,
  listMessages,
  readConversation,
  startConversation,
  type ChatConversationItem,
  type ChatMessageItem,
} from '@/api/chat'
import { getProductDetailV2 } from '@/api/productV2'
import { useAuthStore } from '@/stores/auth'
import { useNotifyStore, type WSFramePayload } from '@/stores/notify'

export interface ChatMessageVM {
  messageId: number
  conversationId: number
  senderId: number
  receiverId: number
  clientMsgId: string
  content: string
  createdAt: string
  status?: 'pending' | 'sent' | 'failed'
}

export interface ChatConversationVM {
  conversationId: number
  listingId: number
  buyerId: number
  sellerId: number
  hasUnread: boolean
  updatedAt: string
  lastMessage?: ChatMessageVM
  peerUserId: number
  peerProfile?: PublicUserProfile
  listingTitle?: string
  listingCoverImageUrl?: string
}

interface ConversationCursorState {
  before: number
  hasMore: boolean
}

const DEFAULT_CONVERSATION_LIMIT = 30
const DEFAULT_MESSAGE_LIMIT = 20

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<ChatConversationVM[]>([])
  const messagesByConversation = ref<Record<number, ChatMessageVM[]>>({})
  const activeConversationId = ref<number | null>(null)
  const chatUnreadConversationCount = ref(0)
  const chatUnreadMessageCount = ref(0)
  const sendingMap = ref<Record<string, 'pending' | 'sent' | 'failed'>>({})
  const loading = ref(false)
  const lastError = ref('')

  const messageCursorByConversation = ref<Record<number, ConversationCursorState>>({})
  const profileCache = ref<Record<number, PublicUserProfile>>({})
  const listingCache = ref<Record<number, { title: string; coverImageUrl?: string }>>({})

  const authStore = useAuthStore()
  const notifyStore = useNotifyStore()

  let unlistenFrame: (() => void) | null = null

  const activeMessages = computed(() => {
    if (!activeConversationId.value) {
      return []
    }
    return messagesByConversation.value[activeConversationId.value] || []
  })

  function initialize(): void {
    if (!unlistenFrame) {
      unlistenFrame = notifyStore.registerFrameListener(handleFrame)
    }
  }

  async function bootstrap(): Promise<void> {
    initialize()
    if (!authStore.isAuthenticated) {
      reset()
      return
    }
    await Promise.all([refreshSummary(), loadConversations()])
  }

  async function bootstrapFromListing(listingId: number): Promise<number> {
    initialize()
    const ticket = await issueChatTicket(listingId)
    const started = await startConversation({ chatTicket: ticket.ticket })
    notifyStore.sendFrame({
      type: 'join',
      chatTicket: ticket.ticket,
    })
    await loadConversations()
    await openConversation(started.conversationId)
    return started.conversationId
  }

  async function refreshSummary(): Promise<void> {
    if (!authStore.isAuthenticated) {
      return
    }
    const summary = await getChatSummary()
    chatUnreadConversationCount.value = summary.unreadConversationCount
    chatUnreadMessageCount.value = summary.unreadMessageCount
  }

  async function loadConversations(): Promise<void> {
    if (!authStore.isAuthenticated) {
      return
    }
    loading.value = true
    try {
      const response = await listConversations({ limit: DEFAULT_CONVERSATION_LIMIT })
      conversations.value = response.items.map(toConversationVM)
      await hydrateConversationMeta(conversations.value)
      lastError.value = ''
    } catch (error) {
      lastError.value = error instanceof Error ? error.message : '加载会话失败'
    } finally {
      loading.value = false
    }
  }

  async function openConversation(conversationId: number): Promise<void> {
    if (!conversationId) {
      return
    }
    activeConversationId.value = conversationId
    await loadMessages(conversationId)
    const latestMessage = latestMessageId(conversationId)
    if (latestMessage > 0) {
      await markConversationRead(conversationId, latestMessage)
    }
  }

  async function loadOlderMessages(conversationId: number): Promise<void> {
    const cursor = messageCursorByConversation.value[conversationId]
    if (!cursor || !cursor.hasMore) {
      return
    }
    await loadMessages(conversationId, cursor.before)
  }

  async function loadMessages(conversationId: number, before = 0): Promise<void> {
    try {
      const response = await listMessages(conversationId, {
        before: before > 0 ? before : undefined,
        limit: DEFAULT_MESSAGE_LIMIT,
      })
      const incoming = response.items.map(toMessageVM)
      const merged = mergeMessages(messagesByConversation.value[conversationId] || [], incoming)
      messagesByConversation.value = {
        ...messagesByConversation.value,
        [conversationId]: merged,
      }
      messageCursorByConversation.value = {
        ...messageCursorByConversation.value,
        [conversationId]: {
          before: response.nextBefore,
          hasMore: response.hasMore,
        },
      }
    } catch (error) {
      lastError.value = error instanceof Error ? error.message : '加载消息失败'
    }
  }

  async function sendMessage(content: string): Promise<void> {
    const conversationId = activeConversationId.value
    const currentUserId = authStore.user?.userId
    if (!conversationId || !currentUserId) {
      return
    }
    const trimmed = content.trim()
    if (!trimmed) {
      return
    }
    const clientMsgId = `web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const optimistic: ChatMessageVM = {
      messageId: -Date.now(),
      conversationId,
      senderId: currentUserId,
      receiverId: resolvePeerUserId(conversationId),
      clientMsgId,
      content: trimmed,
      createdAt: new Date().toISOString(),
      status: 'pending',
    }
    upsertMessage(conversationId, optimistic)
    sendingMap.value = {
      ...sendingMap.value,
      [clientMsgId]: 'pending',
    }
    upsertConversationByMessage(conversationId, optimistic, false)

    const sent = notifyStore.sendFrame({
      type: 'send',
      conversationId,
      clientMsgId,
      content: trimmed,
    })
    if (!sent) {
      markMessageFailed(conversationId, clientMsgId)
      lastError.value = '聊天连接未就绪，请稍后重试'
    }
  }

  async function markConversationRead(conversationId: number, lastReadMsgId: number): Promise<void> {
    try {
      await readConversation(conversationId, lastReadMsgId)
      notifyStore.sendFrame({
        type: 'read',
        conversationId,
        lastReadMsgId,
      })
      conversations.value = conversations.value.map((item) =>
        item.conversationId === conversationId
          ? {
              ...item,
              hasUnread: false,
            }
          : item,
      )
      await refreshSummary()
    } catch (error) {
      lastError.value = error instanceof Error ? error.message : '更新已读状态失败'
    }
  }

  function handleFrame(frame: WSFramePayload): void {
    const type = String(frame.type || '').toLowerCase()
    if (!type) {
      return
    }
    if (type === 'chat_message') {
      handleChatMessage(frame)
      return
    }
    if (type === 'send_ack') {
      handleSendAck(frame)
      return
    }
    if (type === 'read_ack') {
      void refreshSummary()
      return
    }
    if (type === 'error' && String(frame.code || '').startsWith('CHAT_')) {
      lastError.value = String(frame.message || '聊天操作失败')
      return
    }
  }

  function handleChatMessage(frame: WSFramePayload): void {
    const conversationId = Number(frame.conversationId || 0)
    const messageId = Number(frame.messageId || 0)
    if (conversationId <= 0 || messageId <= 0) {
      return
    }
    const message: ChatMessageVM = {
      messageId,
      conversationId,
      senderId: Number(frame.senderId || 0),
      receiverId: Number(frame.receiverId || 0),
      clientMsgId: String(frame.clientMsgId || ''),
      content: String(frame.content || ''),
      createdAt: String(frame.createdAt || new Date().toISOString()),
      status: 'sent',
    }
    upsertMessage(conversationId, message)
    const selfId = authStore.user?.userId || 0
    const incomingForMe = message.senderId !== selfId
    upsertConversationByMessage(conversationId, message, incomingForMe)

    if (incomingForMe && activeConversationId.value === conversationId) {
      void markConversationRead(conversationId, message.messageId)
    } else if (incomingForMe) {
      chatUnreadMessageCount.value += 1
      const target = conversations.value.find((item) => item.conversationId === conversationId)
      if (target && !target.hasUnread) {
        chatUnreadConversationCount.value += 1
      }
    }
  }

  function handleSendAck(frame: WSFramePayload): void {
    const conversationId = Number(frame.conversationId || 0)
    const messageId = Number(frame.messageId || 0)
    const clientMsgId = String(frame.clientMsgId || '')
    if (conversationId <= 0 || messageId <= 0 || !clientMsgId) {
      return
    }
    const current = messagesByConversation.value[conversationId] || []
    const nextList = current.map((item) =>
      item.clientMsgId === clientMsgId
        ? {
            ...item,
            messageId,
            createdAt: String(frame.createdAt || item.createdAt),
            status: 'sent',
          }
        : item,
    )
    messagesByConversation.value = {
      ...messagesByConversation.value,
      [conversationId]: sortMessages(nextList),
    }
    sendingMap.value = {
      ...sendingMap.value,
      [clientMsgId]: 'sent',
    }
  }

  function upsertMessage(conversationId: number, message: ChatMessageVM): void {
    const current = messagesByConversation.value[conversationId] || []
    const exists = current.some(
      (item) => item.messageId === message.messageId || (message.clientMsgId && item.clientMsgId === message.clientMsgId),
    )
    if (exists) {
      const nextList = current.map((item) => {
        if (item.messageId === message.messageId || (message.clientMsgId && item.clientMsgId === message.clientMsgId)) {
          return {
            ...item,
            ...message,
          }
        }
        return item
      })
      messagesByConversation.value = {
        ...messagesByConversation.value,
        [conversationId]: sortMessages(nextList),
      }
      return
    }
    messagesByConversation.value = {
      ...messagesByConversation.value,
      [conversationId]: sortMessages([...current, message]),
    }
  }

  function upsertConversationByMessage(conversationId: number, message: ChatMessageVM, hasUnread: boolean): void {
    const idx = conversations.value.findIndex((item) => item.conversationId === conversationId)
    if (idx < 0) {
      return
    }
    const next = {
      ...conversations.value[idx],
      updatedAt: message.createdAt,
      hasUnread: hasUnread ? true : conversations.value[idx].hasUnread,
      lastMessage: message,
    }
    const copied = conversations.value.slice()
    copied[idx] = next
    copied.sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))
    conversations.value = copied
  }

  function markMessageFailed(conversationId: number, clientMsgId: string): void {
    sendingMap.value = {
      ...sendingMap.value,
      [clientMsgId]: 'failed',
    }
    const current = messagesByConversation.value[conversationId] || []
    messagesByConversation.value = {
      ...messagesByConversation.value,
      [conversationId]: current.map((item) =>
        item.clientMsgId === clientMsgId
          ? {
              ...item,
              status: 'failed',
            }
          : item,
      ),
    }
  }

  async function hydrateConversationMeta(items: ChatConversationVM[]): Promise<void> {
    const unknownPeerIds = Array.from(
      new Set(items.map((item) => item.peerUserId).filter((item) => item > 0 && !profileCache.value[item])),
    )
    if (unknownPeerIds.length > 0) {
      try {
        const profiles = await getUserProfilesByUserIds(unknownPeerIds)
        const next = { ...profileCache.value }
        for (const profile of profiles) {
          next[profile.userId] = profile
        }
        profileCache.value = next
      } catch {
        // ignore profile hydration failure for chat list
      }
    }

    const unknownListingIds = Array.from(
      new Set(items.map((item) => item.listingId).filter((item) => item > 0 && !listingCache.value[item])),
    )
    if (unknownListingIds.length > 0) {
      const settled = await Promise.allSettled(unknownListingIds.map((listingId) => getProductDetailV2(listingId)))
      const next = { ...listingCache.value }
      for (let i = 0; i < settled.length; i += 1) {
        const result = settled[i]
        if (result.status !== 'fulfilled') {
          continue
        }
        const listingId = unknownListingIds[i]
        next[listingId] = {
          title: result.value.title,
          coverImageUrl: result.value.coverImageUrl,
        }
      }
      listingCache.value = next
    }

    conversations.value = conversations.value.map((item) => ({
      ...item,
      peerProfile: profileCache.value[item.peerUserId],
      listingTitle: listingCache.value[item.listingId]?.title,
      listingCoverImageUrl: listingCache.value[item.listingId]?.coverImageUrl,
    }))
  }

  function toConversationVM(item: ChatConversationItem): ChatConversationVM {
    const currentUserId = authStore.user?.userId || 0
    return {
      conversationId: item.conversationId,
      listingId: item.listingId,
      buyerId: item.buyerId,
      sellerId: item.sellerId,
      hasUnread: item.hasUnread,
      updatedAt: item.updatedAt,
      lastMessage: item.lastMessage ? toMessageVM(item.lastMessage) : undefined,
      peerUserId: item.buyerId === currentUserId ? item.sellerId : item.buyerId,
    }
  }

  function toMessageVM(item: ChatMessageItem): ChatMessageVM {
    return {
      messageId: item.messageId,
      conversationId: item.conversationId,
      senderId: item.senderId,
      receiverId: item.receiverId,
      clientMsgId: item.clientMsgId,
      content: item.content,
      createdAt: item.createdAt,
      status: 'sent',
    }
  }

  function mergeMessages(current: ChatMessageVM[], incoming: ChatMessageVM[]): ChatMessageVM[] {
    const merged = new Map<string, ChatMessageVM>()
    for (const item of current) {
      merged.set(`${item.messageId}:${item.clientMsgId}`, item)
    }
    for (const item of incoming) {
      merged.set(`${item.messageId}:${item.clientMsgId}`, item)
    }
    return sortMessages(Array.from(merged.values()))
  }

  function sortMessages(messages: ChatMessageVM[]): ChatMessageVM[] {
    return messages.slice().sort(compareMessageOrder)
  }

  function compareMessageOrder(a: ChatMessageVM, b: ChatMessageVM): number {
    const aTime = Date.parse(a.createdAt)
    const bTime = Date.parse(b.createdAt)
    if (Number.isFinite(aTime) && Number.isFinite(bTime) && aTime !== bTime) {
      return aTime - bTime
    }
    if (a.messageId !== b.messageId) {
      return a.messageId - b.messageId
    }
    return a.clientMsgId.localeCompare(b.clientMsgId)
  }

  function latestMessageId(conversationId: number): number {
    const list = messagesByConversation.value[conversationId] || []
    if (list.length === 0) {
      return 0
    }
    return list[list.length - 1].messageId
  }

  function resolvePeerUserId(conversationId: number): number {
    const target = conversations.value.find((item) => item.conversationId === conversationId)
    return target?.peerUserId || 0
  }

  function reset(): void {
    conversations.value = []
    messagesByConversation.value = {}
    activeConversationId.value = null
    chatUnreadConversationCount.value = 0
    chatUnreadMessageCount.value = 0
    sendingMap.value = {}
    messageCursorByConversation.value = {}
    lastError.value = ''
  }

  return {
    conversations,
    messagesByConversation,
    activeConversationId,
    activeMessages,
    chatUnreadConversationCount,
    chatUnreadMessageCount,
    sendingMap,
    loading,
    lastError,
    bootstrap,
    bootstrapFromListing,
    loadConversations,
    openConversation,
    loadOlderMessages,
    sendMessage,
    refreshSummary,
    initialize,
    reset,
  }
})
