<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { forceOffShelf, getAdminProduct, listAdminProducts, type ProductSummary } from '@/api/adminProduct'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const status = ref('')
const ownerUserId = ref('')
const selectedProductId = ref<number | null>(null)
const actionError = ref('')

const productsQuery = useQuery({
  queryKey: computed(() => ['admin-products', page.value, size.value, keyword.value, status.value, ownerUserId.value]),
  queryFn: () =>
    listAdminProducts({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      ownerUserId: ownerUserId.value ? Number(ownerUserId.value) : undefined,
    }),
})

const detailQuery = useQuery({
  queryKey: computed(() => ['admin-product-detail', selectedProductId.value]),
  queryFn: () => getAdminProduct(selectedProductId.value as number),
  enabled: computed(() => selectedProductId.value !== null),
})

const offShelfMutation = useMutation({
  mutationFn: (product: ProductSummary) => forceOffShelf(product.productId, '后台强制下架'),
  onSuccess: async () => {
    actionError.value = ''
    await queryClient.invalidateQueries({ queryKey: ['admin-products'] })
    if (selectedProductId.value != null) {
      await queryClient.invalidateQueries({ queryKey: ['admin-product-detail', selectedProductId.value] })
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '操作失败'
  },
})

const totalPage = computed(() => {
  const total = productsQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / size.value), 1)
})

function onSearch() {
  page.value = 1
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">商品管理</h1>
      <p class="mt-1 text-sm text-slate-500">按状态和卖家检索商品，支持后台强制下架。</p>
    </div>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-5">
        <input v-model="keyword" placeholder="标题/描述/商品编号" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="status" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部状态</option>
          <option value="DRAFT">DRAFT</option>
          <option value="ON_SALE">ON_SALE</option>
          <option value="OFF_SHELF">OFF_SHELF</option>
        </select>
        <input v-model="ownerUserId" placeholder="ownerUserId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="onSearch">查询</button>
      </div>
    </div>

    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-4 py-3 text-left">商品</th>
              <th class="px-4 py-3 text-left">状态</th>
              <th class="px-4 py-3 text-left">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="product in productsQuery.data.value?.items || []" :key="product.productId" class="border-t border-slate-100">
              <td class="px-4 py-3">
                <button class="text-left text-blue-600 hover:underline" @click="selectedProductId = product.productId">
                  {{ product.title }}
                </button>
                <p class="text-xs text-slate-500">{{ product.productNo }}</p>
              </td>
              <td class="px-4 py-3">{{ product.status }}</td>
              <td class="px-4 py-3">
                <button
                  class="rounded bg-slate-900 px-2 py-1 text-xs text-white"
                  :disabled="product.status === 'OFF_SHELF'"
                  @click="offShelfMutation.mutate(product)"
                >
                  强制下架
                </button>
              </td>
            </tr>
            <tr v-if="(productsQuery.data.value?.items || []).length === 0">
              <td colspan="3" class="px-4 py-10 text-center text-slate-400">暂无数据</td>
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
        <h2 class="text-lg font-semibold text-slate-900">商品详情</h2>
        <div v-if="detailQuery.data.value" class="mt-3 space-y-2 text-sm text-slate-700">
          <p>标题：{{ detailQuery.data.value.title }}</p>
          <p>状态：{{ detailQuery.data.value.status }}</p>
          <p>owner：{{ detailQuery.data.value.ownerUserId }}</p>
          <p>SKU 数：{{ detailQuery.data.value.skus.length }}</p>
        </div>
        <p v-else class="mt-3 text-sm text-slate-400">点击左侧商品查看详情</p>
      </aside>
    </div>
  </section>
</template>
