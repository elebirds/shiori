<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { presignProductUpload, uploadByPresignedUrl } from '@/api/media'
import {
  createProductV2,
  publishProductV2,
  type ProductCategoryCode,
  type ProductConditionLevel,
  type ProductTradeMode,
  type SkuInput,
} from '@/api/productV2'
import { ApiBizError } from '@/types/result'

const router = useRouter()

interface DraftSku extends SkuInput {
  localId: string
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

const form = reactive({
  title: '',
  description: '',
  coverObjectKey: '',
  categoryCode: 'TEXTBOOK' as ProductCategoryCode,
  conditionLevel: 'GOOD' as ProductConditionLevel,
  tradeMode: 'MEETUP' as ProductTradeMode,
  campusCode: '',
})

const skus = ref<DraftSku[]>([
  {
    localId: crypto.randomUUID(),
    skuName: '',
    specJson: '',
    priceCent: 100,
    stock: 1,
  },
])

const publishDirectly = ref(true)
const resultMessage = ref('')
const resultError = ref('')
const uploadMessage = ref('')
const uploadingCover = ref(false)
const coverPreviewUrl = ref('')
const selectedCoverName = ref('')

const createMutation = useMutation({
  mutationFn: async () => {
    const created = await createProductV2({
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      coverObjectKey: form.coverObjectKey.trim() || undefined,
      categoryCode: form.categoryCode,
      conditionLevel: form.conditionLevel,
      tradeMode: form.tradeMode,
      campusCode: form.campusCode.trim(),
      skus: skus.value.map((item) => ({
        skuName: item.skuName.trim(),
        specJson: item.specJson?.trim() || undefined,
        priceCent: item.priceCent,
        stock: item.stock,
      })),
    })

    if (publishDirectly.value) {
      await publishProductV2(created.productId)
      return {
        ...created,
        status: 'ON_SALE',
      }
    }
    return created
  },
})

function addSku(): void {
  skus.value.push({
    localId: crypto.randomUUID(),
    skuName: '',
    specJson: '',
    priceCent: 100,
    stock: 1,
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
    return '校区编码不能为空'
  }
  if (skus.value.length === 0) {
    return '请至少添加一个 SKU'
  }

  for (const item of skus.value) {
    if (!item.skuName.trim()) {
      return 'SKU 名称不能为空'
    }
    if (!Number.isInteger(item.priceCent) || item.priceCent <= 0) {
      return 'SKU 价格必须为正整数（分）'
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
    resultMessage.value = `创建成功：${created.productNo}（${created.status}）`
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
      <p class="mt-1 text-sm text-stone-600">v2 商品需填写分类、成色、交易方式与校区信息。</p>
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
          校区编码
          <input
            v-model.trim="form.campusCode"
            type="text"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            placeholder="如：main_campus"
          />
        </label>

        <label class="text-sm text-stone-700 sm:col-span-2">
          商品描述
          <textarea
            v-model.trim="form.description"
            rows="4"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            placeholder="成色、笔记情况、交易地点等"
          />
        </label>

        <div class="space-y-2 sm:col-span-2">
          <label class="block text-sm text-stone-700">
            封面图片（OSS 预签名直传）
            <input
              type="file"
              accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm"
              :disabled="uploadingCover"
              @change="handleCoverChange"
            />
          </label>
          <p class="text-xs text-stone-500">支持 jpg/jpeg/png/webp，上传成功后会自动回填 objectKey。</p>
          <p v-if="selectedCoverName" class="text-xs text-stone-600">已选文件：{{ selectedCoverName }}</p>
          <p v-if="uploadingCover" class="text-xs text-amber-700">正在上传封面...</p>
          <p v-if="uploadMessage" class="text-xs text-emerald-700">{{ uploadMessage }}</p>
        </div>

        <label class="text-sm text-stone-700 sm:col-span-2">
          封面 object key（自动回填，可手工覆盖）
          <input
            v-model.trim="form.coverObjectKey"
            type="text"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 outline-none transition focus:border-amber-500"
            placeholder="product/1001/202603/xxx.jpg"
          />
        </label>

        <div v-if="coverPreviewUrl" class="sm:col-span-2">
          <p class="mb-1 text-xs text-stone-600">封面预览</p>
          <img :src="coverPreviewUrl" alt="封面预览" class="h-48 w-full rounded-xl border border-stone-200 object-cover sm:w-72" />
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
              placeholder='如：{"edition":"7th"}'
            />
          </label>

          <label class="text-sm text-stone-700">
            价格（分）
            <input
              v-model.number="sku.priceCent"
              type="number"
              min="1"
              step="1"
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

      <label class="flex items-center gap-2 text-sm text-stone-700">
        <input v-model="publishDirectly" type="checkbox" class="h-4 w-4 rounded border-stone-300 text-stone-900" />
        创建后立即上架（调用 publish）
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

