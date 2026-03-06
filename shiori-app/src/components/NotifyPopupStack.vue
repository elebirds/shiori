<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useNotifyStore } from '@/stores/notify'
import { describeNotifyMessage, formatNotifyMinute } from '@/utils/notifyMessage'

const router = useRouter()
const route = useRoute()
const notifyStore = useNotifyStore()

const AUTO_DISMISS_MS = 8_000
const MAX_POPUP_ITEMS = 3
const dismissedIds = ref(new Set<string>())
const timers = new Map<string, ReturnType<typeof setTimeout>>()

const popupItems = computed(() => {
  if (route.path === '/notifications') {
    return []
  }
  return notifyStore.messages
    .filter((item) => !item.read && !dismissedIds.value.has(item.id))
    .slice(0, MAX_POPUP_ITEMS)
    .map((item) => ({
      message: item,
      descriptor: describeNotifyMessage(item),
    }))
})

watch(
  () => notifyStore.messages.map((item) => `${item.id}:${item.read ? '1' : '0'}`).join('|'),
  () => {
    const aliveIDs = new Set(notifyStore.messages.map((item) => item.id))

    for (const id of dismissedIds.value) {
      if (!aliveIDs.has(id)) {
        dismissedIds.value.delete(id)
      }
    }

    for (const item of notifyStore.messages) {
      if (item.read || dismissedIds.value.has(item.id) || timers.has(item.id)) {
        continue
      }
      scheduleAutoDismiss(item.id)
    }

    for (const [id, timer] of timers.entries()) {
      const target = notifyStore.messages.find((item) => item.id === id)
      if (!target || target.read || dismissedIds.value.has(id)) {
        clearTimeout(timer)
        timers.delete(id)
      }
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  for (const timer of timers.values()) {
    clearTimeout(timer)
  }
  timers.clear()
})

function scheduleAutoDismiss(id: string): void {
  clearTimer(id)
  timers.set(
    id,
    setTimeout(() => {
      dismiss(id)
    }, AUTO_DISMISS_MS),
  )
}

function clearTimer(id: string): void {
  const timer = timers.get(id)
  if (!timer) {
    return
  }
  clearTimeout(timer)
  timers.delete(id)
}

function dismiss(id: string): void {
  clearTimer(id)
  const next = new Set(dismissedIds.value)
  next.add(id)
  dismissedIds.value = next
}

async function openCenter(messageId: string): Promise<void> {
  dismiss(messageId)
  void notifyStore.markRead(messageId)
  await router.push('/notifications')
}

function categoryLabel(type: string): string {
  if (type === 'order') {
    return '订单'
  }
  if (type === 'system') {
    return '系统'
  }
  return '通知'
}
</script>

<template>
  <aside class="pointer-events-none fixed right-4 top-[22rem] z-40 hidden w-80 space-y-2 md:block">
    <article
      v-for="entry in popupItems"
      :key="entry.message.id"
      class="pointer-events-auto rounded-xl border border-sky-200 bg-white/95 p-3 shadow-lg shadow-sky-900/10 backdrop-blur"
    >
      <div class="flex items-start gap-2">
        <button type="button" class="flex min-w-0 flex-1 items-start gap-2 text-left" @click="openCenter(entry.message.id)">
          <span
            class="mt-0.5 flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full text-xs font-semibold"
            :class="entry.descriptor.category === 'order' ? 'bg-amber-100 text-amber-700' : 'bg-sky-100 text-sky-700'"
          >
            {{ categoryLabel(entry.descriptor.category) }}
          </span>
          <span class="min-w-0 flex-1">
            <span class="flex items-center justify-between gap-2">
              <span class="truncate text-sm font-semibold text-stone-900">{{ entry.descriptor.title }}</span>
              <span class="text-[11px] text-stone-500">{{ formatNotifyMinute(entry.message.createdAt, entry.message.receivedAt) }}</span>
            </span>
            <span class="mt-0.5 line-clamp-2 block text-xs text-stone-700">{{ entry.descriptor.summary }}</span>
            <span class="mt-1 block truncate text-[11px] text-stone-500">{{ entry.descriptor.aggregateLabel }}</span>
          </span>
        </button>
        <button
          type="button"
          class="rounded-md p-1 text-stone-500 transition hover:bg-stone-100 hover:text-stone-700"
          @click.stop="dismiss(entry.message.id)"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4">
            <path d="M18 6L6 18M6 6l12 12" />
          </svg>
        </button>
      </div>
    </article>
  </aside>
</template>
