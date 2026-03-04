<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { getAccessToken } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useNotifyStore } from '@/stores/notify'

const router = useRouter()
const authStore = useAuthStore()
const notifyStore = useNotifyStore()

const busy = ref(false)
const dropdownOpen = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const avatarPreviewUrl = ref('')
let avatarBlobUrl: string | null = null

const commonNav = [
  { path: '/products', label: '商品' },
]

const secureNav = [
  { path: '/sell', label: '发布' },
  { path: '/my-products', label: '我的商品' },
  { path: '/orders', label: '订单' },
  { path: '/seller/orders', label: '卖家工作台' },
]

const navItems = computed(() => {
  if (authStore.isAuthenticated) {
    return [...commonNav, ...secureNav]
  }
  return commonNav
})

const username = computed(() => authStore.user?.username || '游客')
const profileNickname = computed(() => authStore.profile?.nickname || username.value)

async function handleLogout(): Promise<void> {
  if (busy.value) {
    return
  }

  busy.value = true
  try {
    await authStore.logoutSession()
    notifyStore.disconnect()
    notifyStore.clearMessages()
    dropdownOpen.value = false
    clearAvatarPreview()
    await router.push('/login')
  } finally {
    busy.value = false
  }
}

async function ensureProfileLoaded(): Promise<void> {
  if (!authStore.isAuthenticated || authStore.profile) {
    return
  }
  try {
    await authStore.fetchMyProfile()
  } catch {
    // ignore profile loading failures in header to avoid blocking navigation
  }
}

function clearAvatarPreview(): void {
  if (avatarBlobUrl) {
    URL.revokeObjectURL(avatarBlobUrl)
    avatarBlobUrl = null
  }
  avatarPreviewUrl.value = ''
}

async function hydrateAvatarPreview(avatarUrl?: string): Promise<void> {
  clearAvatarPreview()
  if (!avatarUrl) {
    return
  }
  if (avatarUrl.startsWith('http://') || avatarUrl.startsWith('https://')) {
    avatarPreviewUrl.value = avatarUrl
    return
  }

  const token = getAccessToken()
  if (!token) {
    return
  }

  try {
    const response = await fetch(avatarUrl, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })
    if (!response.ok) {
      return
    }
    const blob = await response.blob()
    avatarBlobUrl = URL.createObjectURL(blob)
    avatarPreviewUrl.value = avatarBlobUrl
  } catch {
    avatarPreviewUrl.value = ''
  }
}

function toggleDropdown(): void {
  dropdownOpen.value = !dropdownOpen.value
}

function closeDropdown(): void {
  dropdownOpen.value = false
}

function handleDocumentClick(event: MouseEvent): void {
  if (!menuRef.value) {
    return
  }
  const target = event.target as Node | null
  if (target && !menuRef.value.contains(target)) {
    closeDropdown()
  }
}

onMounted(() => {
  void ensureProfileLoaded()
  document.addEventListener('click', handleDocumentClick)
})

watch(
  () => authStore.profile?.avatarUrl,
  (avatarUrl) => {
    void hydrateAvatarPreview(avatarUrl)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  clearAvatarPreview()
})
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
          v-if="!authStore.isAuthenticated"
          to="/login"
          class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
        >
          登录
        </RouterLink>

        <div v-else ref="menuRef" class="relative flex items-center gap-2">
          <span class="relative h-8 w-8 overflow-hidden rounded-full border border-stone-200 bg-stone-100">
            <img v-if="avatarPreviewUrl" :src="avatarPreviewUrl" alt="avatar" class="h-full w-full object-cover" />
            <span v-else class="flex h-full w-full items-center justify-center text-xs text-stone-500">头像</span>
            <span
              v-if="notifyStore.unreadCount > 0"
              class="absolute -right-1 -top-1 rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-semibold leading-none text-white"
            >
              {{ notifyStore.unreadCount > 99 ? '99+' : notifyStore.unreadCount }}
            </span>
          </span>
          <span class="hidden leading-tight sm:block">
            <span class="block text-sm font-medium text-stone-900">{{ profileNickname }}</span>
            <span class="block text-xs text-stone-500">@{{ username }}</span>
          </span>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-full border border-stone-300 text-stone-600 transition hover:bg-stone-100"
            @click.stop="toggleDropdown"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="h-4 w-4 transition-transform"
              :class="dropdownOpen ? 'rotate-180' : ''"
            >
              <path d="M6 9l6 6 6-6" />
            </svg>
          </button>

          <div
            v-if="dropdownOpen"
            class="absolute right-0 top-full mt-2 w-48 rounded-xl border border-stone-200 bg-white py-1 shadow-lg"
          >
            <RouterLink
              to="/profile"
              class="block px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
              @click="closeDropdown"
            >
              个人中心
            </RouterLink>
            <RouterLink
              to="/profile/edit"
              class="block px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
              @click="closeDropdown"
            >
              编辑资料
            </RouterLink>
            <RouterLink
              to="/notifications"
              class="flex items-center justify-between px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
              @click="closeDropdown"
            >
              <span>通知</span>
              <span
                v-if="notifyStore.unreadCount > 0"
                class="rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-semibold leading-none text-white"
              >
                {{ notifyStore.unreadCount > 99 ? '99+' : notifyStore.unreadCount }}
              </span>
            </RouterLink>
            <RouterLink
              to="/account/security"
              class="block px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
              @click="closeDropdown"
            >
              账号与安全
            </RouterLink>
            <button
              type="button"
              class="block w-full px-3 py-2 text-left text-sm text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="busy"
              @click="handleLogout"
            >
              退出登录
            </button>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>
