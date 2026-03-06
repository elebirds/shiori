<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, reactive } from 'vue'

import { getWalletBalance, listWalletLedger } from '@/api/payment'

const ledgerDraft = reactive({
  bizType: '',
  changeType: '',
  bizNo: '',
  createdFrom: '',
  createdTo: '',
})

const ledgerApplied = reactive({
  bizType: '',
  changeType: '',
  bizNo: '',
  createdFrom: '',
  createdTo: '',
})

const ledgerPager = reactive({
  page: 1,
  size: 10,
})

const walletQuery = useQuery({
  queryKey: ['payment-wallet-balance'],
  queryFn: () => getWalletBalance(),
})

const ledgerQuery = useQuery({
  queryKey: computed(() => [
    'payment-wallet-ledger',
    ledgerPager.page,
    ledgerPager.size,
    ledgerApplied.bizType,
    ledgerApplied.changeType,
    ledgerApplied.bizNo,
    ledgerApplied.createdFrom,
    ledgerApplied.createdTo,
  ]),
  queryFn: () =>
    listWalletLedger({
      page: ledgerPager.page,
      size: ledgerPager.size,
      bizType: ledgerApplied.bizType || undefined,
      changeType: ledgerApplied.changeType || undefined,
      bizNo: ledgerApplied.bizNo || undefined,
      createdFrom: ledgerApplied.createdFrom || undefined,
      createdTo: ledgerApplied.createdTo || undefined,
    }),
})

const wallet = computed(() => {
  return (
    walletQuery.data.value || {
      availableBalanceCent: 0,
      frozenBalanceCent: 0,
      totalBalanceCent: 0,
    }
  )
})

const ledgerItems = computed(() => ledgerQuery.data.value?.items || [])
const ledgerTotal = computed(() => ledgerQuery.data.value?.total || 0)
const ledgerTotalPages = computed(() => Math.max(1, Math.ceil(ledgerTotal.value / ledgerPager.size)))

const CHANGE_TYPE_TEXT: Record<string, string> = {
  CDK_REDEEM: 'CDK 兑换入账',
  ORDER_PAY_RESERVE_BUYER: '订单支付冻结',
  ORDER_PAY_SETTLE_BUYER: '订单支付扣款',
  ORDER_PAY_SETTLE_SELLER: '订单货款入账',
  ORDER_REFUND_BUYER: '订单退款入账',
  ORDER_REFUND_SELLER: '订单退款扣款',
}

function formatMoney(priceCent: number): string {
  return `¥ ${(priceCent / 100).toFixed(2)}`
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

function formatDelta(amountCent: number): string {
  const sign = amountCent > 0 ? '+' : ''
  return `${sign}${formatMoney(amountCent).replace('¥ ', '¥')}`
}

function changeTypeText(changeType: string): string {
  return CHANGE_TYPE_TEXT[changeType] || changeType
}

function deltaClass(amountCent: number): string {
  if (amountCent > 0) {
    return 'text-emerald-700'
  }
  if (amountCent < 0) {
    return 'text-rose-700'
  }
  return 'text-stone-500'
}

function applyLedgerFilter(): void {
  ledgerPager.page = 1
  ledgerApplied.bizType = ledgerDraft.bizType.trim()
  ledgerApplied.changeType = ledgerDraft.changeType.trim()
  ledgerApplied.bizNo = ledgerDraft.bizNo.trim()
  ledgerApplied.createdFrom = ledgerDraft.createdFrom
  ledgerApplied.createdTo = ledgerDraft.createdTo
}

function clearLedgerFilter(): void {
  ledgerDraft.bizType = ''
  ledgerDraft.changeType = ''
  ledgerDraft.bizNo = ''
  ledgerDraft.createdFrom = ''
  ledgerDraft.createdTo = ''
  applyLedgerFilter()
}
</script>

<template>
  <section class="space-y-5">
    <header
      class="overflow-hidden rounded-3xl border border-[var(--shiori-pay-blue-500)]/25 px-5 py-6 text-white shadow-[var(--shiori-pay-shadow)] shiori-pay-rise"
      style="background: linear-gradient(128deg, var(--shiori-pay-blue-900) 0%, var(--shiori-pay-blue-700) 58%, var(--shiori-pay-blue-500) 100%);"
    >
      <p class="text-xs uppercase tracking-[0.26em] text-blue-100/90">钱包中心</p>
      <h1 class="mt-2 font-display text-3xl tracking-wide">余额钱包</h1>
      <p class="mt-2 text-sm text-blue-50/90">用于订单余额支付与退款回流，资金流水支持按业务号与变更类型检索。</p>
    </header>

    <div v-if="walletQuery.isLoading.value" class="rounded-2xl border border-blue-100 bg-[var(--shiori-pay-surface)] p-6 text-sm text-[var(--shiori-pay-mute)]">
      正在加载钱包余额...
    </div>

    <div
      v-else-if="walletQuery.error.value instanceof Error"
      class="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700"
    >
      {{ walletQuery.error.value.message }}
    </div>

    <template v-else>
      <div class="grid gap-3 sm:grid-cols-3 shiori-pay-rise-delay">
        <article class="rounded-2xl border border-blue-100 bg-white/95 p-4 shadow-sm">
          <p class="text-xs uppercase tracking-[0.18em] text-[var(--shiori-pay-mute)]">可用余额</p>
          <p class="mt-2 text-2xl font-semibold text-[var(--shiori-pay-blue-800)]">{{ formatMoney(wallet.availableBalanceCent) }}</p>
        </article>
        <article class="rounded-2xl border border-blue-100 bg-white/95 p-4 shadow-sm">
          <p class="text-xs uppercase tracking-[0.18em] text-[var(--shiori-pay-mute)]">冻结余额</p>
          <p class="mt-2 text-2xl font-semibold text-[var(--shiori-pay-ink)]">{{ formatMoney(wallet.frozenBalanceCent) }}</p>
        </article>
        <article class="rounded-2xl border border-blue-100 bg-white/95 p-4 shadow-sm">
          <p class="text-xs uppercase tracking-[0.18em] text-[var(--shiori-pay-mute)]">总余额</p>
          <p class="mt-2 text-2xl font-semibold text-[var(--shiori-pay-blue-900)]">{{ formatMoney(wallet.totalBalanceCent) }}</p>
        </article>
      </div>

      <div>
        <article class="rounded-2xl border border-blue-100 bg-white p-5 shadow-sm">
          <h2 class="text-lg font-semibold text-[var(--shiori-pay-ink)]">资金流水</h2>

          <div class="mt-3 grid gap-2 md:grid-cols-2">
            <select
              v-model="ledgerDraft.bizType"
              class="rounded-xl border border-blue-200 px-3 py-2 text-sm outline-none transition focus:border-[var(--shiori-pay-blue-600)]"
            >
              <option value="">全部业务类型</option>
              <option value="ORDER">ORDER</option>
              <option value="CDK">CDK</option>
            </select>
            <select
              v-model="ledgerDraft.changeType"
              class="rounded-xl border border-blue-200 px-3 py-2 text-sm outline-none transition focus:border-[var(--shiori-pay-blue-600)]"
            >
              <option value="">全部变更类型</option>
              <option value="CDK_REDEEM">CDK_REDEEM</option>
              <option value="ORDER_PAY_RESERVE_BUYER">ORDER_PAY_RESERVE_BUYER</option>
              <option value="ORDER_PAY_SETTLE_BUYER">ORDER_PAY_SETTLE_BUYER</option>
              <option value="ORDER_PAY_SETTLE_SELLER">ORDER_PAY_SETTLE_SELLER</option>
              <option value="ORDER_REFUND_BUYER">ORDER_REFUND_BUYER</option>
              <option value="ORDER_REFUND_SELLER">ORDER_REFUND_SELLER</option>
            </select>
            <input
              v-model.trim="ledgerDraft.bizNo"
              type="text"
              placeholder="业务号（订单号/批次号）"
              class="rounded-xl border border-blue-200 px-3 py-2 text-sm outline-none transition focus:border-[var(--shiori-pay-blue-600)]"
            />
            <div class="flex gap-2">
              <button
                type="button"
                class="flex-1 rounded-xl bg-[var(--shiori-pay-blue-700)] px-3 py-2 text-sm text-white transition hover:bg-[var(--shiori-pay-blue-800)]"
                @click="applyLedgerFilter"
              >
                查询
              </button>
              <button
                type="button"
                class="rounded-xl border border-blue-200 px-3 py-2 text-sm text-[var(--shiori-pay-blue-700)] transition hover:bg-blue-50"
                @click="clearLedgerFilter"
              >
                清空
              </button>
            </div>
            <input
              v-model="ledgerDraft.createdFrom"
              type="datetime-local"
              class="rounded-xl border border-blue-200 px-3 py-2 text-sm outline-none transition focus:border-[var(--shiori-pay-blue-600)]"
            />
            <input
              v-model="ledgerDraft.createdTo"
              type="datetime-local"
              class="rounded-xl border border-blue-200 px-3 py-2 text-sm outline-none transition focus:border-[var(--shiori-pay-blue-600)]"
            />
          </div>

          <p v-if="ledgerQuery.error.value instanceof Error" class="mt-3 text-sm text-rose-600">{{ ledgerQuery.error.value.message }}</p>

          <div class="mt-3 max-h-96 overflow-auto rounded-xl border border-blue-100">
            <table class="w-full text-left text-xs">
              <thead class="bg-blue-50 text-[var(--shiori-pay-mute)]">
                <tr>
                  <th class="px-3 py-2">时间</th>
                  <th class="px-3 py-2">变更类型</th>
                  <th class="px-3 py-2">业务</th>
                  <th class="px-3 py-2">可用变更</th>
                  <th class="px-3 py-2">冻结变更</th>
                  <th class="px-3 py-2">余额快照</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in ledgerItems" :key="item.id" class="border-t border-blue-50 text-[var(--shiori-pay-ink)]">
                  <td class="px-3 py-2">{{ formatTime(item.createdAt) }}</td>
                  <td class="px-3 py-2">{{ changeTypeText(item.changeType) }}</td>
                  <td class="px-3 py-2">{{ item.bizType }} / {{ item.bizNo }}</td>
                  <td class="px-3 py-2 font-medium" :class="deltaClass(item.deltaAvailableCent)">{{ formatDelta(item.deltaAvailableCent) }}</td>
                  <td class="px-3 py-2 font-medium" :class="deltaClass(item.deltaFrozenCent)">{{ formatDelta(item.deltaFrozenCent) }}</td>
                  <td class="px-3 py-2 text-[11px]">
                    可用 {{ formatMoney(item.availableAfterCent) }}<br />
                    冻结 {{ formatMoney(item.frozenAfterCent) }}
                  </td>
                </tr>
                <tr v-if="ledgerItems.length === 0 && !ledgerQuery.isLoading.value">
                  <td colspan="6" class="px-3 py-8 text-center text-xs text-[var(--shiori-pay-mute)]">暂无流水记录</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="mt-3 flex items-center justify-between text-sm">
            <span class="text-[var(--shiori-pay-mute)]">第 {{ ledgerPager.page }} / {{ ledgerTotalPages }} 页，共 {{ ledgerTotal }} 条</span>
            <div class="flex gap-2">
              <button
                type="button"
                class="rounded-lg border border-blue-200 px-3 py-1.5 text-[var(--shiori-pay-blue-700)] transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="ledgerPager.page <= 1 || ledgerQuery.isFetching.value"
                @click="ledgerPager.page -= 1"
              >
                上一页
              </button>
              <button
                type="button"
                class="rounded-lg border border-blue-200 px-3 py-1.5 text-[var(--shiori-pay-blue-700)] transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="ledgerPager.page >= ledgerTotalPages || ledgerQuery.isFetching.value"
                @click="ledgerPager.page += 1"
              >
                下一页
              </button>
            </div>
          </div>
        </article>
      </div>
    </template>
  </section>
</template>
