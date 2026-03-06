<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    src?: string | null
    name?: string | null
    alt?: string
    sizeClass?: string
    roundedClass?: string
    fallbackSizeClass?: string
    showInitial?: boolean
  }>(),
  {
    src: '',
    name: '',
    alt: '用户头像',
    sizeClass: 'h-10 w-10',
    roundedClass: 'rounded-full',
    fallbackSizeClass: 'h-5 w-5',
    showInitial: false,
  },
)

const normalizedSrc = computed(() => (props.src || '').trim())
const fallbackInitial = computed(() => {
  const normalized = (props.name || '').trim()
  if (!normalized) {
    return ''
  }
  return normalized.slice(0, 1).toUpperCase()
})
</script>

<template>
  <span
    class="inline-flex shrink-0 overflow-hidden border border-stone-200 bg-gradient-to-br from-stone-100 to-stone-200 text-stone-500"
    :class="[sizeClass, roundedClass]"
  >
    <img v-if="normalizedSrc" :src="normalizedSrc" :alt="alt" class="h-full w-full object-cover" />
    <span v-else class="relative flex h-full w-full items-center justify-center">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" class="text-stone-400" :class="fallbackSizeClass">
        <path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z" />
        <path d="M4 20a8 8 0 0 1 16 0" />
      </svg>
      <span
        v-if="showInitial && fallbackInitial"
        class="absolute bottom-0.5 right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-white/90 px-1 text-[9px] font-semibold text-stone-600"
      >
        {{ fallbackInitial }}
      </span>
    </span>
  </span>
</template>
