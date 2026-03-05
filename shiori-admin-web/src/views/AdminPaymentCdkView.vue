<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'

import { createAdminCdkBatch, type CdkItemResponse, type CreateCdkBatchResponse } from '@/api/adminPayment'
import { ApiBizError } from '@/types/result'

const form = reactive({
  quantity: 20,
  amountYuan: '10.00',
  expireAt: '',
})

const result = ref<CreateCdkBatchResponse | null>(null)
const actionMessage = ref('')
const actionError = ref('')

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

const createMutation = useMutation({
  mutationFn: () => {
    const amountCent = yuanTextToCent(form.amountYuan)
    if (amountCent == null) {
      throw new Error('面额必须大于 0 元，且最多保留两位小数')
    }
    return createAdminCdkBatch({
      quantity: Math.max(1, Math.floor(Number(form.quantity) || 1)),
      amountCent,
      expireAt: form.expireAt || undefined,
    })
  },
  onSuccess: (response) => {
    result.value = response
    actionError.value = ''
    actionMessage.value = `创建成功：批次 ${response.batchNo}，共 ${response.quantity} 个 CDK`
  },
  onError: (error) => {
    actionMessage.value = ''
    actionError.value = error instanceof ApiBizError ? error.message : error instanceof Error ? error.message : '创建失败，请稍后重试'
  },
})

const resultCount = computed(() => result.value?.codes?.length || 0)
const currentAmountCent = computed(() => yuanTextToCent(form.amountYuan))

function formatMoney(amountCent: number): string {
  return `¥${(amountCent / 100).toFixed(2)}`
}

function formatTime(raw?: string): string {
  if (!raw) {
    return '永久有效'
  }
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  return parsed.toLocaleString('zh-CN')
}

function csvEscape(value: string): string {
  return `"${String(value || '').replace(/"/g, '""')}"`
}

async function copyText(text: string, successMessage: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    actionError.value = ''
    actionMessage.value = successMessage
  } catch {
    actionMessage.value = ''
    actionError.value = '复制失败，请手动复制'
  }
}

async function copySingle(code: string): Promise<void> {
  await copyText(code, '单条 CDK 已复制到剪贴板')
}

async function copyAllCodes(codes: CdkItemResponse[]): Promise<void> {
  const content = codes.map((item) => item.code).join('\n')
  await copyText(content, `已复制 ${codes.length} 条 CDK`)
}

function downloadCsv(codes: CdkItemResponse[]): void {
  if (!codes.length) {
    return
  }
  const lines = ['code,codeMask', ...codes.map((item) => `${csvEscape(item.code)},${csvEscape(item.codeMask)}`)]
  const blob = new Blob([`${lines.join('\n')}\n`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `cdk-batch-${result.value?.batchNo || Date.now()}.csv`
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

function handleSubmit(): void {
  if (currentAmountCent.value == null) {
    actionMessage.value = ''
    actionError.value = '面额必须大于 0 元，且最多保留两位小数'
    return
  }
  createMutation.mutate()
}
</script>

<template>
  <section class="space-y-6">
    <header class="rounded-xl border border-blue-100 bg-gradient-to-r from-blue-800 to-blue-600 px-5 py-5 text-white">
      <h1 class="text-2xl font-semibold">支付 / CDK 管理</h1>
      <p class="mt-1 text-sm text-blue-100">创建后明文码只在当前页面展示一次，刷新页面后不会再返回。</p>
    </header>

    <article class="rounded-xl border border-slate-200 bg-white p-5">
      <h2 class="text-lg font-semibold text-slate-900">创建 CDK 批次</h2>
      <div class="mt-4 grid grid-cols-1 gap-3 md:grid-cols-4">
        <label class="space-y-1 text-sm text-slate-600">
          <span>数量（1-500）</span>
          <input v-model.number="form.quantity" type="number" min="1" max="500" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
        </label>
        <label class="space-y-1 text-sm text-slate-600">
          <span>面额（元）</span>
          <input
            v-model.trim="form.amountYuan"
            type="text"
            inputmode="decimal"
            placeholder="0.00"
            class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
        <label class="space-y-1 text-sm text-slate-600 md:col-span-2">
          <span>过期时间（可选）</span>
          <input v-model="form.expireAt" type="datetime-local" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
        </label>
      </div>
      <div class="mt-4 flex items-center gap-3">
        <button
          type="button"
          class="rounded-md bg-blue-700 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-600 disabled:cursor-not-allowed disabled:opacity-65"
          :disabled="createMutation.isPending.value"
          @click="handleSubmit"
        >
          {{ createMutation.isPending.value ? '创建中...' : '创建批次' }}
        </button>
        <span class="text-sm text-slate-500">当前面额：{{ currentAmountCent == null ? '-' : formatMoney(currentAmountCent) }}</span>
      </div>
      <p v-if="actionMessage" class="mt-3 text-sm text-emerald-700">{{ actionMessage }}</p>
      <p v-if="actionError" class="mt-2 text-sm text-rose-600">{{ actionError }}</p>
    </article>

    <article v-if="result" class="rounded-xl border border-slate-200 bg-white p-5">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div class="space-y-1 text-sm text-slate-700">
          <p>批次号：{{ result.batchNo }}</p>
          <p>数量：{{ result.quantity }}，面额：{{ formatMoney(result.amountCent) }}</p>
          <p>过期时间：{{ formatTime(result.expireAt) }}</p>
          <p class="text-rose-600">安全提示：明文码仅此页可见，请立即导出并妥善保管。</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <button class="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-100" @click="copyAllCodes(result.codes)">
            复制全部明文
          </button>
          <button class="rounded border border-slate-300 px-3 py-1.5 text-xs text-slate-700 hover:bg-slate-100" @click="downloadCsv(result.codes)">
            下载 CSV
          </button>
        </div>
      </div>

      <div class="mt-4 overflow-hidden rounded-lg border border-slate-200">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-3 py-2 text-left">序号</th>
              <th class="px-3 py-2 text-left">明文码（一次性）</th>
              <th class="px-3 py-2 text-left">掩码</th>
              <th class="px-3 py-2 text-left">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, index) in result.codes" :key="item.code" class="border-t border-slate-100">
              <td class="px-3 py-2 text-slate-500">{{ index + 1 }}</td>
              <td class="px-3 py-2 font-medium text-slate-900">{{ item.code }}</td>
              <td class="px-3 py-2 text-slate-600">{{ item.codeMask }}</td>
              <td class="px-3 py-2">
                <button class="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700 hover:bg-slate-100" @click="copySingle(item.code)">
                  复制
                </button>
              </td>
            </tr>
            <tr v-if="resultCount === 0">
              <td colspan="4" class="px-3 py-8 text-center text-slate-400">本批次未生成可展示的 CDK 数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
