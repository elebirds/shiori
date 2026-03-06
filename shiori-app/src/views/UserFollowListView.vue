<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getUserProfileByUserNo, listUserFollowersByUserNo, listUserFollowingByUserNo } from '@/api/auth'
import ResultState from '@/components/ResultState.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()

const page = ref(1)
const size = 20

const routeUserNo = computed(() => String(route.params.userNo || '').trim())
const followMode = computed<'followers' | 'following'>(() => {
  if (route.meta.followMode === 'following') {
    return 'following'
  }
  return 'followers'
})

watch([routeUserNo, followMode], () => {
  page.value = 1
})

const profileQuery = useQuery({
  queryKey: computed(() => ['public-profile', routeUserNo.value]),
  queryFn: () => getUserProfileByUserNo(routeUserNo.value),
  enabled: computed(() => Boolean(routeUserNo.value)),
  retry: false,
})

const listQuery = useQuery({
  queryKey: computed(() => ['follow-list', followMode.value, routeUserNo.value, page.value, size]),
  queryFn: async () => {
    if (followMode.value === 'followers') {
      return listUserFollowersByUserNo(routeUserNo.value, { page: page.value, size })
    }
    return listUserFollowingByUserNo(routeUserNo.value, { page: page.value, size })
  },
  enabled: computed(() => Boolean(routeUserNo.value)),
})

const profile = computed(() => profileQuery.data.value)
const items = computed(() => listQuery.data.value?.items || [])
const total = computed(() => listQuery.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const title = computed(() => (followMode.value === 'followers' ? '粉丝列表' : '关注列表'))
const headerName = computed(() => profile.value?.nickname || profile.value?.username || routeUserNo.value)
const listErrorMessage = computed(() => (listQuery.error.value instanceof Error ? listQuery.error.value.message : ''))
const profileErrorMessage = computed(() => (profileQuery.error.value instanceof Error ? profileQuery.error.value.message : ''))

function goBack(): void {
  if (!routeUserNo.value) {
    void router.push('/products')
    return
  }
  void router.push(`/u/${encodeURIComponent(routeUserNo.value)}`)
}

function goUser(userNo: string): void {
  void router.push(`/u/${encodeURIComponent(userNo)}`)
}
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/95 p-4">
      <div class="flex items-center justify-between gap-3">
        <div>
          <h1 class="text-xl font-semibold text-stone-900">{{ headerName }} · {{ title }}</h1>
          <p class="mt-1 text-sm text-stone-600">共 {{ total }} 人</p>
        </div>
        <button
          type="button"
          class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
          @click="goBack"
        >
          返回主页
        </button>
      </div>
    </header>

    <ResultState
      :loading="profileQuery.isLoading.value"
      :error="profileErrorMessage"
      :empty="!profileQuery.isLoading.value && !profile"
      empty-text="未找到该用户"
    >
      <ResultState
        :loading="listQuery.isLoading.value || listQuery.isFetching.value"
        :error="listErrorMessage"
        :empty="!listQuery.isLoading.value && items.length === 0"
        :empty-text="followMode === 'followers' ? '还没有粉丝' : '还没有关注任何人'"
      >
        <section class="space-y-3">
          <article
            v-for="item in items"
            :key="item.userId"
            class="flex items-center justify-between gap-3 rounded-2xl border border-stone-200 bg-white/95 p-4"
          >
            <button type="button" class="flex min-w-0 items-center gap-3 text-left" @click="goUser(item.userNo)">
              <UserAvatar :src="item.avatarUrl" :name="item.nickname || item.userNo" size-class="h-12 w-12" fallback-size-class="h-5 w-5" show-initial />
              <div class="min-w-0">
                <p class="line-clamp-1 text-sm font-semibold text-stone-900">{{ item.nickname || item.userNo }}</p>
                <p class="mt-0.5 line-clamp-1 text-xs text-stone-500">@{{ item.userNo }}</p>
                <p class="mt-1 line-clamp-1 text-xs text-stone-600">{{ item.bio || '这个人很神秘，还没有简介' }}</p>
              </div>
            </button>
            <span class="shrink-0 text-xs text-stone-500">
              {{ new Date(item.followedAt).toLocaleString('zh-CN') }}
            </span>
          </article>

          <div class="flex items-center justify-between rounded-2xl border border-stone-200 bg-white/90 px-4 py-3 text-sm">
            <span class="text-stone-600">第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
            <div class="flex gap-2">
              <button
                type="button"
                class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="page <= 1 || listQuery.isFetching.value"
                @click="page -= 1"
              >
                上一页
              </button>
              <button
                type="button"
                class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="page >= totalPages || listQuery.isFetching.value"
                @click="page += 1"
              >
                下一页
              </button>
            </div>
          </div>
        </section>
      </ResultState>
    </ResultState>
  </section>
</template>
