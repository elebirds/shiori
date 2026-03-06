<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    imageUrl?: string
    alt?: string
  }>(),
  {
    imageUrl: '',
    alt: '图片预览',
  },
)

const emit = defineEmits<{
  (event: 'close'): void
}>()

function closePreview(): void {
  emit('close')
}

function handleEscape(event: KeyboardEvent): void {
  if (event.key !== 'Escape' || !props.open) {
    return
  }
  closePreview()
}

watch(
  () => props.open,
  (open) => {
    if (typeof document === 'undefined') {
      return
    }
    document.body.style.overflow = open ? 'hidden' : ''
  },
)

onMounted(() => {
  window.addEventListener('keydown', handleEscape)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleEscape)
  if (typeof document !== 'undefined') {
    document.body.style.overflow = ''
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="image-lightbox">
      <div v-if="open && imageUrl" class="fixed inset-0 z-[120] flex items-center justify-center p-4">
        <button
          type="button"
          class="absolute inset-0 bg-black/80 backdrop-blur-[1px]"
          aria-label="关闭图片预览"
          @click="closePreview"
        />

        <div class="relative z-[1] flex max-h-full max-w-full items-center justify-center">
          <img :src="imageUrl" :alt="alt || '图片预览'" class="max-h-[86vh] max-w-[92vw] rounded-xl object-contain shadow-[0_24px_72px_rgba(0,0,0,0.45)]" />
          <button
            type="button"
            class="absolute -right-2 -top-2 flex h-9 w-9 items-center justify-center rounded-full border border-white/40 bg-black/60 text-lg text-white transition hover:bg-black/80"
            aria-label="关闭图片预览"
            @click="closePreview"
          >
            ×
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.image-lightbox-enter-active,
.image-lightbox-leave-active {
  transition: opacity 0.18s ease;
}

.image-lightbox-enter-from,
.image-lightbox-leave-to {
  opacity: 0;
}
</style>
