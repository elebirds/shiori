<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { deliverSellerOrderV2, finishSellerOrderV2, getOrderTimelineV2, getSellerOrderDetailV2 } from '@/api/orderV2'
import { ApiBizError } from '@/types/result'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const orderNo = computed(() => String(route.params.orderNo || ''))

const detailQuery = useQuery({
  queryKey: computed(() => ['seller-order-detail-v2', orderNo.value]),
  queryFn: () => getSellerOrderDetailV2(orderNo.value),
  enabled: computed(() => orderNo.value.length > 0),
})

const timelineQuery = useQuery({
  queryKey: computed(() => ['seller-order-timeline-v2', orderNo.value]),
  queryFn: () => getOrderTimelineV2(orderNo.value, { page: 1, size: 50 }),
  enabled: computed(() => orderNo.value.length > 0),
})

const deliverMutation = useMutation({
  mutationFn: () => deliverSellerOrderV2(orderNo.value, { reason: '卖家发货' }),
  onSuccess: async () => {
    await detailQuery.refetch()
    await timelineQuery.refetch()
    await queryClient.invalidateQueries({ queryKey: ['seller-orders-v2'] })
  },
})

const finishMutation = useMutation({
  mutationFn: () => finishSellerOrderV2(orderNo.value, { reason: '卖家完成履约' }),
  onSuccess: async () => {
    await detailQuery.refetch()
    await timelineQuery.refetch()
    await queryClient.invalidateQueries({ queryKey: ['seller-orders-v2'] })
  },
})

const detail = computed(() => detailQuery.data.value)
const timelineItems = computed(() => timelineQuery.data.value?.items || [])
const errorMessage = computed(() => (detailQuery.error.value instanceof Error ? detailQuery.error.value.message : ''))

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

async function handleDeliver(): Promise<void> {
  try {
    await deliverMutation.mutateAsync()
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function handleFinish(): Promise<void> {
  try {
    await finishMutation.mutateAsync()
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
      @click="router.push('/seller/orders')"
    >
      返回卖家工作台
    </button>

    <ResultState
      :loading="detailQuery.isLoading.value"
      :error="errorMessage"
      :empty="!detailQuery.isLoading.value && !detail"
      empty-text="订单不存在或你无权限"
    >
      <article v-if="detail" class="space-y-4 rounded-2xl border border-stone-200 bg-white/95 p-5">
        <header class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 class="font-display text-2xl text-stone-900">卖家订单 {{ detail.orderNo }}</h1>
            <p class="mt-1 text-sm text-stone-600">创建于 {{ formatTime(detail.createdAt) }}</p>
          </div>
          <span class="rounded-full bg-stone-100 px-3 py-1 text-xs font-semibold text-stone-700">{{ detail.status }}</span>
        </header>

        <div class="grid gap-3 rounded-xl bg-stone-50 p-4 text-sm text-stone-700 sm:grid-cols-2">
          <p>买家用户ID：{{ detail.buyerUserId }}</p>
          <p>卖家用户ID：{{ detail.sellerUserId }}</p>
          <p>订单金额：{{ formatMoney(detail.totalAmountCent) }}</p>
          <p>支付时间：{{ formatTime(detail.paidAt) }}</p>
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

        <section class="space-y-2">
          <h2 class="text-base font-semibold text-stone-900">履约时间线</h2>
          <div class="max-h-64 space-y-2 overflow-auto rounded-xl border border-stone-200 bg-stone-50 p-3">
            <article v-for="(item, index) in timelineItems" :key="`${item.createdAt}-${index}`" class="rounded-lg bg-white p-3 text-sm">
              <div class="flex items-center justify-between">
                <p class="font-medium text-stone-800">{{ item.fromStatus }} -> {{ item.toStatus }}</p>
                <span class="text-xs text-stone-500">{{ item.source }}</span>
              </div>
              <p class="mt-1 text-xs text-stone-600">操作人：{{ item.operatorUserId ?? '-' }}</p>
              <p class="mt-1 text-xs text-stone-600">时间：{{ formatTime(item.createdAt) }}</p>
              <p class="mt-1 text-xs text-stone-600">备注：{{ item.reason || '-' }}</p>
            </article>
            <p v-if="timelineItems.length === 0" class="text-sm text-stone-500">暂无履约记录</p>
          </div>
        </section>

        <div class="flex gap-2">
          <button
            v-if="detail.status === 'PAID'"
            type="button"
            class="rounded-xl bg-indigo-700 px-4 py-2 text-sm font-medium text-white transition hover:bg-indigo-600 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="deliverMutation.isPending.value"
            @click="handleDeliver"
          >
            {{ deliverMutation.isPending.value ? '提交中...' : '标记发货' }}
          </button>
          <button
            v-if="detail.status === 'DELIVERING'"
            type="button"
            class="rounded-xl bg-emerald-700 px-4 py-2 text-sm font-medium text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="finishMutation.isPending.value"
            @click="handleFinish"
          >
            {{ finishMutation.isPending.value ? '提交中...' : '标记完成' }}
          </button>
        </div>
      </article>
    </ResultState>
  </section>
</template>

