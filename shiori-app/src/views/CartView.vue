<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { checkoutCartV2, getCartV2, removeCartItemV2, updateCartItemV2 } from '@/api/orderV2'
import { ApiBizError } from '@/types/result'

const router = useRouter()
const queryClient = useQueryClient()

const selectedItemIds = ref<number[]>([])
const actionError = ref('')
const busyItemIds = ref<number[]>([])

const cartQuery = useQuery({
  queryKey: ['order-cart'],
  queryFn: getCartV2,
})

const cartItems = computed(() => cartQuery.data.value?.items || [])

watch(
  cartItems,
  (items) => {
    const ids = items.map((item) => item.itemId)
    if (selectedItemIds.value.length === 0) {
      selectedItemIds.value = [...ids]
      return
    }
    const set = new Set(ids)
    selectedItemIds.value = selectedItemIds.value.filter((id) => set.has(id))
  },
  { immediate: true },
)

const selectedItems = computed(() => {
  const selected = new Set(selectedItemIds.value)
  return cartItems.value.filter((item) => selected.has(item.itemId))
})

const selectedTotalCent = computed(() => selectedItems.value.reduce((sum, item) => sum + item.subtotalCent, 0))
const selectedCount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0))

const allChecked = computed({
  get: () => cartItems.value.length > 0 && cartItems.value.every((item) => selectedItemIds.value.includes(item.itemId)),
  set: (checked: boolean) => {
    if (checked) {
      selectedItemIds.value = cartItems.value.map((item) => item.itemId)
      return
    }
    selectedItemIds.value = []
  },
})

const updateQuantityMutation = useMutation({
  mutationFn: async ({ itemId, quantity }: { itemId: number; quantity: number }) => {
    busyItemIds.value = [...new Set([...busyItemIds.value, itemId])]
    return updateCartItemV2(itemId, { quantity })
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['order-cart'] })
  },
  onSettled: (_result, _error, variables) => {
    busyItemIds.value = busyItemIds.value.filter((id) => id !== variables.itemId)
  },
})

const removeItemMutation = useMutation({
  mutationFn: async (itemId: number) => {
    busyItemIds.value = [...new Set([...busyItemIds.value, itemId])]
    return removeCartItemV2(itemId)
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['order-cart'] })
  },
  onSettled: (_result, _error, itemId) => {
    busyItemIds.value = busyItemIds.value.filter((id) => id !== itemId)
  },
})

const checkoutMutation = useMutation({
  mutationFn: () =>
    checkoutCartV2(
      { itemIds: [...selectedItemIds.value] },
      `cart-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
    ),
  onSuccess: async (response) => {
    await queryClient.invalidateQueries({ queryKey: ['order-cart'] })
    await queryClient.invalidateQueries({ queryKey: ['orders-v2'] })
    await router.push(`/orders/${response.orderNo}`)
  },
})

function formatMoney(priceCent?: number): string {
  return `¥ ${((priceCent || 0) / 100).toFixed(2)}`
}

function toggleItem(itemId: number): void {
  if (selectedItemIds.value.includes(itemId)) {
    selectedItemIds.value = selectedItemIds.value.filter((id) => id !== itemId)
    return
  }
  selectedItemIds.value = [...selectedItemIds.value, itemId]
}

function isItemBusy(itemId: number): boolean {
  return busyItemIds.value.includes(itemId)
}

function changeQuantity(itemId: number, quantity: number): void {
  const normalized = Number.isFinite(quantity) ? Math.max(1, Math.floor(quantity)) : 1
  actionError.value = ''
  updateQuantityMutation.mutate({ itemId, quantity: normalized }, {
    onError: (error) => {
      actionError.value = error instanceof ApiBizError ? error.message : '更新数量失败'
    },
  })
}

function removeItem(itemId: number): void {
  actionError.value = ''
  removeItemMutation.mutate(itemId, {
    onError: (error) => {
      actionError.value = error instanceof ApiBizError ? error.message : '移除失败'
    },
  })
}

function checkout(): void {
  if (selectedItemIds.value.length === 0) {
    actionError.value = '请先选择要结算的商品'
    return
  }
  actionError.value = ''
  checkoutMutation.mutate(undefined, {
    onError: (error) => {
      actionError.value = error instanceof ApiBizError ? error.message : '结算失败，请稍后重试'
    },
  })
}
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/90 p-4">
      <h1 class="font-display text-2xl text-stone-900">购物车</h1>
      <p class="mt-1 text-sm text-stone-600">统一管理同一卖家的多规格商品，确认后一次下单。</p>
    </header>

    <ResultState
      :loading="cartQuery.isLoading.value"
      :error="cartQuery.error.value instanceof Error ? cartQuery.error.value.message : ''"
      :empty="!cartQuery.isLoading.value && cartItems.length === 0"
      empty-text="购物车还是空的，先去逛逛商品吧。"
    >
      <div class="grid gap-4 lg:grid-cols-[1.6fr_1fr]">
        <article class="rounded-2xl border border-stone-200 bg-white/95 p-4">
          <label class="flex items-center gap-2 border-b border-stone-100 pb-3 text-sm font-medium text-stone-700">
            <input v-model="allChecked" type="checkbox" />
            全选
          </label>

          <ul class="divide-y divide-stone-100">
            <li v-for="item in cartItems" :key="item.itemId" class="py-4">
              <div class="flex items-start gap-3">
                <input
                  :checked="selectedItemIds.includes(item.itemId)"
                  type="checkbox"
                  class="mt-1"
                  @change="toggleItem(item.itemId)"
                />

                <img
                  v-if="item.coverImageUrl"
                  :src="item.coverImageUrl"
                  :alt="item.productTitle || '商品'"
                  class="h-20 w-20 rounded-lg border border-stone-200 object-cover"
                />
                <div v-else class="flex h-20 w-20 items-center justify-center rounded-lg border border-dashed border-stone-300 text-xs text-stone-400">
                  暂无图片
                </div>

                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-semibold text-stone-900">{{ item.productTitle || `商品 #${item.productId}` }}</p>
                  <p class="mt-1 text-xs text-stone-500">{{ item.displayName }}</p>
                  <p class="mt-1 text-xs text-stone-500">库存 {{ item.stock ?? '-' }}</p>
                  <p class="mt-1 text-sm font-semibold text-stone-900">{{ formatMoney(item.priceCent) }}</p>
                </div>

                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="h-8 w-8 rounded-lg border border-stone-300 text-sm text-stone-700"
                    :disabled="isItemBusy(item.itemId) || item.quantity <= 1"
                    @click="changeQuantity(item.itemId, item.quantity - 1)"
                  >
                    -
                  </button>
                  <input
                    :value="item.quantity"
                    type="number"
                    min="1"
                    step="1"
                    class="w-16 rounded-lg border border-stone-300 px-2 py-1 text-center text-sm"
                    :disabled="isItemBusy(item.itemId)"
                    @change="changeQuantity(item.itemId, Number(($event.target as HTMLInputElement).value))"
                  />
                  <button
                    type="button"
                    class="h-8 w-8 rounded-lg border border-stone-300 text-sm text-stone-700"
                    :disabled="isItemBusy(item.itemId)"
                    @click="changeQuantity(item.itemId, item.quantity + 1)"
                  >
                    +
                  </button>
                </div>

                <div class="text-right">
                  <p class="text-sm font-semibold text-stone-900">{{ formatMoney(item.subtotalCent) }}</p>
                  <button
                    type="button"
                    class="mt-2 text-xs text-rose-600 transition hover:text-rose-700 disabled:opacity-60"
                    :disabled="isItemBusy(item.itemId)"
                    @click="removeItem(item.itemId)"
                  >
                    移除
                  </button>
                </div>
              </div>
            </li>
          </ul>
        </article>

        <aside class="space-y-3 rounded-2xl border border-stone-200 bg-white/95 p-4">
          <h2 class="text-base font-semibold text-stone-900">结算信息</h2>
          <p class="text-sm text-stone-600">已选数量：{{ selectedCount }}</p>
          <p class="text-xl font-semibold text-stone-900">{{ formatMoney(selectedTotalCent) }}</p>
          <button
            type="button"
            class="w-full rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="selectedItemIds.length === 0 || checkoutMutation.isPending.value"
            @click="checkout"
          >
            {{ checkoutMutation.isPending.value ? '结算中...' : '去结算' }}
          </button>
          <button
            type="button"
            class="w-full rounded-xl border border-stone-300 px-4 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
            @click="router.push('/products')"
          >
            继续逛逛
          </button>
          <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>
        </aside>
      </div>
    </ResultState>
  </section>
</template>
