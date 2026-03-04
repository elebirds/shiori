<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()

const draft = ref('')
const messageViewportRef = ref<HTMLElement | null>(null)
const loadingOlderMessages = ref(false)

const conversations = computed(() => chatStore.conversations)
const activeConversationId = computed(() => chatStore.activeConversationId)
const activeMessages = computed(() => chatStore.activeMessages)
const unreadCount = computed(() => chatStore.chatUnreadMessageCount)

const activeConversation = computed(() => {
  if (!activeConversationId.value) {
    return null
  }
  return conversations.value.find((item) => item.conversationId === activeConversationId.value) || null
})

const canLoadOlder = computed(() => {
  const conversationId = activeConversationId.value
  if (!conversationId) {
    return false
  }
  return chatStore.hasOlderMessages(conversationId)
})

onMounted(async () => {
  await chatStore.bootstrap()
  const queryConversationId = Number(route.query.conversationId || 0)
  if (queryConversationId > 0) {
    await chatStore.openConversation(queryConversationId)
  } else if (chatStore.conversations.length > 0) {
    await chatStore.openConversation(chatStore.conversations[0].conversationId)
  }
})

watch(
  () => route.query.conversationId,
  async (raw) => {
    const conversationId = Number(raw || 0)
    if (conversationId > 0 && conversationId !== chatStore.activeConversationId) {
      await chatStore.openConversation(conversationId)
    }
  },
)

function formatTime(raw: string): string {
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  return parsed.toLocaleString('zh-CN')
}

function isMine(senderId: number): boolean {
  return senderId === authStore.user?.userId
}

async function selectConversation(conversationId: number): Promise<void> {
  await router.replace({
    path: '/chat',
    query: {
      conversationId: String(conversationId),
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
  loadingOlderMessages.value = false
}

function handleMessageScroll(): void {
  void loadOlderByScrollTop()
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
      <aside class="h-[60vh] min-h-[420px] max-h-[760px] overflow-auto rounded-2xl border border-stone-200 bg-white/95 lg:h-[70vh]">
        <article
          v-for="item in conversations"
          :key="item.conversationId"
          class="cursor-pointer border-b border-stone-100 px-4 py-3 transition hover:bg-stone-50"
          :class="activeConversationId === item.conversationId ? 'bg-amber-50' : ''"
          @click="selectConversation(item.conversationId)"
        >
          <div class="flex items-start gap-3">
            <img
              v-if="item.peerProfile?.avatarUrl"
              :src="item.peerProfile.avatarUrl"
              alt="avatar"
              class="h-10 w-10 rounded-full border border-stone-200 object-cover"
            />
            <div v-else class="flex h-10 w-10 items-center justify-center rounded-full border border-stone-200 bg-stone-100 text-xs text-stone-500">头像</div>
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2">
                <p class="truncate text-sm font-semibold text-stone-900">{{ item.peerProfile?.nickname || `用户 ${item.peerUserId}` }}</p>
                <span v-if="item.hasUnread" class="rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-semibold text-white">新</span>
              </div>
              <p class="truncate text-xs text-stone-500">{{ item.listingTitle || `商品 ${item.listingId}` }}</p>
              <p class="mt-1 truncate text-xs text-stone-600">{{ item.lastMessage?.content || '暂无消息' }}</p>
            </div>
          </div>
        </article>
        <div v-if="conversations.length === 0" class="p-6 text-center text-sm text-stone-500">暂无会话</div>
      </aside>

      <section class="flex h-[60vh] min-h-[420px] max-h-[760px] flex-col rounded-2xl border border-stone-200 bg-white/95 lg:h-[70vh]">
        <header class="border-b border-stone-100 px-4 py-3">
          <h2 class="text-sm font-semibold text-stone-900">
            {{ activeConversation?.peerProfile?.nickname || (activeConversation ? `用户 ${activeConversation.peerUserId}` : '请选择会话') }}
          </h2>
          <p class="mt-1 text-xs text-stone-500">{{ activeConversation?.listingTitle || '' }}</p>
        </header>

        <div ref="messageViewportRef" class="flex-1 space-y-3 overflow-y-auto px-4 py-3" @scroll.passive="handleMessageScroll">
          <article
            v-for="item in activeMessages"
            :key="`${item.messageId}-${item.clientMsgId}`"
            class="flex"
            :class="isMine(item.senderId) ? 'justify-end' : 'justify-start'"
          >
            <div
              class="max-w-[80%] rounded-2xl px-3 py-2 text-sm"
              :class="isMine(item.senderId) ? 'bg-stone-900 text-white' : 'bg-stone-100 text-stone-800'"
            >
              <p class="whitespace-pre-wrap break-words">{{ item.content }}</p>
              <p class="mt-1 text-[10px]" :class="isMine(item.senderId) ? 'text-stone-300' : 'text-stone-500'">
                {{ formatTime(item.createdAt) }}
                <span v-if="isMine(item.senderId) && item.status === 'pending'"> · 发送中</span>
                <span v-if="isMine(item.senderId) && item.status === 'failed'" class="text-rose-400"> · 发送失败</span>
              </p>
            </div>
          </article>
          <p v-if="activeConversation && activeMessages.length === 0" class="text-sm text-stone-500">还没有消息，发送第一条咨询吧。</p>
        </div>

        <footer class="border-t border-stone-100 p-3">
          <div class="flex items-end gap-2">
            <textarea
              v-model="draft"
              rows="3"
              class="min-h-[80px] flex-1 resize-y rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-stone-500"
              placeholder="输入咨询内容（纯文本）"
              :disabled="!activeConversation"
            />
            <button
              type="button"
              class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="!activeConversation || !draft.trim()"
              @click="submitMessage"
            >
              发送
            </button>
          </div>
        </footer>
      </section>
    </div>
  </section>
</template>
