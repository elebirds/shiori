<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import { presignProductUpload, resolveProductMediaUrls, uploadByPresignedUrl } from '@/api/media'
import type { OrderReviewItemResponse, OrderReviewUpsertRequest } from '@/api/orderV2'

type StarField = 'communicationStar' | 'timelinessStar' | 'credibilityStar'

interface ReviewImagePreview {
  objectKey: string
  url: string
}

const REVIEW_IMAGE_MAX_COUNT = 6
const REVIEW_IMAGE_MAX_SIZE_BYTES = 5 * 1024 * 1024

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
  imageObjectKeys: [],
})
const hoverStars = reactive<Record<StarField, number | null>>({
  communicationStar: null,
  timelinessStar: null,
  credibilityStar: null,
})
const draggingField = ref<StarField | null>(null)
const reviewImages = ref<ReviewImagePreview[]>([])
const uploadError = ref('')
const uploadBusy = ref(false)
let hydrateSeq = 0

const timelinessLabel = computed(() => (props.reviewerRole === 'BUYER' ? '发货及时性' : '付款及时性'))
const credibilityLabel = computed(() => (props.reviewerRole === 'BUYER' ? '描述相符度' : '交易配合度'))
const titleText = computed(() => (props.mode === 'edit' ? '修改评价' : '提交评价'))
const submitText = computed(() => (props.mode === 'edit' ? '保存修改' : '提交评价'))
const commentLength = computed(() => (form.comment || '').length)
const imageCountText = computed(() => `${reviewImages.value.length}/${REVIEW_IMAGE_MAX_COUNT}`)
const imageLimitReached = computed(() => reviewImages.value.length >= REVIEW_IMAGE_MAX_COUNT)

const ratingItems = computed(
  () =>
    [
      { key: 'communicationStar', label: '沟通体验' },
      { key: 'timelinessStar', label: timelinessLabel.value },
      { key: 'credibilityStar', label: credibilityLabel.value },
    ] as Array<{ key: StarField; label: string }>,
)

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
    uploadError.value = ''
    void hydrateInitialReviewImages(initialReview?.imageObjectKeys || [])
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('pointerup', handleGlobalPointerUp)
  window.addEventListener('pointercancel', handleGlobalPointerUp)
})

onBeforeUnmount(() => {
  window.removeEventListener('pointerup', handleGlobalPointerUp)
  window.removeEventListener('pointercancel', handleGlobalPointerUp)
})

function activeStar(field: StarField): number {
  return hoverStars[field] ?? form[field]
}

function isStarFilled(field: StarField, star: number): boolean {
  return star <= activeStar(field)
}

function setStar(field: StarField, value: number): void {
  form[field] = Math.max(1, Math.min(5, value))
}

function setStarHover(field: StarField, value: number): void {
  hoverStars[field] = Math.max(1, Math.min(5, value))
}

function clearStarHover(field: StarField): void {
  if (draggingField.value === field) {
    return
  }
  hoverStars[field] = null
}

function beginStarDrag(field: StarField, event: PointerEvent): void {
  if (props.submitting) {
    return
  }
  draggingField.value = field
  updateStarByPointer(field, event)
}

function moveStarDrag(field: StarField, event: PointerEvent): void {
  if (draggingField.value !== field) {
    return
  }
  updateStarByPointer(field, event)
}

function endStarDrag(field: StarField): void {
  if (draggingField.value !== field) {
    return
  }
  draggingField.value = null
  hoverStars[field] = null
}

function updateStarByPointer(field: StarField, event: PointerEvent): void {
  const el = event.currentTarget as HTMLElement | null
  if (!el) {
    return
  }
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0) {
    return
  }
  const ratio = (event.clientX - rect.left) / rect.width
  const nextValue = Math.max(1, Math.min(5, Math.ceil(ratio * 5)))
  setStar(field, nextValue)
  hoverStars[field] = nextValue
}

function handleGlobalPointerUp(): void {
  if (!draggingField.value) {
    return
  }
  hoverStars[draggingField.value] = null
  draggingField.value = null
}

async function hydrateInitialReviewImages(imageObjectKeys: string[]): Promise<void> {
  const seq = ++hydrateSeq
  const normalized = normalizeImageObjectKeys(imageObjectKeys)
  if (normalized.length === 0) {
    reviewImages.value = []
    form.imageObjectKeys = []
    return
  }
  try {
    const response = await resolveProductMediaUrls(normalized)
    if (seq !== hydrateSeq) {
      return
    }
    reviewImages.value = normalized.map((objectKey) => ({
      objectKey,
      url: response.urls?.[objectKey] || '',
    }))
  } catch {
    if (seq !== hydrateSeq) {
      return
    }
    reviewImages.value = normalized.map((objectKey) => ({ objectKey, url: '' }))
  }
  form.imageObjectKeys = reviewImages.value.map((item) => item.objectKey)
}

async function onImageSelected(event: Event): Promise<void> {
  const target = event.target as HTMLInputElement | null
  const files = Array.from(target?.files || [])
  if (target) {
    target.value = ''
  }
  if (files.length === 0 || props.submitting || uploadBusy.value || imageLimitReached.value) {
    return
  }

  uploadError.value = ''
  uploadBusy.value = true
  try {
    for (const file of files) {
      if (reviewImages.value.length >= REVIEW_IMAGE_MAX_COUNT) {
        break
      }
      if (!file.type.startsWith('image/')) {
        uploadError.value = '仅支持上传图片文件'
        continue
      }
      if (file.size > REVIEW_IMAGE_MAX_SIZE_BYTES) {
        uploadError.value = '单张图片不能超过 5MB'
        continue
      }
      try {
        const presigned = await presignProductUpload({
          fileName: file.name,
          contentType: file.type || undefined,
        })
        await uploadByPresignedUrl(presigned.uploadUrl, file, presigned.requiredHeaders)
        const resolveResult = await resolveProductMediaUrls([presigned.objectKey])
        reviewImages.value = [
          ...reviewImages.value,
          {
            objectKey: presigned.objectKey,
            url: resolveResult.urls?.[presigned.objectKey] || '',
          },
        ]
      } catch {
        uploadError.value = `图片 ${file.name} 上传失败，请稍后重试`
      }
    }
  } finally {
    uploadBusy.value = false
    form.imageObjectKeys = reviewImages.value.map((item) => item.objectKey)
  }
}

function removeImage(index: number): void {
  if (index < 0 || index >= reviewImages.value.length || props.submitting) {
    return
  }
  const next = reviewImages.value.slice()
  next.splice(index, 1)
  reviewImages.value = next
  form.imageObjectKeys = next.map((item) => item.objectKey)
  uploadError.value = ''
}

function normalizeImageObjectKeys(imageObjectKeys: string[]): string[] {
  return Array.from(
    new Set(
      (imageObjectKeys || [])
        .map((item) => (typeof item === 'string' ? item.trim() : ''))
        .filter((item) => item && item.startsWith('product/')),
    ),
  ).slice(0, REVIEW_IMAGE_MAX_COUNT)
}

function closeModal(): void {
  if (props.submitting || uploadBusy.value) {
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
    imageObjectKeys: reviewImages.value.map((item) => item.objectKey),
  })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[60] flex items-center justify-center bg-black/35 px-4">
      <div class="w-full max-w-xl rounded-2xl border border-stone-200 bg-white p-5 shadow-2xl">
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
          <div v-for="item in ratingItems" :key="item.key">
            <div class="flex items-center justify-between">
              <p class="text-sm font-medium text-stone-800">{{ item.label }}</p>
              <p class="text-xs font-medium text-amber-700">{{ form[item.key] }} 星</p>
            </div>
            <div
              class="mt-2 inline-flex touch-none select-none rounded-xl border border-stone-300 bg-stone-50 px-2 py-1"
              @pointerdown.prevent="beginStarDrag(item.key, $event)"
              @pointermove.prevent="moveStarDrag(item.key, $event)"
              @pointerup="endStarDrag(item.key)"
              @pointerleave="clearStarHover(item.key)"
            >
              <button
                v-for="star in [1, 2, 3, 4, 5]"
                :key="`${item.key}-${star}`"
                type="button"
                class="rounded-md p-1 transition hover:scale-110"
                @click="setStar(item.key, star)"
                @mouseenter="setStarHover(item.key, star)"
                @focus="setStarHover(item.key, star)"
                @blur="clearStarHover(item.key)"
              >
                <svg viewBox="0 0 24 24" class="h-6 w-6" :class="isStarFilled(item.key, star) ? 'fill-amber-400 text-amber-500' : 'fill-transparent text-stone-300'">
                  <path
                    stroke="currentColor"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="1.8"
                    d="M12 3.8l2.5 5.2 5.7.8-4.1 3.9 1 5.7L12 16.7 6.9 19.4l1-5.7-4.1-3.9 5.7-.8L12 3.8z"
                  />
                </svg>
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

          <section class="space-y-2">
            <div class="flex items-center justify-between">
              <p class="text-sm font-medium text-stone-800">评价附图（可选）</p>
              <span class="text-xs text-stone-500">{{ imageCountText }}</span>
            </div>

            <label
              class="inline-flex cursor-pointer items-center rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
              :class="imageLimitReached || submitting ? 'cursor-not-allowed opacity-60 hover:bg-white' : ''"
            >
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp"
                multiple
                class="hidden"
                :disabled="imageLimitReached || submitting || uploadBusy"
                @change="onImageSelected"
              />
              {{ uploadBusy ? '上传中...' : imageLimitReached ? '已达上限' : '上传图片' }}
            </label>

            <p v-if="uploadError" class="text-xs text-rose-600">{{ uploadError }}</p>

            <div v-if="reviewImages.length > 0" class="grid grid-cols-3 gap-2 sm:grid-cols-4">
              <article
                v-for="(image, index) in reviewImages"
                :key="image.objectKey"
                class="group relative aspect-square overflow-hidden rounded-lg border border-stone-200 bg-stone-100"
              >
                <img v-if="image.url" :src="image.url" alt="评价附图" class="h-full w-full object-cover" />
                <div v-else class="flex h-full items-center justify-center px-2 text-center text-[11px] text-stone-500">图片已上传</div>
                <button
                  type="button"
                  class="absolute right-1 top-1 hidden h-6 w-6 items-center justify-center rounded-full bg-black/60 text-white transition hover:bg-black/80 group-hover:flex"
                  @click="removeImage(index)"
                >
                  ×
                </button>
              </article>
            </div>
          </section>
        </section>

        <footer class="mt-5 flex items-center justify-end gap-2">
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
            :disabled="submitting || uploadBusy"
            @click="closeModal"
          >
            取消
          </button>
          <button
            type="button"
            class="rounded-lg bg-stone-900 px-3 py-1.5 text-sm text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="submitting || uploadBusy"
            @click="submit"
          >
            {{ submitting ? '提交中...' : submitText }}
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
