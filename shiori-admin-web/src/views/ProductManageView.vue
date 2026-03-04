<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  batchOffShelfV2,
  forceOffShelfV2,
  getAdminProductV2,
  listAdminProductsV2,
  type AdminProductSummaryV2,
  type ProductCategoryCode,
  type ProductConditionLevel,
  type ProductSortBy,
  type ProductSortDir,
  type ProductStatus,
  type ProductTradeMode,
} from '@/api/adminProductV2'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const status = ref<ProductStatus | ''>('')
const ownerUserId = ref('')
const categoryCode = ref<ProductCategoryCode | ''>('')
const conditionLevel = ref<ProductConditionLevel | ''>('')
const tradeMode = ref<ProductTradeMode | ''>('')
const campusCode = ref('')
const sortBy = ref<ProductSortBy>('CREATED_AT')
const sortDir = ref<ProductSortDir>('DESC')
const selectedProductId = ref<number | null>(null)
const selectedIds = ref<number[]>([])
const actionError = ref('')
const actionMessage = ref('')

const productsQuery = useQuery({
  queryKey: computed(() => [
    'admin-products-v2',
    page.value,
    size.value,
    keyword.value,
    status.value,
    ownerUserId.value,
    categoryCode.value,
    conditionLevel.value,
    tradeMode.value,
    campusCode.value,
    sortBy.value,
    sortDir.value,
  ]),
  queryFn: () =>
    listAdminProductsV2({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      ownerUserId: ownerUserId.value ? Number(ownerUserId.value) : undefined,
      categoryCode: categoryCode.value || undefined,
      conditionLevel: conditionLevel.value || undefined,
      tradeMode: tradeMode.value || undefined,
      campusCode: campusCode.value || undefined,
      sortBy: sortBy.value,
      sortDir: sortDir.value,
    }),
})

const detailQuery = useQuery({
  queryKey: computed(() => ['admin-product-detail-v2', selectedProductId.value]),
  queryFn: () => getAdminProductV2(selectedProductId.value as number),
  enabled: computed(() => selectedProductId.value !== null),
})

watch(
  () => productsQuery.data.value?.items || [],
  (items) => {
    const idSet = new Set(items.map((item) => item.productId))
    selectedIds.value = selectedIds.value.filter((id) => idSet.has(id))
  },
)

const offShelfMutation = useMutation({
  mutationFn: (product: AdminProductSummaryV2) => forceOffShelfV2(product.productId, '后台强制下架'),
  onSuccess: async () => {
    actionError.value = ''
    actionMessage.value = '已执行强制下架'
    await queryClient.invalidateQueries({ queryKey: ['admin-products-v2'] })
    if (selectedProductId.value != null) {
      await queryClient.invalidateQueries({ queryKey: ['admin-product-detail-v2', selectedProductId.value] })
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '操作失败'
  },
})

const batchOffShelfMutation = useMutation({
  mutationFn: () => batchOffShelfV2(selectedIds.value, '后台批量强制下架'),
  onSuccess: async (result) => {
    actionError.value = ''
    actionMessage.value = `批量完成：${result.successCount}/${result.total}`
    selectedIds.value = []
    await queryClient.invalidateQueries({ queryKey: ['admin-products-v2'] })
    if (selectedProductId.value != null) {
      await queryClient.invalidateQueries({ queryKey: ['admin-product-detail-v2', selectedProductId.value] })
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '批量下架失败'
  },
})

const totalPage = computed(() => {
  const total = productsQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / size.value), 1)
})

const currentItems = computed(() => productsQuery.data.value?.items || [])
const allChecked = computed({
  get: () => currentItems.value.length > 0 && currentItems.value.every((item) => selectedIds.value.includes(item.productId)),
  set: (checked: boolean) => {
    if (checked) {
      selectedIds.value = currentItems.value.map((item) => item.productId)
      return
    }
    selectedIds.value = []
  },
})

function onSearch(): void {
  page.value = 1
  actionMessage.value = ''
}

function toggleSelect(productId: number): void {
  if (selectedIds.value.includes(productId)) {
    selectedIds.value = selectedIds.value.filter((id) => id !== productId)
  } else {
    selectedIds.value = [...selectedIds.value, productId]
  }
}

function formatMoney(priceCent?: number): string {
  if (priceCent == null) {
    return '-'
  }
  return `¥${(priceCent / 100).toFixed(2)}`
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">商品管理</h1>
      <p class="mt-1 text-sm text-slate-500">v2 筛选 + 强制下架 + 批量下架。</p>
    </div>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-5 lg:grid-cols-6">
        <input v-model="keyword" placeholder="标题/描述/商品编号" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="status" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部状态</option>
          <option value="DRAFT">DRAFT</option>
          <option value="ON_SALE">ON_SALE</option>
          <option value="OFF_SHELF">OFF_SHELF</option>
        </select>
        <input v-model="ownerUserId" placeholder="ownerUserId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="categoryCode" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部分类</option>
          <option value="TEXTBOOK">TEXTBOOK</option>
          <option value="EXAM_MATERIAL">EXAM_MATERIAL</option>
          <option value="NOTE">NOTE</option>
          <option value="OTHER">OTHER</option>
        </select>
        <select v-model="conditionLevel" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部成色</option>
          <option value="NEW">NEW</option>
          <option value="LIKE_NEW">LIKE_NEW</option>
          <option value="GOOD">GOOD</option>
          <option value="FAIR">FAIR</option>
        </select>
        <select v-model="tradeMode" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部交易</option>
          <option value="MEETUP">MEETUP</option>
          <option value="DELIVERY">DELIVERY</option>
          <option value="BOTH">BOTH</option>
        </select>
        <input v-model="campusCode" placeholder="campusCode" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="sortBy" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="CREATED_AT">CREATED_AT</option>
          <option value="MIN_PRICE">MIN_PRICE</option>
          <option value="MAX_PRICE">MAX_PRICE</option>
        </select>
        <select v-model="sortDir" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="DESC">DESC</option>
          <option value="ASC">ASC</option>
        </select>
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="onSearch">查询</button>
      </div>
    </div>

    <div class="flex flex-wrap items-center gap-3">
      <button
        class="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="selectedIds.length === 0 || batchOffShelfMutation.isPending.value"
        @click="batchOffShelfMutation.mutate()"
      >
        批量下架（{{ selectedIds.length }}）
      </button>
      <p v-if="actionMessage" class="text-sm text-emerald-700">{{ actionMessage }}</p>
      <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>
    </div>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-4 py-3 text-left">
                <input v-model="allChecked" type="checkbox" />
              </th>
              <th class="px-4 py-3 text-left">商品</th>
              <th class="px-4 py-3 text-left">状态</th>
              <th class="px-4 py-3 text-left">价格区间</th>
              <th class="px-4 py-3 text-left">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="product in productsQuery.data.value?.items || []" :key="product.productId" class="border-t border-slate-100">
              <td class="px-4 py-3">
                <input :checked="selectedIds.includes(product.productId)" type="checkbox" @change="toggleSelect(product.productId)" />
              </td>
              <td class="px-4 py-3">
                <button class="text-left text-blue-600 hover:underline" @click="selectedProductId = product.productId">
                  {{ product.title }}
                </button>
                <p class="text-xs text-slate-500">{{ product.productNo }}</p>
                <p class="text-xs text-slate-500">
                  {{ product.categoryCode }} / {{ product.conditionLevel }} / {{ product.tradeMode }} / {{ product.campusCode }}
                </p>
              </td>
              <td class="px-4 py-3">{{ product.status }}</td>
              <td class="px-4 py-3">{{ formatMoney(product.minPriceCent) }} ~ {{ formatMoney(product.maxPriceCent) }}</td>
              <td class="px-4 py-3">
                <button
                  class="rounded bg-slate-900 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="product.status === 'OFF_SHELF'"
                  @click="offShelfMutation.mutate(product)"
                >
                  强制下架
                </button>
              </td>
            </tr>
            <tr v-if="(productsQuery.data.value?.items || []).length === 0">
              <td colspan="5" class="px-4 py-10 text-center text-slate-400">暂无数据</td>
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
          <p>简介：{{ detailQuery.data.value.description || '暂无简介' }}</p>
          <p>状态：{{ detailQuery.data.value.status }}</p>
          <p>owner：{{ detailQuery.data.value.ownerUserId }}</p>
          <p>
            分类：{{ detailQuery.data.value.categoryCode }} / {{ detailQuery.data.value.conditionLevel }} /
            {{ detailQuery.data.value.tradeMode }}
          </p>
          <p>校区：{{ detailQuery.data.value.campusCode }}</p>
          <p>价格区间：{{ formatMoney(detailQuery.data.value.minPriceCent) }} ~ {{ formatMoney(detailQuery.data.value.maxPriceCent) }}</p>
          <p>总库存：{{ detailQuery.data.value.totalStock ?? 0 }}</p>
          <p>SKU 数：{{ detailQuery.data.value.skus.length }}</p>
          <div class="space-y-1">
            <p class="font-medium text-slate-800">详情：</p>
            <div v-if="detailQuery.data.value.detailHtml" class="rich-content rounded-md border border-slate-200 bg-slate-50 p-2" v-html="detailQuery.data.value.detailHtml" />
            <p v-else class="text-slate-500">暂无详情</p>
          </div>
        </div>
        <p v-else class="mt-3 text-sm text-slate-400">点击左侧商品查看详情</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.rich-content :deep(img) {
  max-width: 100%;
  border-radius: 0.375rem;
}

.rich-content :deep(ul),
.rich-content :deep(ol) {
  margin-left: 0;
  padding-left: 1.5rem;
  list-style-position: outside;
}

.rich-content :deep(ul) {
  list-style: disc;
}

.rich-content :deep(ol) {
  list-style: decimal;
}

.rich-content :deep(li) {
  margin: 0.25rem 0;
}

.rich-content :deep(h1) {
  margin: 0.75rem 0 0.5rem;
  font-size: 1.375rem;
  font-weight: 700;
}

.rich-content :deep(h2) {
  margin: 0.75rem 0 0.5rem;
  font-size: 1.2rem;
  font-weight: 700;
}

.rich-content :deep(h3) {
  margin: 0.75rem 0 0.5rem;
  font-size: 1.05rem;
  font-weight: 600;
}

.rich-content :deep(blockquote) {
  margin: 0.75rem 0;
  border-left: 3px solid #cbd5e1;
  padding-left: 0.75rem;
  color: #475569;
}

.rich-content :deep(a) {
  color: #2563eb;
  text-decoration: underline;
  word-break: break-all;
}

.rich-content :deep(s) {
  text-decoration: line-through;
}

.rich-content :deep(hr) {
  margin: 0.75rem 0;
  border: 0;
  border-top: 1px solid #cbd5e1;
}
</style>
