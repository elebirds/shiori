<script setup lang="ts">
defineProps<{
  loading?: boolean
  error?: string
  empty?: boolean
  emptyText?: string
}>()
</script>

<template>
  <div v-if="loading" class="rounded-2xl border border-amber-200/60 bg-white/80 p-8 text-center text-amber-700">
    正在加载...
  </div>
  <div v-else-if="error" class="rounded-2xl border border-rose-200/70 bg-rose-50 p-8 text-center text-rose-700">
    {{ error }}
  </div>
  <div v-else-if="empty" class="rounded-2xl border border-stone-200 bg-white/80 p-8 text-center text-stone-500">
    {{ emptyText || '暂无数据' }}
  </div>
  <slot v-else />
</template>
