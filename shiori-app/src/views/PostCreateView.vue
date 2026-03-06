<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { createPostV2 } from '@/api/social'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { ApiBizError } from '@/types/result'

const router = useRouter()

const contentHtml = ref('')
const submitError = ref('')

const submitMutation = useMutation({
  mutationFn: () =>
    createPostV2({
      contentHtml: contentHtml.value,
    }),
  onSuccess: () => {
    void router.push('/square')
  },
})

const canSubmit = computed(() => !submitMutation.isPending.value && hasMeaningfulContent(contentHtml.value))

function hasMeaningfulContent(raw: string): boolean {
  if (!raw || !raw.trim()) {
    return false
  }
  const doc = new DOMParser().parseFromString(raw, 'text/html')
  const text = (doc.body.textContent || '').trim()
  if (text.length > 0) {
    return true
  }
  return doc.querySelector('img,hr') !== null
}

function goBack(): void {
  void router.push('/square')
}

async function handleSubmit(): Promise<void> {
  submitError.value = ''
  if (!hasMeaningfulContent(contentHtml.value)) {
    submitError.value = '帖子内容不能为空'
    return
  }

  try {
    await submitMutation.mutateAsync()
  } catch (error) {
    if (error instanceof ApiBizError) {
      submitError.value = error.message
      return
    }
    if (error instanceof Error) {
      submitError.value = error.message
      return
    }
    submitError.value = '发布失败，请稍后重试'
  }
}
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/95 p-4 sm:p-5">
      <h1 class="text-2xl font-semibold text-stone-900">发布帖子</h1>
      <p class="mt-1 text-sm text-stone-600">分享你的闲置经验、交易心得或商品动态。</p>
    </header>

    <section class="space-y-4 rounded-2xl border border-stone-200 bg-white/95 p-4 sm:p-5">
      <RichTextEditor v-model="contentHtml" placeholder="写点什么吧，例如：今天上架了几本专业课资料，支持面交..." />

      <p v-if="submitError" class="text-sm text-rose-600">{{ submitError }}</p>

      <div class="flex flex-wrap items-center justify-end gap-2">
        <button
          type="button"
          class="rounded-lg border border-stone-300 px-4 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
          :disabled="submitMutation.isPending.value"
          @click="goBack"
        >
          取消
        </button>
        <button
          type="button"
          class="rounded-lg bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-60"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          {{ submitMutation.isPending.value ? '发布中...' : '发布帖子' }}
        </button>
      </div>
    </section>
  </section>
</template>
