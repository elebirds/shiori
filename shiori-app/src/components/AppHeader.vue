<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getAccessToken } from '@/api/http'
import { redeemCdk } from '@/api/payment'
import UserAvatar from '@/components/UserAvatar.vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNotifyStore } from '@/stores/notify'
import { resolvePaymentErrorMessage } from '@/utils/paymentError'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const notifyStore = useNotifyStore()
const chatStore = useChatStore()

const busy = ref(false)
const dropdownOpen = ref(false)
const mobileMenuOpen = ref(false)
const isMobileLayout = ref(false)
const cdkDialogOpen = ref(false)
const cdkCode = ref('')
const cdkMessage = ref('')
const cdkError = ref('')
const cdkSubmitting = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const avatarPreviewUrl = ref('')
let avatarBlobUrl: string | null = null
const MOBILE_BREAKPOINT_PX = 768

const commonNav = [
  { path: '/products', label: '商品' },
]

const secureNav = [
  { path: '/square', label: '广场' },
  { path: '/sell', label: '发布' },
  { path: '/my-products', label: '我的商品' },
  { path: '/orders', label: '订单' },
  { path: '/wallet', label: '钱包' },
  { path: '/seller/orders', label: '卖家工作台' },
  { path: '/seller/refunds', label: '退款审核' },
]

const navItems = computed(() => {
  if (authStore.isAuthenticated) {
    return [...commonNav, ...secureNav]
  }
  return commonNav
})

const username = computed(() => authStore.user?.username || '游客')
const profileNickname = computed(() => authStore.profile?.nickname || username.value)
const showBackButton = computed(() => route.name === 'product-detail')

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
    closeMobileMenu()
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

function updateLayoutMode(): void {
  isMobileLayout.value = window.innerWidth < MOBILE_BREAKPOINT_PX
}

function toggleMobileMenu(): void {
  mobileMenuOpen.value = !mobileMenuOpen.value
  if (mobileMenuOpen.value) {
    closeDropdown()
  }
}

function closeMobileMenu(): void {
  mobileMenuOpen.value = false
}

function openCdkDialog(): void {
  cdkDialogOpen.value = true
  cdkMessage.value = ''
  cdkError.value = ''
  cdkCode.value = ''
  closeDropdown()
  closeMobileMenu()
}

function closeCdkDialog(): void {
  if (cdkSubmitting.value) {
    return
  }
  cdkDialogOpen.value = false
}

async function handleCdkRedeem(): Promise<void> {
  cdkMessage.value = ''
  cdkError.value = ''
  const code = cdkCode.value.trim()
  if (!code) {
    cdkError.value = '请输入 CDK 兑换码'
    return
  }

  cdkSubmitting.value = true
  try {
    const response = await redeemCdk({ code })
    cdkCode.value = ''
    cdkMessage.value = `兑换成功，已入账 ¥ ${(response.redeemAmountCent / 100).toFixed(2)}`
  } catch (error) {
    cdkError.value = resolvePaymentErrorMessage(error, 'CDK 兑换失败，请稍后重试')
  } finally {
    cdkSubmitting.value = false
  }
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
  updateLayoutMode()
  void ensureProfileLoaded()
  window.addEventListener('resize', updateLayoutMode)
  document.addEventListener('click', handleDocumentClick)
})

watch(
  () => authStore.profile?.avatarUrl,
  (avatarUrl) => {
    void hydrateAvatarPreview(avatarUrl)
  },
  { immediate: true },
)

watch(
  () => route.fullPath,
  () => {
    closeDropdown()
    closeMobileMenu()
    cdkDialogOpen.value = false
  },
)

watch(
  isMobileLayout,
  (mobile) => {
    if (!mobile) {
      closeMobileMenu()
    }
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateLayoutMode)
  document.removeEventListener('click', handleDocumentClick)
  clearAvatarPreview()
})
</script>

<template>
  <header class="sticky top-0 z-20 border-b border-stone-200/70 bg-white/80 backdrop-blur-xl">
    <div class="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
      <div class="flex items-center gap-2">
        <button
          v-if="showBackButton"
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-lg border border-stone-300 text-sm text-stone-700 transition hover:bg-stone-100"
          aria-label="返回"
          @click="router.back()"
        >
          <span aria-hidden="true">&lt;</span>
        </button>
        <RouterLink to="/products" class="font-display text-xl tracking-wide text-stone-900">Shiori</RouterLink>
      </div>

      <nav class="hidden items-center gap-1 md:flex">
        <RouterLink
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="relative rounded-lg px-3 py-2 text-sm text-stone-700 transition hover:bg-amber-100/80 hover:text-stone-900"
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

        <template v-else>
          <RouterLink
            to="/chat"
            class="relative flex h-8 w-8 items-center justify-center text-stone-700 transition duration-200 hover:-translate-y-0.5 hover:scale-105 hover:text-stone-900"
            active-class="text-stone-900"
            aria-label="聊天"
            title="聊天"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="h-5 w-5 transition-transform duration-200">
              <path d="M6 9a3 3 0 0 1 3-3h6a3 3 0 0 1 3 3v5a3 3 0 0 1-3 3H9l-3 2v-2.5A3 3 0 0 1 3 14V9a3 3 0 0 1 3-3z" />
            </svg>
            <span
              v-if="chatStore.chatUnreadMessageCount > 0"
              class="absolute -right-1 -top-1 rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-semibold leading-none text-white"
            >
              {{ chatStore.chatUnreadMessageCount > 99 ? '99+' : chatStore.chatUnreadMessageCount }}
            </span>
          </RouterLink>

          <RouterLink
            to="/cart"
            class="flex h-8 w-8 items-center justify-center text-stone-700 transition duration-200 hover:-translate-y-0.5 hover:scale-105 hover:text-stone-900"
            active-class="text-stone-900"
            aria-label="购物车"
            title="购物车"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="h-5 w-5 transition-transform duration-200">
              <circle cx="9" cy="19" r="1.6" />
              <circle cx="17" cy="19" r="1.6" />
              <path d="M3 5h2l2.2 9.2a1 1 0 0 0 1 .8h8.5a1 1 0 0 0 1-.8L20 8H7.1" />
            </svg>
          </RouterLink>

          <RouterLink
            to="/notifications"
            class="relative flex h-8 w-8 items-center justify-center text-stone-700 transition duration-200 hover:-translate-y-0.5 hover:scale-105 hover:text-stone-900"
            active-class="text-stone-900"
            aria-label="通知中心"
            title="通知中心"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="h-5 w-5 transition-transform duration-200">
              <path d="M15 17H5a1 1 0 0 1-1-1v-.8a4 4 0 0 1 1.17-2.83L6 11.6V9a6 6 0 1 1 12 0v2.6l.83.77A4 4 0 0 1 20 15.2v.8a1 1 0 0 1-1 1h-4" />
              <path d="M9 18a3 3 0 0 0 6 0" />
            </svg>
            <span
              v-if="notifyStore.unreadCount > 0"
              class="absolute -right-1 -top-1 z-20 rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-semibold leading-none text-white"
            >
              {{ notifyStore.unreadCount > 99 ? '99+' : notifyStore.unreadCount }}
            </span>
          </RouterLink>

          <div v-if="!isMobileLayout" ref="menuRef" class="relative flex items-center gap-2">
            <span class="relative h-8 w-8">
              <UserAvatar :src="avatarPreviewUrl" :name="profileNickname" size-class="h-8 w-8" fallback-size-class="h-4 w-4" />
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
              <button
                type="button"
                class="block w-full px-3 py-2 text-left text-sm text-stone-700 transition hover:bg-stone-100"
                @click="openCdkDialog"
              >
                CDK 兑换
              </button>
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

          <RouterLink
            v-else
            to="/profile"
            class="relative"
            aria-label="个人中心"
            title="个人中心"
          >
            <UserAvatar :src="avatarPreviewUrl" :name="profileNickname" size-class="h-8 w-8" fallback-size-class="h-4 w-4" />
          </RouterLink>
        </template>

        <button
          v-if="isMobileLayout"
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full border border-stone-300 text-stone-700 transition hover:bg-stone-100"
          aria-label="打开菜单"
          @click.stop="toggleMobileMenu"
        >
          <svg v-if="!mobileMenuOpen" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" class="h-4 w-4">
            <path d="M4 7h16M4 12h16M4 17h16" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" class="h-4 w-4">
            <path d="M6 6l12 12M18 6l-12 12" />
          </svg>
        </button>
      </div>
    </div>
  </header>

  <div v-if="isMobileLayout && mobileMenuOpen" class="fixed inset-0 z-30">
    <button
      type="button"
      class="absolute inset-0 bg-stone-900/35"
      aria-label="关闭侧边菜单"
      @click="closeMobileMenu"
    />
    <aside class="absolute right-0 top-0 h-full w-72 max-w-[88vw] border-l border-stone-200 bg-white shadow-2xl">
      <div class="flex items-center justify-between border-b border-stone-200 px-4 py-4">
        <p class="text-sm font-semibold tracking-wide text-stone-700">菜单</p>
        <button
          type="button"
          class="flex h-8 w-8 items-center justify-center rounded-full border border-stone-300 text-stone-700 transition hover:bg-stone-100"
          aria-label="关闭"
          @click="closeMobileMenu"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" class="h-4 w-4">
            <path d="M6 6l12 12M18 6l-12 12" />
          </svg>
        </button>
      </div>

      <div class="flex h-[calc(100%-65px)] flex-col overflow-y-auto p-4">
        <nav class="space-y-1">
          <RouterLink
            v-for="item in navItems"
            :key="`mobile-${item.path}`"
            :to="item.path"
            class="block rounded-lg px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
            active-class="bg-stone-100 text-stone-900"
            @click="closeMobileMenu"
          >
            {{ item.label }}
          </RouterLink>
          <RouterLink
            v-if="authStore.isAuthenticated"
            to="/chat"
            class="flex items-center justify-between rounded-lg px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
            active-class="bg-stone-100 text-stone-900"
            @click="closeMobileMenu"
          >
            <span>聊天</span>
            <span
              v-if="chatStore.chatUnreadMessageCount > 0"
              class="rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] font-semibold leading-none text-white"
            >
              {{ chatStore.chatUnreadMessageCount > 99 ? '99+' : chatStore.chatUnreadMessageCount }}
            </span>
          </RouterLink>
          <RouterLink
            v-if="authStore.isAuthenticated"
            to="/cart"
            class="block rounded-lg px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
            active-class="bg-stone-100 text-stone-900"
            @click="closeMobileMenu"
          >
            购物车
          </RouterLink>
        </nav>

        <div class="mt-4 border-t border-stone-200 pt-4">
          <template v-if="authStore.isAuthenticated">
            <p class="px-3 text-xs text-stone-500">账号</p>
            <div class="mt-2 space-y-1">
              <RouterLink
                to="/profile"
                class="block rounded-lg px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
                @click="closeMobileMenu"
              >
                个人中心
              </RouterLink>
              <button
                type="button"
                class="block w-full rounded-lg px-3 py-2 text-left text-sm text-stone-700 transition hover:bg-stone-100"
                @click="openCdkDialog"
              >
                CDK 兑换
              </button>
              <RouterLink
                to="/account/security"
                class="block rounded-lg px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
                @click="closeMobileMenu"
              >
                账号与安全
              </RouterLink>
              <button
                type="button"
                class="block w-full rounded-lg px-3 py-2 text-left text-sm text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="busy"
                @click="handleLogout"
              >
                退出登录
              </button>
            </div>
          </template>

          <RouterLink
            v-else
            to="/login"
            class="block rounded-lg px-3 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
            @click="closeMobileMenu"
          >
            登录
          </RouterLink>
        </div>
      </div>
    </aside>
  </div>

  <div v-if="cdkDialogOpen" class="fixed inset-0 z-40">
    <button
      type="button"
      class="absolute inset-0 bg-stone-900/45"
      aria-label="关闭 CDK 兑换弹窗"
      @click="closeCdkDialog"
    />
    <div class="absolute inset-0 flex items-center justify-center p-4">
      <section class="w-full max-w-md rounded-2xl border border-blue-100 bg-white p-5 shadow-2xl">
        <div class="flex items-center justify-between">
          <h3 class="text-base font-semibold text-stone-900">CDK 兑换</h3>
          <button
            type="button"
            class="flex h-8 w-8 items-center justify-center rounded-full border border-stone-300 text-stone-600 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="cdkSubmitting"
            aria-label="关闭"
            @click="closeCdkDialog"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" class="h-4 w-4">
              <path d="M6 6l12 12M18 6l-12 12" />
            </svg>
          </button>
        </div>

        <p class="mt-2 text-sm text-stone-500">输入管理员发放的 CDK，兑换金额将直接入账可用余额。</p>

        <div class="mt-4 space-y-3">
          <input
            v-model.trim="cdkCode"
            type="text"
            maxlength="128"
            placeholder="请输入 CDK 兑换码"
            class="h-11 w-full rounded-xl border border-blue-200 px-3 text-sm text-stone-800 outline-none transition focus:border-[var(--shiori-pay-blue-600)] focus:ring-2 focus:ring-[var(--shiori-pay-blue-500)]/20"
            @keyup.enter="handleCdkRedeem"
          />
          <button
            type="button"
            class="h-11 w-full rounded-xl bg-[var(--shiori-pay-blue-700)] px-5 text-sm font-medium text-white transition hover:bg-[var(--shiori-pay-blue-800)] disabled:cursor-not-allowed disabled:opacity-65"
            :disabled="cdkSubmitting"
            @click="handleCdkRedeem"
          >
            {{ cdkSubmitting ? '兑换中...' : '立即兑换' }}
          </button>
        </div>

        <p v-if="cdkMessage" class="mt-3 text-sm text-emerald-600">{{ cdkMessage }}</p>
        <p v-if="cdkError" class="mt-3 text-sm text-rose-600">{{ cdkError }}</p>
      </section>
    </div>
  </div>
</template>
