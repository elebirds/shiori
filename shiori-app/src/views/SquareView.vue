<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getUserProfilesByUserIds, type PublicUserProfile } from '@/api/auth'
import { deletePostV2, listSquareFeedPostsV2 } from '@/api/social'
import PostFeedCard from '@/components/PostFeedCard.vue'
import ResultState from '@/components/ResultState.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const queryClient = useQueryClient()

const page = ref(1)
const size = 10
const deletingPostId = ref<number | null>(null)
const currentUserId = computed(() => authStore.user?.userId || null)

const feedQuery = useQuery({
  queryKey: computed(() => ['square-feed-posts', page.value, size]),
  enabled: computed(() => authStore.isAuthenticated),
  queryFn: () =>
    listSquareFeedPostsV2({
      page: page.value,
      size,
    }),
})

const feedItems = computed(() => feedQuery.data.value?.items || [])
const feedTotal = computed(() => feedQuery.data.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(feedTotal.value / size)))

const authorIdsInFeed = computed(() => {
  const values = feedItems.value.map((item) => item.authorUserId).filter((item) => item > 0)
  return Array.from(new Set(values))
})

const authorProfilesQuery = useQuery({
  queryKey: computed(() => ['square-feed-author-profiles', authorIdsInFeed.value.join(',')]),
  enabled: computed(() => authorIdsInFeed.value.length > 0),
  queryFn: () => getUserProfilesByUserIds(authorIdsInFeed.value),
})

const authorProfileMap = computed(() => {
  const map = new Map<number, PublicUserProfile>()
  for (const item of authorProfilesQuery.data.value || []) {
    map.set(item.userId, item)
  }
  return map
})

const loadErrorMessage = computed(() => {
  if (feedQuery.error.value instanceof Error) {
    return feedQuery.error.value.message
  }
  if (authorProfilesQuery.error.value instanceof Error) {
    return authorProfilesQuery.error.value.message
  }
  return ''
})

const deleteMutation = useMutation({
  mutationFn: (postId: number) => deletePostV2(postId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['square-feed-posts'] })
  },
  onSettled: () => {
    deletingPostId.value = null
  },
})

function goCreate(): void {
  void router.push('/posts/new')
}

function goUser(userNo: string): void {
  if (!userNo) {
    return
  }
  void router.push(`/u/${encodeURIComponent(userNo)}`)
}

function goProduct(productId: number): void {
  void router.push(`/products/${productId}`)
}

async function handleDelete(postId: number): Promise<void> {
  if (!postId || deleteMutation.isPending.value) {
    return
  }
  const confirmed = window.confirm('确认删除这条帖子吗？')
  if (!confirmed) {
    return
  }
  deletingPostId.value = postId
  await deleteMutation.mutateAsync(postId)
}

function refreshFeed(): void {
  void queryClient.invalidateQueries({ queryKey: ['square-feed-posts'] })
}
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/95 p-4 sm:p-5">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 class="text-2xl font-semibold text-stone-900">广场</h1>
          <p class="mt-1 text-sm text-stone-600">正在查看你和关注用户的动态流。</p>
        </div>

        <div class="flex items-center gap-2">
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
            @click="refreshFeed"
          >
            刷新
          </button>
          <button
            type="button"
            class="rounded-lg bg-stone-900 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-stone-700"
            @click="goCreate"
          >
            写帖子
          </button>
        </div>
      </div>
    </header>

    <ResultState
      :loading="feedQuery.isLoading.value"
      :error="loadErrorMessage"
      :empty="!feedQuery.isLoading.value && feedItems.length === 0"
      empty-text="关注动态暂时为空，去用户主页关注更多同学吧"
    >
      <section class="space-y-3">
        <PostFeedCard
          v-for="item in feedItems"
          :key="item.postId"
          :post="item"
          :author-profile="authorProfileMap.get(item.authorUserId)"
          :current-user-id="currentUserId"
          :allow-delete="true"
          :deleting="deleteMutation.isPending.value && deletingPostId === item.postId"
          @open-user="goUser"
          @open-product="goProduct"
          @delete="handleDelete"
        />

        <div class="flex items-center justify-between rounded-2xl border border-stone-200 bg-white/90 px-4 py-3 text-sm">
          <span class="text-stone-600">第 {{ page }} / {{ totalPages }} 页，共 {{ feedTotal }} 条</span>
          <div class="flex gap-2">
            <button
              type="button"
              class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="page <= 1 || feedQuery.isFetching.value"
              @click="page -= 1"
            >
              上一页
            </button>
            <button
              type="button"
              class="rounded-lg border border-stone-300 px-3 py-1.5 text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="page >= totalPages || feedQuery.isFetching.value"
              @click="page += 1"
            >
              下一页
            </button>
          </div>
        </div>
      </section>
    </ResultState>
  </section>
</template>
