<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { cancelOrder, getOrderDetail, payOrder } from '@/api/order'
import { ApiBizError } from '@/types/result'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const orderNo = computed(() => String(route.params.orderNo || ''))

const query = useQuery({
  queryKey: computed(() => ['order-detail', orderNo.value]),
  queryFn: () => getOrderDetail(orderNo.value),
  enabled: computed(() => orderNo.value.length > 0),
  refetchInterval: (state) => {
    const status = state.state.data?.status
    return status === 'UNPAID' ? 3000 : false
  },
})

const payMutation = useMutation({
  mutationFn: () => payOrder(orderNo.value, { paymentNo: `web-pay-${Date.now()}` }),
  onSuccess: async () => {
    await query.refetch()
    await queryClient.invalidateQueries({ queryKey: ['orders'] })
  },
})

const cancelMutation = useMutation({
  mutationFn: () => cancelOrder(orderNo.value, { reason: '用户主动取消' }),
  onSuccess: async () => {
    await query.refetch()
    await queryClient.invalidateQueries({ queryKey: ['orders'] })
  },
})

const detail = computed(() => query.data.value)
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

async function handlePay(): Promise<void> {
  try {
    await payMutation.mutateAsync()
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function handleCancel(): Promise<void> {
  try {
    await cancelMutation.mutateAsync()
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}
</script>

<template>
  <section class="space-y-4">
    <button
      type="button"
      class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
      @click="router.push('/orders')"
    >
      返回订单列表
    </button>

    <ResultState :loading="query.isLoading.value" :error="errorMessage" :empty="!query.isLoading.value && !detail" empty-text="订单不存在">
      <article v-if="detail" class="space-y-4 rounded-2xl border border-stone-200 bg-white/95 p-5">
        <header class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 class="font-display text-2xl text-stone-900">订单 {{ detail.orderNo }}</h1>
            <p class="mt-1 text-sm text-stone-600">创建于 {{ formatTime(detail.createdAt) }}</p>
            <p v-if="detail.status === 'UNPAID'" class="mt-1 text-xs text-amber-700">状态自动刷新中（每 3 秒）</p>
          </div>
          <span
            class="w-fit rounded-full px-3 py-1 text-xs font-semibold"
            :class="
              detail.status === 'PAID'
                ? 'bg-emerald-100 text-emerald-700'
                : detail.status === 'UNPAID'
                  ? 'bg-amber-100 text-amber-700'
                  : 'bg-stone-200 text-stone-700'
            "
          >
            {{ detail.status }}
          </span>
        </header>

        <div class="grid gap-3 rounded-xl bg-stone-50 p-4 text-sm text-stone-700 sm:grid-cols-2">
          <p>买家用户ID：{{ detail.buyerUserId }}</p>
          <p>卖家用户ID：{{ detail.sellerUserId }}</p>
          <p>订单金额：{{ formatMoney(detail.totalAmountCent) }}</p>
          <p>支付时间：{{ formatTime(detail.paidAt) }}</p>
          <p class="sm:col-span-2">超时关单时间：{{ formatTime(detail.timeoutAt) }}</p>
        </div>

        <section>
          <h2 class="text-base font-semibold text-stone-900">订单明细</h2>
          <div class="mt-3 overflow-hidden rounded-xl border border-stone-200">
            <table class="w-full text-left text-sm">
              <thead class="bg-stone-100 text-stone-600">
                <tr>
                  <th class="px-3 py-2">SKU</th>
                  <th class="px-3 py-2">规格</th>
                  <th class="px-3 py-2">单价</th>
                  <th class="px-3 py-2">数量</th>
                  <th class="px-3 py-2">小计</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.items" :key="`${item.skuId}-${item.productId}`" class="border-t border-stone-100">
                  <td class="px-3 py-2 font-medium text-stone-800">{{ item.skuName }}</td>
                  <td class="px-3 py-2 text-stone-600">{{ item.specJson || '-' }}</td>
                  <td class="px-3 py-2 text-stone-700">{{ formatMoney(item.priceCent) }}</td>
                  <td class="px-3 py-2 text-stone-700">{{ item.quantity }}</td>
                  <td class="px-3 py-2 font-medium text-stone-900">{{ formatMoney(item.subtotalCent) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <div class="flex gap-2" v-if="detail.status === 'UNPAID'">
          <button
            type="button"
            class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="payMutation.isPending.value"
            @click="handlePay"
          >
            {{ payMutation.isPending.value ? '支付中...' : '立即支付' }}
          </button>
          <button
            type="button"
            class="rounded-xl border border-stone-300 px-4 py-2 text-sm text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="cancelMutation.isPending.value"
            @click="handleCancel"
          >
            {{ cancelMutation.isPending.value ? '取消中...' : '取消订单' }}
          </button>
        </div>
      </article>
    </ResultState>
  </section>
</template>
