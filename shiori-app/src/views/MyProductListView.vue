<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import {
  listMyProductsV2,
  offShelfProductV2,
  publishProductV2,
  type ProductCategoryCode,
  type ProductConditionLevel,
  type ProductStatus,
  type ProductTradeMode,
} from '@/api/productV2'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()
const router = useRouter()

const pager = reactive({
  page: 1,
  size: 10,
})
const keywordInput = ref('')
const keyword = ref('')
const campusCodeInput = ref('')
const campusCode = ref('')
const status = ref<'ALL' | ProductStatus>('ALL')
const categoryCode = ref<ProductCategoryCode | ''>('')
const conditionLevel = ref<ProductConditionLevel | ''>('')
const tradeMode = ref<ProductTradeMode | ''>('')

const query = useQuery({
  queryKey: computed(() => [
    'my-products-v2',
    pager.page,
    pager.size,
    keyword.value,
    status.value,
    categoryCode.value,
    conditionLevel.value,
    tradeMode.value,
    campusCode.value,
  ]),
  queryFn: () =>
    listMyProductsV2({
      page: pager.page,
      size: pager.size,
      keyword: keyword.value || undefined,
      status: status.value === 'ALL' ? undefined : status.value,
      categoryCode: categoryCode.value || undefined,
      conditionLevel: conditionLevel.value || undefined,
      tradeMode: tradeMode.value || undefined,
      campusCode: campusCode.value || undefined,
    }),
})

const publishMutation = useMutation({
  mutationFn: (productId: number) => publishProductV2(productId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['my-products-v2'] })
  },
})

const offShelfMutation = useMutation({
  mutationFn: (productId: number) => offShelfProductV2(productId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['my-products-v2'] })
  },
})

const items = computed(() => query.data.value?.items || [])
const total = computed(() => query.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pager.size)))
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))
const mobileFilterOpen = ref(false)
const isMobileViewport = ref(false)

let mobileMediaQuery: MediaQueryList | null = null

function syncMobileViewport(): void {
  if (!mobileMediaQuery) {
    return
  }
  isMobileViewport.value = mobileMediaQuery.matches
  if (!isMobileViewport.value) {
    mobileFilterOpen.value = false
  }
}

onMounted(() => {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return
  }
  mobileMediaQuery = window.matchMedia('(max-width: 767px)')
  syncMobileViewport()
  mobileMediaQuery.addEventListener('change', syncMobileViewport)
})

onBeforeUnmount(() => {
  mobileMediaQuery?.removeEventListener('change', syncMobileViewport)
  mobileMediaQuery = null
})

const operateError = computed(() => {
  const first = publishMutation.error.value || offShelfMutation.error.value
  return first instanceof Error ? first.message : ''
})

const PRODUCT_STATUS_TEXT: Record<ProductStatus, string> = {
  DRAFT: '草稿',
  ON_SALE: '在售',
  OFF_SHELF: '已下架',
}

const CATEGORY_TEXT: Record<ProductCategoryCode, string> = {
  TEXTBOOK: '教材',
  EXAM_MATERIAL: '考试资料',
  NOTE: '笔记',
  OTHER: '其他',
}

const CONDITION_TEXT: Record<ProductConditionLevel, string> = {
  NEW: '全新',
  LIKE_NEW: '近新',
  GOOD: '良好',
  FAIR: '一般',
}

const TRADE_MODE_TEXT: Record<ProductTradeMode, string> = {
  MEETUP: '面交',
  DELIVERY: '邮寄',
  BOTH: '均可',
}

function applyFilter(): void {
  pager.page = 1
  keyword.value = keywordInput.value.trim()
  campusCode.value = campusCodeInput.value.trim()
  mobileFilterOpen.value = false
}

function resetFilter(): void {
  keywordInput.value = ''
  keyword.value = ''
  campusCodeInput.value = ''
  campusCode.value = ''
  status.value = 'ALL'
  categoryCode.value = ''
  conditionLevel.value = ''
  tradeMode.value = ''
  pager.page = 1
  mobileFilterOpen.value = false
}

function statusText(statusValue: ProductStatus): string {
  return PRODUCT_STATUS_TEXT[statusValue] || statusValue
}

function categoryText(value: ProductCategoryCode): string {
  return CATEGORY_TEXT[value] || value
}

function conditionText(value: ProductConditionLevel): string {
  return CONDITION_TEXT[value] || value
}

function tradeModeText(value: ProductTradeMode): string {
  return TRADE_MODE_TEXT[value] || value
}

function toEdit(productId: number): void {
  void router.push(`/my-products/${productId}/edit`)
}

function toCreate(): void {
  void router.push('/sell')
}

async function handlePublish(productId: number): Promise<void> {
  try {
    await publishMutation.mutateAsync(productId)
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function handleOffShelf(productId: number): Promise<void> {
  try {
    await offShelfMutation.mutateAsync(productId)
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
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 class="font-display text-2xl text-stone-900">我的商品</h1>
          <p class="mt-1 text-sm text-stone-600">可按分类、成色和交易方式筛选，并快速编辑上下架。</p>
        </div>
        <button
          type="button"
          class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700"
          @click="toCreate"
        >
          新建商品
        </button>
      </div>
    </header>

    <section class="rounded-2xl border border-stone-200 bg-white/90 p-4 md:hidden">
      <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <p class="text-sm font-medium text-stone-700">筛选条件</p>
        <span class="inline-flex w-fit items-center rounded-full border border-amber-200/70 bg-white/90 px-3 py-1 text-xs text-amber-700">
          当前 {{ total }} 条
        </span>
      </div>
      <button type="button" class="mt-3 w-full rounded-xl bg-gradient-to-r from-amber-500 to-orange-500 px-4 py-2 text-sm font-medium text-white" @click="mobileFilterOpen = true">
        打开筛选面板
      </button>
    </section>

    <section class="hidden space-y-3 md:block shiori-filter-shell shiori-filter-shell-amber">
      <div class="flex items-center justify-between">
        <p class="text-sm font-medium text-stone-700">筛选条件</p>
        <span class="inline-flex w-fit items-center rounded-full border border-amber-200/70 bg-white/90 px-3 py-1 text-xs text-amber-700">
          当前 {{ total }} 条
        </span>
      </div>
      <div class="shiori-filter-fields">
        <div class="shiori-filter-field">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="m21 21-4.35-4.35M10.8 18a7.2 7.2 0 1 0 0-14.4 7.2 7.2 0 0 0 0 14.4Z" /></svg>
          <input v-model="keywordInput" type="text" class="shiori-filter-control shiori-filter-control-w-lg" placeholder="按标题/描述搜索" @keyup.enter="applyFilter" />
        </div>
        <div class="shiori-filter-field">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M12 21s7-5.2 7-11a7 7 0 1 0-14 0c0 5.8 7 11 7 11Z" /><circle cx="12" cy="10" r="2.5" /></svg>
          <input v-model="campusCodeInput" type="text" class="shiori-filter-control shiori-filter-control-w-md" placeholder="交易校区" @keyup.enter="applyFilter" />
        </div>
        <div class="shiori-filter-field">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M4 8h16M4 16h16" /></svg>
          <select v-model="status" class="shiori-filter-control shiori-filter-control-w-sm">
            <option value="ALL">全部状态</option>
            <option value="DRAFT">草稿</option>
            <option value="ON_SALE">在售</option>
            <option value="OFF_SHELF">已下架</option>
          </select>
        </div>
        <div class="shiori-filter-field">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
          <select v-model="categoryCode" class="shiori-filter-control shiori-filter-control-w-sm">
            <option value="">全部分类</option>
            <option value="TEXTBOOK">教材</option>
            <option value="EXAM_MATERIAL">考试资料</option>
            <option value="NOTE">笔记</option>
            <option value="OTHER">其他</option>
          </select>
        </div>
        <div class="shiori-filter-field">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M4 12h16M8 8h8M8 16h8" /></svg>
          <select v-model="conditionLevel" class="shiori-filter-control shiori-filter-control-w-sm">
            <option value="">全部成色</option>
            <option value="NEW">全新</option>
            <option value="LIKE_NEW">近新</option>
            <option value="GOOD">良好</option>
            <option value="FAIR">一般</option>
          </select>
        </div>
        <div class="shiori-filter-field">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M7 6h10M7 12h7M7 18h4" /></svg>
          <select v-model="tradeMode" class="shiori-filter-control shiori-filter-control-w-sm">
            <option value="">全部交易方式</option>
            <option value="MEETUP">面交</option>
            <option value="DELIVERY">邮寄</option>
            <option value="BOTH">均可</option>
          </select>
        </div>
        <div class="flex gap-2">
          <button type="button" class="shiori-filter-primary" @click="applyFilter">筛选</button>
          <button type="button" class="shiori-filter-secondary" @click="resetFilter">重置</button>
        </div>
      </div>
      <p v-if="query.isFetching.value" class="shiori-filter-hint">正在刷新筛选结果...</p>
    </section>

    <div v-if="isMobileViewport && mobileFilterOpen" class="shiori-filter-sheet md:hidden">
      <button type="button" class="shiori-filter-sheet-mask" aria-label="关闭筛选面板" @click="mobileFilterOpen = false" />
      <section class="shiori-filter-sheet-panel">
        <div class="mx-auto mb-3 h-1.5 w-12 rounded-full bg-stone-300" />
        <div class="flex items-center justify-between">
          <h2 class="text-base font-semibold text-stone-900">筛选条件</h2>
          <button type="button" class="text-sm text-stone-500" @click="mobileFilterOpen = false">关闭</button>
        </div>
        <div class="mt-3 shiori-filter-fields">
          <div class="shiori-filter-field w-full">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="m21 21-4.35-4.35M10.8 18a7.2 7.2 0 1 0 0-14.4 7.2 7.2 0 0 0 0 14.4Z" /></svg>
            <input v-model="keywordInput" type="text" class="shiori-filter-control shiori-filter-control-w-full" placeholder="按标题/描述搜索" @keyup.enter="applyFilter" />
          </div>
          <div class="shiori-filter-field w-full">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M12 21s7-5.2 7-11a7 7 0 1 0-14 0c0 5.8 7 11 7 11Z" /><circle cx="12" cy="10" r="2.5" /></svg>
            <input v-model="campusCodeInput" type="text" class="shiori-filter-control shiori-filter-control-w-full" placeholder="交易校区" @keyup.enter="applyFilter" />
          </div>
          <div class="shiori-filter-field w-full">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M4 8h16M4 16h16" /></svg>
            <select v-model="status" class="shiori-filter-control shiori-filter-control-w-full">
              <option value="ALL">全部状态</option>
              <option value="DRAFT">草稿</option>
              <option value="ON_SALE">在售</option>
              <option value="OFF_SHELF">已下架</option>
            </select>
          </div>
          <div class="shiori-filter-field w-full">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
            <select v-model="categoryCode" class="shiori-filter-control shiori-filter-control-w-full">
              <option value="">全部分类</option>
              <option value="TEXTBOOK">教材</option>
              <option value="EXAM_MATERIAL">考试资料</option>
              <option value="NOTE">笔记</option>
              <option value="OTHER">其他</option>
            </select>
          </div>
          <div class="shiori-filter-field w-full">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M4 12h16M8 8h8M8 16h8" /></svg>
            <select v-model="conditionLevel" class="shiori-filter-control shiori-filter-control-w-full">
              <option value="">全部成色</option>
              <option value="NEW">全新</option>
              <option value="LIKE_NEW">近新</option>
              <option value="GOOD">良好</option>
              <option value="FAIR">一般</option>
            </select>
          </div>
          <div class="shiori-filter-field w-full">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" class="shiori-filter-icon"><path d="M7 6h10M7 12h7M7 18h4" /></svg>
            <select v-model="tradeMode" class="shiori-filter-control shiori-filter-control-w-full">
              <option value="">全部交易方式</option>
              <option value="MEETUP">面交</option>
              <option value="DELIVERY">邮寄</option>
              <option value="BOTH">均可</option>
            </select>
          </div>
        </div>
        <div class="mt-4 flex gap-2">
          <button type="button" class="shiori-filter-primary flex-1" @click="applyFilter">应用筛选</button>
          <button type="button" class="shiori-filter-secondary" @click="resetFilter">重置</button>
        </div>
      </section>
    </div>

    <ResultState :loading="query.isLoading.value" :error="errorMessage" :empty="!query.isLoading.value && items.length === 0" empty-text="暂无商品">
      <div class="space-y-3">
        <article
          v-for="item in items"
          :key="item.productId"
          class="rounded-2xl border border-stone-200 bg-white/95 p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
        >
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div class="min-w-0">
              <p class="line-clamp-1 text-base font-semibold text-stone-900">{{ item.title }}</p>
              <p class="mt-1 line-clamp-2 text-sm text-stone-600">{{ item.description || '暂无描述' }}</p>
              <p class="mt-1 text-xs text-stone-500">{{ item.productNo }}</p>
              <div class="mt-2 flex flex-wrap gap-1 text-xs text-stone-600">
                <span class="rounded-full bg-stone-100 px-2 py-1">{{ categoryText(item.categoryCode) }}</span>
                <span class="rounded-full bg-stone-100 px-2 py-1">{{ conditionText(item.conditionLevel) }}</span>
                <span class="rounded-full bg-stone-100 px-2 py-1">{{ tradeModeText(item.tradeMode) }}</span>
                <span class="rounded-full bg-stone-100 px-2 py-1">{{ item.campusCode }}</span>
              </div>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <span
                class="rounded-full px-3 py-1 text-xs font-semibold"
                :class="
                  item.status === 'ON_SALE'
                    ? 'bg-emerald-100 text-emerald-700'
                    : item.status === 'OFF_SHELF'
                      ? 'bg-stone-200 text-stone-700'
                      : 'bg-amber-100 text-amber-700'
                "
              >
                {{ statusText(item.status) }}
              </span>
              <button
                type="button"
                class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100"
                @click="toEdit(item.productId)"
              >
                编辑
              </button>
              <button
                v-if="item.status !== 'ON_SALE'"
                type="button"
                class="rounded-lg bg-stone-900 px-3 py-1.5 text-xs text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="publishMutation.isPending.value"
                @click="handlePublish(item.productId)"
              >
                上架
              </button>
              <button
                v-if="item.status === 'ON_SALE'"
                type="button"
                class="rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="offShelfMutation.isPending.value"
                @click="handleOffShelf(item.productId)"
              >
                下架
              </button>
            </div>
          </div>
        </article>
      </div>

      <div class="shiori-pagebar">
        <div class="shiori-pageinfo">
          <span class="shiori-pagechip shiori-pagechip-active">第 {{ pager.page }} / {{ totalPages }} 页</span>
          <span class="shiori-pagechip shiori-pagechip-mute">共 {{ total }} 条</span>
        </div>
        <div class="shiori-pageactions">
          <button
            type="button"
            class="shiori-pagebtn"
            :disabled="pager.page <= 1 || query.isFetching.value"
            @click="pager.page -= 1"
          >
            上一页
          </button>
          <button
            type="button"
            class="shiori-pagebtn"
            :disabled="pager.page >= totalPages || query.isFetching.value"
            @click="pager.page += 1"
          >
            下一页
          </button>
        </div>
      </div>
    </ResultState>

    <p v-if="operateError" class="text-sm text-rose-600">{{ operateError }}</p>
  </section>
</template>
