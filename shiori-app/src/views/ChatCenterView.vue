<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useChatStore, type ChatIncomingMessageEvent, type ChatMessageVM } from '@/stores/chat'
import { recordChatToOrderClickV2 } from '@/api/orderV2'
import UserAvatar from '@/components/UserAvatar.vue'
import { formatMessagePreview, parseProductCardContent, parseTradeStatusCardContent } from '@/utils/chatCards'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()

const draft = ref('')
const messageViewportRef = ref<HTMLElement | null>(null)
const loadingOlderMessages = ref(false)
const isAtBottom = ref(true)
const newIncomingCount = ref(0)
const isSwitchingConversation = ref(false)
const TIME_GROUP_GAP_MS = 5 * 60 * 1000
const BOTTOM_THRESHOLD_PX = 48
const WEEKDAY_LABELS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
let unlistenIncomingMessage: (() => void) | null = null

interface ChatTimelineEntry {
  key: string
  type: 'time' | 'message'
  label?: string
  message?: ChatMessageVM
}

const conversations = computed(() => chatStore.conversations)
const activeConversationId = computed(() => chatStore.activeConversationId)
const activeMessages = computed(() => chatStore.activeMessages)
const unreadCount = computed(() => chatStore.chatUnreadMessageCount)
const routeConversationId = computed(() => {
  const raw = Number(route.query.conversationId || 0)
  return Number.isFinite(raw) && raw > 0 ? raw : 0
})
const isMobileLikeLayout = ref(false)
const showConversationList = computed(() => !isMobileLikeLayout.value || routeConversationId.value <= 0)
const showConversationPanel = computed(() => !isMobileLikeLayout.value || routeConversationId.value > 0)

const activeConversation = computed(() => {
  if (!activeConversationId.value) {
    return null
  }
  return conversations.value.find((item) => item.conversationId === activeConversationId.value) || null
})

const selfDisplayName = computed(() => authStore.profile?.nickname || authStore.user?.username || '我')
const peerDisplayName = computed(() => activeConversation.value?.peerProfile?.nickname || '对方')

const canLoadOlder = computed(() => {
  const conversationId = activeConversationId.value
  if (!conversationId) {
    return false
  }
  return chatStore.hasOlderMessages(conversationId)
})

const timelineEntries = computed<ChatTimelineEntry[]>(() => {
  const entries: ChatTimelineEntry[] = []
  let previousTimestamp: number | null = null
  for (const message of activeMessages.value) {
    const currentTimestamp = parseTimestamp(message.createdAt)
    const needDivider =
      entries.length === 0 ||
      previousTimestamp === null ||
      currentTimestamp === null ||
      currentTimestamp - previousTimestamp >= TIME_GROUP_GAP_MS

    if (needDivider) {
      entries.push({
        key: `time-${message.messageId}-${message.clientMsgId}`,
        type: 'time',
        label: formatDividerTime(message.createdAt),
      })
    }
    entries.push({
      key: `message-${message.messageId}-${message.clientMsgId}`,
      type: 'message',
      message,
    })
    previousTimestamp = currentTimestamp
  }
  return entries
})

function updateLayoutMode(): void {
  const width = window.innerWidth
  const height = Math.max(window.innerHeight || 0, 1)
  const aspectRatio = width / height
  isMobileLikeLayout.value = width <= 960 && aspectRatio <= 0.82
}

onMounted(async () => {
  updateLayoutMode()
  window.addEventListener('resize', updateLayoutMode)
  unlistenIncomingMessage = chatStore.registerIncomingMessageListener(handleIncomingMessage)
  await chatStore.bootstrap()
  const queryConversationId = routeConversationId.value
  if (queryConversationId > 0) {
    await openConversationAndStickBottom(queryConversationId)
  } else if (!isMobileLikeLayout.value && chatStore.conversations.length > 0) {
    const firstConversation = chatStore.conversations[0]
    if (firstConversation) {
      await openConversationAndStickBottom(firstConversation.conversationId)
    }
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateLayoutMode)
  if (unlistenIncomingMessage) {
    unlistenIncomingMessage()
    unlistenIncomingMessage = null
  }
})

watch(
  () => route.query.conversationId,
  async (raw) => {
    const conversationId = Number(raw || 0)
    if (conversationId > 0 && conversationId !== chatStore.activeConversationId) {
      await openConversationAndStickBottom(conversationId)
    }
  },
)

function parseTimestamp(raw: string): number | null {
  const timestamp = Date.parse(raw)
  if (!Number.isFinite(timestamp)) {
    return null
  }
  return timestamp
}

function formatClock(date: Date): string {
  const hour = date.getHours()
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${hour}:${minute}`
}

function dayDistanceFromToday(date: Date): number {
  const todayStart = new Date()
  todayStart.setHours(0, 0, 0, 0)
  const targetStart = new Date(date)
  targetStart.setHours(0, 0, 0, 0)
  return Math.floor((todayStart.getTime() - targetStart.getTime()) / (24 * 60 * 60 * 1000))
}

function formatDividerTime(raw: string): string {
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  const now = new Date()
  const distance = dayDistanceFromToday(parsed)
  const clock = formatClock(parsed)
  if (distance === 0) {
    return `今天 ${clock}`
  }
  if (distance === 1) {
    return `昨天 ${clock}`
  }
  if (distance > 1 && distance < 7) {
    return `${WEEKDAY_LABELS[parsed.getDay()]} ${clock}`
  }
  if (parsed.getFullYear() === now.getFullYear()) {
    return `${parsed.getMonth() + 1}月${parsed.getDate()}日 ${clock}`
  }
  return `${parsed.getFullYear()}年${parsed.getMonth() + 1}月${parsed.getDate()}日 ${clock}`
}

function resolveMessagePreview(content: string): string {
  return formatMessagePreview(content || '')
}

function isTradeStatusCard(message?: ChatMessageVM): boolean {
  if (!message) {
    return false
  }
  return Boolean(parseTradeStatusCardContent(message.content))
}

function isProductCard(message?: ChatMessageVM): boolean {
  if (!message) {
    return false
  }
  return Boolean(parseProductCardContent(message.content))
}

function productCardTitle(message?: ChatMessageVM): string {
  if (!message) {
    return ''
  }
  return parseProductCardContent(message.content)?.title || ''
}

function productCardPrice(message?: ChatMessageVM): string {
  if (!message) {
    return ''
  }
  const priceCent = parseProductCardContent(message.content)?.priceCent || 0
  return `¥ ${(priceCent / 100).toFixed(2)}`
}

function productCardCover(message?: ChatMessageVM): string {
  if (!message) {
    return ''
  }
  return parseProductCardContent(message.content)?.coverImageUrl || ''
}

async function openProductCard(message?: ChatMessageVM): Promise<void> {
  const listingId = parseProductCardContent(message?.content || '')?.listingId || 0
  if (!listingId) {
    return
  }
  await router.push({
    path: `/products/${listingId}`,
    query: activeConversation.value?.conversationId ? { conversationId: String(activeConversation.value.conversationId) } : undefined,
  })
}

function tradeStatusCardText(message?: ChatMessageVM): string {
  if (!message) {
    return ''
  }
  return parseTradeStatusCardContent(message.content)?.text || ''
}

function tradeStatusCardOrderNo(message?: ChatMessageVM): string {
  if (!message) {
    return ''
  }
  return parseTradeStatusCardContent(message.content)?.orderNo || ''
}

function isMine(senderId: number): boolean {
  return senderId === authStore.user?.userId
}

function resolveSenderAvatarUrl(senderId: number): string {
  if (isMine(senderId)) {
    return authStore.profile?.avatarUrl || ''
  }
  return activeConversation.value?.peerProfile?.avatarUrl || ''
}

function resolveSenderDisplayName(senderId: number): string {
  return isMine(senderId) ? selfDisplayName.value : peerDisplayName.value
}

async function selectConversation(conversationId: number): Promise<void> {
  await router.replace({
    path: '/chat',
    query: {
      conversationId: String(conversationId),
    },
  })
}

async function backToConversationList(): Promise<void> {
  await router.replace({
    path: '/chat',
  })
}

async function goToListingOrder(): Promise<void> {
  if (!activeConversation.value?.listingId || !activeConversation.value?.conversationId) {
    return
  }
  void recordChatToOrderClickV2({
    source: 'CHAT',
    conversationId: activeConversation.value.conversationId,
    listingId: activeConversation.value.listingId,
  }).catch(() => undefined)
  await router.push({
    path: `/products/${activeConversation.value.listingId}`,
    query: {
      conversationId: String(activeConversation.value.conversationId),
    },
  })
}

async function submitMessage(): Promise<void> {
  const content = draft.value.trim()
  if (!content) {
    return
  }
  await chatStore.sendMessage(content)
  draft.value = ''
  await nextTick()
  if (isAtBottom.value || computeIsAtBottom()) {
    scrollToBottom('smooth')
  }
}

function handleDraftEnter(event: KeyboardEvent): void {
  if (event.isComposing || event.keyCode === 229) {
    return
  }
  if (event.ctrlKey) {
    return
  }
  event.preventDefault()
  void submitMessage()
}

async function loadOlderByScrollTop(): Promise<void> {
  const conversationId = activeConversationId.value
  const viewport = messageViewportRef.value
  if (!conversationId || !viewport || loadingOlderMessages.value || !canLoadOlder.value) {
    return
  }
  if (viewport.scrollTop > 36) {
    return
  }
  loadingOlderMessages.value = true
  const prevHeight = viewport.scrollHeight
  const prevTop = viewport.scrollTop
  await chatStore.loadOlderMessages(conversationId)
  await nextTick()
  const increased = viewport.scrollHeight - prevHeight
  viewport.scrollTop = prevTop + increased
  updateBottomState()
  loadingOlderMessages.value = false
}

function handleMessageScroll(): void {
  updateBottomState()
  void loadOlderByScrollTop()
}

function computeIsAtBottom(): boolean {
  const viewport = messageViewportRef.value
  if (!viewport) {
    return true
  }
  const distance = viewport.scrollHeight - (viewport.scrollTop + viewport.clientHeight)
  return distance <= BOTTOM_THRESHOLD_PX
}

function updateBottomState(): void {
  isAtBottom.value = computeIsAtBottom()
  if (isAtBottom.value && newIncomingCount.value > 0) {
    newIncomingCount.value = 0
  }
}

function scrollToBottom(behavior: ScrollBehavior): void {
  const viewport = messageViewportRef.value
  if (!viewport) {
    return
  }
  viewport.scrollTo({
    top: viewport.scrollHeight,
    behavior,
  })
  isAtBottom.value = true
}

async function openConversationAndStickBottom(conversationId: number): Promise<void> {
  isSwitchingConversation.value = true
  newIncomingCount.value = 0
  await chatStore.openConversation(conversationId)
  await nextTick()
  scrollToBottom('auto')
  updateBottomState()
  isSwitchingConversation.value = false
}

function handleIncomingMessage(event: ChatIncomingMessageEvent): void {
  if (event.conversationId !== activeConversationId.value) {
    return
  }
  if (isSwitchingConversation.value) {
    return
  }
  nextTick(() => {
    if (isAtBottom.value || computeIsAtBottom()) {
      scrollToBottom('smooth')
      newIncomingCount.value = 0
      return
    }
    newIncomingCount.value += 1
  })
}

function jumpToLatest(): void {
  newIncomingCount.value = 0
  scrollToBottom('smooth')
}
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/90 p-4">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="font-display text-2xl text-stone-900">聊天中心</h1>
          <p class="mt-1 text-sm text-stone-600">未读消息 {{ unreadCount }}</p>
        </div>
      </div>
      <p v-if="chatStore.lastError" class="mt-2 text-sm text-rose-600">{{ chatStore.lastError }}</p>
    </header>

    <div class="grid gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
      <aside
        v-if="showConversationList"
        class="h-[60vh] min-h-[420px] max-h-[760px] overflow-auto rounded-2xl border border-stone-200 bg-white/95 lg:h-[70vh]"
      >
        <article
          v-for="item in conversations"
          :key="item.conversationId"
          class="cursor-pointer border-b border-stone-100 px-4 py-3 transition hover:bg-stone-50"
          :class="activeConversationId === item.conversationId ? 'bg-amber-50' : ''"
          @click="selectConversation(item.conversationId)"
        >
          <div class="flex items-start gap-3">
            <UserAvatar
              :src="item.peerProfile?.avatarUrl"
              :name="item.peerProfile?.nickname || ('用户 ' + item.peerUserId)"
              size-class="h-10 w-10"
              fallback-size-class="h-4 w-4"
              show-initial
            />
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2">
                <p class="truncate text-sm font-semibold text-stone-900">{{ item.peerProfile?.nickname || ('用户 ' + item.peerUserId) }}</p>
                <span v-if="item.hasUnread" class="rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-semibold text-white">新</span>
              </div>
              <p class="truncate text-xs text-stone-500">{{ item.listingTitle || ('商品 ' + item.listingId) }}</p>
              <p class="mt-1 truncate text-xs text-stone-600">{{ item.lastMessage ? resolveMessagePreview(item.lastMessage.content) : '暂无消息' }}</p>
            </div>
          </div>
        </article>
        <div v-if="conversations.length === 0" class="p-6 text-center text-sm text-stone-500">暂无会话</div>
      </aside>

      <section
        v-if="showConversationPanel"
        class="flex h-[60vh] min-h-[420px] max-h-[760px] flex-col rounded-2xl border border-stone-200 bg-white/95 lg:h-[70vh]"
      >
        <header class="flex items-center justify-between gap-3 border-b border-stone-100 px-4 py-3">
          <div class="flex min-w-0 items-center gap-2">
            <button
              v-if="isMobileLikeLayout && routeConversationId > 0"
              type="button"
              class="flex h-8 w-8 items-center justify-center rounded-lg border border-stone-300 text-sm text-stone-700 transition hover:bg-stone-100"
              aria-label="返回会话列表"
              @click="backToConversationList"
            >
              <span aria-hidden="true">&lt;</span>
            </button>
            <div class="min-w-0">
              <h2 class="truncate text-sm font-semibold text-stone-900">
                {{ activeConversation?.peerProfile?.nickname || (activeConversation ? ('用户 ' + activeConversation.peerUserId) : '请选择会话') }}
              </h2>
              <p class="mt-1 truncate text-xs text-stone-500">{{ activeConversation?.listingTitle || '' }}</p>
            </div>
          </div>
          <button
            v-if="activeConversation"
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
            @click="goToListingOrder"
          >
            去下单
          </button>
        </header>

        <div ref="messageViewportRef" class="chat-thread flex-1 overflow-y-auto px-4 py-3" @scroll.passive="handleMessageScroll">
          <div class="flex min-h-full flex-col justify-end gap-3">
            <template v-for="entry in timelineEntries" :key="entry.key">
              <p
                v-if="entry.type === 'time'"
                class="mx-auto w-fit rounded-full bg-stone-100/90 px-3 py-1 text-[11px] font-medium tracking-wide text-stone-500 shadow-sm"
              >
                {{ entry.label }}
              </p>
              <article
                v-else
                class="flex items-end gap-2"
                :class="isMine(entry.message?.senderId || 0) ? 'justify-end' : 'justify-start'"
              >
                <div
                  v-if="!isMine(entry.message?.senderId || 0)"
                  class="chat-avatar"
                >
                  <UserAvatar
                    :src="resolveSenderAvatarUrl(entry.message?.senderId || 0)"
                    :name="resolveSenderDisplayName(entry.message?.senderId || 0)"
                    size-class="h-9 w-9"
                    fallback-size-class="h-4 w-4"
                    show-initial
                  />
                </div>

                <div class="flex max-w-[78%] flex-col" :class="isMine(entry.message?.senderId || 0) ? 'items-end' : 'items-start'">
                  <div
                    class="chat-bubble px-3 py-2 text-sm"
                    :class="isMine(entry.message?.senderId || 0) ? 'chat-bubble-mine text-white' : 'chat-bubble-peer text-stone-800'"
                  >
                    <template v-if="isProductCard(entry.message)">
                      <button
                        type="button"
                        class="w-[240px] overflow-hidden rounded-xl border border-stone-200 bg-white text-left transition hover:border-stone-300"
                        @click="openProductCard(entry.message)"
                      >
                        <div class="h-28 w-full bg-stone-100">
                          <img v-if="productCardCover(entry.message)" :src="productCardCover(entry.message)" :alt="productCardTitle(entry.message)" class="h-full w-full object-cover" />
                          <div v-else class="flex h-full items-center justify-center text-xs text-stone-500">商品封面</div>
                        </div>
                        <div class="space-y-1 px-3 py-2">
                          <p class="line-clamp-2 text-xs font-medium text-stone-800">{{ productCardTitle(entry.message) }}</p>
                          <p class="text-xs font-semibold text-rose-600">{{ productCardPrice(entry.message) }}</p>
                          <p class="text-[11px] text-stone-500">点击查看并下单</p>
                        </div>
                      </button>
                    </template>
                    <template v-else-if="isTradeStatusCard(entry.message)">
                      <div class="space-y-1 rounded-xl border border-amber-200 bg-amber-50/90 px-3 py-2 text-stone-800">
                        <p class="text-[11px] font-semibold tracking-wide text-amber-800">交易状态</p>
                        <p class="whitespace-pre-wrap break-words text-sm">{{ tradeStatusCardText(entry.message) }}</p>
                        <p v-if="tradeStatusCardOrderNo(entry.message)" class="text-[11px] text-stone-600">
                          订单号 {{ tradeStatusCardOrderNo(entry.message) }}
                        </p>
                      </div>
                    </template>
                    <p v-else class="whitespace-pre-wrap break-words">{{ entry.message?.content }}</p>
                  </div>
                  <p
                    v-if="isMine(entry.message?.senderId || 0) && (entry.message?.status === 'pending' || entry.message?.status === 'failed')"
                    class="mt-1 px-1 text-[10px]"
                    :class="entry.message?.status === 'failed' ? 'text-rose-500' : 'text-stone-500'"
                  >
                    {{ entry.message?.status === 'failed' ? '发送失败' : '发送中' }}
                  </p>
                </div>

                <div
                  v-if="isMine(entry.message?.senderId || 0)"
                  class="chat-avatar"
                >
                  <UserAvatar
                    :src="resolveSenderAvatarUrl(entry.message?.senderId || 0)"
                    :name="resolveSenderDisplayName(entry.message?.senderId || 0)"
                    size-class="h-9 w-9"
                    fallback-size-class="h-4 w-4"
                    show-initial
                  />
                </div>
              </article>
            </template>

            <p v-if="activeConversation && activeMessages.length === 0" class="py-6 text-center text-sm text-stone-500">
              还没有消息，发送第一条咨询吧。
            </p>
          </div>
        </div>

        <div
          v-if="activeConversation && newIncomingCount > 0"
          class="flex items-center justify-center border-t border-stone-200/80 bg-white/95 px-3 py-2"
        >
          <button
            type="button"
            class="rounded-full bg-stone-900 px-3 py-1 text-xs font-semibold text-white shadow-sm transition hover:bg-stone-700"
            @click="jumpToLatest"
          >
            新消息 {{ newIncomingCount }}
          </button>
        </div>

        <footer class="p-3" :class="activeConversation && newIncomingCount > 0 ? '' : 'border-t border-stone-100'">
          <div class="flex items-end gap-2">
            <textarea
              v-model="draft"
              rows="3"
              class="min-h-[80px] flex-1 resize-y rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-stone-500"
              placeholder="输入咨询内容（纯文本）"
              :disabled="!activeConversation"
              @keydown.enter="handleDraftEnter"
            />
            <button
              type="button"
              class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="!activeConversation || !draft.trim()"
              @click="submitMessage"
            >
              <svg
                aria-hidden="true"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="h-4 w-4"
              >
                <path d="M22 2L11 13" />
                <path d="M22 2L15 22L11 13L2 9L22 2Z" />
              </svg>
              <span class="sr-only">提交消息</span>
            </button>
          </div>
        </footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.chat-thread {
  background:
    radial-gradient(circle at 12% 18%, rgb(250 245 233 / 80%) 0, rgb(250 245 233 / 0%) 40%),
    radial-gradient(circle at 88% 82%, rgb(241 245 249 / 85%) 0, rgb(241 245 249 / 0%) 38%),
    linear-gradient(180deg, #fffcf7 0%, #f8fafc 100%);
}

.chat-bubble {
  position: relative;
  border-radius: 18px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 8%);
}

.chat-bubble-mine {
  background: linear-gradient(135deg, #1f2937 0%, #111827 100%);
  border-bottom-right-radius: 8px;
}

.chat-bubble-mine::after {
  position: absolute;
  right: -9px;
  bottom: 10px;
  width: 0;
  height: 0;
  border-top: 7px solid transparent;
  border-bottom: 7px solid transparent;
  border-left: 10px solid #111827;
  content: '';
}

.chat-bubble-peer {
  border: 1px solid #e7e5e4;
  background: #fff;
  border-bottom-left-radius: 8px;
}

.chat-bubble-peer::after {
  position: absolute;
  bottom: 10px;
  left: -9px;
  width: 0;
  height: 0;
  border-top: 7px solid transparent;
  border-right: 10px solid #fff;
  border-bottom: 7px solid transparent;
  content: '';
}

.chat-avatar {
  flex-shrink: 0;
}
</style>
