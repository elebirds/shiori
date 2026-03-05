<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getWalletBalance, redeemCdk } from '@/api/payment'
import { resolvePaymentErrorMessage } from '@/utils/paymentError'

const router = useRouter()
const cdkCode = ref('')
const redeemMessage = ref('')
const redeemError = ref('')

const walletQuery = useQuery({
  queryKey: ['payment-wallet-balance'],
  queryFn: () => getWalletBalance(),
})

const redeemMutation = useMutation({
  mutationFn: () => redeemCdk({ code: cdkCode.value.trim() }),
  onSuccess: async (response) => {
    redeemError.value = ''
    redeemMessage.value = `兑换成功，已入账 ${formatMoney(response.redeemAmountCent)}`
    cdkCode.value = ''
    await walletQuery.refetch()
  },
  onError: (error) => {
    redeemMessage.value = ''
    redeemError.value = resolvePaymentErrorMessage(error, 'CDK 兑换失败，请稍后重试')
  },
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

function formatMoney(priceCent: number): string {
  return `¥ ${(priceCent / 100).toFixed(2)}`
}

function handleRedeem(): void {
  redeemMessage.value = ''
  redeemError.value = ''
  if (!cdkCode.value.trim()) {
    redeemError.value = '请输入 CDK 兑换码'
    return
  }
  redeemMutation.mutate()
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
      <p class="mt-2 text-sm text-blue-50/90">用于订单余额支付。支持 CDK 快捷充值，资金变化也会持续补充更完整记录。</p>
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

      <div class="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
        <article class="rounded-2xl border border-blue-100 bg-white p-5 shadow-sm">
          <h2 class="text-lg font-semibold text-[var(--shiori-pay-ink)]">CDK 快捷兑换</h2>
          <p class="mt-1 text-sm text-[var(--shiori-pay-mute)]">输入管理员发放的 CDK 后立即入账可用余额。</p>
          <div class="mt-4 flex flex-col gap-3 sm:flex-row">
            <input
              v-model.trim="cdkCode"
              type="text"
              maxlength="128"
              placeholder="请输入 CDK 兑换码"
              class="h-11 flex-1 rounded-xl border border-blue-200 px-3 text-sm text-[var(--shiori-pay-ink)] outline-none transition focus:border-[var(--shiori-pay-blue-600)] focus:ring-2 focus:ring-[var(--shiori-pay-blue-500)]/20"
            />
            <button
              type="button"
              class="h-11 rounded-xl bg-[var(--shiori-pay-blue-700)] px-5 text-sm font-medium text-white transition hover:bg-[var(--shiori-pay-blue-800)] disabled:cursor-not-allowed disabled:opacity-65"
              :disabled="redeemMutation.isPending.value"
              @click="handleRedeem"
            >
              {{ redeemMutation.isPending.value ? '兑换中...' : '立即兑换' }}
            </button>
          </div>
          <p v-if="redeemMessage" class="mt-3 text-sm text-emerald-600">{{ redeemMessage }}</p>
          <p v-if="redeemError" class="mt-3 text-sm text-rose-600">{{ redeemError }}</p>
        </article>

        <article class="rounded-2xl border border-dashed border-blue-200 bg-[var(--shiori-pay-surface)] p-5">
          <h2 class="text-lg font-semibold text-[var(--shiori-pay-ink)]">资金流水</h2>
          <p class="mt-2 text-sm text-[var(--shiori-pay-mute)]">
            交易流水正在完善中，后续会提供按时间筛选、收入支出分类和明细追踪。
          </p>
          <button
            type="button"
            class="mt-4 rounded-xl border border-blue-200 px-4 py-2 text-sm text-[var(--shiori-pay-blue-700)] transition hover:bg-blue-50"
            @click="router.push('/orders')"
          >
            去查看订单支付状态
          </button>
        </article>
      </div>
    </template>
  </section>
</template>
