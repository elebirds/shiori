<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listMyAddresses, type UserAddress } from '@/api/auth'
import { getWalletBalance, redeemCdk } from '@/api/payment'
import {
  getOrderDetailV2,
  payOrderV2,
  updateOrderFulfillmentV2,
  type OrderFulfillmentMode,
  type OrderStatus,
} from '@/api/orderV2'
import { useChatStore } from '@/stores/chat'
import { ApiBizError } from '@/types/result'
import { resolvePaymentErrorMessage } from '@/utils/paymentError'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const chatStore = useChatStore()

const cdkCode = ref('')
const redeemMessage = ref('')
const redeemError = ref('')
const fulfillmentMode = ref<OrderFulfillmentMode | ''>('')
const selectedAddressId = ref<number | null>(null)
const fulfillmentError = ref('')

const orderNo = computed(() => String(route.params.orderNo || ''))
const routeConversationId = computed(() => {
  const raw = Number(route.query.conversationId || 0)
  return Number.isFinite(raw) && raw > 0 ? raw : 0
})

const orderQuery = useQuery({
  queryKey: computed(() => ['checkout-order-v2', orderNo.value]),
  queryFn: () => getOrderDetailV2(orderNo.value),
  enabled: computed(() => orderNo.value.length > 0),
})

const addressQuery = useQuery({
  queryKey: ['my-addresses'],
  queryFn: listMyAddresses,
  enabled: computed(() => Boolean(orderQuery.data.value?.allowDelivery)),
})

const walletQuery = useQuery({
  queryKey: ['payment-wallet-balance'],
  queryFn: () => getWalletBalance(),
})

const fulfillmentMutation = useMutation({
  mutationFn: (payload: { fulfillmentMode: OrderFulfillmentMode; addressId?: number }) =>
    updateOrderFulfillmentV2(orderNo.value, payload),
  onSuccess: async () => {
    await Promise.all([
      orderQuery.refetch(),
      queryClient.invalidateQueries({ queryKey: ['order-detail-v2', orderNo.value] }),
    ])
  },
})

const payMutation = useMutation({
  mutationFn: () => payOrderV2(orderNo.value),
  onSuccess: async () => {
    await Promise.all([
      orderQuery.refetch(),
      walletQuery.refetch(),
      queryClient.invalidateQueries({ queryKey: ['orders-v2'] }),
      queryClient.invalidateQueries({ queryKey: ['order-detail-v2', orderNo.value] }),
    ])
  },
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

const detail = computed(() => orderQuery.data.value)
const addresses = computed(() => addressQuery.data.value || [])
const selectedAddress = computed<UserAddress | null>(() => {
  if (!selectedAddressId.value) {
    return null
  }
  return addresses.value.find((item) => item.addressId === selectedAddressId.value) || null
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

const amountCent = computed(() => detail.value?.totalAmountCent || 0)
const balanceGapCent = computed(() => Math.max(0, amountCent.value - wallet.value.availableBalanceCent))
const balanceEnough = computed(() => balanceGapCent.value <= 0)
const fulfillmentReady = computed(() => {
  const current = detail.value
  if (!current || current.status !== 'UNPAID') {
    return true
  }
  const mode = resolveSelectedFulfillmentMode(current)
  if (!mode) {
    return false
  }
  if (mode === 'DELIVERY') {
    return Boolean(selectedAddressId.value && selectedAddressId.value > 0)
  }
  return true
})
const canPay = computed(() => detail.value?.status === 'UNPAID' && balanceEnough.value && fulfillmentReady.value)

const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  UNPAID: '待支付',
  PAID: '已支付',
  DELIVERING: '待收货',
  FINISHED: '已完成',
  CANCELED: '已取消',
  REFUNDED: '已退款',
}

watch(
  [detail, addresses],
  ([currentDetail, addressList]) => {
    if (!currentDetail) {
      return
    }

    if (!fulfillmentMode.value) {
      const normalized = normalizeFulfillmentMode(currentDetail.fulfillmentMode)
      if (normalized) {
        fulfillmentMode.value = normalized
      } else if (currentDetail.allowMeetup && !currentDetail.allowDelivery) {
        fulfillmentMode.value = 'MEETUP'
      } else if (!currentDetail.allowMeetup && currentDetail.allowDelivery) {
        fulfillmentMode.value = 'DELIVERY'
      }
    }

    const mode = resolveSelectedFulfillmentMode(currentDetail)
    if (mode !== 'DELIVERY') {
      selectedAddressId.value = null
      return
    }
    if (selectedAddressId.value && addressList.some((item) => item.addressId === selectedAddressId.value)) {
      return
    }

    if (currentDetail.shippingAddress) {
      const matched = addressList.find((item) => isSameAddressSnapshot(item, currentDetail.shippingAddress || undefined))
      if (matched) {
        selectedAddressId.value = matched.addressId
        return
      }
    }

    const defaultAddress = addressList.find((item) => item.isDefault)
    selectedAddressId.value = defaultAddress?.addressId || addressList[0]?.addressId || null
  },
  { immediate: true },
)

watch(fulfillmentMode, () => {
  fulfillmentError.value = ''
})

function normalizeFulfillmentMode(raw?: string): OrderFulfillmentMode | '' {
  if (raw === 'MEETUP' || raw === 'DELIVERY') {
    return raw
  }
  return ''
}

function resolveSelectedFulfillmentMode(currentDetail: NonNullable<typeof detail.value>): OrderFulfillmentMode | '' {
  const selected = normalizeFulfillmentMode(fulfillmentMode.value)
  if (selected) {
    return selected
  }
  const detailMode = normalizeFulfillmentMode(currentDetail.fulfillmentMode)
  if (detailMode) {
    return detailMode
  }
  if (currentDetail.allowMeetup && !currentDetail.allowDelivery) {
    return 'MEETUP'
  }
  if (!currentDetail.allowMeetup && currentDetail.allowDelivery) {
    return 'DELIVERY'
  }
  return ''
}

function statusText(status: OrderStatus): string {
  return ORDER_STATUS_TEXT[status] || status
}

function fulfillmentModeText(mode?: string): string {
  if (mode === 'MEETUP') {
    return '线下面交'
  }
  if (mode === 'DELIVERY') {
    return '邮寄配送'
  }
  return '待选择'
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

function isSameAddressSnapshot(
  address: UserAddress,
  snapshot?: {
    receiverName: string
    receiverPhone: string
    province: string
    city: string
    district: string
    detailAddress: string
  },
): boolean {
  if (!snapshot) {
    return false
  }
  return (
    address.receiverName === snapshot.receiverName &&
    address.receiverPhone === snapshot.receiverPhone &&
    address.province === snapshot.province &&
    address.city === snapshot.city &&
    address.district === snapshot.district &&
    address.detailAddress === snapshot.detailAddress
  )
}

function formatAddressLine(address: UserAddress): string {
  return `${address.province} ${address.city} ${address.district} ${address.detailAddress}`
}

function resolveFulfillmentSubmitPayload(): { fulfillmentMode: OrderFulfillmentMode; addressId?: number } | null {
  const current = detail.value
  if (!current) {
    return null
  }

  const mode = resolveSelectedFulfillmentMode(current)
  if (!mode) {
    fulfillmentError.value = '请选择履约方式'
    return null
  }

  if (mode === 'DELIVERY') {
    if (!selectedAddressId.value || selectedAddressId.value <= 0) {
      fulfillmentError.value = '邮寄方式必须选择收货地址'
      return null
    }
    return {
      fulfillmentMode: 'DELIVERY',
      addressId: selectedAddressId.value,
    }
  }

  return {
    fulfillmentMode: 'MEETUP',
  }
}

async function handlePay(): Promise<void> {
  if (!canPay.value || payMutation.isPending.value || fulfillmentMutation.isPending.value) {
    return
  }

  const payload = resolveFulfillmentSubmitPayload()
  if (!payload) {
    return
  }

  fulfillmentError.value = ''
  try {
    await fulfillmentMutation.mutateAsync(payload)
  } catch (error) {
    fulfillmentError.value = error instanceof ApiBizError ? error.message : '保存履约方式失败，请稍后重试'
    return
  }

  try {
    await payMutation.mutateAsync()
    const conversationId = detail.value?.conversationId || routeConversationId.value
    if (conversationId > 0) {
      await chatStore.sendTradeStatusCard(conversationId, 'ORDER_PAID', orderNo.value)
    }
    await router.replace({
      path: `/orders/${orderNo.value}`,
      query: conversationId > 0 ? { conversationId: String(conversationId) } : undefined,
    })
  } catch {
    // error message is handled by computed value
  }
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

const payError = computed(() => {
  if (payMutation.error.value) {
    return resolvePaymentErrorMessage(payMutation.error.value)
  }
  return ''
})
</script>

<template>
  <section class="space-y-4">
    <button
      type="button"
      class="rounded-xl border border-blue-200 bg-white px-3 py-1.5 text-sm text-[var(--shiori-pay-blue-700)] transition hover:bg-blue-50"
      @click="router.push(`/orders/${orderNo}`)"
    >
      返回订单详情
    </button>

    <div
      class="overflow-hidden rounded-3xl border border-[var(--shiori-pay-blue-500)]/25 px-5 py-6 text-white shadow-[var(--shiori-pay-shadow)] shiori-pay-rise"
      style="background: linear-gradient(124deg, var(--shiori-pay-blue-900) 0%, var(--shiori-pay-blue-700) 56%, var(--shiori-pay-blue-500) 100%);"
    >
      <p class="text-xs uppercase tracking-[0.26em] text-blue-100/90">订单收银台</p>
      <h1 class="mt-2 font-display text-3xl tracking-wide">订单余额支付</h1>
      <p class="mt-2 text-sm text-blue-50/90">仅支持余额托管支付，交易完结后资金自动结算到卖家账户。</p>
    </div>

    <div v-if="orderQuery.isLoading.value || walletQuery.isLoading.value" class="rounded-2xl border border-blue-100 bg-[var(--shiori-pay-surface)] p-6 text-sm text-[var(--shiori-pay-mute)]">
      正在加载收银信息...
    </div>

    <div
      v-else-if="orderQuery.error.value instanceof Error || walletQuery.error.value instanceof Error"
      class="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700"
    >
      {{ orderQuery.error.value instanceof Error ? orderQuery.error.value.message : walletQuery.error.value instanceof Error ? walletQuery.error.value.message : '加载失败' }}
    </div>

    <template v-else-if="detail">
      <div class="grid gap-4 lg:grid-cols-[1.12fr_0.88fr]">
        <article class="rounded-2xl border border-blue-100 bg-white p-5 shadow-sm shiori-pay-rise-delay">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p class="text-xs uppercase tracking-[0.18em] text-[var(--shiori-pay-mute)]">订单号</p>
              <p class="mt-1 text-sm font-medium text-[var(--shiori-pay-ink)]">{{ detail.orderNo }}</p>
            </div>
            <span class="rounded-full bg-[var(--shiori-pay-soft)] px-3 py-1 text-xs font-semibold text-[var(--shiori-pay-blue-800)]">{{ statusText(detail.status) }}</span>
          </div>

          <div class="mt-4 grid gap-3 rounded-xl bg-[var(--shiori-pay-surface)] p-4 text-sm text-[var(--shiori-pay-ink)] sm:grid-cols-2">
            <p>创建时间：{{ formatTime(detail.createdAt) }}</p>
            <p>支付时间：{{ formatTime(detail.paidAt) }}</p>
            <p>支付方式：余额支付</p>
            <p>订单金额：{{ formatMoney(detail.totalAmountCent) }}</p>
          </div>

          <section class="mt-4 space-y-3 rounded-xl border border-blue-100 bg-[var(--shiori-pay-surface)] p-4">
            <div class="flex items-center justify-between gap-2">
              <h2 class="text-sm font-semibold text-[var(--shiori-pay-ink)]">履约方式</h2>
              <span class="text-xs text-[var(--shiori-pay-mute)]">当前：{{ fulfillmentModeText(resolveSelectedFulfillmentMode(detail)) }}</span>
            </div>

            <div class="flex flex-wrap gap-2">
              <label
                v-if="detail.allowMeetup"
                class="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-blue-200 bg-white px-3 py-2 text-sm text-[var(--shiori-pay-ink)]"
              >
                <input v-model="fulfillmentMode" type="radio" value="MEETUP" />
                线下面交
              </label>
              <label
                v-if="detail.allowDelivery"
                class="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-blue-200 bg-white px-3 py-2 text-sm text-[var(--shiori-pay-ink)]"
              >
                <input v-model="fulfillmentMode" type="radio" value="DELIVERY" />
                邮寄配送
              </label>
            </div>

            <div v-if="resolveSelectedFulfillmentMode(detail) === 'DELIVERY'" class="space-y-2 rounded-lg border border-blue-100 bg-white p-3">
              <p class="text-xs text-[var(--shiori-pay-mute)]">请选择收货地址</p>
              <p v-if="addressQuery.isLoading.value" class="text-xs text-[var(--shiori-pay-mute)]">地址加载中...</p>
              <p v-else-if="addressQuery.error.value instanceof Error" class="text-xs text-rose-600">{{ addressQuery.error.value.message }}</p>

              <div v-else-if="addresses.length === 0" class="space-y-2 text-xs text-rose-600">
                <p>你还没有可用地址，请先新增收货地址。</p>
                <button
                  type="button"
                  class="rounded-lg border border-blue-200 px-3 py-1.5 text-[var(--shiori-pay-blue-700)] transition hover:bg-blue-50"
                  @click="router.push('/profile/addresses')"
                >
                  去管理地址
                </button>
              </div>

              <div v-else class="space-y-2">
                <label
                  v-for="item in addresses"
                  :key="item.addressId"
                  class="flex cursor-pointer items-start gap-2 rounded-lg border border-blue-100 px-3 py-2 text-sm text-[var(--shiori-pay-ink)] transition hover:border-blue-300"
                  :class="selectedAddressId === item.addressId ? 'bg-blue-50' : 'bg-white'"
                >
                  <input v-model.number="selectedAddressId" type="radio" name="checkout-address" :value="item.addressId" class="mt-1" />
                  <div class="min-w-0">
                    <p class="font-medium">
                      {{ item.receiverName }} {{ item.receiverPhone }}
                      <span v-if="item.isDefault" class="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-[10px] text-amber-700">默认</span>
                    </p>
                    <p class="mt-1 line-clamp-2 text-xs text-[var(--shiori-pay-mute)]">{{ formatAddressLine(item) }}</p>
                  </div>
                </label>
              </div>
            </div>

            <p v-if="fulfillmentError" class="text-xs text-rose-600">{{ fulfillmentError }}</p>
            <p v-if="resolveSelectedFulfillmentMode(detail) === 'DELIVERY' && selectedAddress" class="text-xs text-[var(--shiori-pay-mute)]">
              已选地址：{{ formatAddressLine(selectedAddress) }}
            </p>
          </section>

          <div class="mt-4 overflow-hidden rounded-xl border border-blue-100">
            <table class="w-full text-left text-sm">
              <thead class="bg-blue-50 text-[var(--shiori-pay-mute)]">
                <tr>
                  <th class="px-3 py-2">SKU</th>
                  <th class="px-3 py-2">单价</th>
                  <th class="px-3 py-2">数量</th>
                  <th class="px-3 py-2">小计</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in detail.items" :key="`${item.productId}-${item.skuId}`" class="border-t border-blue-100">
                  <td class="px-3 py-2 text-[var(--shiori-pay-ink)]">{{ item.skuName }}</td>
                  <td class="px-3 py-2 text-[var(--shiori-pay-mute)]">{{ formatMoney(item.priceCent) }}</td>
                  <td class="px-3 py-2 text-[var(--shiori-pay-mute)]">{{ item.quantity }}</td>
                  <td class="px-3 py-2 font-medium text-[var(--shiori-pay-blue-800)]">{{ formatMoney(item.subtotalCent) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>

        <aside class="space-y-4">
          <article class="rounded-2xl border border-blue-100 bg-white p-5 shadow-sm">
            <h2 class="text-lg font-semibold text-[var(--shiori-pay-ink)]">钱包余额</h2>
            <div class="mt-3 space-y-2 text-sm text-[var(--shiori-pay-mute)]">
              <div class="flex items-center justify-between">
                <span>可用余额</span>
                <span class="font-semibold text-[var(--shiori-pay-blue-800)]">{{ formatMoney(wallet.availableBalanceCent) }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span>冻结余额</span>
                <span class="font-semibold text-[var(--shiori-pay-ink)]">{{ formatMoney(wallet.frozenBalanceCent) }}</span>
              </div>
              <div class="flex items-center justify-between border-t border-blue-100 pt-2">
                <span>应付金额</span>
                <span class="text-lg font-semibold text-[var(--shiori-pay-blue-900)]">{{ formatMoney(amountCent) }}</span>
              </div>
            </div>
            <p v-if="!balanceEnough" class="mt-3 text-sm text-rose-600">
              余额不足，还差 {{ formatMoney(balanceGapCent) }}。可先兑换 CDK 后再支付。
            </p>
            <RouterLink to="/wallet" class="mt-3 inline-flex rounded-lg border border-blue-200 px-3 py-1.5 text-xs text-[var(--shiori-pay-blue-700)] transition hover:bg-blue-50">
              前往钱包页面
            </RouterLink>
          </article>

          <article class="rounded-2xl border border-blue-100 bg-white p-5 shadow-sm">
            <h2 class="text-base font-semibold text-[var(--shiori-pay-ink)]">快捷 CDK 兑换</h2>
            <div class="mt-3 flex flex-col gap-3">
              <input
                v-model.trim="cdkCode"
                type="text"
                maxlength="128"
                placeholder="输入 CDK 兑换码"
                class="h-11 rounded-xl border border-blue-200 px-3 text-sm text-[var(--shiori-pay-ink)] outline-none transition focus:border-[var(--shiori-pay-blue-600)] focus:ring-2 focus:ring-[var(--shiori-pay-blue-500)]/20"
              />
              <button
                type="button"
                class="h-11 rounded-xl border border-blue-200 text-sm text-[var(--shiori-pay-blue-700)] transition hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-70"
                :disabled="redeemMutation.isPending.value"
                @click="handleRedeem"
              >
                {{ redeemMutation.isPending.value ? '兑换中...' : '兑换并刷新余额' }}
              </button>
            </div>
            <p v-if="redeemMessage" class="mt-3 text-sm text-emerald-600">{{ redeemMessage }}</p>
            <p v-if="redeemError" class="mt-3 text-sm text-rose-600">{{ redeemError }}</p>
          </article>

          <article class="rounded-2xl border border-dashed border-blue-200 bg-[var(--shiori-pay-surface)] p-5 text-sm text-[var(--shiori-pay-mute)]">
            支付后资金会先进入平台托管，确认收货后自动结算给卖家，过程更安全、更可追踪。
          </article>
        </aside>
      </div>

      <article
        v-if="detail.status !== 'UNPAID'"
        class="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-700"
      >
        当前订单状态为 {{ statusText(detail.status) }}，无需继续支付。
      </article>

      <div class="sticky bottom-4 z-10 rounded-2xl border border-blue-100 bg-white/95 p-4 shadow-[var(--shiori-pay-shadow)] backdrop-blur">
        <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p class="text-xs uppercase tracking-[0.18em] text-[var(--shiori-pay-mute)]">应付总额</p>
            <p class="mt-1 text-2xl font-semibold text-[var(--shiori-pay-blue-900)]">{{ formatMoney(amountCent) }}</p>
          </div>
          <button
            type="button"
            class="h-12 rounded-xl bg-[var(--shiori-pay-blue-700)] px-6 text-sm font-semibold text-white transition hover:bg-[var(--shiori-pay-blue-800)] disabled:cursor-not-allowed disabled:opacity-65"
            :disabled="!canPay || payMutation.isPending.value || fulfillmentMutation.isPending.value"
            @click="handlePay"
          >
            {{ fulfillmentMutation.isPending.value ? '保存履约信息...' : payMutation.isPending.value ? '支付处理中...' : '确认余额支付' }}
          </button>
        </div>
        <p v-if="!fulfillmentReady && detail.status === 'UNPAID'" class="mt-3 text-sm text-rose-600">请先完善履约方式与地址后再支付。</p>
        <p v-if="payError" class="mt-3 text-sm text-rose-600">{{ payError }}</p>
      </div>
    </template>
  </section>
</template>
