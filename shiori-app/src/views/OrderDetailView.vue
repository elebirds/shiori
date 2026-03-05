<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import OrderStageTimeline from '@/components/OrderStageTimeline.vue'
import ResultState from '@/components/ResultState.vue'
import {
  applyOrderRefundV2,
  cancelOrderV2,
  confirmReceiptV2,
  getOrderDetailV2,
  getLatestOrderRefundV2,
  getOrderTimelineV2,
  type OrderRefundStatus,
  type OrderStatus,
  type OrderTimelineItemResponse,
} from '@/api/orderV2'
import { ApiBizError } from '@/types/result'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const chatStore = useChatStore()

const orderNo = computed(() => String(route.params.orderNo || ''))
const routeConversationId = computed(() => {
  const raw = Number(route.query.conversationId || 0)
  return Number.isFinite(raw) && raw > 0 ? raw : 0
})

const query = useQuery({
  queryKey: computed(() => ['order-detail-v2', orderNo.value]),
  queryFn: () => getOrderDetailV2(orderNo.value),
  enabled: computed(() => orderNo.value.length > 0),
  refetchInterval: (state) => {
    const status = state.state.data?.status
    return status === 'UNPAID' ? 3000 : false
  },
})

const detail = computed(() => query.data.value)

const latestRefundQuery = useQuery({
  queryKey: computed(() => ['order-refund-v2', orderNo.value, detail.value?.refundNo || '']),
  queryFn: () => getLatestOrderRefundV2(orderNo.value),
  enabled: computed(() => orderNo.value.length > 0 && Boolean(detail.value?.refundNo)),
})

const timelineQuery = useQuery({
  queryKey: computed(() => ['order-timeline-v2', orderNo.value]),
  queryFn: () => getOrderTimelineV2(orderNo.value, { page: 1, size: 50 }),
  enabled: computed(() => orderNo.value.length > 0),
})

const cancelMutation = useMutation({
  mutationFn: () => cancelOrderV2(orderNo.value, { reason: '用户主动取消' }),
  onSuccess: async () => {
    await query.refetch()
    await timelineQuery.refetch()
    await queryClient.invalidateQueries({ queryKey: ['orders-v2'] })
  },
})

const confirmMutation = useMutation({
  mutationFn: () => confirmReceiptV2(orderNo.value, { reason: '买家已确认收货' }),
  onSuccess: async () => {
    await query.refetch()
    await timelineQuery.refetch()
    await queryClient.invalidateQueries({ queryKey: ['orders-v2'] })
  },
})

const applyRefundMutation = useMutation({
  mutationFn: () => {
    const status = detail.value?.status
    const reason =
      status === 'PAID'
        ? '买家发货前申请退款'
        : status === 'DELIVERING'
          ? '买家发货后申请退款'
          : '买家售后申请退款'
    return applyOrderRefundV2(orderNo.value, { reason })
  },
  onSuccess: async () => {
    await query.refetch()
    await timelineQuery.refetch()
    await latestRefundQuery.refetch()
    await queryClient.invalidateQueries({ queryKey: ['orders-v2'] })
  },
})

const timelineItems = computed(() => timelineQuery.data.value?.items || [])
const latestRefund = computed(() => latestRefundQuery.data.value)
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))
const refundErrorMessage = computed(() => (latestRefundQuery.error.value instanceof Error ? latestRefundQuery.error.value.message : ''))
const canApplyRefund = computed(() => {
  if (!detail.value) {
    return false
  }
  return (detail.value.status === 'PAID' || detail.value.status === 'DELIVERING' || detail.value.status === 'FINISHED') && !detail.value.refundNo
})
const applyRefundButtonText = computed(() => {
  if (applyRefundMutation.isPending.value) {
    return '提交中...'
  }
  return detail.value?.status === 'PAID' ? '立即退款' : '申请退款'
})

const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  UNPAID: '待支付',
  PAID: '已支付',
  DELIVERING: '待收货',
  FINISHED: '已完成',
  CANCELED: '已取消',
}

const REFUND_STATUS_TEXT: Record<OrderRefundStatus, string> = {
  REQUESTED: '待审核',
  REJECTED: '已拒绝',
  PENDING_FUNDS: '待补款',
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

function transitionText(item: OrderTimelineItemResponse): string {
  return `${statusText(item.fromStatus)} -> ${statusText(item.toStatus)}`
}

function handlePay(): void {
  const conversationId = detail.value?.conversationId || routeConversationId.value
  void router.push({
    path: `/checkout/${orderNo.value}`,
    query: conversationId && conversationId > 0 ? { conversationId: String(conversationId) } : undefined,
  })
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

async function handleConfirm(): Promise<void> {
  try {
    await confirmMutation.mutateAsync()
    const conversationId = detail.value?.conversationId || routeConversationId.value
    if (conversationId && conversationId > 0) {
      await chatStore.sendTradeStatusCard(conversationId, 'ORDER_FINISHED', orderNo.value)
    }
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function handleApplyRefund(): Promise<void> {
  try {
    await applyRefundMutation.mutateAsync()
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
          <div class="flex flex-wrap items-center gap-2">
            <span class="w-fit rounded-full px-3 py-1 text-xs font-semibold" :class="statusClass(detail.status)">
              {{ statusText(detail.status) }}
            </span>
            <span
              v-if="detail.refundStatus"
              class="w-fit rounded-full px-3 py-1 text-xs font-semibold"
              :class="refundStatusClass(detail.refundStatus)"
            >
              退款{{ refundStatusText(detail.refundStatus) }}
            </span>
          </div>
        </header>

        <div class="grid gap-3 rounded-xl bg-stone-50 p-4 text-sm text-stone-700 sm:grid-cols-2">
          <p>订单金额：{{ formatMoney(detail.totalAmountCent) }}</p>
          <p>支付时间：{{ formatTime(detail.paidAt) }}</p>
          <p>支付方式：余额支付</p>
          <p class="sm:col-span-2">超时关单时间：{{ formatTime(detail.timeoutAt) }}</p>
          <p v-if="detail.refundNo">退款单号：{{ detail.refundNo }}</p>
          <p v-if="detail.refundNo">退款金额：{{ formatMoney(detail.refundAmountCent || 0) }}</p>
          <p v-if="detail.refundNo" class="sm:col-span-2">退款更新时间：{{ formatTime(detail.refundUpdatedAt) }}</p>
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

        <OrderStageTimeline
          :status="detail.status"
          :created-at="detail.createdAt"
          :paid-at="detail.paidAt"
          :timeline-items="timelineItems"
        />

        <section class="space-y-3 rounded-xl border border-stone-200 bg-stone-50 p-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 class="text-base font-semibold text-stone-900">退款进度</h2>
            <button
              v-if="canApplyRefund"
              type="button"
              class="rounded-lg bg-rose-700 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-rose-600 disabled:cursor-not-allowed disabled:opacity-70"
              :disabled="applyRefundMutation.isPending.value"
              @click="handleApplyRefund"
            >
              {{ applyRefundButtonText }}
            </button>
          </div>

          <p v-if="!detail.refundNo" class="text-sm text-stone-500">当前订单暂无退款申请。</p>

          <div v-else class="space-y-2 rounded-lg bg-white p-3 text-sm text-stone-700">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-medium text-stone-900">退款状态</span>
              <span class="rounded-full px-2.5 py-1 text-xs font-semibold" :class="refundStatusClass(latestRefund?.status || detail.refundStatus)">
                {{ refundStatusText(latestRefund?.status || detail.refundStatus) }}
              </span>
            </div>
            <p>退款单号：{{ latestRefund?.refundNo || detail.refundNo }}</p>
            <p>退款金额：{{ formatMoney(latestRefund?.amountCent || detail.refundAmountCent || 0) }}</p>
            <p>申请原因：{{ latestRefund?.applyReason || '-' }}</p>
            <p>审核截止：{{ formatTime(latestRefund?.reviewDeadlineAt) }}</p>
            <p>审核时间：{{ formatTime(latestRefund?.reviewedAt) }}</p>
            <p>拒绝原因：{{ latestRefund?.rejectReason || '-' }}</p>
            <p>支付退款单：{{ latestRefund?.paymentNo || '-' }}</p>
            <p v-if="latestRefund?.lastError" class="text-rose-600">最近错误：{{ latestRefund.lastError }}</p>
            <p v-if="refundErrorMessage" class="text-rose-600">{{ refundErrorMessage }}</p>
          </div>
        </section>

        <section class="space-y-2">
          <h2 class="text-base font-semibold text-stone-900">操作记录</h2>
          <div class="max-h-64 space-y-2 overflow-auto rounded-xl border border-stone-200 bg-stone-50 p-3">
            <article v-for="(item, index) in timelineItems" :key="`${item.createdAt}-${index}`" class="rounded-lg bg-white p-3 text-sm">
              <div class="flex items-center justify-between">
                <p class="font-medium text-stone-800">{{ transitionText(item) }}</p>
                <span class="text-xs text-stone-500">{{ item.source }}</span>
              </div>
              <p class="mt-1 text-xs text-stone-600">操作人：{{ item.operatorUserId ?? '-' }}</p>
              <p class="mt-1 text-xs text-stone-600">时间：{{ formatTime(item.createdAt) }}</p>
              <p class="mt-1 text-xs text-stone-600">备注：{{ item.reason || '-' }}</p>
            </article>
            <p v-if="timelineItems.length === 0" class="text-sm text-stone-500">暂无履约记录</p>
          </div>
        </section>

        <div class="flex gap-2" v-if="detail.status === 'UNPAID' || detail.status === 'DELIVERING'">
          <button
            v-if="detail.status === 'UNPAID'"
            type="button"
            class="rounded-xl bg-[var(--shiori-pay-blue-700)] px-4 py-2 text-sm font-medium text-white transition hover:bg-[var(--shiori-pay-blue-800)]"
            @click="handlePay"
          >
            前往收银台
          </button>
          <button
            v-if="detail.status === 'UNPAID'"
            type="button"
            class="rounded-xl border border-stone-300 px-4 py-2 text-sm text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="cancelMutation.isPending.value"
            @click="handleCancel"
          >
            {{ cancelMutation.isPending.value ? '取消中...' : '取消订单' }}
          </button>
          <button
            v-if="detail.status === 'DELIVERING'"
            type="button"
            class="rounded-xl bg-emerald-700 px-4 py-2 text-sm font-medium text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="confirmMutation.isPending.value"
            @click="handleConfirm"
          >
            {{ confirmMutation.isPending.value ? '提交中...' : '确认收货' }}
          </button>
        </div>
      </article>
    </ResultState>
  </section>
</template>
