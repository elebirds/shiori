<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import UserAvatar from '@/components/UserAvatar.vue'
import { useChatPopupStore } from '@/stores/chatPopup'
import { formatMessagePreview } from '@/utils/chatCards'

const router = useRouter()
const chatPopupStore = useChatPopupStore()

const items = computed(() => chatPopupStore.items)

function formatPreview(raw: string): string {
  const normalized = formatMessagePreview(raw || '').trim()
  if (!normalized) {
    return '[空消息]'
  }
  if (normalized.length <= 40) {
    return normalized
  }
  return `${normalized.slice(0, 40)}...`
}

function formatTime(raw: string): string {
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${date.getHours()}:${minute}`
}

async function openConversation(conversationId: number): Promise<void> {
  chatPopupStore.dismissConversation(conversationId)
  await router.push({
    path: '/chat',
    query: {
      conversationId: String(conversationId),
    },
  })
}

function dismiss(id: string): void {
  chatPopupStore.dismiss(id)
}
</script>

<template>
  <aside class="pointer-events-none fixed right-4 top-20 z-40 hidden w-80 space-y-2 md:block">
    <article
      v-for="item in items"
      :key="item.id"
      class="pointer-events-auto rounded-xl border border-amber-200 bg-white/95 p-3 shadow-lg shadow-amber-900/10 backdrop-blur"
    >
      <div class="flex items-start gap-2">
        <button
          type="button"
          class="flex min-w-0 flex-1 items-start gap-2 text-left"
          @click="openConversation(item.conversationId)"
        >
          <UserAvatar
            :src="item.peerAvatarUrl"
            :name="item.peerNickname"
            size-class="h-9 w-9"
            fallback-size-class="h-4 w-4"
            show-initial
          />
          <span class="min-w-0 flex-1">
            <span class="flex items-center justify-between gap-2">
              <span class="truncate text-sm font-semibold text-stone-900">{{ item.peerNickname || `会话 ${item.conversationId}` }}</span>
              <span class="text-[11px] text-stone-500">{{ formatTime(item.createdAt) }}</span>
            </span>
            <span class="mt-0.5 line-clamp-2 block text-xs text-stone-700">{{ formatPreview(item.content) }}</span>
            <span
              v-if="item.unreadCount > 1"
              class="mt-1 inline-flex rounded-full bg-rose-600 px-2 py-0.5 text-[10px] font-semibold text-white"
            >
              新消息 {{ item.unreadCount }}
            </span>
          </span>
        </button>
        <button
          type="button"
          class="rounded-md p-1 text-stone-500 transition hover:bg-stone-100 hover:text-stone-700"
          @click.stop="dismiss(item.id)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4">
            <path d="M18 6L6 18M6 6l12 12" />
          </svg>
        </button>
      </div>
    </article>
  </aside>
</template>
