<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { presignProductUpload, uploadByPresignedUrl } from '@/api/media'
import {
  getMyProductDetailV2,
  offShelfProductV2,
  publishProductV2,
  updateProductV2,
  type ProductCategoryCode,
  type ProductConditionLevel,
  type ProductTradeMode,
} from '@/api/productV2'
import { ApiBizError } from '@/types/result'

interface DraftSku {
  localId: string
  id?: number
  skuName: string
  specJson: string
  priceYuan: string
  stock: number
}

const categoryOptions: Array<{ label: string; value: ProductCategoryCode }> = [
  { label: '教材', value: 'TEXTBOOK' },
  { label: '考试资料', value: 'EXAM_MATERIAL' },
  { label: '笔记', value: 'NOTE' },
  { label: '其他', value: 'OTHER' },
]

const conditionOptions: Array<{ label: string; value: ProductConditionLevel }> = [
  { label: '全新', value: 'NEW' },
  { label: '近新', value: 'LIKE_NEW' },
  { label: '良好', value: 'GOOD' },
  { label: '一般', value: 'FAIR' },
]

const tradeModeOptions: Array<{ label: string; value: ProductTradeMode }> = [
  { label: '面交', value: 'MEETUP' },
  { label: '邮寄', value: 'DELIVERY' },
  { label: '均可', value: 'BOTH' },
]

const route = useRoute()
const router = useRouter()

const productId = computed(() => Number(route.params.id))

const form = reactive({
  title: '',
  description: '',
  detailHtml: '',
  coverObjectKey: '',
  categoryCode: 'TEXTBOOK' as ProductCategoryCode,
  conditionLevel: 'GOOD' as ProductConditionLevel,
  tradeMode: 'MEETUP' as ProductTradeMode,
  campusCode: '',
})
const skus = ref<DraftSku[]>([])

const coverPreviewUrl = ref('')
const selectedCoverName = ref('')
const uploadMessage = ref('')
const uploadingCover = ref(false)
const resultMessage = ref('')
const resultError = ref('')

const detailQuery = useQuery({
  queryKey: computed(() => ['my-product-detail-v2', productId.value]),
  queryFn: () => getMyProductDetailV2(productId.value),
  enabled: computed(() => Number.isFinite(productId.value) && productId.value > 0),
})

watch(
  () => detailQuery.data.value,
  (detail) => {
    if (!detail) {
      return
    }
    form.title = detail.title || ''
    form.description = detail.description || ''
    form.detailHtml = detail.detailHtml || ''
    form.coverObjectKey = detail.coverObjectKey || ''
    form.categoryCode = detail.categoryCode
    form.conditionLevel = detail.conditionLevel
    form.tradeMode = detail.tradeMode
    form.campusCode = detail.campusCode || ''
    skus.value = detail.skus.map((sku) => ({
      localId: crypto.randomUUID(),
      id: sku.skuId,
      skuName: sku.skuName,
      specJson: sku.specJson || '',
      priceYuan: centToYuanText(sku.priceCent),
      stock: sku.stock,
    }))
  },
  { immediate: true },
)

const updateMutation = useMutation({
  mutationFn: () =>
    updateProductV2(productId.value, {
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      detailHtml: form.detailHtml.trim() || undefined,
      coverObjectKey: form.coverObjectKey.trim() || undefined,
      categoryCode: form.categoryCode,
      conditionLevel: form.conditionLevel,
      tradeMode: form.tradeMode,
      campusCode: form.campusCode.trim(),
      skus: skus.value.map((item) => {
        const priceCent = yuanTextToCent(item.priceYuan)
        if (priceCent == null) {
          throw new Error('SKU 价格必须大于 0 元，且最多保留两位小数')
        }
        return {
          id: item.id,
          skuName: item.skuName.trim(),
          specJson: item.specJson?.trim() || undefined,
          priceCent,
          stock: item.stock,
        }
      }),
    }),
})

const publishMutation = useMutation({
  mutationFn: () => publishProductV2(productId.value),
  onSuccess: async () => {
    await detailQuery.refetch()
  },
})

const offShelfMutation = useMutation({
  mutationFn: () => offShelfProductV2(productId.value),
  onSuccess: async () => {
    await detailQuery.refetch()
  },
})

const errorMessage = computed(() => (detailQuery.error.value instanceof Error ? detailQuery.error.value.message : ''))
const status = computed(() => detailQuery.data.value?.status || '')
const PRODUCT_STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  ON_SALE: '在售',
  OFF_SHELF: '已下架',
}

function centToYuanText(cent: number): string {
  return (Math.max(0, Number(cent) || 0) / 100).toFixed(2)
}

function yuanTextToCent(raw: string): number | null {
  const normalized = raw.trim()
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) {
    return null
  }
  const amount = Number(normalized)
  if (!Number.isFinite(amount) || amount <= 0) {
    return null
  }
  return Math.round(amount * 100)
}

function addSku(): void {
  skus.value.push({
    localId: crypto.randomUUID(),
    skuName: '',
    specJson: '',
    priceYuan: '1.00',
    stock: 0,
  })
}

function removeSku(localId: string): void {
  if (skus.value.length <= 1) {
    return
  }
  skus.value = skus.value.filter((item) => item.localId !== localId)
}

function validate(): string | null {
  if (!form.title.trim()) {
    return '商品标题不能为空'
  }
  if (!form.campusCode.trim()) {
    return '交易校区不能为空'
  }
  if (skus.value.length === 0) {
    return '请至少保留一个 SKU'
  }

  for (const item of skus.value) {
    if (!item.skuName.trim()) {
      return 'SKU 名称不能为空'
    }
    if (yuanTextToCent(item.priceYuan) == null) {
      return 'SKU 价格必须大于 0 元，且最多保留两位小数'
    }
    if (!Number.isInteger(item.stock) || item.stock < 0) {
      return 'SKU 库存必须为非负整数'
    }
  }

  return null
}

async function handleCoverChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }

  uploadMessage.value = ''
  resultError.value = ''
  uploadingCover.value = true

  try {
    const presigned = await presignProductUpload({
      fileName: file.name,
      contentType: file.type || undefined,
    })
    await uploadByPresignedUrl(presigned.uploadUrl, file, presigned.requiredHeaders)

    form.coverObjectKey = presigned.objectKey
    selectedCoverName.value = file.name
    uploadMessage.value = '封面上传成功'

    if (coverPreviewUrl.value) {
      URL.revokeObjectURL(coverPreviewUrl.value)
    }
    coverPreviewUrl.value = URL.createObjectURL(file)
  } catch (error) {
    if (error instanceof ApiBizError) {
      resultError.value = error.message
    } else if (error instanceof Error) {
      resultError.value = error.message
    } else {
      resultError.value = '封面上传失败'
    }
  } finally {
    uploadingCover.value = false
    input.value = ''
  }
}

async function handleSubmit(): Promise<void> {
  resultError.value = ''
  resultMessage.value = ''

  const validationError = validate()
  if (validationError) {
    resultError.value = validationError
    return
  }

  try {
    await updateMutation.mutateAsync()
    resultMessage.value = '保存成功'
    await detailQuery.refetch()
  } catch (error) {
    if (error instanceof ApiBizError) {
      resultError.value = error.message
    } else if (error instanceof Error) {
      resultError.value = error.message
    } else {
      resultError.value = '更新失败'
    }
  }
}

async function handlePublish(): Promise<void> {
  resultError.value = ''
  resultMessage.value = ''
  try {
    await publishMutation.mutateAsync()
    resultMessage.value = '已上架'
  } catch (error) {
    if (error instanceof ApiBizError) {
      resultError.value = error.message
    }
  }
}

async function handleOffShelf(): Promise<void> {
  resultError.value = ''
  resultMessage.value = ''
  try {
    await offShelfMutation.mutateAsync()
    resultMessage.value = '已下架'
  } catch (error) {
    if (error instanceof ApiBizError) {
      resultError.value = error.message
    }
  }
}

onUnmounted(() => {
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
})
</script>

<template>
  <section class="space-y-4">
    <button
      type="button"
      class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
      @click="router.push('/my-products')"
    >
      返回我的商品
    </button>

    <ResultState :loading="detailQuery.isLoading.value" :error="errorMessage" :empty="!detailQuery.isLoading.value && !detailQuery.data.value" empty-text="商品不存在">
      <form class="space-y-4 rounded-2xl border border-stone-200 bg-white/95 p-5" @submit.prevent="handleSubmit">
        <div class="flex items-center justify-between">
          <h1 class="font-display text-2xl text-stone-900">编辑商品</h1>
          <span
            class="rounded-full px-3 py-1 text-xs font-semibold"
            :class="
              status === 'ON_SALE'
                ? 'bg-emerald-100 text-emerald-700'
                : status === 'OFF_SHELF'
                  ? 'bg-stone-200 text-stone-700'
                  : 'bg-amber-100 text-amber-700'
            "
          >
            {{ PRODUCT_STATUS_TEXT[status] || '未知状态' }}
          </span>
        </div>

        <div class="grid gap-3 sm:grid-cols-2">
          <label class="text-sm text-stone-700 sm:col-span-2">
            商品标题
            <input
              v-model.trim="form.title"
              type="text"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            />
          </label>

          <label class="text-sm text-stone-700">
            分类
            <select
              v-model="form.categoryCode"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            >
              <option v-for="item in categoryOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>

          <label class="text-sm text-stone-700">
            成色
            <select
              v-model="form.conditionLevel"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            >
              <option v-for="item in conditionOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>

          <label class="text-sm text-stone-700">
            交易方式
            <select
              v-model="form.tradeMode"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            >
              <option v-for="item in tradeModeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>

          <label class="text-sm text-stone-700">
            交易校区
            <input
              v-model.trim="form.campusCode"
              type="text"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            />
          </label>

          <label class="text-sm text-stone-700 sm:col-span-2">
            商品简介
            <textarea
              v-model.trim="form.description"
              rows="4"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            />
          </label>

          <div class="space-y-1 text-sm text-stone-700 sm:col-span-2">
            <p>商品详情（富文本）</p>
            <RichTextEditor v-model="form.detailHtml" placeholder="支持图文混排、字号、列表等内容" />
          </div>

          <div class="space-y-2 sm:col-span-2">
            <label class="block text-sm text-stone-700">
              更换封面
              <input
                type="file"
                accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
                class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm"
                :disabled="uploadingCover"
                @change="handleCoverChange"
              />
            </label>
            <p class="text-xs text-stone-500">支持 jpg/jpeg/png/webp，上传成功后会自动更新封面。</p>
            <p v-if="selectedCoverName" class="text-xs text-stone-600">已选文件：{{ selectedCoverName }}</p>
            <p v-if="uploadingCover" class="text-xs text-amber-700">正在上传封面...</p>
            <p v-if="uploadMessage" class="text-xs text-emerald-700">{{ uploadMessage }}</p>
          </div>

          <div v-if="coverPreviewUrl || detailQuery.data.value?.coverImageUrl" class="sm:col-span-2">
            <p class="mb-1 text-xs text-stone-600">封面预览</p>
            <img
              :src="coverPreviewUrl || detailQuery.data.value?.coverImageUrl"
              alt="封面预览"
              class="h-48 w-full rounded-xl border border-stone-200 object-cover sm:w-72"
            />
          </div>
        </div>

        <section class="space-y-3">
          <div class="flex items-center justify-between">
            <h2 class="text-base font-semibold text-stone-900">SKU 列表</h2>
            <button
              type="button"
              class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
              @click="addSku"
            >
              添加 SKU
            </button>
          </div>

          <article v-for="(sku, index) in skus" :key="sku.localId" class="grid gap-2 rounded-xl border border-stone-200 bg-stone-50 p-3 sm:grid-cols-2">
            <label class="text-sm text-stone-700">
              SKU 名称
              <input
                v-model.trim="sku.skuName"
                type="text"
                class="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
                :placeholder="`SKU ${index + 1}`"
              />
            </label>

            <label class="text-sm text-stone-700">
              规格描述
              <input
                v-model.trim="sku.specJson"
                type="text"
                class="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
              />
            </label>

            <label class="text-sm text-stone-700">
              价格（元）
              <input
                v-model.trim="sku.priceYuan"
                type="text"
                inputmode="decimal"
                placeholder="0.00"
                class="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
              />
            </label>

            <label class="text-sm text-stone-700">
              库存
              <input
                v-model.number="sku.stock"
                type="number"
                min="0"
                step="1"
                class="mt-1 w-full rounded-lg border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
              />
            </label>

            <div class="sm:col-span-2">
              <button
                type="button"
                class="rounded-lg border border-rose-300 px-3 py-1.5 text-xs text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="skus.length <= 1"
                @click="removeSku(sku.localId)"
              >
                删除该 SKU
              </button>
            </div>
          </article>
        </section>

        <div class="flex flex-wrap gap-2">
          <button
            type="submit"
            class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="updateMutation.isPending.value"
          >
            {{ updateMutation.isPending.value ? '保存中...' : '保存修改' }}
          </button>

          <button
            v-if="status !== 'ON_SALE'"
            type="button"
            class="rounded-xl border border-stone-300 px-4 py-2 text-sm text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="publishMutation.isPending.value"
            @click="handlePublish"
          >
            {{ publishMutation.isPending.value ? '上架中...' : '上架商品' }}
          </button>

          <button
            v-if="status === 'ON_SALE'"
            type="button"
            class="rounded-xl border border-stone-300 px-4 py-2 text-sm text-stone-700 transition hover:bg-stone-100 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="offShelfMutation.isPending.value"
            @click="handleOffShelf"
          >
            {{ offShelfMutation.isPending.value ? '下架中...' : '下架商品' }}
          </button>
        </div>

        <p v-if="resultError" class="text-sm text-rose-600">{{ resultError }}</p>
        <p v-if="resultMessage" class="text-sm text-emerald-700">{{ resultMessage }}</p>
      </form>
    </ResultState>
  </section>
</template>
