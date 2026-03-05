<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'

import AppHeader from '@/components/AppHeader.vue'
import ChatPopupStack from '@/components/ChatPopupStack.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatPopupStore } from '@/stores/chatPopup'
import { useChatStore, type ChatIncomingMessageEvent } from '@/stores/chat'
import { useNotifyStore } from '@/stores/notify'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const chatPopupStore = useChatPopupStore()
const notifyStore = useNotifyStore()
let unlistenIncoming: (() => void) | null = null
let audioContext: AudioContext | null = null
let lastSoundAt = 0

const CHAT_SOUND_MIN_INTERVAL_MS = 2_000

const latestMessages = computed(() => notifyStore.messages.slice(0, 3))

function shouldShowChatPopup(event: ChatIncomingMessageEvent): boolean {
  const route = router.currentRoute.value
  if (route.path !== '/chat') {
    return true
  }
  const routeConversationId = Number(route.query.conversationId || 0)
  const currentConversationId = chatStore.activeConversationId || (routeConversationId > 0 ? routeConversationId : 0)
  return currentConversationId !== event.conversationId
}

function resolveConversationMeta(conversationId: number): { peerNickname: string; peerAvatarUrl: string } {
  const target = chatStore.conversations.find((item) => item.conversationId === conversationId)
  if (!target) {
    return {
      peerNickname: `会话 ${conversationId}`,
      peerAvatarUrl: '',
    }
  }
  return {
    peerNickname: target.peerProfile?.nickname || `用户 ${target.peerUserId}`,
    peerAvatarUrl: target.peerProfile?.avatarUrl || '',
  }
}

function handleIncomingMessage(event: ChatIncomingMessageEvent): void {
  if (!shouldShowChatPopup(event)) {
    return
  }
  const meta = resolveConversationMeta(event.conversationId)
  chatPopupStore.enqueue({
    conversationId: event.conversationId,
    senderId: event.senderId,
    content: event.content,
    createdAt: event.createdAt,
    peerNickname: meta.peerNickname,
    peerAvatarUrl: meta.peerAvatarUrl,
  })
  playChatPopupSound()
}

function playChatPopupSound(): void {
  const now = Date.now()
  if (now - lastSoundAt < CHAT_SOUND_MIN_INTERVAL_MS) {
    return
  }
  lastSoundAt = now

  const AudioConstructor = window.AudioContext || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (!AudioConstructor) {
    return
  }

  try {
    if (!audioContext) {
      audioContext = new AudioConstructor()
    }
    if (audioContext.state === 'suspended') {
      void audioContext.resume()
    }
    const startAt = audioContext.currentTime
    const oscillator = audioContext.createOscillator()
    const gain = audioContext.createGain()
    oscillator.type = 'triangle'
    oscillator.frequency.setValueAtTime(740, startAt)
    oscillator.frequency.exponentialRampToValueAtTime(980, startAt + 0.11)
    gain.gain.setValueAtTime(0.0001, startAt)
    gain.gain.exponentialRampToValueAtTime(0.08, startAt + 0.015)
    gain.gain.exponentialRampToValueAtTime(0.0001, startAt + 0.2)
    oscillator.connect(gain)
    gain.connect(audioContext.destination)
    oscillator.start(startAt)
    oscillator.stop(startAt + 0.22)
  } catch {
    // ignore audio failures to avoid breaking message flow
  }
}

onMounted(() => {
  unlistenIncoming = chatStore.registerIncomingMessageListener(handleIncomingMessage)
})

watch(
  () => authStore.isAuthenticated,
  (authed) => {
    if (!authed) {
      chatPopupStore.clearAll()
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (unlistenIncoming) {
    unlistenIncoming()
    unlistenIncoming = null
  }
  if (audioContext) {
    void audioContext.close()
    audioContext = null
  }
  chatPopupStore.clearAll()
})
</script>

<template>
  <div class="min-h-screen bg-[var(--shiori-bg)] text-stone-900">
    <div class="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div class="absolute -left-20 top-24 h-64 w-64 rounded-full bg-amber-300/30 blur-3xl"></div>
      <div class="absolute -right-10 top-1/3 h-72 w-72 rounded-full bg-lime-200/30 blur-3xl"></div>
    </div>

    <AppHeader />

    <ChatPopupStack />

    <main class="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <RouterView />
    </main>

    <aside
      v-if="latestMessages.length > 0"
      class="fixed bottom-4 right-4 hidden w-80 rounded-2xl border border-amber-200 bg-white/95 p-4 shadow-lg shadow-amber-900/10 backdrop-blur md:block"
    >
      <p class="mb-2 text-sm font-semibold text-stone-800">最近通知</p>
      <ul class="space-y-2 text-xs text-stone-600">
        <li v-for="item in latestMessages" :key="item.id" class="rounded-lg bg-stone-50 p-2">
          <p class="font-medium text-stone-800">{{ item.type }} - {{ item.aggregateId }}</p>
          <p>{{ item.createdAt }}</p>
        </li>
      </ul>
    </aside>
  </div>
</template>
