<script setup lang="ts">
import { computed } from 'vue'

import AppHeader from '@/components/AppHeader.vue'
import { useNotifyStore } from '@/stores/notify'

const notifyStore = useNotifyStore()

const latestMessages = computed(() => notifyStore.messages.slice(0, 3))
</script>

<template>
  <div class="min-h-screen bg-[var(--shiori-bg)] text-stone-900">
    <div class="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div class="absolute -left-20 top-24 h-64 w-64 rounded-full bg-amber-300/30 blur-3xl"></div>
      <div class="absolute -right-10 top-1/3 h-72 w-72 rounded-full bg-lime-200/30 blur-3xl"></div>
    </div>

    <AppHeader />

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
