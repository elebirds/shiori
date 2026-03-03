<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { cancelAdminOrder, getAdminOrder, listAdminOrders, type OrderSummary } from '@/api/adminOrder'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()
const page = ref(1)
const size = ref(10)
const orderNo = ref('')
const status = ref('')
const buyerUserId = ref('')
const sellerUserId = ref('')
const selectedOrderNo = ref<string | null>(null)
const actionError = ref('')

const ordersQuery = useQuery({
  queryKey: computed(() => ['admin-orders', page.value, size.value, orderNo.value, status.value, buyerUserId.value, sellerUserId.value]),
  queryFn: () =>
    listAdminOrders({
      page: page.value,
      size: size.value,
      orderNo: orderNo.value || undefined,
      status: status.value || undefined,
      buyerUserId: buyerUserId.value ? Number(buyerUserId.value) : undefined,
      sellerUserId: sellerUserId.value ? Number(sellerUserId.value) : undefined,
    }),
})

const detailQuery = useQuery({
  queryKey: computed(() => ['admin-order-detail', selectedOrderNo.value]),
  queryFn: () => getAdminOrder(selectedOrderNo.value as string),
  enabled: computed(() => selectedOrderNo.value !== null),
})

const cancelMutation = useMutation({
  mutationFn: (order: OrderSummary) => cancelAdminOrder(order.orderNo, '后台手动取消未支付订单'),
  onSuccess: async () => {
    actionError.value = ''
    await queryClient.invalidateQueries({ queryKey: ['admin-orders'] })
    if (selectedOrderNo.value) {
      await queryClient.invalidateQueries({ queryKey: ['admin-order-detail', selectedOrderNo.value] })
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '操作失败'
  },
})

const totalPage = computed(() => {
  const total = ordersQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / size.value), 1)
})

function onSearch() {
  page.value = 1
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">订单管理</h1>
      <p class="mt-1 text-sm text-slate-500">检索订单并手动取消未支付订单。</p>
    </div>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-6">
        <input v-model="orderNo" placeholder="orderNo" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="status" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部状态</option>
          <option value="UNPAID">UNPAID</option>
          <option value="PAID">PAID</option>
          <option value="CANCELED">CANCELED</option>
        </select>
        <input v-model="buyerUserId" placeholder="buyerUserId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="sellerUserId" placeholder="sellerUserId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="onSearch">查询</button>
      </div>
    </div>

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
              <td class="px-4 py-3">¥{{ (order.totalAmountCent / 100).toFixed(2) }}</td>
              <td class="px-4 py-3">
                <button
                  class="rounded bg-slate-900 px-2 py-1 text-xs text-white"
                  :disabled="order.status !== 'UNPAID'"
                  @click="cancelMutation.mutate(order)"
                >
                  取消订单
                </button>
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

      <aside class="rounded-xl border border-slate-200 bg-white p-4">
        <h2 class="text-lg font-semibold text-slate-900">订单详情</h2>
        <div v-if="detailQuery.data.value" class="mt-3 space-y-2 text-sm text-slate-700">
          <p>订单号：{{ detailQuery.data.value.orderNo }}</p>
          <p>状态：{{ detailQuery.data.value.status }}</p>
          <p>买家：{{ detailQuery.data.value.buyerUserId }}</p>
          <p>卖家：{{ detailQuery.data.value.sellerUserId }}</p>
          <p>条目数：{{ detailQuery.data.value.items.length }}</p>
        </div>
        <p v-else class="mt-3 text-sm text-slate-400">点击左侧订单查看详情</p>
      </aside>
    </div>
  </section>
</template>
