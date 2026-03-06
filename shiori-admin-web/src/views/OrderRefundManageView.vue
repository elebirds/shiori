<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'

import {
  listAdminOrderRefundsV2,
  retryAdminOrderRefundV2,
  type OrderRefundResponse,
  type OrderRefundStatus,
} from '@/api/adminOrderV2'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()

const page = ref(1)
const size = ref(10)
const refundNoInput = ref('')
const orderNoInput = ref('')
const statusInput = ref<OrderRefundStatus | ''>('')
const buyerUserIdInput = ref('')
const sellerUserIdInput = ref('')
const refundNo = ref('')
const orderNo = ref('')
const status = ref<OrderRefundStatus | ''>('')
const buyerUserId = ref('')
const sellerUserId = ref('')
const actionMessage = ref('')
const actionError = ref('')

const query = useQuery({
  queryKey: computed(() => [
    'admin-order-refunds-v2',
    page.value,
    size.value,
    refundNo.value,
    orderNo.value,
    status.value,
    buyerUserId.value,
    sellerUserId.value,
  ]),
  queryFn: () =>
    listAdminOrderRefundsV2({
      page: page.value,
      size: size.value,
      refundNo: refundNo.value || undefined,
      orderNo: orderNo.value || undefined,
      status: status.value || undefined,
      buyerUserId: parseOptionalPositiveInt(buyerUserId.value),
      sellerUserId: parseOptionalPositiveInt(sellerUserId.value),
    }),
})

const retryMutation = useMutation({
  mutationFn: (targetRefundNo: string) => retryAdminOrderRefundV2(targetRefundNo),
  onSuccess: async () => {
    actionError.value = ''
    actionMessage.value = '重试请求已提交'
    await queryClient.invalidateQueries({ queryKey: ['admin-order-refunds-v2'] })
    await queryClient.invalidateQueries({ queryKey: ['admin-orders-v2'] })
  },
  onError: (error) => {
    actionMessage.value = ''
    actionError.value = error instanceof ApiBizError ? error.message : '重试失败'
  },
})

const REFUND_STATUS_TEXT: Record<OrderRefundStatus, string> = {
  REQUESTED: '待审核',
  REJECTED: '已拒绝',
  PENDING_FUNDS: '待补款',
  SUCCEEDED: '已退款',
}

const totalPage = computed(() => {
  const total = query.data.value?.total || 0
  return Math.max(Math.ceil(total / size.value), 1)
})

function formatMoney(amountCent: number): string {
  return `¥${(amountCent / 100).toFixed(2)}`
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

function refundStatusText(statusText: OrderRefundStatus): string {
  return REFUND_STATUS_TEXT[statusText] || statusText
}

function refundStatusClass(statusText: OrderRefundStatus): string {
  if (statusText === 'REQUESTED') {
    return 'bg-amber-100 text-amber-700'
  }
  if (statusText === 'SUCCEEDED') {
    return 'bg-emerald-100 text-emerald-700'
  }
  if (statusText === 'PENDING_FUNDS') {
    return 'bg-rose-100 text-rose-700'
  }
  return 'bg-slate-200 text-slate-700'
}

function onSearch(): void {
  page.value = 1
  actionMessage.value = ''
  refundNo.value = refundNoInput.value.trim()
  orderNo.value = orderNoInput.value.trim()
  status.value = statusInput.value
  buyerUserId.value = buyerUserIdInput.value.trim()
  sellerUserId.value = sellerUserIdInput.value.trim()
}

function parseOptionalPositiveInt(raw: string): number | undefined {
  const normalized = raw.trim()
  if (!normalized) {
    return undefined
  }
  const parsed = Number(normalized)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return undefined
  }
  return Math.floor(parsed)
}

async function handleRetry(item: OrderRefundResponse): Promise<void> {
  actionMessage.value = ''
  actionError.value = ''
  try {
    await retryMutation.mutateAsync(item.refundNo)
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">退款管理</h1>
      <p class="mt-1 text-sm text-slate-500">查看退款全链路状态，并对挂起退款执行人工重试。</p>
    </div>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-7">
        <input v-model="refundNoInput" placeholder="refundNo" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="orderNoInput" placeholder="orderNo" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="statusInput" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部状态</option>
          <option value="REQUESTED">REQUESTED</option>
          <option value="PENDING_FUNDS">PENDING_FUNDS</option>
          <option value="SUCCEEDED">SUCCEEDED</option>
          <option value="REJECTED">REJECTED</option>
        </select>
        <input v-model="buyerUserIdInput" placeholder="buyerUserId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="sellerUserIdInput" placeholder="sellerUserId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="onSearch">查询</button>
      </div>
    </div>

    <p v-if="actionMessage" class="text-sm text-emerald-700">{{ actionMessage }}</p>
    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>

    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-slate-600">
          <tr>
            <th class="px-4 py-3 text-left">退款单号</th>
            <th class="px-4 py-3 text-left">订单号</th>
            <th class="px-4 py-3 text-left">状态</th>
            <th class="px-4 py-3 text-left">金额</th>
            <th class="px-4 py-3 text-left">买家/卖家</th>
            <th class="px-4 py-3 text-left">时效</th>
            <th class="px-4 py-3 text-left">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in query.data.value?.items || []" :key="item.refundNo" class="border-t border-slate-100 align-top">
            <td class="px-4 py-3">
              <p class="font-medium text-slate-900">{{ item.refundNo }}</p>
              <p class="mt-1 text-xs text-slate-500">创建 {{ formatTime(item.createdAt) }}</p>
              <p class="mt-1 text-xs text-slate-500">更新 {{ formatTime(item.updatedAt) }}</p>
            </td>
            <td class="px-4 py-3">
              <p class="font-medium text-slate-900">{{ item.orderNo }}</p>
              <p class="mt-1 text-xs text-slate-500">支付单 {{ item.paymentNo || '-' }}</p>
            </td>
            <td class="px-4 py-3">
              <span class="rounded-full px-2 py-1 text-xs font-semibold" :class="refundStatusClass(item.status)">
                {{ refundStatusText(item.status) }}
              </span>
              <p class="mt-2 text-xs text-slate-500">自动同意：{{ item.autoApproved ? '是' : '否' }}</p>
            </td>
            <td class="px-4 py-3">
              <p class="font-medium text-slate-900">{{ formatMoney(item.amountCent) }}</p>
              <p class="mt-1 text-xs text-slate-500">重试 {{ item.retryCount || 0 }} 次</p>
            </td>
            <td class="px-4 py-3">
              <p class="text-xs text-slate-700">审核人 {{ item.reviewedByUserId ?? '-' }}</p>
              <p class="mt-1 text-xs text-slate-500">申请：{{ item.applyReason || '-' }}</p>
              <p class="mt-1 text-xs text-slate-500">拒绝：{{ item.rejectReason || '-' }}</p>
              <p v-if="item.lastError" class="mt-1 text-xs text-rose-600">错误：{{ item.lastError }}</p>
            </td>
            <td class="px-4 py-3">
              <p class="text-xs text-slate-500">截止 {{ formatTime(item.reviewDeadlineAt) }}</p>
              <p class="mt-1 text-xs text-slate-500">审核 {{ formatTime(item.reviewedAt) }}</p>
            </td>
            <td class="px-4 py-3">
              <button
                class="rounded bg-indigo-700 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="item.status !== 'PENDING_FUNDS' || retryMutation.isPending.value"
                @click="handleRetry(item)"
              >
                重试退款
              </button>
            </td>
          </tr>
          <tr v-if="(query.data.value?.items || []).length === 0 && !query.isLoading.value">
            <td colspan="7" class="px-4 py-10 text-center text-slate-400">暂无数据</td>
          </tr>
        </tbody>
      </table>
      <div class="flex items-center justify-end gap-2 border-t border-slate-100 px-4 py-3 text-sm">
        <button class="rounded border px-2 py-1" :disabled="page <= 1" @click="page -= 1">上一页</button>
        <span>{{ page }} / {{ totalPage }}</span>
        <button class="rounded border px-2 py-1" :disabled="page >= totalPage" @click="page += 1">下一页</button>
      </div>
    </div>
  </section>
</template>
