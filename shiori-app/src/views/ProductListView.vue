<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { listProducts } from '@/api/product'

const router = useRouter()

const page = ref(1)
const size = 12
const keywordInput = ref('')
const keyword = ref('')

const queryKey = computed(() => ['products', page.value, size, keyword.value])

const query = useQuery({
  queryKey,
  queryFn: () =>
    listProducts({
      page: page.value,
      size,
      keyword: keyword.value || undefined,
    }),
})

const items = computed(() => query.data.value?.items || [])
const total = computed(() => query.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const errorMessage = computed(() => (query.error.value instanceof Error ? query.error.value.message : ''))

function applySearch(): void {
  page.value = 1
  keyword.value = keywordInput.value.trim()
}

function goDetail(productId: number): void {
  void router.push(`/products/${productId}`)
}

</script>

<template>
  <section class="space-y-5">
    <div class="flex flex-col gap-3 rounded-2xl border border-stone-200/80 bg-white/90 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 class="font-display text-2xl text-stone-900">商品广场</h1>
        <p class="text-sm text-stone-600">匿名可浏览，登录后可下单</p>
      </div>

      <div class="flex gap-2">
        <input
          v-model="keywordInput"
          type="text"
          class="w-56 rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="搜索商品关键词"
          @keyup.enter="applySearch"
        />
        <button
          type="button"
          class="rounded-xl bg-stone-900 px-4 py-2 text-sm text-white transition hover:bg-stone-700"
          @click="applySearch"
        >
          搜索
        </button>
      </div>
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

          <div class="mt-3 flex items-center justify-between text-xs text-stone-500">
            <span>{{ item.status }}</span>
            <span>编号 {{ item.productNo }}</span>
          </div>
        </article>
      </div>

      <div class="flex items-center justify-between rounded-2xl border border-stone-200 bg-white/90 px-4 py-3 text-sm">
        <span class="text-stone-600">第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
        <div class="flex gap-2">
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="page <= 1 || query.isFetching.value"
            @click="page -= 1"
          >
            上一页
          </button>
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="page >= totalPages || query.isFetching.value"
            @click="page += 1"
          >
            下一页
          </button>
        </div>
      </div>
    </ResultState>

    <p class="text-xs text-stone-500">价格在详情页 SKU 中展示，单位为人民币。</p>
  </section>
</template>
