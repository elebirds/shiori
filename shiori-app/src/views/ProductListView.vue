<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import {
  listProductsV2,
  type ProductCategoryCode,
  type ProductConditionLevel,
  type ProductStatus,
  type ProductSortBy,
  type ProductSortDir,
  type ProductTradeMode,
} from '@/api/productV2'

const router = useRouter()

const page = ref(1)
const size = 12
const keywordInput = ref('')
const keyword = ref('')
const categoryCode = ref<ProductCategoryCode | ''>('')
const conditionLevel = ref<ProductConditionLevel | ''>('')
const tradeMode = ref<ProductTradeMode | ''>('')
const campusCodeInput = ref('')
const campusCode = ref('')
const sortBy = ref<ProductSortBy>('CREATED_AT')
const sortDir = ref<ProductSortDir>('DESC')

const categoryOptions: Array<{ label: string; value: ProductCategoryCode }> = [
  { label: '教材', value: 'TEXTBOOK' },
  { label: '考试资料', value: 'EXAM_MATERIAL' },
  { label: '笔记', value: 'NOTE' },
  { label: '其他', value: 'OTHER' },
]

const conditionOptions: Array<{ label: string; value: ProductConditionLevel }> = [
  { label: '全新', value: 'NEW' },
  { label: '近新', value: 'LIKE_NEW' },
  { label: '良好', value: 'GOOD' },
  { label: '一般', value: 'FAIR' },
]

const tradeModeOptions: Array<{ label: string; value: ProductTradeMode }> = [
  { label: '面交', value: 'MEETUP' },
  { label: '邮寄', value: 'DELIVERY' },
  { label: '均可', value: 'BOTH' },
]

const sortByOptions: Array<{ label: string; value: ProductSortBy }> = [
  { label: '发布时间', value: 'CREATED_AT' },
  { label: '最低价', value: 'MIN_PRICE' },
  { label: '最高价', value: 'MAX_PRICE' },
]

const queryKey = computed(() => [
  'products-v2',
  page.value,
  size,
  keyword.value,
  categoryCode.value,
  conditionLevel.value,
  tradeMode.value,
  campusCode.value,
  sortBy.value,
  sortDir.value,
])

const query = useQuery({
  queryKey,
  queryFn: () =>
    listProductsV2({
      page: page.value,
      size,
      keyword: keyword.value || undefined,
      categoryCode: categoryCode.value || undefined,
      conditionLevel: conditionLevel.value || undefined,
      tradeMode: tradeMode.value || undefined,
      campusCode: campusCode.value || undefined,
      sortBy: sortBy.value,
      sortDir: sortDir.value,
    }),
})

const items = computed(() => query.data.value?.items || [])
const total = computed(() => query.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))

function applyFilter(): void {
  page.value = 1
  keyword.value = keywordInput.value.trim()
  campusCode.value = campusCodeInput.value.trim()
}

function resetFilter(): void {
  keywordInput.value = ''
  campusCodeInput.value = ''
  keyword.value = ''
  campusCode.value = ''
  categoryCode.value = ''
  conditionLevel.value = ''
  tradeMode.value = ''
  sortBy.value = 'CREATED_AT'
  sortDir.value = 'DESC'
  page.value = 1
}

function goDetail(productId: number): void {
  void router.push(`/products/${productId}`)
}

function formatMoney(priceCent?: number): string {
  if (priceCent == null) {
    return '¥ -'
  }
  return `¥ ${(priceCent / 100).toFixed(2)}`
}

function formatPriceRange(minPriceCent?: number, maxPriceCent?: number): string {
  if (minPriceCent == null && maxPriceCent == null) {
    return '价格待定'
  }
  if (minPriceCent === maxPriceCent) {
    return formatMoney(minPriceCent)
  }
  return `${formatMoney(minPriceCent)} ~ ${formatMoney(maxPriceCent)}`
}

function formatCategory(code: ProductCategoryCode): string {
  const match = categoryOptions.find((item) => item.value === code)
  return match?.label || code
}

function formatCondition(code: ProductConditionLevel): string {
  const match = conditionOptions.find((item) => item.value === code)
  return match?.label || code
}

function formatTradeMode(code: ProductTradeMode): string {
  const match = tradeModeOptions.find((item) => item.value === code)
  return match?.label || code
}

function formatStatus(code: ProductStatus): string {
  const map: Record<ProductStatus, string> = {
    DRAFT: '草稿',
    ON_SALE: '在售',
    OFF_SHELF: '已下架',
  }
  return map[code] || code
}
</script>

<template>
  <section class="space-y-5">
    <div class="space-y-4 rounded-3xl border border-amber-200/60 bg-gradient-to-br from-amber-50/80 via-white to-orange-50/60 p-5 shadow-sm">
      <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 class="font-display text-2xl text-stone-900">商品广场</h1>
          <p class="text-sm text-stone-600">支持分类、成色、交易方式和校区筛选</p>
        </div>
        <span class="inline-flex w-fit items-center rounded-full border border-amber-200/70 bg-white/90 px-3 py-1 text-xs font-medium text-amber-700">
          当前 {{ total }} 条商品
        </span>
      </div>

      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <input
          v-model="keywordInput"
          type="text"
          class="rounded-xl border border-stone-300/80 bg-white/90 px-3 py-2 text-sm text-stone-800 shadow-sm outline-none transition placeholder:text-stone-400 focus:border-amber-500 focus:ring-2 focus:ring-amber-200"
          placeholder="关键词"
          @keyup.enter="applyFilter"
        />
        <input
          v-model="campusCodeInput"
          type="text"
          class="rounded-xl border border-stone-300/80 bg-white/90 px-3 py-2 text-sm text-stone-800 shadow-sm outline-none transition placeholder:text-stone-400 focus:border-amber-500 focus:ring-2 focus:ring-amber-200"
          placeholder="交易校区（如主校区）"
          @keyup.enter="applyFilter"
        />
        <select
          v-model="categoryCode"
          class="rounded-xl border border-stone-300/80 bg-white/90 px-3 py-2 text-sm text-stone-800 shadow-sm outline-none transition focus:border-amber-500 focus:ring-2 focus:ring-amber-200"
        >
          <option value="">全部分类</option>
          <option v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <select
          v-model="conditionLevel"
          class="rounded-xl border border-stone-300/80 bg-white/90 px-3 py-2 text-sm text-stone-800 shadow-sm outline-none transition focus:border-amber-500 focus:ring-2 focus:ring-amber-200"
        >
          <option value="">全部成色</option>
          <option v-for="item in conditionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <select
          v-model="tradeMode"
          class="rounded-xl border border-stone-300/80 bg-white/90 px-3 py-2 text-sm text-stone-800 shadow-sm outline-none transition focus:border-amber-500 focus:ring-2 focus:ring-amber-200"
        >
          <option value="">全部交易方式</option>
          <option v-for="item in tradeModeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <select
          v-model="sortBy"
          class="rounded-xl border border-stone-300/80 bg-white/90 px-3 py-2 text-sm text-stone-800 shadow-sm outline-none transition focus:border-amber-500 focus:ring-2 focus:ring-amber-200"
        >
          <option v-for="item in sortByOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <select
          v-model="sortDir"
          class="rounded-xl border border-stone-300/80 bg-white/90 px-3 py-2 text-sm text-stone-800 shadow-sm outline-none transition focus:border-amber-500 focus:ring-2 focus:ring-amber-200"
        >
          <option value="DESC">降序</option>
          <option value="ASC">升序</option>
        </select>
        <div class="flex gap-2">
          <button
            type="button"
            class="flex-1 rounded-xl bg-gradient-to-r from-amber-500 to-orange-500 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:from-amber-600 hover:to-orange-600"
            @click="applyFilter"
          >
            筛选
          </button>
          <button
            type="button"
            class="rounded-xl border border-stone-300/80 bg-white/90 px-4 py-2 text-sm text-stone-700 shadow-sm transition hover:bg-stone-100"
            @click="resetFilter"
          >
            重置
          </button>
        </div>
      </div>
      <p v-if="query.isFetching.value" class="text-xs text-amber-700/90">正在刷新筛选结果...</p>
    </div>

    <ResultState :loading="query.isLoading.value" :error="errorMessage" :empty="!query.isLoading.value && items.length === 0" empty-text="暂无上架商品">
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <article
          v-for="item in items"
          :key="item.productId"
          class="group cursor-pointer rounded-2xl border border-stone-200 bg-white/95 p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
          @click="goDetail(item.productId)"
        >
          <div class="aspect-[4/3] overflow-hidden rounded-xl bg-stone-100">
            <img
              v-if="item.coverImageUrl"
              :src="item.coverImageUrl"
              :alt="item.title"
              class="h-full w-full object-cover transition duration-300 group-hover:scale-105"
            />
            <div v-else class="flex h-full items-center justify-center text-sm text-stone-500">暂无封面</div>
          </div>

          <h2 class="mt-3 line-clamp-1 text-base font-semibold text-stone-900">{{ item.title }}</h2>
          <p class="mt-1 line-clamp-2 text-sm text-stone-600">{{ item.description || '暂无描述' }}</p>

          <div class="mt-3 flex flex-wrap gap-1 text-xs text-stone-600">
            <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCategory(item.categoryCode) }}</span>
            <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatCondition(item.conditionLevel) }}</span>
            <span class="rounded-full bg-stone-100 px-2 py-1">{{ formatTradeMode(item.tradeMode) }}</span>
            <span class="rounded-full bg-stone-100 px-2 py-1">{{ item.campusCode }}</span>
          </div>

          <div class="mt-3 flex items-center justify-between text-sm">
            <span class="font-semibold text-stone-900">{{ formatPriceRange(item.minPriceCent, item.maxPriceCent) }}</span>
            <span class="text-xs text-stone-500">库存 {{ item.totalStock ?? 0 }}</span>
          </div>

          <div class="mt-2 flex items-center justify-between text-xs text-stone-500">
            <span>{{ formatStatus(item.status) }}</span>
            <span>编号 {{ item.productNo }}</span>
          </div>
        </article>
      </div>

      <div class="flex flex-col gap-3 rounded-2xl border border-amber-200/60 bg-white/95 px-4 py-3 text-sm shadow-sm sm:flex-row sm:items-center sm:justify-between">
        <div class="flex flex-wrap items-center gap-2 text-stone-600">
          <span class="rounded-full bg-amber-50 px-2.5 py-1 text-xs text-amber-700">第 {{ page }} / {{ totalPages }} 页</span>
          <span class="rounded-full bg-stone-100 px-2.5 py-1 text-xs">共 {{ total }} 条</span>
        </div>
        <div class="flex gap-2 self-end sm:self-auto">
          <button
            type="button"
            class="rounded-lg border border-stone-300/80 bg-white px-3 py-1.5 text-stone-700 shadow-sm transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="page <= 1 || query.isFetching.value"
            @click="page -= 1"
          >
            上一页
          </button>
          <button
            type="button"
            class="rounded-lg border border-stone-300/80 bg-white px-3 py-1.5 text-stone-700 shadow-sm transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="page >= totalPages || query.isFetching.value"
            @click="page += 1"
          >
            下一页
          </button>
        </div>
      </div>
    </ResultState>
  </section>
</template>
