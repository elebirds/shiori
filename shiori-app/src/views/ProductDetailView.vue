<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { getProductDetail } from '@/api/product'
import { createOrder } from '@/api/order'
import { ApiBizError } from '@/types/result'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const creatingOrder = ref(false)
const createError = ref('')
const createSuccessOrderNo = ref('')
const selectedSkuId = ref<number | null>(null)
const quantity = ref(1)

const productId = computed(() => Number(route.params.id))

const query = useQuery({
  queryKey: computed(() => ['product-detail', productId.value]),
  queryFn: () => getProductDetail(productId.value),
  enabled: computed(() => Number.isFinite(productId.value) && productId.value > 0),
})

const product = computed(() => query.data.value)
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))

watch(
  () => product.value?.skus,
  (skus) => {
    if (skus && skus.length > 0 && !selectedSkuId.value) {
      selectedSkuId.value = skus[0]?.skuId ?? null
    }
  },
  { immediate: true },
)

const selectedSku = computed(() => product.value?.skus.find((item) => item.skuId === selectedSkuId.value) || null)

function formatMoney(priceCent: number): string {
  return `¥ ${(priceCent / 100).toFixed(2)}`
}

async function handleCreateOrder(): Promise<void> {
  if (!authStore.isAuthenticated) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  if (!selectedSkuId.value || quantity.value <= 0) {
    createError.value = '请选择 SKU 并设置正确数量'
    return
  }

  creatingOrder.value = true
  createError.value = ''
  createSuccessOrderNo.value = ''

  try {
    const idempotencyKey = `web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    const response = await createOrder(
      {
        items: [
          {
            productId: productId.value,
            skuId: selectedSkuId.value,
            quantity: quantity.value,
          },
        ],
      },
      idempotencyKey,
    )
    createSuccessOrderNo.value = response.orderNo
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
      <article v-if="product" class="grid gap-4 lg:grid-cols-[1.2fr_1fr]">
        <div class="rounded-2xl border border-stone-200 bg-white/95 p-5">
          <div class="aspect-[4/3] overflow-hidden rounded-xl bg-stone-100">
            <img v-if="product.coverImageUrl" :src="product.coverImageUrl" :alt="product.title" class="h-full w-full object-cover" />
            <div v-else class="flex h-full items-center justify-center text-sm text-stone-500">暂无封面</div>
          </div>

          <h1 class="mt-4 font-display text-3xl text-stone-900">{{ product.title }}</h1>
          <p class="mt-2 whitespace-pre-line text-sm text-stone-700">{{ product.description || '暂无描述' }}</p>
        </div>

        <div class="space-y-4 rounded-2xl border border-stone-200 bg-white/95 p-5">
          <div>
            <h2 class="text-base font-semibold text-stone-900">SKU 列表</h2>
            <p class="mt-1 text-xs text-stone-500">状态：{{ product.status }}，卖家ID：{{ product.ownerUserId }}</p>
          </div>

          <div class="max-h-72 space-y-2 overflow-auto pr-1">
            <label
              v-for="sku in product.skus"
              :key="sku.skuId"
              class="flex cursor-pointer items-center justify-between rounded-xl border px-3 py-2 text-sm transition"
              :class="selectedSkuId === sku.skuId ? 'border-amber-400 bg-amber-50' : 'border-stone-200 hover:border-stone-300'"
            >
              <div>
                <p class="font-medium text-stone-800">{{ sku.skuName }}</p>
                <p class="text-xs text-stone-500">{{ sku.specJson || '默认规格' }}</p>
              </div>
              <div class="text-right">
                <p class="font-semibold text-stone-900">{{ formatMoney(sku.priceCent) }}</p>
                <p class="text-xs text-stone-500">库存 {{ sku.stock }}</p>
              </div>
              <input v-model="selectedSkuId" class="hidden" type="radio" :value="sku.skuId" />
            </label>
          </div>

          <div class="rounded-xl bg-stone-50 p-3 text-sm">
            <label class="flex items-center justify-between gap-3">
              购买数量
              <input
                v-model.number="quantity"
                type="number"
                min="1"
                :max="selectedSku?.stock || 1"
                class="w-24 rounded-lg border border-stone-300 px-2 py-1 text-right"
              />
            </label>
          </div>

          <button
            type="button"
            class="w-full rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="creatingOrder"
            @click="handleCreateOrder"
          >
            {{ creatingOrder ? '下单中...' : '创建订单' }}
          </button>

          <p v-if="createError" class="text-sm text-rose-600">{{ createError }}</p>
          <p v-if="createSuccessOrderNo" class="text-sm text-emerald-700">
            下单成功：{{ createSuccessOrderNo }}，
            <RouterLink class="font-medium text-emerald-800 underline" to="/orders">前往订单列表</RouterLink>
          </p>
        </div>
      </article>
    </ResultState>
  </section>
</template>
