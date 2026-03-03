<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useNotifyStore } from '@/stores/notify'

const router = useRouter()
const authStore = useAuthStore()
const notifyStore = useNotifyStore()

const busy = ref(false)

const commonNav = [
  { path: '/products', label: '商品' },
]

const secureNav = [
  { path: '/sell', label: '发布' },
  { path: '/my-products', label: '我的商品' },
  { path: '/orders', label: '订单' },
  { path: '/notifications', label: '通知' },
  { path: '/profile', label: '我的' },
]

const navItems = computed(() => {
  if (authStore.isAuthenticated) {
    return [...commonNav, ...secureNav]
  }
  return commonNav
})

const username = computed(() => authStore.user?.username || '游客')

async function handleLogout(): Promise<void> {
  if (busy.value) {
    return
  }

  busy.value = true
  try {
    await authStore.logoutSession()
    notifyStore.disconnect()
    notifyStore.clearMessages()
    await router.push('/login')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-stone-200/70 bg-white/80 backdrop-blur-xl">
    <div class="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
      <RouterLink to="/products" class="font-display text-xl tracking-wide text-stone-900">Shiori</RouterLink>

      <nav class="flex items-center gap-1">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="rounded-lg px-3 py-2 text-sm text-stone-700 transition hover:bg-amber-100/80 hover:text-stone-900"
          active-class="bg-amber-200/70 text-stone-900"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="flex items-center gap-2">
        <RouterLink
          v-if="authStore.isAuthenticated"
          to="/notifications"
          class="relative rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
        >
          通知
          <span
            v-if="notifyStore.unreadCount > 0"
            class="absolute -right-2 -top-2 rounded-full bg-amber-900 px-1.5 py-0.5 text-[10px] font-semibold text-amber-50"
          >
            {{ notifyStore.unreadCount }}
          </span>
        </RouterLink>

        <span class="hidden text-sm text-stone-600 sm:inline">{{ username }}</span>

        <RouterLink
          v-if="!authStore.isAuthenticated"
          to="/login"
          class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
        >
          登录
        </RouterLink>

        <button
          v-else
          type="button"
          class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
          :disabled="busy"
          @click="handleLogout"
        >
          退出
        </button>
      </div>
    </div>
  </header>
</template>
