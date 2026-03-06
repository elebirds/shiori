<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import OrderStageTimeline from '@/components/OrderStageTimeline.vue'
import OrderReviewDialog from '@/components/OrderReviewDialog.vue'
import ResultState from '@/components/ResultState.vue'
import {
  createOrderReviewV2,
  deliverSellerOrderV2,
  finishSellerOrderV2,
  getOrderTimelineV2,
  getOrderReviewContextV2,
  getSellerOrderDetailV2,
  type OrderRefundStatus,
  type OrderStatus,
  type OrderReviewUpsertRequest,
  type OrderTimelineItemResponse,
  updateMyOrderReviewV2,
} from '@/api/orderV2'
import { ApiBizError } from '@/types/result'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const chatStore = useChatStore()

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

const reviewContextQuery = useQuery({
  queryKey: computed(() => ['seller-order-review-context-v2', orderNo.value]),
  queryFn: () => getOrderReviewContextV2(orderNo.value),
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
    await reviewContextQuery.refetch()
    await queryClient.invalidateQueries({ queryKey: ['seller-orders-v2'] })
  },
})

const createReviewMutation = useMutation({
  mutationFn: (payload: OrderReviewUpsertRequest) => createOrderReviewV2(orderNo.value, payload),
  onSuccess: async () => {
    await Promise.all([detailQuery.refetch(), reviewContextQuery.refetch()])
  },
})

const updateReviewMutation = useMutation({
  mutationFn: (payload: OrderReviewUpsertRequest) => updateMyOrderReviewV2(orderNo.value, payload),
  onSuccess: async () => {
    await Promise.all([detailQuery.refetch(), reviewContextQuery.refetch()])
  },
})

const detail = computed(() => detailQuery.data.value)
const timelineItems = computed(() => timelineQuery.data.value?.items || [])
const reviewContext = computed(() => reviewContextQuery.data.value)
const errorMessage = computed(() => (detailQuery.error.value instanceof Error ? detailQuery.error.value.message : ''))
const reviewErrorMessage = computed(() => (reviewContextQuery.error.value instanceof Error ? reviewContextQuery.error.value.message : ''))
const reviewModalOpen = ref(false)
const reviewMode = ref<'create' | 'edit'>('create')
const actionErrorMessage = ref('')

const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  UNPAID: '待支付',
  PAID: '已支付',
  DELIVERING: '待收货',
  FINISHED: '已完成',
  CANCELED: '已取消',
  REFUNDED: '已退款',
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
  return 'bg-stone-100 text-stone-700'
}

function fulfillmentModeText(mode?: string): string {
  if (mode === 'MEETUP') {
    return '线下面交'
  }
  if (mode === 'DELIVERY') {
    return '邮寄配送'
  }
  return '待买家选择'
}

function shippingAddressText(): string {
  if (!detail.value?.shippingAddress) {
    return '-'
  }
  const shipping = detail.value.shippingAddress
  return `${shipping.province} ${shipping.city} ${shipping.district} ${shipping.detailAddress}`
}

function transitionText(item: OrderTimelineItemResponse): string {
  return `${statusText(item.fromStatus)} -> ${statusText(item.toStatus)}`
}

async function handleDeliver(): Promise<void> {
  try {
    actionErrorMessage.value = ''
    await deliverMutation.mutateAsync()
    const conversationId = detail.value?.conversationId
    if (conversationId && conversationId > 0) {
      await chatStore.sendTradeStatusCard(conversationId, 'ORDER_DELIVERED', orderNo.value)
    }
  } catch (error) {
    if (error instanceof ApiBizError) {
      actionErrorMessage.value = error.message
      return
    }
  }
}

async function handleFinish(): Promise<void> {
  try {
    actionErrorMessage.value = ''
    await finishMutation.mutateAsync()
  } catch (error) {
    if (error instanceof ApiBizError) {
      actionErrorMessage.value = error.message
      return
    }
  }
}

function openCreateReviewModal(): void {
  reviewMode.value = 'create'
  reviewModalOpen.value = true
}

function openEditReviewModal(): void {
  reviewMode.value = 'edit'
  reviewModalOpen.value = true
}

async function submitReview(payload: OrderReviewUpsertRequest): Promise<void> {
  try {
    actionErrorMessage.value = ''
    if (reviewMode.value === 'edit') {
      await updateReviewMutation.mutateAsync(payload)
    } else {
      await createReviewMutation.mutateAsync(payload)
    }
    reviewModalOpen.value = false
  } catch (error) {
    if (error instanceof ApiBizError) {
      actionErrorMessage.value = error.message
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
          <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-full bg-stone-100 px-3 py-1 text-xs font-semibold text-stone-700">{{ statusText(detail.status) }}</span>
            <span
              v-if="detail.refundStatus"
              class="rounded-full px-3 py-1 text-xs font-semibold"
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
          <p v-if="detail.refundNo">退款单号：{{ detail.refundNo }}</p>
          <p v-if="detail.refundNo">退款金额：{{ formatMoney(detail.refundAmountCent || 0) }}</p>
          <p v-if="detail.refundNo">退款更新时间：{{ formatTime(detail.refundUpdatedAt) }}</p>
        </div>

        <section class="rounded-xl border border-stone-200 bg-stone-50 p-4 text-sm text-stone-700">
          <h2 class="text-base font-semibold text-stone-900">履约信息</h2>
          <div class="mt-2 grid gap-2 sm:grid-cols-2">
            <p>履约方式：{{ fulfillmentModeText(detail.fulfillmentMode) }}</p>
            <template v-if="detail.fulfillmentMode === 'DELIVERY'">
              <p>收件人：{{ detail.shippingAddress?.receiverName || '-' }} {{ detail.shippingAddress?.receiverPhone || '' }}</p>
              <p class="sm:col-span-2">收货地址：{{ shippingAddressText() }}</p>
            </template>
          </div>
        </section>

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
          :my-review-submitted="detail.myReviewSubmitted"
          :counterparty-review-submitted="detail.counterpartyReviewSubmitted"
        />

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

        <section v-if="detail.status === 'FINISHED'" class="space-y-3 rounded-xl border border-stone-200 bg-stone-50 p-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 class="text-base font-semibold text-stone-900">交易互评</h2>
            <span class="text-xs text-stone-600">评价截止：{{ formatTime(reviewContext?.reviewDeadlineAt) }}</span>
          </div>

          <p v-if="reviewErrorMessage" class="text-xs text-rose-600">{{ reviewErrorMessage }}</p>
          <p v-if="actionErrorMessage" class="text-xs text-rose-600">{{ actionErrorMessage }}</p>

          <div class="grid gap-3 sm:grid-cols-2">
            <article class="rounded-lg bg-white p-3 text-sm">
              <p class="font-medium text-stone-800">我的评价</p>
              <p v-if="reviewContext?.myReview" class="mt-1 text-stone-700">
                综合 {{ reviewContext.myReview.overallStar }} 星
              </p>
              <p v-else class="mt-1 text-stone-500">尚未提交</p>
              <p class="mt-1 text-xs text-stone-600">
                {{ reviewContext?.myReview?.comment || '暂无补充评论' }}
              </p>
            </article>

            <article class="rounded-lg bg-white p-3 text-sm">
              <p class="font-medium text-stone-800">对方评价</p>
              <p v-if="reviewContext?.counterpartyReview" class="mt-1 text-stone-700">
                综合 {{ reviewContext.counterpartyReview.overallStar }} 星
              </p>
              <p v-else class="mt-1 text-stone-500">对方尚未提交</p>
              <p class="mt-1 text-xs text-stone-600">
                {{ reviewContext?.counterpartyReview?.comment || '暂无补充评论' }}
              </p>
            </article>
          </div>

          <div class="flex flex-wrap gap-2">
            <button
              v-if="reviewContext?.canCreateReview"
              type="button"
              class="rounded-lg bg-stone-900 px-3 py-1.5 text-xs text-white transition hover:bg-stone-700"
              @click="openCreateReviewModal"
            >
              去评价
            </button>
            <button
              v-if="reviewContext?.canEditReview"
              type="button"
              class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
              @click="openEditReviewModal"
            >
              修改评价
            </button>
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
        <p v-if="actionErrorMessage" class="text-xs text-rose-600">{{ actionErrorMessage }}</p>
      </article>

      <OrderReviewDialog
        :open="reviewModalOpen"
        :reviewer-role="'SELLER'"
        :mode="reviewMode"
        :submitting="createReviewMutation.isPending.value || updateReviewMutation.isPending.value"
        :initial-review="reviewContext?.myReview"
        @close="reviewModalOpen = false"
        @submit="submitReview"
      />
    </ResultState>
  </section>
</template>
