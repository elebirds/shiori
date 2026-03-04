<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { createOrderV2 } from '@/api/orderV2'
import { getProductDetailV2, type ProductCategoryCode, type ProductConditionLevel, type ProductTradeMode } from '@/api/productV2'
import { ApiBizError } from '@/types/result'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const chatStore = useChatStore()

const creatingOrder = ref(false)
const creatingChat = ref(false)
const createError = ref('')
const skuQuantities = ref<Record<number, number>>({})

const productId = computed(() => Number(route.params.id))

const query = useQuery({
  queryKey: computed(() => ['product-detail-v2', productId.value]),
  queryFn: () => getProductDetailV2(productId.value),
  enabled: computed(() => Number.isFinite(productId.value) && productId.value > 0),
})

const product = computed(() => query.data.value)
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))

watch(
  () => product.value?.skus,
  (skus) => {
    if (!skus) {
      return
    }
    const next: Record<number, number> = {}
    skus.forEach((sku) => {
      next[sku.skuId] = skuQuantities.value[sku.skuId] ?? 0
    })
    skuQuantities.value = next
  },
  { immediate: true },
)

const selectedItems = computed(() => {
  const skus = product.value?.skus || []
  return skus
    .map((sku) => {
      const rawQuantity = skuQuantities.value[sku.skuId] ?? 0
      const quantity = Number.isFinite(rawQuantity) ? Math.floor(rawQuantity) : 0
      return { sku, quantity }
    })
    .filter((item) => item.quantity > 0)
})

const selectedTotalAmount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.sku.priceCent * item.quantity, 0))

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

function normalizeQuantity(skuId: number): void {
  const current = skuQuantities.value[skuId] ?? 0
  const next = Number.isFinite(current) ? Math.max(0, Math.floor(current)) : 0
  skuQuantities.value = {
    ...skuQuantities.value,
    [skuId]: next,
  }
}

function formatCategory(code: ProductCategoryCode): string {
  const map: Record<ProductCategoryCode, string> = {
    TEXTBOOK: '教材',
    EXAM_MATERIAL: '考试资料',
    NOTE: '笔记',
    OTHER: '其他',
  }
  return map[code] || code
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

async function handleCreateOrder(): Promise<void> {
  if (!authStore.isAuthenticated) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  if (selectedItems.value.length === 0) {
    createError.value = '请至少选择一个 SKU 并填写数量'
    return
  }

  const stockInvalidItem = selectedItems.value.find((item) => item.quantity > item.sku.stock)
  if (stockInvalidItem) {
    createError.value = `SKU「${stockInvalidItem.sku.skuName}」库存不足`
    return
  }

  creatingOrder.value = true
  createError.value = ''

  try {
    const idempotencyKey = `web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const response = await createOrderV2(
      {
        items: selectedItems.value.map((item) => ({
          productId: productId.value,
          skuId: item.sku.skuId,
          quantity: item.quantity,
        })),
      },
      idempotencyKey,
    )
    await router.push(`/orders/${response.orderNo}`)
  } catch (error) {
    if (error instanceof ApiBizError) {
      createError.value = error.message
    } else {
      createError.value = '创建订单失败，请稍后重试'
    }
  } finally {
    creatingOrder.value = false
  }
}

async function handleConsultSeller(): Promise<void> {
  if (!authStore.isAuthenticated) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!product.value) {
    return
  }
  creatingChat.value = true
  createError.value = ''
  try {
    const conversationId = await chatStore.bootstrapFromListing(product.value.productId)
    await router.push({
      path: '/chat',
      query: {
        conversationId: String(conversationId),
      },
    })
  } catch (error) {
    createError.value = error instanceof Error ? error.message : '发起咨询失败，请稍后重试'
  } finally {
    creatingChat.value = false
  }
}
</script>

<template>
  <section class="space-y-4">
    <button
      type="button"
      class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
      @click="router.back()"
    >
      返回列表
    </button>

    <ResultState :loading="query.isLoading.value" :error="errorMessage" :empty="!query.isLoading.value && !product" empty-text="商品不存在或已下架">
      <div v-if="product" class="space-y-4">
        <article class="grid gap-4 lg:grid-cols-[1.2fr_1fr]">
          <div class="rounded-2xl border border-stone-200 bg-white/95 p-5">
            <div class="aspect-[4/3] overflow-hidden rounded-xl bg-stone-100">
              <img v-if="product.coverImageUrl" :src="product.coverImageUrl" :alt="product.title" class="h-full w-full object-cover" />
              <div v-else class="flex h-full items-center justify-center text-sm text-stone-500">暂无封面</div>
            </div>

            <h1 class="mt-4 font-display text-3xl text-stone-900">{{ product.title }}</h1>
            <div class="mt-2 space-y-2">
              <h2 class="text-sm font-semibold text-stone-900">商品简介</h2>
              <p class="whitespace-pre-line text-sm text-stone-700">{{ product.description || '暂无简介' }}</p>
            </div>

            <div class="mt-4 flex flex-wrap gap-2 text-xs text-stone-700">
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCategory(product.categoryCode) }}</span>
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCondition(product.conditionLevel) }}</span>
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatTradeMode(product.tradeMode) }}</span>
              <span class="rounded-full bg-stone-100 px-2 py-1">{{ product.campusCode }}</span>
            </div>
          </div>

          <div class="space-y-4 rounded-2xl border border-stone-200 bg-white/95 p-5">
            <div>
              <h2 class="text-base font-semibold text-stone-900">SKU 列表</h2>
              <p class="mt-1 text-xs text-stone-500">状态：{{ product.status }}，卖家ID：{{ product.ownerUserId }}</p>
              <p class="mt-1 text-xs text-stone-500">价格区间：{{ formatPriceRange(product.minPriceCent, product.maxPriceCent) }}，库存：{{ product.totalStock ?? 0 }}</p>
            </div>

            <div class="max-h-72 space-y-2 overflow-auto pr-1">
              <article
                v-for="sku in product.skus"
                :key="sku.skuId"
                class="grid grid-cols-[1fr_auto] items-center gap-3 rounded-xl border border-stone-200 px-3 py-2 text-sm transition hover:border-stone-300"
              >
                <div>
                  <p class="font-medium text-stone-800">{{ sku.skuName }}</p>
                  <p class="text-xs text-stone-500">{{ sku.specJson || '默认规格' }}</p>
                  <p class="mt-1 text-xs text-stone-500">库存 {{ sku.stock }}</p>
                </div>
                <div class="flex items-center gap-3">
                  <p class="font-semibold text-stone-900">{{ formatMoney(sku.priceCent) }}</p>
                  <label class="flex items-center gap-1 text-xs text-stone-600">
                    数量
                    <input
                      v-model.number="skuQuantities[sku.skuId]"
                      type="number"
                      min="0"
                      :max="sku.stock"
                      class="w-20 rounded-lg border border-stone-300 px-2 py-1 text-right"
                      @blur="normalizeQuantity(sku.skuId)"
                    />
                  </label>
                </div>
              </article>
            </div>

            <div class="rounded-xl bg-stone-50 p-3 text-sm text-stone-700">
              <p>已选 SKU 数：{{ selectedItems.length }}</p>
              <p class="mt-1">预估金额：{{ formatMoney(selectedTotalAmount) }}</p>
            </div>

            <button
              type="button"
              class="w-full rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="creatingOrder"
              @click="handleCreateOrder"
            >
              {{ creatingOrder ? '下单中...' : '创建订单' }}
            </button>

            <button
              type="button"
              class="w-full rounded-xl border border-stone-300 px-4 py-2 text-sm font-medium text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="creatingChat"
              @click="handleConsultSeller"
            >
              {{ creatingChat ? '咨询中...' : '咨询卖家' }}
            </button>

            <p v-if="createError" class="text-sm text-rose-600">{{ createError }}</p>
          </div>
        </article>

        <article class="rounded-2xl border border-stone-200 bg-white/95 p-5">
          <h2 class="text-base font-semibold text-stone-900">商品详情</h2>
          <div v-if="product.detailHtml" class="rich-content mt-3 text-sm text-stone-700" v-html="product.detailHtml" />
          <p v-else class="mt-3 text-sm text-stone-500">暂无详情</p>
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
