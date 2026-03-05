<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

import type { OrderReviewItemResponse, OrderReviewUpsertRequest } from '@/api/orderV2'

const props = withDefaults(
  defineProps<{
    open: boolean
    reviewerRole: 'BUYER' | 'SELLER'
    mode: 'create' | 'edit'
    submitting?: boolean
    initialReview?: OrderReviewItemResponse
  }>(),
  {
    submitting: false,
    initialReview: undefined,
  },
)

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'submit', payload: OrderReviewUpsertRequest): void
}>()

const form = reactive<OrderReviewUpsertRequest>({
  communicationStar: 5,
  timelinessStar: 5,
  credibilityStar: 5,
  comment: '',
})

const timelinessLabel = computed(() => (props.reviewerRole === 'BUYER' ? '发货及时性' : '付款及时性'))
const credibilityLabel = computed(() => (props.reviewerRole === 'BUYER' ? '描述相符度' : '交易配合度'))
const titleText = computed(() => (props.mode === 'edit' ? '修改评价' : '提交评价'))
const submitText = computed(() => (props.mode === 'edit' ? '保存修改' : '提交评价'))
const commentLength = computed(() => (form.comment || '').length)

watch(
  () => [props.open, props.initialReview] as const,
  ([open, initialReview]) => {
    if (!open) {
      return
    }
    form.communicationStar = initialReview?.communicationStar ?? 5
    form.timelinessStar = initialReview?.timelinessStar ?? 5
    form.credibilityStar = initialReview?.credibilityStar ?? 5
    form.comment = initialReview?.comment || ''
  },
  { immediate: true },
)

function setStar(key: 'communicationStar' | 'timelinessStar' | 'credibilityStar', value: number): void {
  form[key] = value
}

function closeModal(): void {
  if (props.submitting) {
    return
  }
  emit('close')
}

function submit(): void {
  emit('submit', {
    communicationStar: form.communicationStar,
    timelinessStar: form.timelinessStar,
    credibilityStar: form.credibilityStar,
    comment: form.comment?.trim() || undefined,
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[60] flex items-center justify-center bg-black/35 px-4">
      <div class="w-full max-w-lg rounded-2xl border border-stone-200 bg-white p-5 shadow-2xl">
        <header class="flex items-center justify-between">
          <h2 class="text-lg font-semibold text-stone-900">{{ titleText }}</h2>
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-2 py-1 text-xs text-stone-600 transition hover:bg-stone-100"
            @click="closeModal"
          >
            关闭
          </button>
        </header>

        <section class="mt-4 space-y-4">
          <div>
            <p class="text-sm font-medium text-stone-800">沟通体验</p>
            <div class="mt-2 flex flex-wrap gap-2">
              <button
                v-for="star in [1, 2, 3, 4, 5]"
                :key="`communication-${star}`"
                type="button"
                class="rounded-lg border px-3 py-1 text-sm transition"
                :class="form.communicationStar === star ? 'border-amber-500 bg-amber-50 text-amber-700' : 'border-stone-300 text-stone-700 hover:bg-stone-100'"
                @click="setStar('communicationStar', star)"
              >
                {{ star }} 星
              </button>
            </div>
          </div>

          <div>
            <p class="text-sm font-medium text-stone-800">{{ timelinessLabel }}</p>
            <div class="mt-2 flex flex-wrap gap-2">
              <button
                v-for="star in [1, 2, 3, 4, 5]"
                :key="`timeliness-${star}`"
                type="button"
                class="rounded-lg border px-3 py-1 text-sm transition"
                :class="form.timelinessStar === star ? 'border-amber-500 bg-amber-50 text-amber-700' : 'border-stone-300 text-stone-700 hover:bg-stone-100'"
                @click="setStar('timelinessStar', star)"
              >
                {{ star }} 星
              </button>
            </div>
          </div>

          <div>
            <p class="text-sm font-medium text-stone-800">{{ credibilityLabel }}</p>
            <div class="mt-2 flex flex-wrap gap-2">
              <button
                v-for="star in [1, 2, 3, 4, 5]"
                :key="`credibility-${star}`"
                type="button"
                class="rounded-lg border px-3 py-1 text-sm transition"
                :class="form.credibilityStar === star ? 'border-amber-500 bg-amber-50 text-amber-700' : 'border-stone-300 text-stone-700 hover:bg-stone-100'"
                @click="setStar('credibilityStar', star)"
              >
                {{ star }} 星
              </button>
            </div>
          </div>

          <label class="block text-sm text-stone-700">
            补充评论（可选）
            <textarea
              v-model="form.comment"
              rows="4"
              maxlength="280"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            />
            <span class="mt-1 block text-right text-xs text-stone-500">{{ commentLength }}/280</span>
          </label>
        </section>

        <footer class="mt-5 flex items-center justify-end gap-2">
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
            :disabled="submitting"
            @click="closeModal"
          >
            取消
          </button>
          <button
            type="button"
            class="rounded-lg bg-stone-900 px-3 py-1.5 text-sm text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="submitting"
            @click="submit"
          >
            {{ submitting ? '提交中...' : submitText }}
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

