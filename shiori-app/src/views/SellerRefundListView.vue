<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import {
  approveSellerRefundV2,
  listSellerRefundsV2,
  rejectSellerRefundV2,
  type OrderRefundResponse,
  type OrderRefundStatus,
} from '@/api/orderV2'
import { ApiBizError } from '@/types/result'

const router = useRouter()
const queryClient = useQueryClient()

const pager = reactive({
  page: 1,
  size: 10,
})
const status = ref<OrderRefundStatus | ''>('')
const actionMessage = ref('')

const query = useQuery({
  queryKey: computed(() => ['seller-refunds-v2', status.value, pager.page, pager.size]),
  queryFn: () =>
    listSellerRefundsV2({
      status: status.value || undefined,
      page: pager.page,
      size: pager.size,
    }),
})

const approveMutation = useMutation({
  mutationFn: ({ refundNo, reason }: { refundNo: string; reason?: string }) => approveSellerRefundV2(refundNo, { reason }),
  onSuccess: async () => {
    actionMessage.value = '退款审核已通过'
    await queryClient.invalidateQueries({ queryKey: ['seller-refunds-v2'] })
    await queryClient.invalidateQueries({ queryKey: ['seller-orders-v2'] })
  },
})

const rejectMutation = useMutation({
  mutationFn: ({ refundNo, reason }: { refundNo: string; reason?: string }) => rejectSellerRefundV2(refundNo, { reason }),
  onSuccess: async () => {
    actionMessage.value = '退款申请已拒绝'
    await queryClient.invalidateQueries({ queryKey: ['seller-refunds-v2'] })
    await queryClient.invalidateQueries({ queryKey: ['seller-orders-v2'] })
  },
})

const items = computed(() => query.data.value?.items || [])
const total = computed(() => query.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pager.size)))
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))
const actionError = computed(() => {
  const first = approveMutation.error.value || rejectMutation.error.value
  return first instanceof Error ? first.message : ''
})

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

function refundStatusText(statusText?: OrderRefundStatus): string {
  if (!statusText) {
    return '-'
  }
  return REFUND_STATUS_TEXT[statusText] || statusText
}

function refundStatusClass(statusText?: OrderRefundStatus): string {
  if (statusText === 'REQUESTED') {
    return 'bg-amber-100 text-amber-700'
  }
  if (statusText === 'SUCCEEDED') {
    return 'bg-emerald-100 text-emerald-700'
  }
  if (statusText === 'PENDING_FUNDS') {
    return 'bg-rose-100 text-rose-700'
  }
  if (statusText === 'REJECTED') {
    return 'bg-stone-200 text-stone-700'
  }
  return 'bg-stone-100 text-stone-700'
}

function applyFilter(): void {
  pager.page = 1
  actionMessage.value = ''
}

function requestReason(title: string, suggested: string): string | null {
  const raw = window.prompt(title, suggested)
  if (raw === null) {
    return null
  }
  const normalized = raw.trim()
  return normalized || undefined
}

async function handleApprove(refund: OrderRefundResponse): Promise<void> {
  const reason = requestReason(`退款 ${refund.refundNo} 审核通过备注（可空）`, '卖家审核通过')
  if (reason === null) {
    return
  }
  actionMessage.value = ''
  try {
    await approveMutation.mutateAsync({ refundNo: refund.refundNo, reason })
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function handleReject(refund: OrderRefundResponse): Promise<void> {
  const reason = requestReason(`退款 ${refund.refundNo} 审核拒绝备注（可空）`, '卖家审核拒绝')
  if (reason === null) {
    return
  }
  actionMessage.value = ''
  try {
    await rejectMutation.mutateAsync({ refundNo: refund.refundNo, reason })
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
      <h1 class="font-display text-2xl text-stone-900">卖家退款审核台</h1>
      <p class="mt-1 text-sm text-stone-600">处理 DELIVERING / FINISHED 订单退款申请，超时会由系统自动同意。</p>
      <button
        type="button"
        class="mt-3 rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
        @click="router.push('/seller/orders')"
      >
        返回卖家订单
      </button>
    </header>

    <section class="rounded-2xl border border-stone-200 bg-white/90 p-4">
      <div class="grid gap-2 md:grid-cols-[1fr_auto_auto]">
        <select
          v-model="status"
          class="rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
        >
          <option value="">全部状态</option>
          <option value="REQUESTED">待审核</option>
          <option value="PENDING_FUNDS">待补款</option>
          <option value="SUCCEEDED">已退款</option>
          <option value="REJECTED">已拒绝</option>
        </select>
        <button
          type="button"
          class="rounded-xl bg-stone-900 px-4 py-2 text-sm text-white transition hover:bg-stone-700"
          @click="applyFilter"
        >
          查询
        </button>
        <button
          type="button"
          class="rounded-xl border border-stone-300 px-4 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
          @click="query.refetch()"
        >
          刷新
        </button>
      </div>
    </section>

    <ResultState :loading="query.isLoading.value" :error="errorMessage" :empty="!query.isLoading.value && items.length === 0" empty-text="暂无退款申请">
      <div class="space-y-3">
        <article v-for="item in items" :key="item.refundNo" class="rounded-2xl border border-stone-200 bg-white/95 p-4">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div class="space-y-1 text-sm text-stone-700">
              <p class="text-xs text-stone-500">退款单号 {{ item.refundNo }}</p>
              <p class="text-xs text-stone-500">订单号 {{ item.orderNo }}</p>
              <p class="font-medium text-stone-900">退款金额 {{ formatMoney(item.amountCent) }}</p>
              <p>申请理由：{{ item.applyReason || '-' }}</p>
              <p>申请时间：{{ formatTime(item.createdAt) }}</p>
              <p>审核截止：{{ formatTime(item.reviewDeadlineAt) }}</p>
              <p>审核时间：{{ formatTime(item.reviewedAt) }}</p>
              <p>拒绝理由：{{ item.rejectReason || '-' }}</p>
              <p v-if="item.lastError" class="text-rose-600">最近错误：{{ item.lastError }}</p>
            </div>

            <div class="flex flex-col items-start gap-2 sm:items-end">
              <span class="rounded-full px-3 py-1 text-xs font-semibold" :class="refundStatusClass(item.status)">
                {{ refundStatusText(item.status) }}
              </span>
              <div class="flex gap-2" v-if="item.status === 'REQUESTED'">
                <button
                  type="button"
                  class="rounded-lg bg-emerald-700 px-3 py-1.5 text-xs text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:opacity-65"
                  :disabled="approveMutation.isPending.value"
                  @click="handleApprove(item)"
                >
                  审核通过
                </button>
                <button
                  type="button"
                  class="rounded-lg bg-rose-700 px-3 py-1.5 text-xs text-white transition hover:bg-rose-600 disabled:cursor-not-allowed disabled:opacity-65"
                  :disabled="rejectMutation.isPending.value"
                  @click="handleReject(item)"
                >
                  审核拒绝
                </button>
              </div>
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

    <p v-if="actionMessage" class="text-sm text-emerald-700">{{ actionMessage }}</p>
    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>
  </section>
</template>
