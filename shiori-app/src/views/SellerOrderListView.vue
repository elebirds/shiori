<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { deliverSellerOrderV2, finishSellerOrderV2, listSellerOrdersV2, type OrderRefundStatus, type OrderStatus } from '@/api/orderV2'
import { ApiBizError } from '@/types/result'
import { useChatStore } from '@/stores/chat'

const router = useRouter()
const queryClient = useQueryClient()
const chatStore = useChatStore()

const pager = reactive({
  page: 1,
  size: 10,
})
const orderNoInput = ref('')
const orderNo = ref('')
const status = ref<OrderStatus | ''>('')
const createdFrom = ref('')
const createdTo = ref('')

const query = useQuery({
  queryKey: computed(() => ['seller-orders-v2', pager.page, pager.size, orderNo.value, status.value, createdFrom.value, createdTo.value]),
  queryFn: () =>
    listSellerOrdersV2({
      page: pager.page,
      size: pager.size,
      orderNo: orderNo.value || undefined,
      status: status.value || undefined,
      createdFrom: createdFrom.value || undefined,
      createdTo: createdTo.value || undefined,
    }),
})

const deliverMutation = useMutation({
  mutationFn: (targetOrderNo: string) => deliverSellerOrderV2(targetOrderNo, { reason: '卖家发货' }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['seller-orders-v2'] })
  },
})

const finishMutation = useMutation({
  mutationFn: (targetOrderNo: string) => finishSellerOrderV2(targetOrderNo, { reason: '卖家完成履约' }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['seller-orders-v2'] })
  },
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
  REFUNDED: '已退款',
}

const REFUND_STATUS_TEXT: Record<OrderRefundStatus, string> = {
  REQUESTED: '退款待审核',
  REJECTED: '退款已拒绝',
  PENDING_FUNDS: '退款待补款',
  SUCCEEDED: '已退款',
}

const actionError = computed(() => {
  const first = deliverMutation.error.value || finishMutation.error.value
  return first instanceof Error ? first.message : ''
})

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

function statusText(orderStatus: OrderStatus): string {
  return ORDER_STATUS_TEXT[orderStatus] || orderStatus
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

function applyFilter(): void {
  pager.page = 1
  orderNo.value = orderNoInput.value.trim()
}

function goDetail(targetOrderNo: string): void {
  void router.push(`/seller/orders/${targetOrderNo}`)
}

async function handleDeliver(targetOrderNo: string, conversationId?: number): Promise<void> {
  try {
    await deliverMutation.mutateAsync(targetOrderNo)
    if (conversationId && conversationId > 0) {
      await chatStore.sendTradeStatusCard(conversationId, 'ORDER_DELIVERED', targetOrderNo)
    }
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function handleFinish(targetOrderNo: string): Promise<void> {
  try {
    await finishMutation.mutateAsync(targetOrderNo)
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
      <h1 class="font-display text-2xl text-stone-900">卖家订单工作台</h1>
      <p class="mt-1 text-sm text-stone-600">聚焦待发货、配送中订单，支持发货与完成操作</p>
      <button
        type="button"
        class="mt-3 rounded-lg border border-amber-300 bg-amber-50 px-3 py-1.5 text-sm text-amber-700 transition hover:bg-amber-100"
        @click="router.push('/seller/refunds')"
      >
        前往退款审核台
      </button>
    </header>

    <section class="rounded-2xl border border-stone-200 bg-white/90 p-4">
      <div class="grid gap-2 md:grid-cols-5">
        <input
          v-model="orderNoInput"
          type="text"
          class="rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="订单号"
          @keyup.enter="applyFilter"
        />
        <select
          v-model="status"
          class="rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
        >
          <option value="">全部状态</option>
          <option value="UNPAID">待支付</option>
          <option value="PAID">已支付</option>
          <option value="DELIVERING">待收货</option>
          <option value="FINISHED">已完成</option>
          <option value="CANCELED">已取消</option>
        </select>
        <input
          v-model="createdFrom"
          type="datetime-local"
          class="rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
        />
        <input
          v-model="createdTo"
          type="datetime-local"
          class="rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
        />
        <button
          type="button"
          class="rounded-xl bg-stone-900 px-4 py-2 text-sm text-white transition hover:bg-stone-700"
          @click="applyFilter"
        >
          查询
        </button>
      </div>
    </section>

    <ResultState :loading="query.isLoading.value" :error="errorMessage" :empty="!query.isLoading.value && items.length === 0" empty-text="暂无卖家订单">
      <div class="space-y-3">
        <article v-for="item in items" :key="item.orderNo" class="rounded-2xl border border-stone-200 bg-white/95 p-4">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p class="text-sm text-stone-500">订单号 {{ item.orderNo }}</p>
              <p class="mt-1 text-base font-semibold text-stone-900">{{ formatMoney(item.totalAmountCent) }}</p>
              <p class="mt-1 text-xs text-stone-500">创建 {{ formatTime(item.createdAt) }} / 支付 {{ formatTime(item.paidAt) }}</p>
              <p v-if="item.refundNo" class="mt-1 text-xs text-stone-500">
                退款单 {{ item.refundNo }} / {{ formatMoney(item.refundAmountCent || 0) }} / 更新 {{ formatTime(item.refundUpdatedAt) }}
              </p>
            </div>

            <div class="flex flex-wrap items-center gap-2">
              <button
                type="button"
                class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
                @click="goDetail(item.orderNo)"
              >
                查看详情
              </button>
              <span class="rounded-full bg-stone-100 px-3 py-1 text-xs font-semibold text-stone-700">{{ statusText(item.status) }}</span>
              <span
                v-if="item.refundStatus"
                class="rounded-full px-3 py-1 text-xs font-semibold"
                :class="refundStatusClass(item.refundStatus)"
              >
                {{ refundStatusText(item.refundStatus) }}
              </span>
              <button
                v-if="item.status === 'PAID'"
                type="button"
                class="rounded-lg bg-indigo-700 px-3 py-1.5 text-xs text-white transition hover:bg-indigo-600 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="deliverMutation.isPending.value"
                @click="handleDeliver(item.orderNo, item.conversationId)"
              >
                标记发货
              </button>
              <button
                v-if="item.status === 'DELIVERING'"
                type="button"
                class="rounded-lg bg-emerald-700 px-3 py-1.5 text-xs text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="finishMutation.isPending.value"
                @click="handleFinish(item.orderNo)"
              >
                标记完成
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
