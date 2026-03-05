<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import { listAdminOrderReviewsV2, updateAdminOrderReviewVisibilityV2, type AdminOrderReviewItem } from '@/api/adminOrderReviewV2'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()

const page = ref(1)
const size = ref(20)
const reviewedUserId = ref('')
const reviewerUserId = ref('')
const reviewerRole = ref<'' | 'BUYER' | 'SELLER'>('')
const visibilityStatus = ref<'' | 'VISIBLE' | 'HIDDEN_BY_ADMIN'>('')
const minOverallStar = ref('')
const maxOverallStar = ref('')
const createdFrom = ref('')
const createdTo = ref('')
const actionReason = ref('后台人工治理')
const actionError = ref('')
const actionMessage = ref('')

const reviewsQuery = useQuery({
  queryKey: computed(() => [
    'admin-order-reviews-v2',
    page.value,
    size.value,
    reviewedUserId.value,
    reviewerUserId.value,
    reviewerRole.value,
    visibilityStatus.value,
    minOverallStar.value,
    maxOverallStar.value,
    createdFrom.value,
    createdTo.value,
  ]),
  queryFn: () =>
    listAdminOrderReviewsV2({
      page: page.value,
      size: size.value,
      reviewedUserId: reviewedUserId.value ? Number(reviewedUserId.value) : undefined,
      reviewerUserId: reviewerUserId.value ? Number(reviewerUserId.value) : undefined,
      reviewerRole: reviewerRole.value || undefined,
      visibilityStatus: visibilityStatus.value || undefined,
      minOverallStar: minOverallStar.value ? Number(minOverallStar.value) : undefined,
      maxOverallStar: maxOverallStar.value ? Number(maxOverallStar.value) : undefined,
      createdFrom: createdFrom.value || undefined,
      createdTo: createdTo.value || undefined,
    }),
})

const updateVisibilityMutation = useMutation({
  mutationFn: ({ review, visible }: { review: AdminOrderReviewItem; visible: boolean }) =>
    updateAdminOrderReviewVisibilityV2(review.reviewId, {
      visible,
      reason: actionReason.value || undefined,
    }),
  onSuccess: (_data, variables) => {
    actionError.value = ''
    actionMessage.value = variables.visible ? '评价已恢复可见' : '评价已隐藏'
    void queryClient.invalidateQueries({ queryKey: ['admin-order-reviews-v2'] })
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '操作失败'
  },
})

const totalPage = computed(() => {
  const total = reviewsQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / size.value), 1)
})

function onSearch(): void {
  page.value = 1
  actionError.value = ''
  actionMessage.value = ''
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

function roleText(role: 'BUYER' | 'SELLER'): string {
  return role === 'BUYER' ? '买家评价' : '卖家评价'
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">评价治理</h1>
      <p class="mt-1 text-sm text-slate-500">支持按用户/角色/星级筛选评价，并执行隐藏与恢复。</p>
    </div>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-6">
        <input v-model="reviewedUserId" placeholder="被评价人 userId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="reviewerUserId" placeholder="评价人 userId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="reviewerRole" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部角色</option>
          <option value="BUYER">BUYER</option>
          <option value="SELLER">SELLER</option>
        </select>
        <select v-model="visibilityStatus" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部可见性</option>
          <option value="VISIBLE">VISIBLE</option>
          <option value="HIDDEN_BY_ADMIN">HIDDEN_BY_ADMIN</option>
        </select>
        <input v-model="minOverallStar" placeholder="最小星级" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="maxOverallStar" placeholder="最大星级" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
      </div>
      <div class="mt-3 grid grid-cols-1 gap-3 md:grid-cols-4">
        <input v-model="createdFrom" type="datetime-local" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="createdTo" type="datetime-local" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model.trim="actionReason" placeholder="治理原因（用于隐藏/恢复）" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="onSearch">查询</button>
      </div>
    </div>

    <p v-if="actionMessage" class="text-sm text-emerald-700">{{ actionMessage }}</p>
    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>

    <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <table class="min-w-full text-sm">
        <thead class="bg-slate-50 text-slate-600">
          <tr>
            <th class="px-4 py-3 text-left">评价ID</th>
            <th class="px-4 py-3 text-left">订单号</th>
            <th class="px-4 py-3 text-left">角色</th>
            <th class="px-4 py-3 text-left">评分</th>
            <th class="px-4 py-3 text-left">评论</th>
            <th class="px-4 py-3 text-left">可见性</th>
            <th class="px-4 py-3 text-left">治理</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in reviewsQuery.data.value?.items || []" :key="item.reviewId" class="border-t border-slate-100">
            <td class="px-4 py-3">{{ item.reviewId }}</td>
            <td class="px-4 py-3">{{ item.orderNo }}</td>
            <td class="px-4 py-3">
              <p>{{ roleText(item.reviewerRole) }}</p>
              <p class="text-xs text-slate-500">R{{ item.reviewerUserId }} -> T{{ item.reviewedUserId }}</p>
            </td>
            <td class="px-4 py-3">
              <p class="font-medium">{{ Number(item.overallStar).toFixed(1) }} 星</p>
              <p class="mt-1 text-xs text-slate-500">沟通{{ item.communicationStar }} / 及时{{ item.timelinessStar }} / 可信{{ item.credibilityStar }}</p>
            </td>
            <td class="px-4 py-3">
              <p class="line-clamp-3">{{ item.comment || '-' }}</p>
              <p class="mt-1 text-xs text-slate-500">{{ formatTime(item.createdAt) }}</p>
            </td>
            <td class="px-4 py-3">
              <p>{{ item.visibilityStatus }}</p>
              <p class="mt-1 text-xs text-slate-500">{{ item.visibilityReason || '-' }}</p>
            </td>
            <td class="px-4 py-3">
              <div class="flex flex-wrap gap-1">
                <button
                  class="rounded bg-rose-600 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="item.visibilityStatus === 'HIDDEN_BY_ADMIN' || updateVisibilityMutation.isPending.value"
                  @click="updateVisibilityMutation.mutate({ review: item, visible: false })"
                >
                  隐藏
                </button>
                <button
                  class="rounded bg-emerald-600 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="item.visibilityStatus === 'VISIBLE' || updateVisibilityMutation.isPending.value"
                  @click="updateVisibilityMutation.mutate({ review: item, visible: true })"
                >
                  恢复
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="(reviewsQuery.data.value?.items || []).length === 0">
            <td colspan="7" class="px-4 py-10 text-center text-slate-400">暂无评价数据</td>
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

