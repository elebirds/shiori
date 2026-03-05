<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const navItems = [
  { path: '/users', label: '用户管理' },
  { path: '/products', label: '商品管理' },
  { path: '/orders', label: '订单管理' },
  { path: '/payments/cdks', label: '支付/CDK' },
  { path: '/chat-governance', label: '聊天治理' },
]

const username = computed(() => authStore.user?.username || '管理员')

async function onLogout() {
  await authStore.logoutSession()
  await router.replace('/login')
}
</script>

<template>
  <header class="border-b border-slate-200 bg-white/95 backdrop-blur">
    <div class="mx-auto flex w-full max-w-7xl items-center justify-between px-6 py-4">
      <div class="flex items-center gap-6">
        <div>
          <p class="text-xs uppercase tracking-[0.24em] text-slate-500">Shiori</p>
          <p class="text-lg font-semibold text-slate-900">管理后台</p>
        </div>
        <nav class="flex items-center gap-2">
          <RouterLink
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="rounded-md px-3 py-2 text-sm font-medium transition"
            :class="route.path.startsWith(item.path) ? 'bg-blue-600 text-white' : 'text-slate-600 hover:bg-slate-100'"
          >
            {{ item.label }}
          </RouterLink>
        </nav>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-sm text-slate-600">{{ username }}</span>
        <button
          class="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700"
          type="button"
          @click="onLogout"
        >
          退出
        </button>
      </div>
    </div>
  </header>
</template>
