<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'

import AppHeader from '@/components/AppHeader.vue'
import ChatPopupStack from '@/components/ChatPopupStack.vue'
import NotifyPopupStack from '@/components/NotifyPopupStack.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatPopupStore } from '@/stores/chatPopup'
import { useChatStore, type ChatIncomingMessageEvent } from '@/stores/chat'

const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const chatPopupStore = useChatPopupStore()
let unlistenIncoming: (() => void) | null = null
let audioContext: AudioContext | null = null
let lastSoundAt = 0

const CHAT_SOUND_MIN_INTERVAL_MS = 2_000

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
    <NotifyPopupStack />

    <main class="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <RouterView />
    </main>
  </div>
</template>
