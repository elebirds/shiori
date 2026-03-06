<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useNotifyStore } from '@/stores/notify'
import { createPopupLifecycle } from '@/utils/popupLifecycle'
import { describeNotifyMessage, formatNotifyMinute } from '@/utils/notifyMessage'

const router = useRouter()
const route = useRoute()
const notifyStore = useNotifyStore()

const AUTO_DISMISS_MS = 10_000
const MAX_POPUP_ITEMS = 3
const dismissedIds = ref(new Set<string>())
const popupLifecycle = createPopupLifecycle(AUTO_DISMISS_MS)

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
    popupLifecycle.pruneByAliveIds(aliveIDs)

    const nextDismissed = new Set(dismissedIds.value)
    for (const id of nextDismissed) {
      if (!aliveIDs.has(id)) {
        nextDismissed.delete(id)
      }
    }
    dismissedIds.value = nextDismissed

    for (const item of notifyStore.messages) {
      if (item.read || dismissedIds.value.has(item.id)) {
        continue
      }
      popupLifecycle.schedule(item.id, () => {
        dismissOnly(item.id)
      })
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  popupLifecycle.clearAll()
})

function dismissOnly(id: string): void {
  popupLifecycle.clear(id)
  const next = new Set(dismissedIds.value)
  next.add(id)
  dismissedIds.value = next
}

async function markReadAndDismiss(id: string): Promise<void> {
  dismissOnly(id)
  await notifyStore.markRead(id)
}

async function openCenter(messageId: string): Promise<void> {
  await markReadAndDismiss(messageId)
  await router.push('/notifications')
}

async function markReadFromPopup(messageId: string): Promise<void> {
  await markReadAndDismiss(messageId)
}

async function closePopup(messageId: string): Promise<void> {
  await markReadAndDismiss(messageId)
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
  <aside class="pointer-events-none fixed right-4 top-20 z-40 hidden w-80 space-y-2 md:block">
    <TransitionGroup name="notify-popup" tag="div" class="space-y-2">
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
          <div class="flex items-center gap-1">
            <button
              type="button"
              class="rounded-md p-1 text-emerald-600 transition hover:bg-emerald-50 hover:text-emerald-700"
              title="标记已读"
              aria-label="标记已读"
              @click.stop="markReadFromPopup(entry.message.id)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4">
                <path d="M20 7L10 17L4 11" />
              </svg>
            </button>
            <button
              type="button"
              class="rounded-md p-1 text-stone-500 transition hover:bg-stone-100 hover:text-stone-700"
              title="关闭并标记已读"
              aria-label="关闭并标记已读"
              @click.stop="closePopup(entry.message.id)"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="h-4 w-4">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </article>
    </TransitionGroup>
  </aside>
</template>

<style scoped>
.notify-popup-enter-active,
.notify-popup-leave-active {
  transition: all 0.24s ease;
}

.notify-popup-enter-from {
  opacity: 0;
  transform: translateX(18px) translateY(-6px) scale(0.98);
}

.notify-popup-leave-to {
  opacity: 0;
  transform: translateX(20px) scale(0.96);
}

.notify-popup-move {
  transition: transform 0.24s ease;
}
</style>
