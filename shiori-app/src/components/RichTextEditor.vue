<script setup lang="ts">
import Image from '@tiptap/extension-image'
import TextStyle from '@tiptap/extension-text-style'
import Underline from '@tiptap/extension-underline'
import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { computed, onUnmounted, ref, watch } from 'vue'

import { presignProductUpload, uploadByPresignedUrl } from '@/api/media'
import { ApiBizError } from '@/types/result'

const FONT_SIZE_CLASSES = ['rt-fs-sm', 'rt-fs-md', 'rt-fs-lg', 'rt-fs-xl'] as const

type FontSizeClass = (typeof FONT_SIZE_CLASSES)[number]

const RichImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      dataObjectKey: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-object-key'),
        renderHTML: (attributes) => (attributes.dataObjectKey ? { 'data-object-key': attributes.dataObjectKey } : {}),
      },
    }
  },
})

const RichTextStyle = TextStyle.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      class: {
        default: null,
        parseHTML: (element) => {
          const className = (element.getAttribute('class') || '').trim()
          return FONT_SIZE_CLASSES.includes(className as FontSizeClass) ? className : null
        },
        renderHTML: (attributes) => (attributes.class ? { class: attributes.class } : {}),
      },
    }
  },
})

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
  }>(),
  {
    modelValue: '',
    placeholder: '请补充商品详情...',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const errorMessage = ref('')
const objectUrls = new Set<string>()

const editor = useEditor({
  content: props.modelValue || '',
  editorProps: {
    attributes: {
      class: 'ProseMirror',
      'data-testid': 'rich-editor-content',
    },
  },
  extensions: [
    StarterKit.configure({
      heading: {
        levels: [1, 2, 3, 4],
      },
    }),
    Underline,
    RichTextStyle,
    RichImage,
  ],
  onUpdate({ editor: currentEditor }) {
    emit('update:modelValue', currentEditor.getHTML())
  },
})

const currentFontClass = computed(() => {
  return (editor.value?.getAttributes('textStyle').class as FontSizeClass | undefined) || ''
})

watch(
  () => props.modelValue,
  (value) => {
    const current = editor.value?.getHTML() || ''
    if (value === current || !editor.value) {
      return
    }
    editor.value.commands.setContent(value || '', false)
  },
)

function openImagePicker(): void {
  if (uploading.value) {
    return
  }
  fileInput.value?.click()
}

function setFontSize(className: FontSizeClass): void {
  editor.value?.chain().focus().setMark('textStyle', { class: className }).run()
}

function isFontSizeActive(className: FontSizeClass): boolean {
  return currentFontClass.value === className
}

async function handleImageSelect(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !editor.value) {
    return
  }
  uploading.value = true
  errorMessage.value = ''

  try {
    const presigned = await presignProductUpload({
      fileName: file.name,
      contentType: file.type || undefined,
    })
    await uploadByPresignedUrl(presigned.uploadUrl, file, presigned.requiredHeaders)

    const previewUrl = URL.createObjectURL(file)
    objectUrls.add(previewUrl)

    editor.value
      .chain()
      .focus()
      .setImage({
        src: previewUrl,
        alt: file.name,
        title: file.name,
        dataObjectKey: presigned.objectKey,
      })
      .run()
  } catch (error) {
    if (error instanceof ApiBizError) {
      errorMessage.value = error.message
    } else if (error instanceof Error) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '详情图片上传失败'
    }
  } finally {
    uploading.value = false
    input.value = ''
  }
}

onUnmounted(() => {
  editor.value?.destroy()
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
  objectUrls.clear()
})
</script>

<template>
  <div class="space-y-2">
    <div class="flex flex-wrap items-center gap-2 rounded-xl border border-stone-300 bg-stone-50 p-2">
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="editor?.isActive('bold') ? 'bg-stone-200' : ''"
        @click="editor?.chain().focus().toggleBold().run()"
      >
        加粗
      </button>
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="editor?.isActive('italic') ? 'bg-stone-200' : ''"
        @click="editor?.chain().focus().toggleItalic().run()"
      >
        斜体
      </button>
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="editor?.isActive('underline') ? 'bg-stone-200' : ''"
        @click="editor?.chain().focus().toggleUnderline().run()"
      >
        下划线
      </button>
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="editor?.isActive('bulletList') ? 'bg-stone-200' : ''"
        @click="editor?.chain().focus().toggleBulletList().run()"
      >
        无序列表
      </button>
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="editor?.isActive('orderedList') ? 'bg-stone-200' : ''"
        @click="editor?.chain().focus().toggleOrderedList().run()"
      >
        有序列表
      </button>
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="editor?.isActive('blockquote') ? 'bg-stone-200' : ''"
        @click="editor?.chain().focus().toggleBlockquote().run()"
      >
        引用
      </button>

      <div class="mx-1 h-5 w-px bg-stone-300" />

      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="isFontSizeActive('rt-fs-sm') ? 'bg-stone-200' : ''"
        @click="setFontSize('rt-fs-sm')"
      >
        小
      </button>
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="isFontSizeActive('rt-fs-md') ? 'bg-stone-200' : ''"
        @click="setFontSize('rt-fs-md')"
      >
        默认
      </button>
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="isFontSizeActive('rt-fs-lg') ? 'bg-stone-200' : ''"
        @click="setFontSize('rt-fs-lg')"
      >
        大
      </button>
      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100"
        :class="isFontSizeActive('rt-fs-xl') ? 'bg-stone-200' : ''"
        @click="setFontSize('rt-fs-xl')"
      >
        特大
      </button>

      <div class="mx-1 h-5 w-px bg-stone-300" />

      <button
        type="button"
        class="rounded-md border border-stone-300 px-2 py-1 text-xs text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="uploading"
        @click="openImagePicker"
      >
        {{ uploading ? '上传中...' : '插入图片' }}
      </button>
      <input
        ref="fileInput"
        type="file"
        accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
        class="hidden"
        @change="handleImageSelect"
      />
    </div>

    <div class="rich-editor-content rounded-xl border border-stone-300 bg-white px-3 py-2">
      <EditorContent v-if="editor" :editor="editor" />
      <p v-else class="text-sm text-stone-500">{{ placeholder }}</p>
    </div>

    <p v-if="errorMessage" class="text-xs text-rose-600">{{ errorMessage }}</p>
  </div>
</template>

<style scoped>
.rich-editor-content :deep(.ProseMirror) {
  min-height: 180px;
  outline: none;
  white-space: pre-wrap;
}

.rich-editor-content :deep(.ProseMirror img) {
  max-width: 100%;
  border-radius: 0.5rem;
}
</style>
