<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import RichTextEditor from '@/components/RichTextEditor.vue'
import { useProductMeta } from '@/composables/useProductMeta'
import { presignProductUpload, uploadByPresignedUrl } from '@/api/media'
import {
  createProductV2,
  publishProductV2,
  type ProductCategoryCode,
  type ProductSubCategoryCode,
  type ProductConditionLevel,
  type ProductTradeMode,
  type SpecItemInput,
} from '@/api/productV2'
import { ApiBizError } from '@/types/result'

const router = useRouter()

interface SpecDimensionDraft {
  localId: string
  name: string
  valuesText: string
}

interface SkuMatrixRow {
  localKey: string
  specItems: SpecItemInput[]
  priceYuan: string
  stock: number
}

const { campusOptions: metaCampusOptions, categoryOptions: metaCategoryOptions, subCategoriesByCategory } = useProductMeta()

const categoryOptions = computed(() => metaCategoryOptions.value || [])
const subCategoryOptions = computed(() => subCategoriesByCategory.value.get(form.categoryCode) || [])
const campusOptions = computed(() => metaCampusOptions.value || [])

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

const form = reactive({
  title: '',
  description: '',
  detailHtml: '',
  coverObjectKey: '',
  categoryCode: '' as ProductCategoryCode,
  subCategoryCode: '' as ProductSubCategoryCode,
  conditionLevel: 'GOOD' as ProductConditionLevel,
  tradeMode: 'MEETUP' as ProductTradeMode,
  campusCode: '',
})

const dimensions = ref<SpecDimensionDraft[]>([
  {
    localId: crypto.randomUUID(),
    name: '版本',
    valuesText: '标准版',
  },
])

const rows = ref<SkuMatrixRow[]>([])
const batchPriceYuan = ref('')
const batchStock = ref<number | null>(null)

const publishDirectly = ref(true)
const resultMessage = ref('')
const resultError = ref('')
const uploadMessage = ref('')
const uploadingCover = ref(false)
const coverPreviewUrl = ref('')
const selectedCoverName = ref('')

watch(
  dimensions,
  () => {
    syncRowsByDimensions()
  },
  { deep: true, immediate: true },
)

watch(
  categoryOptions,
  (options) => {
    if (!options || options.length === 0) {
      form.categoryCode = ''
      form.subCategoryCode = ''
      return
    }
    if (!options.some((item) => item.categoryCode === form.categoryCode)) {
      form.categoryCode = options[0]?.categoryCode || ''
    }
  },
  { immediate: true },
)

watch(
  subCategoryOptions,
  (options) => {
    if (!options || options.length === 0) {
      form.subCategoryCode = ''
      return
    }
    if (!options.some((item) => item.subCategoryCode === form.subCategoryCode)) {
      form.subCategoryCode = options[0]?.subCategoryCode || ''
    }
  },
  { immediate: true },
)

watch(
  campusOptions,
  (options) => {
    if (!options || options.length === 0) {
      form.campusCode = ''
      return
    }
    if (!options.some((item) => item.campusCode === form.campusCode)) {
      form.campusCode = options[0]?.campusCode || ''
    }
  },
  { immediate: true },
)

const createMutation = useMutation({
  mutationFn: async () => {
    const created = await createProductV2({
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      detailHtml: form.detailHtml.trim() || undefined,
      coverObjectKey: form.coverObjectKey.trim() || undefined,
      categoryCode: form.categoryCode,
      subCategoryCode: form.subCategoryCode,
      conditionLevel: form.conditionLevel,
      tradeMode: form.tradeMode,
      campusCode: form.campusCode.trim(),
      skus: rows.value.map((row) => {
        const priceCent = yuanTextToCent(row.priceYuan)
        if (priceCent == null) {
          throw new Error('价格必须大于 0 元，且最多保留两位小数')
        }
        return {
          specItems: row.specItems,
          priceCent,
          stock: row.stock,
        }
      }),
    })

    if (publishDirectly.value) {
      await publishProductV2(created.productId)
      return created
    }
    return created
  },
})

function normalizeDimensionName(name: string): string {
  return name.trim()
}

function parseDimensionValues(raw: string): string[] {
  const tokens = raw
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
  return Array.from(new Set(tokens))
}

function keyOfSpecItems(specItems: SpecItemInput[]): string {
  return specItems.map((item) => `${item.name}=${item.value}`).join('|')
}

function buildSpecCombinations(): SpecItemInput[][] {
  const normalizedDimensions = dimensions.value
    .map((dimension) => ({
      name: normalizeDimensionName(dimension.name),
      values: parseDimensionValues(dimension.valuesText),
    }))
    .filter((dimension) => dimension.name && dimension.values.length > 0)

  if (normalizedDimensions.length === 0) {
    return []
  }

  const combinations: SpecItemInput[][] = []

  const walk = (depth: number, current: SpecItemInput[]) => {
    if (depth >= normalizedDimensions.length) {
      combinations.push(current.map((item) => ({ ...item })))
      return
    }
    const dimension = normalizedDimensions[depth]
    if (!dimension) {
      return
    }
    dimension.values.forEach((value) => {
      walk(depth + 1, [...current, { name: dimension.name, value }])
    })
  }

  walk(0, [])
  return combinations
}

function syncRowsByDimensions(): void {
  const combinations = buildSpecCombinations()
  const existing = new Map(rows.value.map((row) => [row.localKey, row]))
  rows.value = combinations.map((specItems) => {
    const localKey = keyOfSpecItems(specItems)
    const hit = existing.get(localKey)
    if (hit) {
      return {
        ...hit,
        specItems,
      }
    }
    return {
      localKey,
      specItems,
      priceYuan: '1.00',
      stock: 1,
    }
  })
}

function addDimension(): void {
  dimensions.value.push({
    localId: crypto.randomUUID(),
    name: '',
    valuesText: '',
  })
}

function removeDimension(localId: string): void {
  if (dimensions.value.length <= 1) {
    return
  }
  dimensions.value = dimensions.value.filter((item) => item.localId !== localId)
}

function applyBatch(): void {
  const normalizedPrice = batchPriceYuan.value.trim()
  const hasPrice = normalizedPrice.length > 0
  const hasStock = Number.isInteger(batchStock.value)

  if (!hasPrice && !hasStock) {
    return
  }

  rows.value = rows.value.map((row) => ({
    ...row,
    priceYuan: hasPrice ? normalizedPrice : row.priceYuan,
    stock: hasStock ? Number(batchStock.value) : row.stock,
  }))
}

function formatSpecItems(specItems: SpecItemInput[]): string {
  return specItems.map((item) => `${item.name}：${item.value}`).join(' / ')
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

function validate(): string | null {
  if (!form.title.trim()) {
    return '商品标题不能为空'
  }
  if (!form.categoryCode.trim()) {
    return '分类不能为空'
  }
  if (!form.subCategoryCode.trim()) {
    return '子分类不能为空'
  }
  if (!form.campusCode.trim()) {
    return '交易校区不能为空'
  }

  const normalizedDimensionNames = dimensions.value.map((item) => normalizeDimensionName(item.name)).filter(Boolean)
  if (normalizedDimensionNames.length === 0) {
    return '请至少设置一个规格维度'
  }
  if (new Set(normalizedDimensionNames).size !== normalizedDimensionNames.length) {
    return '规格维度名称不能重复'
  }

  if (rows.value.length === 0) {
    return '请先为规格维度补齐可选值'
  }

  for (const row of rows.value) {
    if (row.specItems.length === 0) {
      return '存在未生成规格组合的 SKU'
    }
    if (yuanTextToCent(row.priceYuan) == null) {
      return `规格「${formatSpecItems(row.specItems)}」价格不合法`
    }
    if (!Number.isInteger(row.stock) || row.stock < 0) {
      return `规格「${formatSpecItems(row.specItems)}」库存必须为非负整数`
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

async function submit(): Promise<void> {
  resultError.value = ''
  resultMessage.value = ''

  const validationError = validate()
  if (validationError) {
    resultError.value = validationError
    return
  }

  try {
    const created = await createMutation.mutateAsync()
    resultMessage.value = `发布成功：${created.productNo}`
    void router.push(`/products/${created.productId}`)
  } catch (error) {
    if (error instanceof ApiBizError) {
      resultError.value = error.message
    } else if (error instanceof Error) {
      resultError.value = error.message
    } else {
      resultError.value = '创建商品失败'
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
    <header class="rounded-2xl border border-stone-200 bg-white/90 p-4">
      <h1 class="font-display text-2xl text-stone-900">发布商品</h1>
      <p class="mt-1 text-sm text-stone-600">先设置规格维度，再自动生成可售组合，价格和库存一目了然。</p>
    </header>

    <form class="space-y-4 rounded-2xl border border-stone-200 bg-white/95 p-5" @submit.prevent="submit">
      <div class="grid gap-3 sm:grid-cols-2">
        <label class="text-sm text-stone-700 sm:col-span-2">
          商品标题
          <input
            v-model.trim="form.title"
            type="text"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            placeholder="例如：高等数学（第七版）"
          />
        </label>

        <label class="text-sm text-stone-700">
          分类
          <select
            v-model="form.categoryCode"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
          >
            <option v-if="categoryOptions.length === 0" value="">暂无可用分类</option>
            <option v-for="item in categoryOptions" :key="item.categoryCode" :value="item.categoryCode">{{ item.categoryName }}</option>
          </select>
        </label>

        <label class="text-sm text-stone-700">
          子分类
          <select
            v-model="form.subCategoryCode"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
          >
            <option v-if="subCategoryOptions.length === 0" value="">暂无可用子分类</option>
            <option v-for="item in subCategoryOptions" :key="item.subCategoryCode" :value="item.subCategoryCode">{{ item.subCategoryName }}</option>
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
          <select
            v-model="form.campusCode"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
          >
            <option v-if="campusOptions.length === 0" value="">暂无可用校区</option>
            <option v-for="item in campusOptions" :key="item.campusCode" :value="item.campusCode">{{ item.campusName }}</option>
          </select>
        </label>

        <label class="text-sm text-stone-700 sm:col-span-2">
          商品简介
          <textarea
            v-model.trim="form.description"
            rows="4"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            placeholder="可补充成色细节、交易地点和时间等"
          />
        </label>

        <div class="space-y-1 text-sm text-stone-700 sm:col-span-2">
          <p>商品详情（图文）</p>
          <RichTextEditor v-model="form.detailHtml" placeholder="支持图文混排、字号、列表等内容" />
        </div>

        <div class="space-y-2 sm:col-span-2">
          <label class="block text-sm text-stone-700">
            封面图片
            <input
              type="file"
              accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm"
              :disabled="uploadingCover"
              @change="handleCoverChange"
            />
          </label>
          <p class="text-xs text-stone-500">支持 jpg/jpeg/png/webp，上传成功后自动绑定封面。</p>
          <p v-if="selectedCoverName" class="text-xs text-stone-600">已选文件：{{ selectedCoverName }}</p>
          <p v-if="uploadingCover" class="text-xs text-amber-700">正在上传封面...</p>
          <p v-if="uploadMessage" class="text-xs text-emerald-700">{{ uploadMessage }}</p>
        </div>

        <div v-if="coverPreviewUrl" class="sm:col-span-2">
          <p class="mb-1 text-xs text-stone-600">封面预览</p>
          <img :src="coverPreviewUrl" alt="封面预览" class="h-48 w-full rounded-xl border border-stone-200 object-cover sm:w-72" />
        </div>
      </div>

      <section class="space-y-3 rounded-xl border border-stone-200 bg-stone-50 p-4">
        <div class="flex items-center justify-between">
          <h2 class="text-base font-semibold text-stone-900">规格维度</h2>
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
            @click="addDimension"
          >
            新增维度
          </button>
        </div>

        <article
          v-for="(dimension, index) in dimensions"
          :key="dimension.localId"
          class="grid gap-2 rounded-xl border border-stone-200 bg-white p-3 sm:grid-cols-[140px_1fr_auto]"
        >
          <input
            v-model.trim="dimension.name"
            type="text"
            class="rounded-lg border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            :placeholder="`维度 ${index + 1}`"
          />
          <input
            v-model="dimension.valuesText"
            type="text"
            class="rounded-lg border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            placeholder="可选值，多个值用逗号分隔，例如：标准版, 加练版"
          />
          <button
            type="button"
            class="rounded-lg border border-rose-300 px-3 py-2 text-xs text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-60"
            :disabled="dimensions.length <= 1"
            @click="removeDimension(dimension.localId)"
          >
            删除
          </button>
        </article>
      </section>

      <section class="space-y-3 rounded-xl border border-stone-200 bg-white p-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="text-base font-semibold text-stone-900">SKU 组合矩阵</h2>
          <div class="flex flex-wrap items-center gap-2">
            <input
              v-model.trim="batchPriceYuan"
              type="text"
              inputmode="decimal"
              placeholder="批量价格（元）"
              class="w-36 rounded-lg border border-stone-300 px-3 py-1.5 text-sm"
            />
            <input
              v-model.number="batchStock"
              type="number"
              min="0"
              step="1"
              placeholder="批量库存"
              class="w-28 rounded-lg border border-stone-300 px-3 py-1.5 text-sm"
            />
            <button
              type="button"
              class="rounded-lg border border-stone-300 px-3 py-1.5 text-sm text-stone-700 transition hover:bg-stone-100"
              @click="applyBatch"
            >
              批量应用
            </button>
          </div>
        </div>

        <div v-if="rows.length === 0" class="rounded-xl border border-dashed border-stone-300 p-6 text-center text-sm text-stone-500">
          先填写规格维度与可选值，即可自动生成 SKU 组合。
        </div>

        <div v-else class="overflow-x-auto">
          <table class="min-w-full text-sm">
            <thead>
              <tr class="border-b border-stone-200 text-left text-stone-600">
                <th class="px-2 py-2">规格组合</th>
                <th class="px-2 py-2">价格（元）</th>
                <th class="px-2 py-2">库存</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.localKey" class="border-b border-stone-100 last:border-0">
                <td class="px-2 py-2 text-stone-800">{{ formatSpecItems(row.specItems) }}</td>
                <td class="px-2 py-2">
                  <input
                    v-model.trim="row.priceYuan"
                    type="text"
                    inputmode="decimal"
                    class="w-32 rounded-lg border border-stone-300 px-3 py-1.5"
                    placeholder="0.00"
                  />
                </td>
                <td class="px-2 py-2">
                  <input
                    v-model.number="row.stock"
                    type="number"
                    min="0"
                    step="1"
                    class="w-24 rounded-lg border border-stone-300 px-3 py-1.5"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <label class="flex items-center gap-2 text-sm text-stone-700">
        <input v-model="publishDirectly" type="checkbox" class="h-4 w-4 rounded border-stone-300 text-stone-900" />
        创建后立即上架
      </label>

      <div class="flex gap-2">
        <button
          type="submit"
          class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
          :disabled="createMutation.isPending.value"
        >
          {{ createMutation.isPending.value ? '提交中...' : '创建商品' }}
        </button>
      </div>

      <p v-if="resultError" class="text-sm text-rose-600">{{ resultError }}</p>
      <p v-if="resultMessage" class="text-sm text-emerald-700">{{ resultMessage }}</p>
    </form>
  </section>
</template>
