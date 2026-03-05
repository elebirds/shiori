<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getUserProfileByUserNo } from '@/api/auth'
import {
  listUserProductsV2,
  type ProductCategoryCode,
  type ProductConditionLevel,
  type ProductTradeMode,
} from '@/api/productV2'
import ResultState from '@/components/ResultState.vue'
import { useAuthStore } from '@/stores/auth'

type CenterTab = 'products' | 'reviews' | 'moments'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref<CenterTab>('products')
const page = ref(1)
const size = 12
const routeUserNo = computed(() => String(route.params.userNo || '').trim())

watch(routeUserNo, () => {
  activeTab.value = 'products'
  page.value = 1
})

const profileQuery = useQuery({
  queryKey: computed(() => ['public-profile', routeUserNo.value]),
  queryFn: () => getUserProfileByUserNo(routeUserNo.value),
  enabled: computed(() => Boolean(routeUserNo.value)),
  retry: false,
})

const ownerUserId = computed(() => profileQuery.data.value?.userId ?? null)

const productsQuery = useQuery({
  queryKey: computed(() => ['user-products-v2', ownerUserId.value, page.value, size]),
  queryFn: () =>
    listUserProductsV2(ownerUserId.value as number, {
      page: page.value,
      size,
      sortBy: 'CREATED_AT',
      sortDir: 'DESC',
    }),
  enabled: computed(() => activeTab.value === 'products' && Boolean(ownerUserId.value)),
})

const profile = computed(() => profileQuery.data.value)
const profileErrorMessage = computed(() => (profileQuery.error.value instanceof Error ? profileQuery.error.value.message : ''))
const productsErrorMessage = computed(() => (productsQuery.error.value instanceof Error ? productsQuery.error.value.message : ''))
const items = computed(() => productsQuery.data.value?.items || [])
const total = computed(() => productsQuery.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

const displayNickname = computed(() => profile.value?.nickname || profile.value?.username || '未知用户')
const displayUsername = computed(() => profile.value?.username || 'unknown')
const displayBio = computed(() => profile.value?.bio || '这个人很神秘，还没有简介')
const tags = computed(() => {
  const values: string[] = []
  values.push(formatGender(profile.value?.gender))
  if (profile.value?.age != null) {
    values.push(`${profile.value.age}岁`)
  }
  values.push('校园用户')
  return values.filter((value) => value.length > 0)
})

const isSelf = computed(() => {
  if (!authStore.isAuthenticated) {
    return false
  }
  return routeUserNo.value === String(authStore.user?.userNo || '')
})

function goEdit(): void {
  void router.push('/profile/edit')
}

function goDetail(productId: number): void {
  void router.push(`/products/${productId}`)
}

function switchTab(tab: CenterTab): void {
  activeTab.value = tab
  if (tab === 'products') {
    page.value = 1
  }
}

function formatGender(gender?: number): string {
  if (gender === 1) {
    return '男'
  }
  if (gender === 2) {
    return '女'
  }
  if (gender === 9) {
    return '保密'
  }
  return '未设置'
}

function formatMoney(priceCent?: number): string {
  if (priceCent == null) {
    return '¥ -'
  }
  return `¥ ${(priceCent / 100).toFixed(2)}`
}

function formatPriceRange(minPriceCent?: number, maxPriceCent?: number): string {
  if (minPriceCent == null && maxPriceCent == null) {
    return '价格待定'
  }
  if (minPriceCent === maxPriceCent) {
    return formatMoney(minPriceCent)
  }
  return `${formatMoney(minPriceCent)} ~ ${formatMoney(maxPriceCent)}`
}

function formatCategory(code: ProductCategoryCode): string {
  if (code === 'TEXTBOOK') {
    return '教材'
  }
  if (code === 'EXAM_MATERIAL') {
    return '考试资料'
  }
  if (code === 'NOTE') {
    return '笔记'
  }
  return '其他'
}

function formatCondition(code: ProductConditionLevel): string {
  if (code === 'NEW') {
    return '全新'
  }
  if (code === 'LIKE_NEW') {
    return '近新'
  }
  if (code === 'GOOD') {
    return '良好'
  }
  return '一般'
}

function formatTradeMode(code: ProductTradeMode): string {
  if (code === 'MEETUP') {
    return '面交'
  }
  if (code === 'DELIVERY') {
    return '邮寄'
  }
  return '均可'
}
</script>

<template>
  <section class="space-y-4">
    <ResultState
      :loading="profileQuery.isLoading.value"
      :error="profileErrorMessage"
      :empty="!profileQuery.isLoading.value && !profile"
      empty-text="未找到该用户"
    >
      <div class="space-y-4">
        <section class="relative overflow-hidden rounded-3xl border border-stone-200 bg-white/95 p-5 shadow-sm sm:p-6">
          <div class="pointer-events-none absolute inset-0 bg-gradient-to-br from-lime-300/35 via-emerald-200/20 to-amber-200/25"></div>
          <div class="relative flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
            <div class="flex min-w-0 items-center gap-4">
              <div class="h-20 w-20 overflow-hidden rounded-full border border-white/80 bg-stone-100 shadow-sm sm:h-24 sm:w-24">
                <img v-if="profile?.avatarUrl" :src="profile.avatarUrl" alt="avatar" class="h-full w-full object-cover" />
                <div v-else class="flex h-full w-full items-center justify-center text-xs text-stone-500">暂无头像</div>
              </div>
              <div class="min-w-0">
                <p class="line-clamp-1 text-xl font-semibold text-stone-900 sm:text-2xl">{{ displayNickname }}</p>
                <p class="mt-1 text-sm text-stone-600">@{{ displayUsername }}</p>
                <p class="mt-2 line-clamp-2 text-sm text-stone-700">{{ displayBio }}</p>
                <div class="mt-3 flex flex-wrap gap-2">
                  <span v-for="tag in tags" :key="tag" class="rounded-full bg-white/70 px-3 py-1 text-xs text-stone-700">
                    {{ tag }}
                  </span>
                </div>
              </div>
            </div>

            <button
              v-if="isSelf"
              type="button"
              class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700"
              @click="goEdit"
            >
              编辑
            </button>
          </div>

          <div class="relative mt-5 grid grid-cols-3 gap-2 text-center">
            <div class="rounded-xl bg-white/70 px-3 py-2">
              <p class="text-lg font-semibold text-stone-900">0</p>
              <p class="text-xs text-stone-600">粉丝</p>
            </div>
            <div class="rounded-xl bg-white/70 px-3 py-2">
              <p class="text-lg font-semibold text-stone-900">0</p>
              <p class="text-xs text-stone-600">关注</p>
            </div>
            <div class="rounded-xl bg-white/70 px-3 py-2">
              <p class="text-lg font-semibold text-stone-900">刚刚来过</p>
              <p class="text-xs text-stone-600">最近访问</p>
            </div>
          </div>
        </section>

        <section class="grid grid-cols-3 gap-3">
          <article class="rounded-2xl border border-stone-200 bg-white/95 p-4">
            <h2 class="text-sm font-semibold text-stone-900">信用档案</h2>
            <p class="mt-2 text-xs text-stone-600">交易信用能力正在完善中，后续会展示买卖双方评价与信用分。</p>
          </article>
          <article class="rounded-2xl border border-stone-200 bg-white/95 p-4">
            <h2 class="text-sm font-semibold text-stone-900">我的帖子</h2>
            <p class="mt-2 text-xs text-stone-600">帖子功能即将上线，敬请期待你的好物分享。</p>
          </article>
          <article class="rounded-2xl border border-stone-200 bg-white/95 p-4">
            <h2 class="text-sm font-semibold text-stone-900">夸夸墙</h2>
            <p class="mt-2 text-xs text-stone-600">收到的好评会在这里展示，帮助更多人认识你。</p>
          </article>
        </section>

        <section class="rounded-2xl border border-stone-200 bg-white/95 p-3 sm:p-4">
          <div class="flex gap-2 border-b border-stone-200 pb-3">
            <button
              type="button"
              class="rounded-lg px-3 py-1.5 text-sm transition"
              :class="activeTab === 'products' ? 'bg-stone-900 text-white' : 'text-stone-700 hover:bg-stone-100'"
              @click="switchTab('products')"
            >
              宝贝
            </button>
            <button
              type="button"
              class="rounded-lg px-3 py-1.5 text-sm transition"
              :class="activeTab === 'reviews' ? 'bg-stone-900 text-white' : 'text-stone-700 hover:bg-stone-100'"
              @click="switchTab('reviews')"
            >
              评价
            </button>
            <button
              type="button"
              class="rounded-lg px-3 py-1.5 text-sm transition"
              :class="activeTab === 'moments' ? 'bg-stone-900 text-white' : 'text-stone-700 hover:bg-stone-100'"
              @click="switchTab('moments')"
            >
              动态
            </button>
          </div>

          <div class="pt-4">
            <ResultState
              v-if="activeTab === 'products'"
              :loading="productsQuery.isLoading.value || productsQuery.isFetching.value"
              :error="productsErrorMessage"
              :empty="!productsQuery.isLoading.value && items.length === 0"
              empty-text="这个用户暂时没有在售商品"
            >
              <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <article
                  v-for="item in items"
                  :key="item.productId"
                  class="group cursor-pointer rounded-2xl border border-stone-200 bg-white/95 p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
                  @click="goDetail(item.productId)"
                >
                  <div class="aspect-[4/3] overflow-hidden rounded-xl bg-stone-100">
                    <img
                      v-if="item.coverImageUrl"
                      :src="item.coverImageUrl"
                      :alt="item.title"
                      class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
                    />
                    <div v-else class="flex h-full w-full items-center justify-center text-sm text-stone-500">暂无封面</div>
                  </div>

                  <h3 class="mt-3 line-clamp-1 text-base font-semibold text-stone-900">{{ item.title }}</h3>
                  <p class="mt-1 line-clamp-2 text-sm text-stone-600">{{ item.description || '暂无描述' }}</p>

                  <div class="mt-3 flex flex-wrap gap-1 text-xs text-stone-600">
                    <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCategory(item.categoryCode) }}</span>
                    <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCondition(item.conditionLevel) }}</span>
                    <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatTradeMode(item.tradeMode) }}</span>
                  </div>

                  <div class="mt-3 flex items-center justify-between text-sm">
                    <span class="font-semibold text-stone-900">{{ formatPriceRange(item.minPriceCent, item.maxPriceCent) }}</span>
                    <span class="text-xs text-stone-500">库存 {{ item.totalStock ?? 0 }}</span>
                  </div>
                </article>
              </div>

              <div class="mt-4 flex items-center justify-between rounded-2xl border border-stone-200 bg-white/90 px-4 py-3 text-sm">
                <span class="text-stone-600">第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
                <div class="flex gap-2">
                  <button
                    type="button"
                    class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="page <= 1 || productsQuery.isFetching.value"
                    @click="page -= 1"
                  >
                    上一页
                  </button>
                  <button
                    type="button"
                    class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="page >= totalPages || productsQuery.isFetching.value"
                    @click="page += 1"
                  >
                    下一页
                  </button>
                </div>
              </div>
            </ResultState>

            <section
              v-else
              class="rounded-2xl border border-dashed border-stone-300 bg-stone-50/70 px-4 py-10 text-center text-sm text-stone-600"
            >
              即将上线
            </section>
          </div>
        </section>
      </div>
    </ResultState>
  </section>
</template>
