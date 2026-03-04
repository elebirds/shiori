<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  cancelAdminOrderV2,
  deliverAdminOrderV2,
  finishAdminOrderV2,
  getAdminOrderV2,
  listAdminOrdersV2,
  listAdminOrderStatusAuditsV2,
  type OrderStatus,
  type OrderSummary,
} from '@/api/adminOrderV2'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()
const page = ref(1)
const size = ref(10)
const orderNo = ref('')
const status = ref<OrderStatus | ''>('')
const buyerUserId = ref('')
const sellerUserId = ref('')
const selectedOrderNo = ref<string | null>(null)
const actionError = ref('')
const actionMessage = ref('')

const ordersQuery = useQuery({
  queryKey: computed(() => ['admin-orders-v2', page.value, size.value, orderNo.value, status.value, buyerUserId.value, sellerUserId.value]),
  queryFn: () =>
    listAdminOrdersV2({
      page: page.value,
      size: size.value,
      orderNo: orderNo.value || undefined,
      status: status.value || undefined,
      buyerUserId: buyerUserId.value ? Number(buyerUserId.value) : undefined,
      sellerUserId: sellerUserId.value ? Number(sellerUserId.value) : undefined,
    }),
})

const detailQuery = useQuery({
  queryKey: computed(() => ['admin-order-detail-v2', selectedOrderNo.value]),
  queryFn: () => getAdminOrderV2(selectedOrderNo.value as string),
  enabled: computed(() => selectedOrderNo.value !== null),
})

const timelineQuery = useQuery({
  queryKey: computed(() => ['admin-order-timeline-v2', selectedOrderNo.value]),
  queryFn: () => listAdminOrderStatusAuditsV2(selectedOrderNo.value as string, 1, 50),
  enabled: computed(() => selectedOrderNo.value !== null),
})

const cancelMutation = useMutation({
  mutationFn: (order: OrderSummary) => cancelAdminOrderV2(order.orderNo, '后台手动取消订单'),
  onSuccess: async () => {
    actionError.value = ''
    actionMessage.value = '取消订单成功'
    await queryClient.invalidateQueries({ queryKey: ['admin-orders-v2'] })
    if (selectedOrderNo.value) {
      await detailQuery.refetch()
      await timelineQuery.refetch()
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '取消失败'
  },
})

const deliverMutation = useMutation({
  mutationFn: (order: OrderSummary) => deliverAdminOrderV2(order.orderNo, '后台标记发货'),
  onSuccess: async () => {
    actionError.value = ''
    actionMessage.value = '发货操作成功'
    await queryClient.invalidateQueries({ queryKey: ['admin-orders-v2'] })
    if (selectedOrderNo.value) {
      await detailQuery.refetch()
      await timelineQuery.refetch()
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '发货失败'
  },
})

const finishMutation = useMutation({
  mutationFn: (order: OrderSummary) => finishAdminOrderV2(order.orderNo, '后台标记完成'),
  onSuccess: async () => {
    actionError.value = ''
    actionMessage.value = '完成操作成功'
    await queryClient.invalidateQueries({ queryKey: ['admin-orders-v2'] })
    if (selectedOrderNo.value) {
      await detailQuery.refetch()
      await timelineQuery.refetch()
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '完成失败'
  },
})

const totalPage = computed(() => {
  const total = ordersQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / size.value), 1)
})

function onSearch(): void {
  page.value = 1
  actionMessage.value = ''
}

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
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">订单管理</h1>
      <p class="mt-1 text-sm text-slate-500">支持发货/完成/取消与履约审计时间线查看。</p>
    </div>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-6">
        <input v-model="orderNo" placeholder="orderNo" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="status" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部状态</option>
          <option value="UNPAID">UNPAID</option>
          <option value="PAID">PAID</option>
          <option value="DELIVERING">DELIVERING</option>
          <option value="FINISHED">FINISHED</option>
          <option value="CANCELED">CANCELED</option>
        </select>
        <input v-model="buyerUserId" placeholder="buyerUserId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="sellerUserId" placeholder="sellerUserId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="onSearch">查询</button>
      </div>
    </div>

    <p v-if="actionMessage" class="text-sm text-emerald-700">{{ actionMessage }}</p>
    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-4 py-3 text-left">订单号</th>
              <th class="px-4 py-3 text-left">状态</th>
              <th class="px-4 py-3 text-left">金额</th>
              <th class="px-4 py-3 text-left">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in ordersQuery.data.value?.items || []" :key="order.orderNo" class="border-t border-slate-100">
              <td class="px-4 py-3">
                <button class="text-left text-blue-600 hover:underline" @click="selectedOrderNo = order.orderNo">
                  {{ order.orderNo }}
                </button>
              </td>
              <td class="px-4 py-3">{{ order.status }}</td>
              <td class="px-4 py-3">{{ formatMoney(order.totalAmountCent) }}</td>
              <td class="px-4 py-3">
                <div class="flex flex-wrap gap-1">
                  <button
                    class="rounded bg-slate-900 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="order.status !== 'UNPAID'"
                    @click="cancelMutation.mutate(order)"
                  >
                    取消
                  </button>
                  <button
                    class="rounded bg-indigo-700 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="order.status !== 'PAID'"
                    @click="deliverMutation.mutate(order)"
                  >
                    发货
                  </button>
                  <button
                    class="rounded bg-emerald-700 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="order.status !== 'DELIVERING'"
                    @click="finishMutation.mutate(order)"
                  >
                    完成
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="(ordersQuery.data.value?.items || []).length === 0">
              <td colspan="4" class="px-4 py-10 text-center text-slate-400">暂无数据</td>
            </tr>
          </tbody>
        </table>
        <div class="flex items-center justify-end gap-2 border-t border-slate-100 px-4 py-3 text-sm">
          <button class="rounded border px-2 py-1" :disabled="page <= 1" @click="page -= 1">上一页</button>
          <span>{{ page }} / {{ totalPage }}</span>
          <button class="rounded border px-2 py-1" :disabled="page >= totalPage" @click="page += 1">下一页</button>
        </div>
      </div>

      <aside class="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
        <h2 class="text-lg font-semibold text-slate-900">订单详情与审计</h2>
        <div v-if="detailQuery.data.value" class="space-y-2 text-sm text-slate-700">
          <p>订单号：{{ detailQuery.data.value.orderNo }}</p>
          <p>状态：{{ detailQuery.data.value.status }}</p>
          <p>买家：{{ detailQuery.data.value.buyerUserId }}</p>
          <p>卖家：{{ detailQuery.data.value.sellerUserId }}</p>
          <p>金额：{{ formatMoney(detailQuery.data.value.totalAmountCent) }}</p>
          <p>创建时间：{{ formatTime(detailQuery.data.value.createdAt) }}</p>
          <p>支付时间：{{ formatTime(detailQuery.data.value.paidAt) }}</p>
          <p>条目数：{{ detailQuery.data.value.items.length }}</p>
        </div>
        <p v-else class="text-sm text-slate-400">点击左侧订单查看详情</p>

        <div class="border-t border-slate-200 pt-3">
          <h3 class="text-sm font-semibold text-slate-900">审计时间线</h3>
          <div class="mt-2 max-h-72 space-y-2 overflow-auto">
            <article
              v-for="(item, index) in timelineQuery.data.value?.items || []"
              :key="`${item.createdAt}-${index}`"
              class="rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-700"
            >
              <p class="font-medium">{{ item.fromStatus }} -> {{ item.toStatus }}</p>
              <p class="mt-1">来源：{{ item.source }} / 操作人：{{ item.operatorUserId ?? '-' }}</p>
              <p class="mt-1">时间：{{ formatTime(item.createdAt) }}</p>
              <p class="mt-1">备注：{{ item.reason || '-' }}</p>
            </article>
            <p v-if="(timelineQuery.data.value?.items || []).length === 0" class="text-xs text-slate-400">暂无审计记录</p>
          </div>
        </div>
      </aside>
    </div>
  </section>
</template>

