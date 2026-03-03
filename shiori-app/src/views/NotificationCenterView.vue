<script setup lang="ts">
import { computed } from 'vue'

import { useNotifyStore } from '@/stores/notify'

const notifyStore = useNotifyStore()

const messages = computed(() => notifyStore.messages)
const connected = computed(() => notifyStore.connected)
const unreadCount = computed(() => notifyStore.unreadCount)

function formatTime(timestamp: string, receivedAt: number): string {
  const fromEvent = new Date(timestamp)
  if (!Number.isNaN(fromEvent.getTime())) {
    return fromEvent.toLocaleString('zh-CN')
  }
  return new Date(receivedAt).toLocaleString('zh-CN')
}

function payloadPreview(payload: Record<string, unknown>): string {
  try {
    return JSON.stringify(payload, null, 2)
  } catch {
    return '{}'
  }
}
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/90 p-4">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 class="font-display text-2xl text-stone-900">通知中心</h1>
          <p class="mt-1 text-sm text-stone-600">实时消费 notify WebSocket 的事件消息。</p>
        </div>
        <div class="flex items-center gap-2">
          <span
            class="rounded-full px-3 py-1 text-xs font-semibold"
            :class="connected ? 'bg-emerald-100 text-emerald-700' : 'bg-stone-200 text-stone-700'"
          >
            {{ connected ? '已连接' : '未连接' }}
          </span>
          <span class="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-700">未读 {{ unreadCount }}</span>
        </div>
      </div>
    </header>

    <section class="flex flex-wrap gap-2">
      <button
        type="button"
        class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
        @click="notifyStore.markAllRead"
      >
        全部标记已读
      </button>
      <button
        type="button"
        class="rounded-lg border border-rose-300 px-3 py-1.5 text-sm text-rose-700 transition hover:bg-rose-50"
        @click="notifyStore.clearMessages"
      >
        清空通知
      </button>
    </section>

    <section v-if="messages.length > 0" class="space-y-3">
      <article
        v-for="item in messages"
        :key="item.id"
        class="rounded-2xl border p-4 transition"
        :class="item.read ? 'border-stone-200 bg-white/80' : 'border-amber-300 bg-amber-50/60'"
      >
        <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p class="text-base font-semibold text-stone-900">{{ item.type }}</p>
            <p class="mt-1 text-xs text-stone-500">订单：{{ item.aggregateId }}</p>
            <p class="mt-1 text-xs text-stone-500">时间：{{ formatTime(item.createdAt, item.receivedAt) }}</p>
          </div>
          <button
            v-if="!item.read"
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
            @click="notifyStore.markRead(item.id)"
          >
            标记已读
          </button>
        </div>
        <pre class="mt-3 max-h-48 overflow-auto rounded-xl bg-stone-900/95 p-3 text-xs text-stone-100">{{ payloadPreview(item.payload) }}</pre>
      </article>
    </section>

    <section v-else class="rounded-2xl border border-stone-200 bg-white/90 p-8 text-center text-sm text-stone-500">
      还没有通知消息，完成支付后会在这里出现 `OrderPaid` 事件。
    </section>
  </section>
</template>

