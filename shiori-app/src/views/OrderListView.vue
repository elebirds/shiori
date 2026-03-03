<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive } from 'vue'

import ResultState from '@/components/ResultState.vue'
import { cancelOrder, listMyOrders, payOrder } from '@/api/order'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()

const pager = reactive({
  page: 1,
  size: 10,
})

const query = useQuery({
  queryKey: computed(() => ['orders', pager.page, pager.size]),
  queryFn: () => listMyOrders({ page: pager.page, size: pager.size }),
})

const payMutation = useMutation({
  mutationFn: (orderNo: string) => payOrder(orderNo, { paymentNo: `web-pay-${Date.now()}` }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['orders'] })
  },
})

const cancelMutation = useMutation({
  mutationFn: (orderNo: string) => cancelOrder(orderNo, { reason: '用户主动取消' }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['orders'] })
  },
})

const actionError = computed(() => {
  if (payMutation.error.value instanceof Error) {
    return payMutation.error.value.message
  }
  if (cancelMutation.error.value instanceof Error) {
    return cancelMutation.error.value.message
  }
  return ''
})

const items = computed(() => query.data.value?.items || [])
const total = computed(() => query.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pager.size)))
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))

function formatMoney(priceCent: number): string {
  return `¥ ${(priceCent / 100).toFixed(2)}`
}

function formatTime(raw?: string): string {
  if (!raw) {
    return '-'
  }

  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  return parsed.toLocaleString('zh-CN')
}

async function handlePay(orderNo: string): Promise<void> {
  try {
    await payMutation.mutateAsync(orderNo)
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function handleCancel(orderNo: string): Promise<void> {
  try {
    await cancelMutation.mutateAsync(orderNo)
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/90 p-4">
      <h1 class="font-display text-2xl text-stone-900">我的订单</h1>
      <p class="mt-1 text-sm text-stone-600">支持模拟支付与主动取消，后续可在此扩展履约状态</p>
    </header>

    <ResultState :loading="query.isLoading.value" :error="errorMessage" :empty="!query.isLoading.value && items.length === 0" empty-text="你还没有订单">
      <div class="space-y-3">
        <article v-for="item in items" :key="item.orderNo" class="rounded-2xl border border-stone-200 bg-white/95 p-4">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p class="text-sm text-stone-500">订单号 {{ item.orderNo }}</p>
              <p class="mt-1 text-base font-semibold text-stone-900">{{ formatMoney(item.totalAmountCent) }}</p>
              <p class="mt-1 text-xs text-stone-500">创建时间 {{ formatTime(item.createdAt) }}</p>
              <p class="mt-1 text-xs text-stone-500">支付时间 {{ formatTime(item.paidAt) }}</p>
            </div>

            <div class="flex items-center gap-2">
              <RouterLink
                :to="`/orders/${item.orderNo}`"
                class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
              >
                查看详情
              </RouterLink>
              <span
                class="rounded-full px-3 py-1 text-xs font-semibold"
                :class="
                  item.status === 'PAID'
                    ? 'bg-emerald-100 text-emerald-700'
                    : item.status === 'UNPAID'
                      ? 'bg-amber-100 text-amber-700'
                      : 'bg-stone-200 text-stone-700'
                "
              >
                {{ item.status }}
              </span>

              <button
                v-if="item.status === 'UNPAID'"
                type="button"
                class="rounded-lg bg-stone-900 px-3 py-1.5 text-xs text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="payMutation.isPending.value"
                @click="handlePay(item.orderNo)"
              >
                立即支付
              </button>

              <button
                v-if="item.status === 'UNPAID'"
                type="button"
                class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="cancelMutation.isPending.value"
                @click="handleCancel(item.orderNo)"
              >
                取消订单
              </button>
            </div>
          </div>
        </article>
      </div>

      <div class="flex items-center justify-between rounded-2xl border border-stone-200 bg-white/90 px-4 py-3 text-sm">
        <span class="text-stone-600">第 {{ pager.page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
        <div class="flex gap-2">
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="pager.page <= 1 || query.isFetching.value"
            @click="pager.page -= 1"
          >
            上一页
          </button>
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="pager.page >= totalPages || query.isFetching.value"
            @click="pager.page += 1"
          >
            下一页
          </button>
        </div>
      </div>
    </ResultState>

    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>
  </section>
</template>
