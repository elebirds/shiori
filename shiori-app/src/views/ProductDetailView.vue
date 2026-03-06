<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import UserAvatar from '@/components/UserAvatar.vue'
import ResultState from '@/components/ResultState.vue'
import { useProductMeta } from '@/composables/useProductMeta'
import { getUserProfilesByUserIds } from '@/api/auth'
import { addCartItemV2, createOrderV2, listProductReviewsV2 } from '@/api/orderV2'
import { resolveProductMediaUrls } from '@/api/media'
import {
  getProductDetailV2,
  type ProductCategoryCode,
  type ProductSubCategoryCode,
  type ProductConditionLevel,
  type ProductStatus,
  type ProductTradeMode,
  type SkuResponse,
} from '@/api/productV2'
import { ApiBizError } from '@/types/result'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()
const { categoryNameMap, subCategoryNameMap, campusNameMap } = useProductMeta()

const buying = ref(false)
const addingCart = ref(false)
const creatingChat = ref(false)
const actionError = ref('')
const actionMessage = ref('')
const quantity = ref(1)
const selectedSpecs = ref<Record<string, string>>({})

const productId = computed(() => Number(route.params.id))
const chatConversationId = computed(() => {
  const raw = Number(route.query.conversationId || 0)
  return Number.isFinite(raw) && raw > 0 ? raw : 0
})

const query = useQuery({
  queryKey: computed(() => ['product-detail-v2', productId.value]),
  queryFn: () => getProductDetailV2(productId.value),
  enabled: computed(() => Number.isFinite(productId.value) && productId.value > 0),
})

const product = computed(() => query.data.value)
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))
const productReviewPageSize = 8
const sellerUserId = computed(() => Number(product.value?.ownerUserId || 0))

const sellerProfileQuery = useQuery({
  queryKey: computed(() => ['product-seller-profile', sellerUserId.value]),
  queryFn: async () => {
    if (sellerUserId.value <= 0) {
      return null
    }
    const profiles = await getUserProfilesByUserIds([sellerUserId.value])
    return profiles[0] || null
  },
  enabled: computed(() => sellerUserId.value > 0),
})

const sellerProfile = computed(() => sellerProfileQuery.data.value || null)
const sellerDisplayName = computed(() => {
  if (sellerProfile.value?.nickname) {
    return sellerProfile.value.nickname
  }
  if (sellerProfile.value?.username) {
    return sellerProfile.value.username
  }
  if (sellerUserId.value > 0) {
    return `用户 ${sellerUserId.value}`
  }
  return '未知用户'
})
const sellerHandle = computed(() => {
  if (sellerProfile.value?.userNo) {
    return `@${sellerProfile.value.userNo}`
  }
  if (sellerUserId.value > 0) {
    return `@${sellerUserId.value}`
  }
  return '@-'
})
const sellerSignature = computed(() => {
  const bio = sellerProfile.value?.bio?.trim()
  if (bio) {
    return bio
  }
  return '这个人很神秘，还没有签名'
})
const canOpenSellerProfile = computed(() => Boolean(sellerProfile.value?.userNo))

const productReviewQuery = useQuery({
  queryKey: computed(() => ['product-reviews-v2', productId.value, productReviewPageSize]),
  queryFn: () =>
    listProductReviewsV2(productId.value, {
      page: 1,
      size: productReviewPageSize,
    }),
  enabled: computed(() => Number.isFinite(productId.value) && productId.value > 0),
})

const productReviews = computed(() => productReviewQuery.data.value?.items || [])
const productReviewTotal = computed(() => productReviewQuery.data.value?.total || 0)
const productReviewErrorMessage = computed(() => (productReviewQuery.error.value instanceof Error ? productReviewQuery.error.value.message : ''))
const reviewImageObjectKeys = computed(() =>
  Array.from(
    new Set(
      productReviews.value
        .flatMap((item) => item.imageObjectKeys || [])
        .map((item) => item.trim())
        .filter((item) => item),
    ),
  ),
)

const reviewImageUrlsQuery = useQuery({
  queryKey: computed(() => ['product-review-image-urls', reviewImageObjectKeys.value.join(',')]),
  queryFn: async () => {
    if (reviewImageObjectKeys.value.length === 0) {
      return {} as Record<string, string>
    }
    const response = await resolveProductMediaUrls(reviewImageObjectKeys.value)
    return response.urls || {}
  },
  enabled: computed(() => reviewImageObjectKeys.value.length > 0),
})

const specOptions = computed(() => {
  const skus = product.value?.skus || []
  const optionMap = new Map<string, Set<string>>()
  skus.forEach((sku) => {
    sku.specItems.forEach((item) => {
      if (!optionMap.has(item.name)) {
        optionMap.set(item.name, new Set<string>())
      }
      optionMap.get(item.name)?.add(item.value)
    })
  })
  return Array.from(optionMap.entries()).map(([name, values]) => ({
    name,
    values: Array.from(values),
  }))
})

watch(
  () => product.value?.skus,
  (skus) => {
    actionError.value = ''
    actionMessage.value = ''
    quantity.value = 1
    const nextSelected: Record<string, string> = {}
    if (!skus || skus.length === 0) {
      selectedSpecs.value = nextSelected
      return
    }

    specOptions.value.forEach((dimension) => {
      const preferred = skus.find((sku) => specValueOfSku(sku, dimension.name) && sku.stock > 0)
      const fallback = skus.find((sku) => specValueOfSku(sku, dimension.name))
      const target = preferred || fallback
      if (target) {
        nextSelected[dimension.name] = specValueOfSku(target, dimension.name) || ''
      }
    })
    selectedSpecs.value = nextSelected
  },
  { immediate: true },
)

const selectedSku = computed(() => {
  const skus = product.value?.skus || []
  if (skus.length === 0) {
    return null
  }
  if (specOptions.value.length === 0) {
    return skus.find((sku) => sku.stock > 0) || skus[0]
  }
  return (
    skus.find((sku) =>
      specOptions.value.every((dimension) => {
        const selectedValue = selectedSpecs.value[dimension.name]
        if (!selectedValue) {
          return false
        }
        return specValueOfSku(sku, dimension.name) === selectedValue
      }),
    ) || null
  )
})

watch(
  selectedSku,
  (sku) => {
    if (!sku) {
      quantity.value = 1
      return
    }
    quantity.value = Math.min(Math.max(1, quantity.value), Math.max(1, sku.stock || 1))
  },
  { immediate: true },
)

const canBuy = computed(() => {
  if (!selectedSku.value) {
    return false
  }
  if (selectedSku.value.stock <= 0) {
    return false
  }
  return quantity.value >= 1 && quantity.value <= selectedSku.value.stock
})

const estimatedAmount = computed(() => {
  if (!selectedSku.value) {
    return 0
  }
  return selectedSku.value.priceCent * quantity.value
})

function specValueOfSku(sku: SkuResponse, specName: string): string | null {
  return sku.specItems.find((item) => item.name === specName)?.value || null
}

function formatMoney(priceCent: number): string {
  return `¥ ${(priceCent / 100).toFixed(2)}`
}

function formatPriceRange(minPriceCent?: number, maxPriceCent?: number): string {
  if (minPriceCent == null && maxPriceCent == null) {
    return '价格待定'
  }
  if (minPriceCent === maxPriceCent) {
    return formatMoney(minPriceCent || 0)
  }
  return `${formatMoney(minPriceCent || 0)} ~ ${formatMoney(maxPriceCent || 0)}`
}

function formatCategory(code: ProductCategoryCode): string {
  return categoryNameMap.value.get(code) || code
}

function formatSubCategory(code: ProductSubCategoryCode): string {
  return subCategoryNameMap.value.get(code) || code
}

function formatCondition(code: ProductConditionLevel): string {
  const map: Record<ProductConditionLevel, string> = {
    NEW: '全新',
    LIKE_NEW: '近新',
    GOOD: '良好',
    FAIR: '一般',
  }
  return map[code] || code
}

function formatTradeMode(code: ProductTradeMode): string {
  const map: Record<ProductTradeMode, string> = {
    MEETUP: '面交',
    DELIVERY: '邮寄',
    BOTH: '均可',
  }
  return map[code] || code
}

function formatCampus(code: string): string {
  return campusNameMap.value.get(code) || code
}

function formatStatus(code: ProductStatus): string {
  const map: Record<ProductStatus, string> = {
    DRAFT: '草稿',
    ON_SALE: '在售',
    OFF_SHELF: '已下架',
  }
  return map[code] || code
}

function formatReviewRole(role: 'BUYER' | 'SELLER'): string {
  return role === 'BUYER' ? '买家评价' : '卖家评价'
}

function formatReviewTime(time?: string): string {
  if (!time) {
    return '-'
  }
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

function reviewImageUrlOf(objectKey: string): string {
  return reviewImageUrlsQuery.data.value?.[objectKey] || ''
}

function isOverallStarActive(overallStar: number, star: number): boolean {
  return Math.round(Number(overallStar) || 0) >= star
}

function changeQuantity(delta: number): void {
  const sku = selectedSku.value
  if (!sku) {
    return
  }
  quantity.value = Math.min(Math.max(1, quantity.value + delta), Math.max(1, sku.stock || 1))
}

function chooseSpec(specName: string, value: string): void {
  selectedSpecs.value = {
    ...selectedSpecs.value,
    [specName]: value,
  }
  actionError.value = ''
  actionMessage.value = ''
}

function isSpecValueDisabled(specName: string, value: string): boolean {
  const skus = product.value?.skus || []
  const current = {
    ...selectedSpecs.value,
    [specName]: value,
  }
  return !skus.some((sku) => {
    if (sku.stock <= 0) {
      return false
    }
    return specOptions.value.every((dimension) => {
      const selectedValue = current[dimension.name]
      if (!selectedValue) {
        return true
      }
      return specValueOfSku(sku, dimension.name) === selectedValue
    })
  })
}

async function ensureAuthed(): Promise<boolean> {
  if (!authStore.isAuthenticated) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

async function handleBuyNow(): Promise<void> {
  if (!(await ensureAuthed())) {
    return
  }
  if (!selectedSku.value) {
    actionError.value = '请先选择完整规格'
    return
  }
  if (!canBuy.value) {
    actionError.value = '当前数量超过库存，请调整后重试'
    return
  }

  buying.value = true
  actionError.value = ''
  actionMessage.value = ''
  try {
    const idempotencyKey = `buy-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    const response = await createOrderV2(
      {
        items: [
          {
            productId: productId.value,
            skuId: selectedSku.value.skuId,
            quantity: quantity.value,
          },
        ],
        source: chatConversationId.value > 0 ? 'CHAT' : undefined,
        conversationId: chatConversationId.value > 0 ? chatConversationId.value : undefined,
      },
      idempotencyKey,
    )

    if (chatConversationId.value > 0) {
      await chatStore.sendTradeStatusCard(chatConversationId.value, 'ORDER_CREATED', response.orderNo)
    }

    await router.push({
      path: `/orders/${response.orderNo}`,
      query: chatConversationId.value > 0 ? { conversationId: String(chatConversationId.value) } : undefined,
    })
  } catch (error) {
    if (error instanceof ApiBizError) {
      actionError.value = error.message
    } else {
      actionError.value = '下单失败，请稍后重试'
    }
  } finally {
    buying.value = false
  }
}

async function handleAddToCart(): Promise<void> {
  if (!(await ensureAuthed())) {
    return
  }
  if (!selectedSku.value) {
    actionError.value = '请先选择完整规格'
    return
  }
  if (!canBuy.value) {
    actionError.value = '当前数量超过库存，请调整后重试'
    return
  }

  addingCart.value = true
  actionError.value = ''
  actionMessage.value = ''
  try {
    await addCartItemV2({
      productId: productId.value,
      skuId: selectedSku.value.skuId,
      quantity: quantity.value,
    })
    actionMessage.value = '已加入购物车'
  } catch (error) {
    if (error instanceof ApiBizError) {
      actionError.value = error.message
    } else {
      actionError.value = '加入购物车失败，请稍后重试'
    }
  } finally {
    addingCart.value = false
  }
}

async function handleConsultSeller(): Promise<void> {
  if (!(await ensureAuthed())) {
    return
  }
  if (!product.value) {
    return
  }
  creatingChat.value = true
  actionError.value = ''
  try {
    const conversationId = await chatStore.bootstrapFromListing(product.value.productId)
    const priceCent = Number(product.value.minPriceCent ?? product.value.maxPriceCent ?? 0)
    await chatStore.sendProductCard(conversationId, {
      listingId: product.value.productId,
      title: product.value.title,
      priceCent,
      coverImageUrl: product.value.coverImageUrl,
    })
    await router.push({
      path: '/chat',
      query: {
        conversationId: String(conversationId),
      },
    })
  } catch (error) {
    actionError.value = error instanceof Error ? error.message : '发起咨询失败，请稍后重试'
  } finally {
    creatingChat.value = false
  }
}

async function goSellerProfile(): Promise<void> {
  const userNo = sellerProfile.value?.userNo?.trim()
  if (!userNo) {
    return
  }
  await router.push(`/u/${encodeURIComponent(userNo)}`)
}
</script>

<template>
  <section class="space-y-4">
    <ResultState :loading="query.isLoading.value" :error="errorMessage" :empty="!query.isLoading.value && !product" empty-text="商品不存在或已下架">
      <div v-if="product" class="space-y-4">
        <article class="grid gap-4 lg:grid-cols-[1.2fr_1fr]">
          <div class="rounded-2xl border border-stone-200 bg-white/95 p-5">
            <div class="aspect-[4/3] overflow-hidden rounded-xl bg-stone-100">
              <img v-if="product.coverImageUrl" :src="product.coverImageUrl" :alt="product.title" class="h-full w-full object-cover" />
              <div v-else class="flex h-full items-center justify-center text-sm text-stone-500">暂无封面</div>
            </div>

            <h1 class="mt-4 font-display text-3xl text-stone-900">{{ product.title }}</h1>
            <div class="mt-4 rounded-xl border border-stone-200 bg-stone-50/70 p-3">
              <p class="text-xs tracking-wide text-stone-500">售卖人</p>
              <button
                type="button"
                class="mt-2 flex w-full items-center gap-3 rounded-lg px-2 py-2 text-left transition"
                :class="canOpenSellerProfile ? 'hover:bg-stone-100' : 'cursor-default'"
                @click="goSellerProfile"
              >
                <UserAvatar
                  :src="sellerProfile?.avatarUrl"
                  :name="sellerDisplayName"
                  size-class="h-10 w-10"
                  fallback-size-class="h-4 w-4"
                  show-initial
                />
                <div class="min-w-0 flex-1">
                  <p class="truncate text-xs text-stone-500">{{ sellerDisplayName }} {{ sellerHandle }}</p>
                  <p class="mt-1 truncate text-xs text-stone-600">{{ sellerSignature }}</p>
                </div>
                <span v-if="canOpenSellerProfile" class="text-xs text-stone-400">查看主页</span>
              </button>
            </div>
            <div class="mt-2 space-y-2">
              <h2 class="text-sm font-semibold text-stone-900">商品简介</h2>
              <p class="whitespace-pre-line text-sm text-stone-700">{{ product.description || '暂无简介' }}</p>
            </div>

            <div class="mt-4 flex flex-wrap gap-2 text-xs text-stone-700">
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCategory(product.categoryCode) }}</span>
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatSubCategory(product.subCategoryCode) }}</span>
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCondition(product.conditionLevel) }}</span>
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatTradeMode(product.tradeMode) }}</span>
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCampus(product.campusCode) }}</span>
            </div>
          </div>

          <div class="space-y-4 rounded-2xl border border-stone-200 bg-white/95 p-5">
            <div>
              <p class="text-xs text-stone-500">商品状态：{{ formatStatus(product.status) }}</p>
              <p class="mt-1 text-2xl font-semibold text-stone-900">{{ formatPriceRange(product.minPriceCent, product.maxPriceCent) }}</p>
              <p class="mt-1 text-xs text-stone-500">总库存：{{ product.totalStock ?? 0 }}</p>
            </div>

            <div v-for="dimension in specOptions" :key="dimension.name" class="space-y-2">
              <p class="text-sm font-medium text-stone-800">{{ dimension.name }}</p>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="value in dimension.values"
                  :key="`${dimension.name}-${value}`"
                  type="button"
                  class="rounded-lg border px-3 py-1.5 text-sm transition"
                  :class="[
                    selectedSpecs[dimension.name] === value
                      ? 'border-stone-900 bg-stone-900 text-white'
                      : 'border-stone-300 bg-white text-stone-700 hover:border-stone-500',
                    isSpecValueDisabled(dimension.name, value) ? 'cursor-not-allowed opacity-40' : '',
                  ]"
                  :disabled="isSpecValueDisabled(dimension.name, value)"
                  @click="chooseSpec(dimension.name, value)"
                >
                  {{ value }}
                </button>
              </div>
            </div>

            <div class="rounded-xl bg-stone-50 p-3 text-sm text-stone-700">
              <p>已选规格：{{ selectedSku?.displayName || '请选择规格' }}</p>
              <p class="mt-1">库存：{{ selectedSku?.stock ?? '-' }}</p>
            </div>

            <div class="flex items-center gap-3">
              <span class="text-sm text-stone-700">数量</span>
              <button
                type="button"
                class="h-8 w-8 rounded-lg border border-stone-300 text-sm text-stone-700"
                :disabled="!selectedSku || quantity <= 1"
                @click="changeQuantity(-1)"
              >
                -
              </button>
              <input
                v-model.number="quantity"
                type="number"
                min="1"
                :max="selectedSku?.stock || 1"
                class="w-20 rounded-lg border border-stone-300 px-2 py-1 text-center text-sm"
              />
              <button
                type="button"
                class="h-8 w-8 rounded-lg border border-stone-300 text-sm text-stone-700"
                :disabled="!selectedSku || quantity >= (selectedSku?.stock || 1)"
                @click="changeQuantity(1)"
              >
                +
              </button>
            </div>

            <div class="rounded-xl border border-stone-200 p-3 text-sm text-stone-700">
              <p>预计金额</p>
              <p class="mt-1 text-xl font-semibold text-stone-900">{{ formatMoney(estimatedAmount) }}</p>
            </div>

            <button
              type="button"
              class="w-full rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="!canBuy || buying"
              @click="handleBuyNow"
            >
              {{ buying ? '下单中...' : '立即购买' }}
            </button>

            <button
              type="button"
              class="w-full rounded-xl border border-stone-300 px-4 py-2 text-sm font-medium text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="!canBuy || addingCart"
              @click="handleAddToCart"
            >
              {{ addingCart ? '加入中...' : '加入购物车' }}
            </button>

            <button
              type="button"
              class="w-full rounded-xl border border-stone-300 px-4 py-2 text-sm font-medium text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="creatingChat"
              @click="handleConsultSeller"
            >
              {{ creatingChat ? '咨询中...' : '咨询卖家' }}
            </button>

            <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>
            <p v-if="actionMessage" class="text-sm text-emerald-700">{{ actionMessage }}</p>
          </div>
        </article>

        <article class="rounded-2xl border border-stone-200 bg-white/95 p-5">
          <h2 class="text-base font-semibold text-stone-900">商品详情</h2>
          <div v-if="product.detailHtml" class="rich-content mt-3 text-sm text-stone-700" v-html="product.detailHtml" />
          <p v-else class="mt-3 text-sm text-stone-500">暂无详情</p>
        </article>

        <article class="rounded-2xl border border-stone-200 bg-white/95 p-5">
          <div class="flex items-center justify-between gap-3">
            <h2 class="text-base font-semibold text-stone-900">商品评价</h2>
            <span class="text-xs text-stone-500">共 {{ productReviewTotal }} 条</span>
          </div>

          <p v-if="productReviewQuery.isLoading.value" class="mt-3 text-sm text-stone-500">评价加载中...</p>
          <p v-else-if="productReviewErrorMessage" class="mt-3 text-sm text-rose-600">{{ productReviewErrorMessage }}</p>
          <p v-else-if="productReviews.length === 0" class="mt-3 text-sm text-stone-500">暂时还没有评价。</p>

          <div v-else class="mt-4 space-y-3">
            <article v-for="item in productReviews" :key="item.reviewId" class="rounded-xl border border-stone-200 bg-stone-50/70 p-4">
              <header class="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p class="text-sm font-medium text-stone-900">{{ formatReviewRole(item.reviewerRole) }}</p>
                  <p class="mt-0.5 text-xs text-stone-500">{{ formatReviewTime(item.createdAt) }}</p>
                </div>
                <div class="flex items-center gap-2">
                  <div class="flex items-center">
                    <svg
                      v-for="star in [1, 2, 3, 4, 5]"
                      :key="`review-${item.reviewId}-star-${star}`"
                      viewBox="0 0 24 24"
                      class="h-4 w-4"
                      :class="isOverallStarActive(item.overallStar, star) ? 'fill-amber-400 text-amber-500' : 'fill-transparent text-stone-300'"
                    >
                      <path
                        stroke="currentColor"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                        stroke-width="1.8"
                        d="M12 3.8l2.5 5.2 5.7.8-4.1 3.9 1 5.7L12 16.7 6.9 19.4l1-5.7-4.1-3.9 5.7-.8L12 3.8z"
                      />
                    </svg>
                  </div>
                  <span class="text-xs font-medium text-amber-700">{{ Number(item.overallStar).toFixed(1) }} 星</span>
                </div>
              </header>

              <p class="mt-2 whitespace-pre-line text-sm text-stone-700">{{ item.comment || '该条评价未公开评论内容' }}</p>

              <div v-if="(item.imageObjectKeys || []).length > 0" class="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">
                <article
                  v-for="imageKey in item.imageObjectKeys"
                  :key="`${item.reviewId}-${imageKey}`"
                  class="aspect-square overflow-hidden rounded-lg border border-stone-200 bg-stone-100"
                >
                  <img
                    v-if="reviewImageUrlOf(imageKey)"
                    :src="reviewImageUrlOf(imageKey)"
                    alt="评价图片"
                    class="h-full w-full object-cover"
                  />
                  <div v-else class="flex h-full items-center justify-center px-2 text-center text-[11px] text-stone-500">
                    {{ reviewImageUrlsQuery.isFetching.value ? '图片加载中...' : '图片不可用' }}
                  </div>
                </article>
              </div>
            </article>
          </div>
        </article>
      </div>
    </ResultState>
  </section>
</template>

<style scoped>
.rich-content :deep(img) {
  max-width: 100%;
  border-radius: 0.5rem;
}

.rich-content :deep(ul),
.rich-content :deep(ol) {
  margin-left: 0;
  padding-left: 1.5rem;
  list-style-position: outside;
}

.rich-content :deep(ul) {
  list-style: disc;
}

.rich-content :deep(ol) {
  list-style: decimal;
}

.rich-content :deep(li) {
  margin: 0.25rem 0;
}

.rich-content :deep(h1) {
  margin: 0.75rem 0 0.5rem;
  font-size: 1.5rem;
  font-weight: 700;
}

.rich-content :deep(h2) {
  margin: 0.75rem 0 0.5rem;
  font-size: 1.25rem;
  font-weight: 700;
}

.rich-content :deep(h3) {
  margin: 0.75rem 0 0.5rem;
  font-size: 1.125rem;
  font-weight: 600;
}

.rich-content :deep(blockquote) {
  margin: 0.75rem 0;
  border-left: 3px solid #d6d3d1;
  padding-left: 0.75rem;
  color: #57534e;
}

.rich-content :deep(a) {
  color: #2563eb;
  text-decoration: underline;
  word-break: break-all;
}

.rich-content :deep(s) {
  text-decoration: line-through;
}

.rich-content :deep(hr) {
  margin: 0.75rem 0;
  border: 0;
  border-top: 1px solid #d6d3d1;
}
</style>
