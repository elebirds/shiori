<script setup lang="ts">
import { computed, ref } from 'vue'

import { useNotifyStore } from '@/stores/notify'
import {
  describeNotifyMessage,
  formatNotifyTime,
  resolveNotifyCategory,
  type NotifyCategory,
} from '@/utils/notifyMessage'

type NotifyTab = 'all' | 'order' | 'system'

const notifyStore = useNotifyStore()
const activeTab = ref<NotifyTab>('all')

const connected = computed(() => notifyStore.connected)
const unreadCount = computed(() => notifyStore.unreadCount)

const messages = computed(() =>
  notifyStore.messages.map((item) => ({
    raw: item,
    descriptor: describeNotifyMessage(item),
  })),
)

const filteredMessages = computed(() => {
  if (activeTab.value === 'all') {
    return messages.value
  }
  const targetCategory: NotifyCategory = activeTab.value
  return messages.value.filter((item) => resolveNotifyCategory(item.raw.type) === targetCategory)
})

const tabOptions = computed(() => [
  { key: 'all' as const, label: '全部通知', count: messages.value.length },
  { key: 'order' as const, label: '订单通知', count: messages.value.filter((item) => item.descriptor.category === 'order').length },
  { key: 'system' as const, label: '系统通知', count: messages.value.filter((item) => item.descriptor.category === 'system').length },
])

function badgeClass(category: NotifyCategory): string {
  if (category === 'order') {
    return 'bg-amber-100 text-amber-700 border-amber-200'
  }
  if (category === 'system') {
    return 'bg-sky-100 text-sky-700 border-sky-200'
  }
  return 'bg-stone-100 text-stone-700 border-stone-200'
}

function categoryLabel(category: NotifyCategory): string {
  if (category === 'order') {
    return '订单'
  }
  if (category === 'system') {
    return '系统'
  }
  return '通知'
}

function cardClass(read: boolean, category: NotifyCategory): string {
  if (read) {
    return 'border-stone-200 bg-white/90'
  }
  if (category === 'order') {
    return 'border-amber-300 bg-amber-50/80'
  }
  if (category === 'system') {
    return 'border-sky-300 bg-sky-50/80'
  }
  return 'border-stone-300 bg-stone-50/90'
}
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/90 p-4 shadow-sm shadow-stone-300/30">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 class="font-display text-2xl text-stone-900">通知中心</h1>
          <p class="mt-1 text-sm text-stone-600">按类型查看系统通知与订单通知，并支持逐条或全部标记已读。</p>
        </div>
        <div class="flex items-center gap-2">
          <span
            class="rounded-full px-3 py-1 text-xs font-semibold"
            :class="connected ? 'bg-emerald-100 text-emerald-700' : 'bg-stone-200 text-stone-700'"
          >
            {{ connected ? '实时连接中' : '连接已断开' }}
          </span>
          <span class="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-700">未读 {{ unreadCount }}</span>
        </div>
      </div>
    </header>

    <section class="rounded-2xl border border-stone-200 bg-white/90 p-3">
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-for="tab in tabOptions"
          :key="tab.key"
          type="button"
          class="inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm transition"
          :class="
            activeTab === tab.key
              ? 'border-amber-300 bg-amber-100 text-amber-800'
              : 'border-stone-300 bg-white text-stone-700 hover:bg-stone-100'
          "
          @click="activeTab = tab.key"
        >
          <span>{{ tab.label }}</span>
          <span class="rounded-full bg-white/80 px-2 py-0.5 text-xs text-stone-700">{{ tab.count }}</span>
        </button>

        <button
          v-if="messages.length > 0 && unreadCount > 0"
          type="button"
          class="ml-auto rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
          @click="notifyStore.markAllRead"
        >
          全部标记已读
        </button>
      </div>
    </section>

    <section v-if="filteredMessages.length > 0" class="space-y-3">
      <article
        v-for="item in filteredMessages"
        :key="item.raw.id"
        class="rounded-2xl border p-4 transition"
        :class="cardClass(item.raw.read, item.descriptor.category)"
      >
        <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <span class="rounded-full border px-2 py-0.5 text-[11px] font-semibold" :class="badgeClass(item.descriptor.category)">
                {{ categoryLabel(item.descriptor.category) }}
              </span>
              <p class="text-base font-semibold text-stone-900">{{ item.descriptor.title }}</p>
              <span
                v-if="!item.raw.read"
                class="rounded-full bg-rose-600 px-2 py-0.5 text-[10px] font-semibold leading-none text-white"
              >
                新
              </span>
            </div>

            <p class="mt-2 text-sm text-stone-700">{{ item.descriptor.summary }}</p>

            <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-stone-500">
              <span>{{ item.descriptor.aggregateLabel }}</span>
              <span>{{ formatNotifyTime(item.raw.createdAt, item.raw.receivedAt) }}</span>
              <span v-if="item.raw.read && item.raw.readAt">已读于 {{ formatNotifyTime(item.raw.readAt, item.raw.receivedAt) }}</span>
            </div>
          </div>

          <button
            v-if="!item.raw.read"
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
            @click="notifyStore.markRead(item.raw.id)"
          >
            标记已读
          </button>
        </div>
      </article>
    </section>

    <section v-else class="rounded-2xl border border-stone-200 bg-white/90 p-8 text-center text-sm text-stone-500">
      当前分类下还没有通知消息。
    </section>
  </section>
</template>
