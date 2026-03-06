<script setup lang="ts">
import { computed, ref } from 'vue'

import type { PublicUserProfile } from '@/api/auth'
import type { PostV2ItemResponse } from '@/api/social'
import ImageLightbox from '@/components/ImageLightbox.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const props = withDefaults(
  defineProps<{
    post: PostV2ItemResponse
    authorProfile?: PublicUserProfile | null
    currentUserId?: number | null
    allowDelete?: boolean
    deleting?: boolean
  }>(),
  {
    authorProfile: null,
    currentUserId: null,
    allowDelete: false,
    deleting: false,
  },
)

const emit = defineEmits<{
  openUser: [userNo: string]
  openProduct: [productId: number]
  delete: [postId: number]
}>()

const authorName = computed(() => {
  const profile = props.authorProfile
  if (profile?.nickname) {
    return profile.nickname
  }
  if (profile?.username) {
    return profile.username
  }
  return `用户${props.post.authorUserId}`
})

const authorUserNo = computed(() => props.authorProfile?.userNo || '')
const sourceLabel = computed(() => (props.post.sourceType === 'AUTO_PRODUCT' ? '商品自动动态' : '手动发布'))
const canDelete = computed(
  () =>
    Boolean(
      props.allowDelete &&
        props.currentUserId &&
        props.currentUserId === props.post.authorUserId &&
        props.post.sourceType === 'MANUAL',
    ),
)
const previewImageUrl = ref('')

function handleOpenUser(): void {
  const userNo = authorUserNo.value
  if (!userNo) {
    return
  }
  emit('openUser', userNo)
}

function handleOpenProduct(): void {
  const productId = props.post.relatedProduct?.productId
  if (!productId) {
    return
  }
  emit('openProduct', productId)
}

function handleDelete(): void {
  emit('delete', props.post.postId)
}

function handlePostContentClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  if (!target) {
    return
  }
  const image = target.closest('img') as HTMLImageElement | null
  if (!image) {
    return
  }
  const imageUrl = (image.currentSrc || image.src || '').trim()
  if (!imageUrl) {
    return
  }
  event.preventDefault()
  previewImageUrl.value = imageUrl
}

function closeImagePreview(): void {
  previewImageUrl.value = ''
}

function formatTime(raw: string): string {
  if (!raw) {
    return '-'
  }
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  return parsed.toLocaleString('zh-CN')
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
</script>

<template>
  <article class="space-y-3 rounded-2xl border border-stone-200 bg-white/95 p-4 shadow-sm">
    <header class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <button
          type="button"
          class="group flex min-w-0 items-center gap-2 text-left"
          :disabled="!authorUserNo"
          @click="handleOpenUser"
        >
          <UserAvatar
            :src="authorProfile?.avatarUrl"
            :name="authorName"
            size-class="h-9 w-9"
            fallback-size-class="h-4 w-4"
            show-initial
          />
          <span class="min-w-0">
            <span class="block line-clamp-1 text-sm font-semibold text-stone-900 group-hover:underline">{{ authorName }}</span>
            <span class="block text-xs text-stone-500">@{{ authorUserNo || post.authorUserId }}</span>
          </span>
        </button>
        <div class="mt-2 flex flex-wrap items-center gap-2 text-xs text-stone-500">
          <span class="rounded-full bg-stone-100 px-2 py-0.5 text-stone-700">{{ sourceLabel }}</span>
          <span>{{ formatTime(post.createdAt) }}</span>
        </div>
      </div>

      <button
        v-if="canDelete"
        type="button"
        class="rounded-lg border border-rose-200 px-2.5 py-1 text-xs text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="deleting"
        @click="handleDelete"
      >
        {{ deleting ? '删除中...' : '删除' }}
      </button>
    </header>

    <div
      v-if="post.contentHtml"
      class="post-content text-sm text-stone-800"
      v-html="post.contentHtml"
      @click="handlePostContentClick"
    ></div>

    <button
      v-if="post.relatedProduct"
      type="button"
      class="group w-full rounded-xl border border-stone-200 bg-stone-50 p-3 text-left transition hover:border-amber-300 hover:bg-amber-50/50"
      @click="handleOpenProduct"
    >
      <div class="flex items-center gap-3">
        <div class="h-16 w-16 overflow-hidden rounded-lg border border-stone-200 bg-stone-100">
          <img
            v-if="post.relatedProduct.coverImageUrl"
            :src="post.relatedProduct.coverImageUrl"
            :alt="post.relatedProduct.title || '商品封面'"
            class="h-full w-full object-cover transition group-hover:scale-105"
          />
          <div v-else class="flex h-full w-full items-center justify-center text-xs text-stone-500">无封面</div>
        </div>
        <div class="min-w-0 flex-1">
          <p class="line-clamp-1 text-sm font-semibold text-stone-900">{{ post.relatedProduct.title || '商品' }}</p>
          <p class="mt-1 text-xs text-stone-600">
            {{ formatPriceRange(post.relatedProduct.minPriceCent, post.relatedProduct.maxPriceCent) }}
          </p>
          <p class="mt-1 line-clamp-1 text-xs text-stone-500">{{ post.relatedProduct.campusCode || '校区待定' }}</p>
        </div>
      </div>
    </button>

    <ImageLightbox :open="Boolean(previewImageUrl)" :image-url="previewImageUrl" alt="帖子图片大图" @close="closeImagePreview" />
  </article>
</template>

<style scoped>
.post-content :deep(p),
.post-content :deep(h1),
.post-content :deep(h2),
.post-content :deep(h3),
.post-content :deep(ul),
.post-content :deep(ol),
.post-content :deep(blockquote) {
  margin: 0.35rem 0;
}

.post-content :deep(img) {
  max-width: 100%;
  border-radius: 0.5rem;
  cursor: zoom-in;
}

.post-content :deep(a) {
  color: rgb(180 83 9);
  text-decoration: underline;
}

.post-content :deep(blockquote) {
  border-left: 3px solid rgb(245 158 11 / 0.7);
  padding-left: 0.65rem;
  color: rgb(87 83 78);
}
</style>
