<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { cancelOrderV2, confirmReceiptV2, listMyOrdersV2, type OrderRefundStatus, type OrderStatus } from '@/api/orderV2'
import { ApiBizError } from '@/types/result'
import { useChatStore } from '@/stores/chat'

const router = useRouter()
const queryClient = useQueryClient()
const chatStore = useChatStore()

const pager = reactive({
  page: 1,
  size: 10,
})

const query = useQuery({
  queryKey: computed(() => ['orders-v2', pager.page, pager.size]),
  queryFn: () => listMyOrdersV2({ page: pager.page, size: pager.size }),
})

const cancelMutation = useMutation({
  mutationFn: (orderNo: string) => cancelOrderV2(orderNo, { reason: '用户主动取消' }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['orders-v2'] })
  },
})

const confirmMutation = useMutation({
  mutationFn: (orderNo: string) => confirmReceiptV2(orderNo, { reason: '买家已收货' }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['orders-v2'] })
  },
})

const actionError = computed(() => {
  const errors = [cancelMutation.error.value, confirmMutation.error.value]
  const matched = errors.find((item) => item instanceof Error)
  return matched instanceof Error ? matched.message : ''
})

const items = computed(() => query.data.value?.items || [])
const total = computed(() => query.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pager.size)))
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))

const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  UNPAID: '待支付',
  PAID: '已支付',
  DELIVERING: '待收货',
  FINISHED: '已完成',
  CANCELED: '已取消',
}

const REFUND_STATUS_TEXT: Record<OrderRefundStatus, string> = {
  REQUESTED: '退款待审核',
  REJECTED: '退款已拒绝',
  PENDING_FUNDS: '退款待补款',
  SUCCEEDED: '已退款',
}

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

function statusClass(status: OrderStatus): string {
  if (status === 'UNPAID') {
    return 'bg-amber-100 text-amber-700'
  }
  if (status === 'PAID') {
    return 'bg-sky-100 text-sky-700'
  }
  if (status === 'DELIVERING') {
    return 'bg-indigo-100 text-indigo-700'
  }
  if (status === 'FINISHED') {
    return 'bg-emerald-100 text-emerald-700'
  }
  return 'bg-stone-200 text-stone-700'
}

function statusText(status: OrderStatus): string {
  return ORDER_STATUS_TEXT[status] || status
}

function refundStatusText(status?: OrderRefundStatus): string {
  if (!status) {
    return '-'
  }
  return REFUND_STATUS_TEXT[status] || status
}

function refundStatusClass(status?: OrderRefundStatus): string {
  if (status === 'REQUESTED') {
    return 'bg-amber-100 text-amber-700'
  }
  if (status === 'SUCCEEDED') {
    return 'bg-emerald-100 text-emerald-700'
  }
  if (status === 'PENDING_FUNDS') {
    return 'bg-rose-100 text-rose-700'
  }
  if (status === 'REJECTED') {
    return 'bg-stone-200 text-stone-700'
  }
  return 'bg-stone-100 text-stone-600'
}

function handlePay(orderNo: string, conversationId?: number): void {
  void router.push({
    path: `/checkout/${orderNo}`,
    query: conversationId && conversationId > 0 ? { conversationId: String(conversationId) } : undefined,
  })
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

async function handleConfirm(orderNo: string, conversationId?: number): Promise<void> {
  try {
    await confirmMutation.mutateAsync(orderNo)
    if (conversationId && conversationId > 0) {
      await chatStore.sendTradeStatusCard(conversationId, 'ORDER_FINISHED', orderNo)
    }
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
      <p class="mt-1 text-sm text-stone-600">可在这里完成支付、取消订单和确认收货。</p>
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
              <p v-if="item.refundNo" class="mt-1 text-xs text-stone-500">
                退款单 {{ item.refundNo }} / {{ formatMoney(item.refundAmountCent || 0) }} / 更新 {{ formatTime(item.refundUpdatedAt) }}
              </p>
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <RouterLink
                :to="`/orders/${item.orderNo}`"
                class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
              >
                查看详情
              </RouterLink>
              <span class="rounded-full px-3 py-1 text-xs font-semibold" :class="statusClass(item.status)">
                {{ statusText(item.status) }}
              </span>
              <span
                v-if="item.refundStatus"
                class="rounded-full px-3 py-1 text-xs font-semibold"
                :class="refundStatusClass(item.refundStatus)"
              >
                {{ refundStatusText(item.refundStatus) }}
              </span>

              <button
                v-if="item.status === 'UNPAID'"
                type="button"
                class="rounded-lg bg-[var(--shiori-pay-blue-700)] px-3 py-1.5 text-xs text-white transition hover:bg-[var(--shiori-pay-blue-800)]"
                @click="handlePay(item.orderNo, item.conversationId)"
              >
                去支付
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

              <button
                v-if="item.status === 'DELIVERING'"
                type="button"
                class="rounded-lg bg-emerald-700 px-3 py-1.5 text-xs text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="confirmMutation.isPending.value"
                @click="handleConfirm(item.orderNo, item.conversationId)"
              >
                确认收货
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
