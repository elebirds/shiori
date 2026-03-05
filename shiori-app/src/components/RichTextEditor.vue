<script setup lang="ts">
import '@wangeditor/editor/dist/css/style.css'

import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'

import { presignProductUpload, uploadByPresignedUrl } from '@/api/media'
import { ApiBizError } from '@/types/result'

const ALLOWED_FONT_SIZE = new Set(['12px', '14px', '16px', '18px', '24px'])
const ALLOWED_TEXT_ALIGN = new Set(['left', 'center', 'right', 'justify'])
const ALLOWED_FONT_SIZE_TAGS = new Set(['span', 'p', 'h1', 'h2', 'h3', 'li', 'blockquote'])
const ALLOWED_TEXT_ALIGN_TAGS = new Set(['p', 'h1', 'h2', 'h3', 'li', 'blockquote'])
const FONT_SIZE_PATTERN = /^(?:[1-9]\d?|1[0-2]\d)px$/
const PERCENT_PATTERN = /^(?:100|[1-9]?\d)(?:\.\d+)?%$/
const PIXEL_PATTERN = /^(?:0|[1-9]\d{0,3})px$/

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

const editorRef = shallowRef<IDomEditor>()
const objectUrls = new Set<string>()
const objectKeyPreviewUrls = new Map<string, string>()
const imageSrcObjectKeys = new Map<string, string>()
const html = ref(normalizeHtmlForEditor(props.modelValue || ''))
const uploading = ref(false)
const errorMessage = ref('')

const toolbarConfig: Partial<IToolbarConfig> = {
  toolbarKeys: [
    'headerSelect',
    '|',
    'bold',
    'italic',
    'underline',
    'through',
    'fontSize',
    '|',
    'bulletedList',
    'numberedList',
    'blockquote',
    '|',
    'insertLink',
    'uploadImage',
    'insertImage',
    'divider',
    '|',
    'justifyLeft',
    'justifyCenter',
    'justifyRight',
    '|',
    'undo',
    'redo',
  ],
}

const editorConfig: Partial<IEditorConfig> = {
  placeholder: props.placeholder,
  MENU_CONF: {
    uploadImage: {
      customUpload: async (file: File, insertFn: (url: string, alt?: string, href?: string) => void) => {
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
          objectKeyPreviewUrls.set(presigned.objectKey, previewUrl)
          imageSrcObjectKeys.set(previewUrl, presigned.objectKey)
          insertFn(previewUrl, file.name)
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
        }
      },
    },
  },
}

watch(
  () => props.modelValue,
  (value) => {
    const incomingStore = normalizeHtmlForStore(value || '')
    const currentStore = normalizeHtmlForStore(html.value || '')
    if (incomingStore === currentStore) {
      return
    }
    html.value = normalizeHtmlForEditor(value || '')
  },
)

watch(html, (value) => {
  const normalized = normalizeHtmlForStore(value || '')
  emit('update:modelValue', normalized)
})

function handleCreated(editor: IDomEditor): void {
  editorRef.value = editor
}

onBeforeUnmount(() => {
  const editor = editorRef.value
  if (editor) {
    editor.destroy?.()
    editorRef.value = undefined
  }
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
  objectUrls.clear()
  objectKeyPreviewUrls.clear()
  imageSrcObjectKeys.clear()
})

function normalizeHtmlForEditor(raw: string): string {
  if (!raw) {
    return ''
  }
  const document = new DOMParser().parseFromString(raw, 'text/html')
  normalizeImageNodesForEditor(document)
  normalizeStyleNodes(document)
  return document.body.innerHTML.trim()
}

function normalizeHtmlForStore(raw: string): string {
  if (!raw) {
    return ''
  }
  const document = new DOMParser().parseFromString(raw, 'text/html')
  normalizeImageNodesForStore(document)
  normalizeStyleNodes(document)
  return document.body.innerHTML.trim()
}

function normalizeImageNodesForEditor(document: Document): void {
  for (const image of Array.from(document.querySelectorAll('img'))) {
    const src = (image.getAttribute('src') || '').trim()
    const objectKey = resolveObjectKey(image.getAttribute('data-object-key'), src)
    if (!objectKey) {
      image.remove()
      continue
    }
    if (src) {
      imageSrcObjectKeys.set(src, objectKey)
    }
    const previewUrl = objectKeyPreviewUrls.get(objectKey)

    image.setAttribute('data-object-key', objectKey)
    if (previewUrl) {
      image.setAttribute('src', previewUrl)
      imageSrcObjectKeys.set(previewUrl, objectKey)
      continue
    }
    if (src) {
      continue
    }
    image.setAttribute('src', objectKey)
    imageSrcObjectKeys.set(objectKey, objectKey)
  }
}

function normalizeImageNodesForStore(document: Document): void {
  for (const image of Array.from(document.querySelectorAll('img'))) {
    const src = (image.getAttribute('src') || '').trim()
    const objectKey = resolveObjectKey(image.getAttribute('data-object-key'), src)
    if (!objectKey) {
      image.remove()
      continue
    }
    image.setAttribute('src', objectKey)
    image.setAttribute('data-object-key', objectKey)
    if (src) {
      imageSrcObjectKeys.set(src, objectKey)
    }
    imageSrcObjectKeys.set(objectKey, objectKey)
  }
}

function resolveObjectKey(rawDataObjectKey: string | null, rawSrc: string): string | null {
  const attrObjectKey = normalizeObjectKey(rawDataObjectKey)
  if (attrObjectKey) {
    return attrObjectKey
  }
  const srcObjectKey = normalizeObjectKey(rawSrc)
  if (srcObjectKey) {
    return srcObjectKey
  }
  if (!rawSrc) {
    return null
  }
  return imageSrcObjectKeys.get(rawSrc) || null
}

function normalizeStyleNodes(document: Document): void {
  for (const element of Array.from(document.querySelectorAll('[style]'))) {
    const styleText = element.getAttribute('style') || ''
    const normalized = normalizeStyleText(element.tagName.toLowerCase(), styleText)
    if (normalized) {
      element.setAttribute('style', normalized)
    } else {
      element.removeAttribute('style')
    }
  }
}

function normalizeStyleText(tagName: string, raw: string): string {
  if (!raw.trim()) {
    return ''
  }
  const declarations: string[] = []
  for (const declaration of raw.split(';')) {
    const [rawName, rawValue] = declaration.split(':', 2)
    if (!rawName || !rawValue) {
      continue
    }
    const name = rawName.trim().toLowerCase()
    const value = rawValue.trim().toLowerCase()
    if (name === 'font-size' && ALLOWED_FONT_SIZE_TAGS.has(tagName) && isAllowedFontSize(value)) {
      declarations.push(`font-size:${value}`)
      continue
    }
    if (name === 'text-align' && ALLOWED_TEXT_ALIGN_TAGS.has(tagName) && ALLOWED_TEXT_ALIGN.has(value)) {
      declarations.push(`text-align:${value}`)
      continue
    }
    if (tagName === 'img' && (name === 'width' || name === 'max-width') && isAllowedWidth(value)) {
      declarations.push(`${name}:${value}`)
      continue
    }
    if (tagName === 'img' && name === 'height' && isAllowedHeight(value)) {
      declarations.push(`height:${value}`)
    }
  }
  return declarations.join(';')
}

function isAllowedFontSize(value: string): boolean {
  return ALLOWED_FONT_SIZE.has(value) || FONT_SIZE_PATTERN.test(value)
}

function isAllowedWidth(value: string): boolean {
  return PERCENT_PATTERN.test(value) || PIXEL_PATTERN.test(value)
}

function isAllowedHeight(value: string): boolean {
  if (value === 'auto') {
    return true
  }
  return PERCENT_PATTERN.test(value) || PIXEL_PATTERN.test(value)
}

function normalizeObjectKey(value: string | null): string | null {
  if (!value) {
    return null
  }
  const trimmed = value.trim()
  if (!trimmed) {
    return null
  }
  return normalizeObjectKeyPath(trimmed) || extractObjectKeyFromUrl(trimmed)
}

function normalizeObjectKeyPath(value: string): string | null {
  if (value.length > 255 || value.includes('..') || value.includes('\\') || value.startsWith('/')) {
    return null
  }
  if (!value.startsWith('product/')) {
    return null
  }
  return value
}

function extractObjectKeyFromUrl(raw: string): string | null {
  if (!raw.includes('://')) {
    return null
  }
  try {
    const parsed = new URL(raw)
    const decodedPath = decodeURIComponent(parsed.pathname || '')
    const markerIndex = decodedPath.indexOf('/product/')
    if (markerIndex >= 0) {
      return normalizeObjectKeyPath(decodedPath.slice(markerIndex + 1))
    }
    if (decodedPath.startsWith('product/')) {
      return normalizeObjectKeyPath(decodedPath)
    }
    return null
  } catch {
    return null
  }
}
</script>

<template>
  <div class="space-y-2">
    <div class="rich-editor overflow-hidden rounded-xl border border-stone-300 bg-white">
      <Toolbar class="border-b border-stone-200 bg-stone-50" :editor="editorRef" :defaultConfig="toolbarConfig" mode="default" />
      <div data-testid="rich-editor-content" class="rich-editor-content px-2 py-2">
        <Editor v-model="html" :defaultConfig="editorConfig" mode="default" @onCreated="handleCreated" />
      </div>
    </div>

    <p v-if="uploading" class="text-xs text-amber-700">详情图片上传中...</p>
    <p v-if="errorMessage" class="text-xs text-rose-600">{{ errorMessage }}</p>
  </div>
</template>

<style scoped>
.rich-editor-content :deep(.w-e-text-container) {
  border: none !important;
  min-height: 180px;
}

.rich-editor-content :deep(.w-e-text-placeholder) {
  top: 12px;
}

.rich-editor-content :deep([data-slate-editor]) {
  min-height: 180px;
  padding: 0;
}

.rich-editor-content :deep(img) {
  max-width: 100%;
  border-radius: 0.5rem;
}
</style>
