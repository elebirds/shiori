<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { followUser, getUserProfileByUserNo, getUserProfilesByUserIds, unfollowUser } from '@/api/auth'
import { getUserCreditProfileV2, listUserPraiseWallV2, listUserReviewsV2 } from '@/api/orderV2'
import {
  listUserProductsV2,
  type ProductCategoryCode,
  type ProductConditionLevel,
  type ProductTradeMode,
} from '@/api/productV2'
import { deletePostV2, listUserPostsV2 } from '@/api/social'
import PostFeedCard from '@/components/PostFeedCard.vue'
import ResultState from '@/components/ResultState.vue'
import { useAuthStore } from '@/stores/auth'

type CenterTab = 'products' | 'reviews' | 'moments'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const queryClient = useQueryClient()

const activeTab = ref<CenterTab>('products')
const page = ref(1)
const size = 12
const reviewPage = ref(1)
const reviewSize = 10
const postPage = ref(1)
const postSize = 10
const deletingPostId = ref<number | null>(null)
const routeUserNo = computed(() => String(route.params.userNo || '').trim())

watch(routeUserNo, () => {
  activeTab.value = 'products'
  page.value = 1
  reviewPage.value = 1
  postPage.value = 1
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

const creditQuery = useQuery({
  queryKey: computed(() => ['user-credit-profile-v2', ownerUserId.value]),
  queryFn: () => getUserCreditProfileV2(ownerUserId.value as number),
  enabled: computed(() => Boolean(ownerUserId.value)),
})

const praisePreviewQuery = useQuery({
  queryKey: computed(() => ['user-praise-wall-preview-v2', ownerUserId.value]),
  queryFn: () =>
    listUserPraiseWallV2(ownerUserId.value as number, {
      page: 1,
      size: 3,
    }),
  enabled: computed(() => Boolean(ownerUserId.value)),
})

const reviewQuery = useQuery({
  queryKey: computed(() => ['user-reviews-v2', ownerUserId.value, reviewPage.value, reviewSize]),
  queryFn: () =>
    listUserReviewsV2(ownerUserId.value as number, {
      page: reviewPage.value,
      size: reviewSize,
    }),
  enabled: computed(() => activeTab.value === 'reviews' && Boolean(ownerUserId.value)),
})

const momentsQuery = useQuery({
  queryKey: computed(() => ['user-posts-v2', ownerUserId.value, postPage.value, postSize]),
  queryFn: () =>
    listUserPostsV2(ownerUserId.value as number, {
      page: postPage.value,
      size: postSize,
    }),
  enabled: computed(() => Boolean(ownerUserId.value)),
})

const reviewerIds = computed(() => {
  const values = (reviewQuery.data.value?.items || []).map((item) => item.reviewerUserId).filter((item) => item > 0)
  return Array.from(new Set(values))
})

const reviewerProfilesQuery = useQuery({
  queryKey: computed(() => ['user-reviewer-profiles', reviewerIds.value.join(',')]),
  queryFn: () => getUserProfilesByUserIds(reviewerIds.value),
  enabled: computed(() => reviewerIds.value.length > 0),
})

const profile = computed(() => profileQuery.data.value)
const profileErrorMessage = computed(() => (profileQuery.error.value instanceof Error ? profileQuery.error.value.message : ''))
const productsErrorMessage = computed(() => (productsQuery.error.value instanceof Error ? productsQuery.error.value.message : ''))
const creditErrorMessage = computed(() => (creditQuery.error.value instanceof Error ? creditQuery.error.value.message : ''))
const reviewErrorMessage = computed(() => (reviewQuery.error.value instanceof Error ? reviewQuery.error.value.message : ''))
const momentsErrorMessage = computed(() => (momentsQuery.error.value instanceof Error ? momentsQuery.error.value.message : ''))
const items = computed(() => productsQuery.data.value?.items || [])
const total = computed(() => productsQuery.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const creditProfile = computed(() => creditQuery.data.value)
const praiseItems = computed(() => praisePreviewQuery.data.value?.items || [])
const praiseTotal = computed(() => praisePreviewQuery.data.value?.total || 0)
const reviewItems = computed(() => reviewQuery.data.value?.items || [])
const reviewTotal = computed(() => reviewQuery.data.value?.total || 0)
const reviewTotalPages = computed(() => Math.max(1, Math.ceil(reviewTotal.value / reviewSize)))
const postItems = computed(() => momentsQuery.data.value?.items || [])
const postTotal = computed(() => momentsQuery.data.value?.total || 0)
const postTotalPages = computed(() => Math.max(1, Math.ceil(postTotal.value / postSize)))
const reviewerProfileMap = computed(() => {
  const map = new Map<number, { nickname: string; avatarUrl?: string; userNo: string }>()
  for (const item of reviewerProfilesQuery.data.value || []) {
    map.set(item.userId, {
      nickname: item.nickname || item.username || item.userNo,
      avatarUrl: item.avatarUrl,
      userNo: item.userNo,
    })
  }
  return map
})

const displayNickname = computed(() => profile.value?.nickname || profile.value?.username || '未知用户')
const displayUsername = computed(() => profile.value?.username || 'unknown')
const displayBio = computed(() => profile.value?.bio || '这个人很神秘，还没有简介')
const followerCount = computed(() => profile.value?.followerCount ?? 0)
const followingCount = computed(() => profile.value?.followingCount ?? 0)
const followedByCurrentUser = computed(() => Boolean(profile.value?.followedByCurrentUser))
const tags = computed(() => {
  const values: string[] = []
  values.push(formatGender(profile.value?.gender))
  if (profile.value?.age != null) {
    values.push(`${profile.value.age}岁`)
  }
  values.push('校园用户')
  return values.filter((value) => value.length > 0)
})
const compositeScoreText = computed(() => {
  const score = creditProfile.value?.composite?.compositeScore100
  return score == null ? '--' : String(score)
})
const compositeGradeText = computed(() => creditProfile.value?.composite?.creditGrade || 'NEW')
const sellerAvgStarText = computed(() => Number(creditProfile.value?.sellerProfile?.avgStar || 0).toFixed(1))
const buyerAvgStarText = computed(() => Number(creditProfile.value?.buyerProfile?.avgStar || 0).toFixed(1))
const praisePreviewComment = computed(() => praiseItems.value[0]?.comment || '暂无上墙评价')
const currentUserId = computed(() => authStore.user?.userId || null)

const isSelf = computed(() => {
  if (!authStore.isAuthenticated) {
    return false
  }
  return routeUserNo.value === String(authStore.user?.userNo || '')
})

const followMutation = useMutation({
  mutationFn: async () => {
    if (!routeUserNo.value) {
      return
    }
    if (followedByCurrentUser.value) {
      await unfollowUser(routeUserNo.value)
      return
    }
    await followUser(routeUserNo.value)
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['public-profile', routeUserNo.value] })
  },
})

const deletePostMutation = useMutation({
  mutationFn: (postId: number) => deletePostV2(postId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['user-posts-v2', ownerUserId.value] })
  },
  onSettled: () => {
    deletingPostId.value = null
  },
})

function goEdit(): void {
  void router.push('/profile/edit')
}

function goCreatePost(): void {
  void router.push('/posts/new')
}

async function toggleFollow(): Promise<void> {
  if (isSelf.value || !routeUserNo.value) {
    return
  }
  if (!authStore.isAuthenticated) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  await followMutation.mutateAsync()
}

function goFollowers(): void {
  if (!routeUserNo.value) {
    return
  }
  void router.push(`/u/${encodeURIComponent(routeUserNo.value)}/followers`)
}

function goFollowing(): void {
  if (!routeUserNo.value) {
    return
  }
  void router.push(`/u/${encodeURIComponent(routeUserNo.value)}/following`)
}

function goDetail(productId: number): void {
  void router.push(`/products/${productId}`)
}

function goPostProduct(productId: number): void {
  void router.push(`/products/${productId}`)
}

function goUser(userNo: string): void {
  if (!userNo) {
    return
  }
  void router.push(`/u/${encodeURIComponent(userNo)}`)
}

function switchTab(tab: CenterTab): void {
  activeTab.value = tab
  if (tab === 'products') {
    page.value = 1
    return
  }
  if (tab === 'reviews') {
    reviewPage.value = 1
    return
  }
  if (tab === 'moments') {
    postPage.value = 1
  }
}

async function handleDeletePost(postId: number): Promise<void> {
  if (!postId || deletePostMutation.isPending.value) {
    return
  }
  const confirmed = window.confirm('确认删除这条帖子吗？')
  if (!confirmed) {
    return
  }
  deletingPostId.value = postId
  await deletePostMutation.mutateAsync(postId)
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

function formatPositiveRate(rate?: number): string {
  if (rate == null) {
    return '--'
  }
  return `${(Number(rate) * 100).toFixed(1)}%`
}

function formatReviewRole(role?: string): string {
  return role === 'BUYER' ? '来自买家' : '来自卖家'
}

function formatReviewTime(raw?: string): string {
  if (!raw) {
    return '-'
  }
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  return parsed.toLocaleString('zh-CN')
}

function reviewerMeta(userId: number): { nickname: string; avatarUrl?: string; userNo: string } {
  const profile = reviewerProfileMap.value.get(userId)
  if (profile) {
    return profile
  }
  return {
    nickname: `用户${userId}`,
    userNo: String(userId),
  }
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
            <button
              v-else
              type="button"
              class="rounded-xl border border-stone-300 px-4 py-2 text-sm font-medium text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="followMutation.isPending.value"
              @click="toggleFollow"
            >
              {{
                followMutation.isPending.value
                  ? '处理中...'
                  : followedByCurrentUser
                    ? '已关注'
                    : '关注'
              }}
            </button>
          </div>

          <div class="relative mt-5 grid grid-cols-3 gap-2 text-center">
            <button
              type="button"
              class="rounded-xl bg-white/70 px-3 py-2 transition hover:bg-white"
              @click="goFollowers"
            >
              <p class="text-lg font-semibold text-stone-900">{{ followerCount }}</p>
              <p class="text-xs text-stone-600">粉丝</p>
            </button>
            <button
              type="button"
              class="rounded-xl bg-white/70 px-3 py-2 transition hover:bg-white"
              @click="goFollowing"
            >
              <p class="text-lg font-semibold text-stone-900">{{ followingCount }}</p>
              <p class="text-xs text-stone-600">关注</p>
            </button>
            <div class="rounded-xl bg-white/70 px-3 py-2">
              <p class="text-lg font-semibold text-stone-900">刚刚来过</p>
              <p class="text-xs text-stone-600">最近访问</p>
            </div>
          </div>
        </section>

        <section class="grid grid-cols-3 gap-3">
          <article class="rounded-2xl border border-stone-200 bg-white/95 p-4">
            <h2 class="text-sm font-semibold text-stone-900">信用档案</h2>
            <p v-if="creditErrorMessage" class="mt-2 text-xs text-rose-600">{{ creditErrorMessage }}</p>
            <template v-else>
              <p class="mt-2 text-2xl font-semibold text-stone-900">{{ compositeScoreText }}</p>
              <p class="mt-1 text-xs text-stone-600">综合分 / 等级 {{ compositeGradeText }}</p>
              <p class="mt-2 text-xs text-stone-600">
                卖家 {{ sellerAvgStarText }} 星 · 买家 {{ buyerAvgStarText }} 星
              </p>
            </template>
          </article>
          <article class="rounded-2xl border border-stone-200 bg-white/95 p-4">
            <h2 class="text-sm font-semibold text-stone-900">我的帖子</h2>
            <p class="mt-2 text-2xl font-semibold text-stone-900">{{ postTotal }}</p>
            <p class="mt-1 text-xs text-stone-600">动态总数</p>
            <button
              v-if="isSelf"
              type="button"
              class="mt-3 rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
              @click="goCreatePost"
            >
              去发帖
            </button>
          </article>
          <article class="rounded-2xl border border-stone-200 bg-white/95 p-4">
            <h2 class="text-sm font-semibold text-stone-900">夸夸墙</h2>
            <p class="mt-2 text-xs text-stone-600 line-clamp-3">{{ praisePreviewComment }}</p>
            <p class="mt-2 text-xs text-stone-500">共 {{ praiseTotal }} 条上墙好评</p>
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

            <ResultState
              v-else-if="activeTab === 'reviews'"
              :loading="reviewQuery.isLoading.value || reviewQuery.isFetching.value"
              :error="reviewErrorMessage"
              :empty="!reviewQuery.isLoading.value && reviewItems.length === 0"
              empty-text="还没有评价记录"
            >
              <div class="space-y-3">
                <article
                  v-for="item in reviewItems"
                  :key="item.reviewId"
                  class="rounded-2xl border border-stone-200 bg-white p-4"
                >
                  <div class="flex items-start justify-between gap-3">
                    <button type="button" class="flex min-w-0 items-center gap-3 text-left" @click="goUser(reviewerMeta(item.reviewerUserId).userNo)">
                      <div class="h-11 w-11 overflow-hidden rounded-full border border-stone-200 bg-stone-100">
                        <img
                          v-if="reviewerMeta(item.reviewerUserId).avatarUrl"
                          :src="reviewerMeta(item.reviewerUserId).avatarUrl"
                          alt="avatar"
                          class="h-full w-full object-cover"
                        />
                        <div v-else class="flex h-full w-full items-center justify-center text-xs text-stone-500">暂无头像</div>
                      </div>
                      <div class="min-w-0">
                        <p class="line-clamp-1 text-sm font-semibold text-stone-900">{{ reviewerMeta(item.reviewerUserId).nickname }}</p>
                        <p class="mt-0.5 text-xs text-stone-500">{{ formatReviewRole(item.reviewerRole) }} · {{ formatReviewTime(item.createdAt) }}</p>
                      </div>
                    </button>
                    <span class="rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-700">{{ Number(item.overallStar).toFixed(1) }} 星</span>
                  </div>

                  <p class="mt-3 text-sm text-stone-700">{{ item.comment || '该评价未公开评论' }}</p>
                  <div class="mt-2 flex flex-wrap gap-2 text-xs text-stone-600">
                    <span class="rounded-full bg-stone-100 px-2 py-1">沟通 {{ item.communicationStar }}</span>
                    <span class="rounded-full bg-stone-100 px-2 py-1">及时 {{ item.timelinessStar }}</span>
                    <span class="rounded-full bg-stone-100 px-2 py-1">可信 {{ item.credibilityStar }}</span>
                  </div>
                </article>
              </div>

              <div class="mt-4 grid gap-3 sm:grid-cols-3">
                <article class="rounded-xl border border-stone-200 bg-white p-3 text-sm">
                  <p class="text-xs text-stone-500">卖家档案</p>
                  <p class="mt-1 font-semibold text-stone-900">{{ sellerAvgStarText }} 星</p>
                  <p class="mt-1 text-xs text-stone-600">好评率 {{ formatPositiveRate(creditProfile?.sellerProfile?.positiveRate) }}</p>
                </article>
                <article class="rounded-xl border border-stone-200 bg-white p-3 text-sm">
                  <p class="text-xs text-stone-500">买家档案</p>
                  <p class="mt-1 font-semibold text-stone-900">{{ buyerAvgStarText }} 星</p>
                  <p class="mt-1 text-xs text-stone-600">好评率 {{ formatPositiveRate(creditProfile?.buyerProfile?.positiveRate) }}</p>
                </article>
                <article class="rounded-xl border border-stone-200 bg-white p-3 text-sm">
                  <p class="text-xs text-stone-500">综合信用</p>
                  <p class="mt-1 font-semibold text-stone-900">{{ compositeScoreText }} 分</p>
                  <p class="mt-1 text-xs text-stone-600">等级 {{ compositeGradeText }}</p>
                </article>
              </div>

              <div class="mt-4 flex items-center justify-between rounded-2xl border border-stone-200 bg-white/90 px-4 py-3 text-sm">
                <span class="text-stone-600">第 {{ reviewPage }} / {{ reviewTotalPages }} 页，共 {{ reviewTotal }} 条</span>
                <div class="flex gap-2">
                  <button
                    type="button"
                    class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="reviewPage <= 1 || reviewQuery.isFetching.value"
                    @click="reviewPage -= 1"
                  >
                    上一页
                  </button>
                  <button
                    type="button"
                    class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="reviewPage >= reviewTotalPages || reviewQuery.isFetching.value"
                    @click="reviewPage += 1"
                  >
                    下一页
                  </button>
                </div>
              </div>
            </ResultState>

            <ResultState
              v-else
              :loading="momentsQuery.isLoading.value || momentsQuery.isFetching.value"
              :error="momentsErrorMessage"
              :empty="!momentsQuery.isLoading.value && postItems.length === 0"
              empty-text="暂时还没有动态"
            >
              <div class="space-y-3">
                <div v-if="isSelf" class="flex justify-end">
                  <button
                    type="button"
                    class="rounded-lg bg-stone-900 px-3 py-1.5 text-sm text-white transition hover:bg-stone-700"
                    @click="goCreatePost"
                  >
                    写帖子
                  </button>
                </div>

                <PostFeedCard
                  v-for="item in postItems"
                  :key="item.postId"
                  :post="item"
                  :author-profile="profile || null"
                  :current-user-id="currentUserId"
                  :allow-delete="isSelf"
                  :deleting="deletePostMutation.isPending.value && deletingPostId === item.postId"
                  @open-user="goUser"
                  @open-product="goPostProduct"
                  @delete="handleDeletePost"
                />

                <div class="mt-4 flex items-center justify-between rounded-2xl border border-stone-200 bg-white/90 px-4 py-3 text-sm">
                  <span class="text-stone-600">第 {{ postPage }} / {{ postTotalPages }} 页，共 {{ postTotal }} 条</span>
                  <div class="flex gap-2">
                    <button
                      type="button"
                      class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
                      :disabled="postPage <= 1 || momentsQuery.isFetching.value"
                      @click="postPage -= 1"
                    >
                      上一页
                    </button>
                    <button
                      type="button"
                      class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
                      :disabled="postPage >= postTotalPages || momentsQuery.isFetching.value"
                      @click="postPage += 1"
                    >
                      下一页
                    </button>
                  </div>
                </div>
              </div>
            </ResultState>
          </div>
        </section>
      </div>
    </ResultState>
  </section>
</template>
