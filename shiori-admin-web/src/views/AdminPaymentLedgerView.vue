<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'

import {
  listAdminReconcileIssues,
  listAdminWalletLedgers,
  updateAdminReconcileIssueStatus,
  type AdminWalletLedgerItemResponse,
  type ReconcileIssueResponse,
} from '@/api/adminPayment'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()

const ledgerPage = ref(1)
const ledgerSize = ref(10)
const ledgerUserIdInput = ref('')
const ledgerBizTypeInput = ref('')
const ledgerBizNoInput = ref('')
const ledgerChangeTypeInput = ref('')
const ledgerCreatedFromInput = ref('')
const ledgerCreatedToInput = ref('')
const ledgerUserId = ref('')
const ledgerBizType = ref('')
const ledgerBizNo = ref('')
const ledgerChangeType = ref('')
const ledgerCreatedFrom = ref('')
const ledgerCreatedTo = ref('')

const issuePage = ref(1)
const issueSize = ref(10)
const issueStatusInput = ref('')
const issueTypeInput = ref('')
const issueStatus = ref('')
const issueType = ref('')

const actionMessage = ref('')
const actionError = ref('')

const ledgerQuery = useQuery({
  queryKey: computed(() => [
    'admin-wallet-ledgers-v2',
    ledgerPage.value,
    ledgerSize.value,
    ledgerUserId.value,
    ledgerBizType.value,
    ledgerBizNo.value,
    ledgerChangeType.value,
    ledgerCreatedFrom.value,
    ledgerCreatedTo.value,
  ]),
  queryFn: () =>
    listAdminWalletLedgers({
      page: ledgerPage.value,
      size: ledgerSize.value,
      userId: parseOptionalPositiveInt(ledgerUserId.value),
      bizType: ledgerBizType.value || undefined,
      bizNo: ledgerBizNo.value || undefined,
      changeType: ledgerChangeType.value || undefined,
      createdFrom: ledgerCreatedFrom.value || undefined,
      createdTo: ledgerCreatedTo.value || undefined,
    }),
})

const issueQuery = useQuery({
  queryKey: computed(() => ['admin-reconcile-issues-v2', issuePage.value, issueSize.value, issueStatus.value, issueType.value]),
  queryFn: () =>
    listAdminReconcileIssues({
      page: issuePage.value,
      size: issueSize.value,
      status: issueStatus.value || undefined,
      issueType: issueType.value || undefined,
    }),
})

const updateIssueMutation = useMutation({
  mutationFn: ({ issueNo, fromStatus, toStatus }: { issueNo: string; fromStatus: string; toStatus: string }) =>
    updateAdminReconcileIssueStatus(issueNo, { fromStatus, toStatus }),
  onSuccess: async () => {
    actionError.value = ''
    actionMessage.value = '状态更新成功'
    await queryClient.invalidateQueries({ queryKey: ['admin-reconcile-issues-v2'] })
  },
  onError: (error) => {
    actionMessage.value = ''
    actionError.value = error instanceof ApiBizError ? error.message : '状态更新失败'
  },
})

const ledgerTotalPage = computed(() => {
  const total = ledgerQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / ledgerSize.value), 1)
})

const issueTotalPage = computed(() => {
  const total = issueQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / issueSize.value), 1)
})

function formatMoney(amountCent: number): string {
  return `¥${(amountCent / 100).toFixed(2)}`
}

function formatDelta(amountCent: number): string {
  const sign = amountCent > 0 ? '+' : ''
  return `${sign}${formatMoney(amountCent)}`
}

function formatTime(raw?: string): string {
  if (!raw) {
    return '-'
  }
  const parsed = new Date(raw)
  if (Number.isNaN(parsed.getTime())) {
    return raw
  }
  return parsed.toLocaleString('zh-CN')
}

function deltaClass(amountCent: number): string {
  if (amountCent > 0) {
    return 'text-emerald-700'
  }
  if (amountCent < 0) {
    return 'text-rose-700'
  }
  return 'text-slate-500'
}

function issueStatusClass(status: string): string {
  if (status === 'NEW') {
    return 'bg-amber-100 text-amber-700'
  }
  if (status === 'ACKED') {
    return 'bg-blue-100 text-blue-700'
  }
  if (status === 'RESOLVED') {
    return 'bg-emerald-100 text-emerald-700'
  }
  return 'bg-slate-100 text-slate-700'
}

function issueStatusText(status: string): string {
  if (status === 'NEW') {
    return '新建'
  }
  if (status === 'ACKED') {
    return '已确认'
  }
  if (status === 'RESOLVED') {
    return '已处理'
  }
  return status
}

function handleLedgerSearch(): void {
  ledgerPage.value = 1
  ledgerUserId.value = ledgerUserIdInput.value.trim()
  ledgerBizType.value = ledgerBizTypeInput.value.trim()
  ledgerBizNo.value = ledgerBizNoInput.value.trim()
  ledgerChangeType.value = ledgerChangeTypeInput.value.trim()
  ledgerCreatedFrom.value = ledgerCreatedFromInput.value
  ledgerCreatedTo.value = ledgerCreatedToInput.value
}

function handleIssueSearch(): void {
  issuePage.value = 1
  issueStatus.value = issueStatusInput.value.trim()
  issueType.value = issueTypeInput.value.trim()
}

function parseOptionalPositiveInt(raw: string): number | undefined {
  const normalized = raw.trim()
  if (!normalized) {
    return undefined
  }
  const parsed = Number(normalized)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return undefined
  }
  return Math.floor(parsed)
}

async function toAcked(item: ReconcileIssueResponse): Promise<void> {
  actionMessage.value = ''
  actionError.value = ''
  try {
    await updateIssueMutation.mutateAsync({
      issueNo: item.issueNo,
      fromStatus: item.status,
      toStatus: 'ACKED',
    })
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function toResolved(item: ReconcileIssueResponse): Promise<void> {
  actionMessage.value = ''
  actionError.value = ''
  try {
    await updateIssueMutation.mutateAsync({
      issueNo: item.issueNo,
      fromStatus: item.status,
      toStatus: 'RESOLVED',
    })
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

function parseDetail(detailJson?: string): string {
  if (!detailJson) {
    return '-'
  }
  if (detailJson.length <= 120) {
    return detailJson
  }
  return `${detailJson.slice(0, 120)}...`
}

function ledgerRowKey(item: AdminWalletLedgerItemResponse): string {
  return `${item.id}-${item.userId}`
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">资金流水与对账告警</h1>
      <p class="mt-1 text-sm text-slate-500">按用户、业务号检索资金流水，并跟踪每日 T+1 对账异常。</p>
    </div>

    <p v-if="actionMessage" class="text-sm text-emerald-700">{{ actionMessage }}</p>
    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>

    <article class="space-y-4 rounded-xl border border-slate-200 bg-white p-4">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold text-slate-900">钱包流水</h2>
      </div>

      <div class="grid grid-cols-1 gap-3 md:grid-cols-7">
        <input v-model="ledgerUserIdInput" placeholder="userId" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="ledgerBizTypeInput" placeholder="bizType" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="ledgerBizNoInput" placeholder="bizNo" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="ledgerChangeTypeInput" placeholder="changeType" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="ledgerCreatedFromInput" type="datetime-local" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <input v-model="ledgerCreatedToInput" type="datetime-local" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="handleLedgerSearch">查询</button>
      </div>

      <div class="overflow-hidden rounded-xl border border-slate-200">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-3 py-2 text-left">时间</th>
              <th class="px-3 py-2 text-left">用户</th>
              <th class="px-3 py-2 text-left">业务</th>
              <th class="px-3 py-2 text-left">变更类型</th>
              <th class="px-3 py-2 text-left">可用变更</th>
              <th class="px-3 py-2 text-left">冻结变更</th>
              <th class="px-3 py-2 text-left">余额快照</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in ledgerQuery.data.value?.items || []" :key="ledgerRowKey(item)" class="border-t border-slate-100">
              <td class="px-3 py-2">{{ formatTime(item.createdAt) }}</td>
              <td class="px-3 py-2">{{ item.userId }}</td>
              <td class="px-3 py-2">{{ item.bizType }} / {{ item.bizNo }}</td>
              <td class="px-3 py-2">{{ item.changeType }}</td>
              <td class="px-3 py-2 font-medium" :class="deltaClass(item.deltaAvailableCent)">{{ formatDelta(item.deltaAvailableCent) }}</td>
              <td class="px-3 py-2 font-medium" :class="deltaClass(item.deltaFrozenCent)">{{ formatDelta(item.deltaFrozenCent) }}</td>
              <td class="px-3 py-2 text-xs">可用 {{ formatMoney(item.availableAfterCent) }} / 冻结 {{ formatMoney(item.frozenAfterCent) }}</td>
            </tr>
            <tr v-if="(ledgerQuery.data.value?.items || []).length === 0 && !ledgerQuery.isLoading.value">
              <td colspan="7" class="px-3 py-8 text-center text-slate-400">暂无流水数据</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex items-center justify-end gap-2 text-sm">
        <button class="rounded border px-2 py-1" :disabled="ledgerPage <= 1" @click="ledgerPage -= 1">上一页</button>
        <span>{{ ledgerPage }} / {{ ledgerTotalPage }}</span>
        <button class="rounded border px-2 py-1" :disabled="ledgerPage >= ledgerTotalPage" @click="ledgerPage += 1">下一页</button>
      </div>
    </article>

    <article class="space-y-4 rounded-xl border border-slate-200 bg-white p-4">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold text-slate-900">对账异常</h2>
      </div>

      <div class="grid grid-cols-1 gap-3 md:grid-cols-[1fr_1fr_auto]">
        <select v-model="issueStatusInput" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部状态</option>
          <option value="NEW">NEW</option>
          <option value="ACKED">ACKED</option>
          <option value="RESOLVED">RESOLVED</option>
        </select>
        <input v-model="issueTypeInput" placeholder="issueType" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="handleIssueSearch">查询</button>
      </div>

      <div class="overflow-hidden rounded-xl border border-slate-200">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-3 py-2 text-left">问题号</th>
              <th class="px-3 py-2 text-left">类型</th>
              <th class="px-3 py-2 text-left">状态</th>
              <th class="px-3 py-2 text-left">严重度</th>
              <th class="px-3 py-2 text-left">详情</th>
              <th class="px-3 py-2 text-left">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in issueQuery.data.value?.items || []" :key="item.issueNo" class="border-t border-slate-100 align-top">
              <td class="px-3 py-2">
                <p class="font-medium text-slate-900">{{ item.issueNo }}</p>
                <p class="mt-1 text-xs text-slate-500">bizNo {{ item.bizNo || '-' }}</p>
                <p class="mt-1 text-xs text-slate-500">创建 {{ formatTime(item.createdAt) }}</p>
              </td>
              <td class="px-3 py-2">{{ item.issueType }}</td>
              <td class="px-3 py-2">
                <span class="rounded-full px-2 py-1 text-xs font-semibold" :class="issueStatusClass(item.status)">
                  {{ issueStatusText(item.status) }}
                </span>
              </td>
              <td class="px-3 py-2">{{ item.severity }}</td>
              <td class="px-3 py-2 text-xs text-slate-600">{{ parseDetail(item.detailJson) }}</td>
              <td class="px-3 py-2">
                <div class="flex flex-wrap gap-1">
                  <button
                    class="rounded bg-indigo-700 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="item.status !== 'NEW' || updateIssueMutation.isPending.value"
                    @click="toAcked(item)"
                  >
                    确认
                  </button>
                  <button
                    class="rounded bg-emerald-700 px-2 py-1 text-xs text-white disabled:cursor-not-allowed disabled:opacity-60"
                    :disabled="item.status === 'RESOLVED' || updateIssueMutation.isPending.value"
                    @click="toResolved(item)"
                  >
                    置为已处理
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="(issueQuery.data.value?.items || []).length === 0 && !issueQuery.isLoading.value">
              <td colspan="6" class="px-3 py-8 text-center text-slate-400">暂无对账异常</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="flex items-center justify-end gap-2 text-sm">
        <button class="rounded border px-2 py-1" :disabled="issuePage <= 1" @click="issuePage -= 1">上一页</button>
        <span>{{ issuePage }} / {{ issueTotalPage }}</span>
        <button class="rounded border px-2 py-1" :disabled="issuePage >= issueTotalPage" @click="issuePage += 1">下一页</button>
      </div>
    </article>
  </section>
</template>
