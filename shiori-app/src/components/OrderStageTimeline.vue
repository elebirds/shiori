<script setup lang="ts">
import { computed } from 'vue'

import type { OrderStatus, OrderTimelineItemResponse } from '@/api/orderV2'

type StageState = 'done' | 'current' | 'pending'

interface StageViewItem {
  key: 'CREATED' | 'PAID' | 'DELIVERED' | 'RECEIVED' | 'REVIEW'
  title: string
  description: string
  state: StageState
  timeText: string
}

const props = withDefaults(
  defineProps<{
    status: OrderStatus
    createdAt?: string
    paidAt?: string
    timelineItems?: OrderTimelineItemResponse[]
  }>(),
  {
    timelineItems: () => [],
  },
)

const stageMeta: Array<Pick<StageViewItem, 'key' | 'title' | 'description'>> = [
  { key: 'CREATED', title: '下单', description: '订单已创建' },
  { key: 'PAID', title: '支付', description: '买家完成付款' },
  { key: 'DELIVERED', title: '发货', description: '卖家已发货' },
  { key: 'RECEIVED', title: '收货', description: '买家确认收货' },
  { key: 'REVIEW', title: '评价', description: '评价功能即将上线' },
]

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

function firstTransitionTime(targetStatus: OrderStatus): string | undefined {
  return props.timelineItems.find((item) => item.toStatus === targetStatus)?.createdAt
}

const paidTime = computed(() => props.paidAt || firstTransitionTime('PAID'))
const deliveredTime = computed(() => firstTransitionTime('DELIVERING'))
const receivedTime = computed(() => firstTransitionTime('FINISHED'))

const isCanceled = computed(() => props.status === 'CANCELED')

const progressIndex = computed(() => {
  if (props.status === 'UNPAID') {
    return 0
  }
  if (props.status === 'PAID') {
    return 1
  }
  if (props.status === 'DELIVERING') {
    return 2
  }
  if (props.status === 'FINISHED') {
    return 3
  }

  if (receivedTime.value) {
    return 3
  }
  if (deliveredTime.value) {
    return 2
  }
  if (paidTime.value) {
    return 1
  }
  return 0
})

const stages = computed<StageViewItem[]>(() => {
  return stageMeta.map((meta, index) => {
    let state: StageState = 'pending'

    if (index <= progressIndex.value) {
      state = 'done'
    }

    if (!isCanceled.value) {
      if (props.status === 'FINISHED' && index === 4) {
        state = 'current'
      } else if (props.status !== 'FINISHED' && index === progressIndex.value + 1) {
        state = 'current'
      }
    }

    const timeValue =
      meta.key === 'CREATED'
        ? props.createdAt
        : meta.key === 'PAID'
          ? paidTime.value
          : meta.key === 'DELIVERED'
            ? deliveredTime.value
            : meta.key === 'RECEIVED'
              ? receivedTime.value
              : undefined

    let timeText = '待完成'
    if (timeValue) {
      timeText = formatTime(timeValue)
    } else if (meta.key === 'REVIEW') {
      timeText = props.status === 'FINISHED' ? '敬请期待' : '功能开发中'
    } else if (isCanceled.value && index > progressIndex.value) {
      timeText = '订单已取消'
    }

    return {
      ...meta,
      state,
      timeText,
    }
  })
})

function cardClass(state: StageState): string {
  if (state === 'done') {
    return 'border-emerald-200 bg-emerald-50/70'
  }
  if (state === 'current') {
    return 'border-sky-300 bg-sky-50/80 ring-1 ring-sky-200'
  }
  return 'border-stone-200 bg-white'
}

function dotClass(state: StageState): string {
  if (state === 'done') {
    return 'bg-emerald-600 text-white'
  }
  if (state === 'current') {
    return 'bg-sky-600 text-white'
  }
  return 'bg-stone-200 text-stone-600'
}

function dotText(state: StageState): string {
  if (state === 'done') {
    return '✓'
  }
  if (state === 'current') {
    return '●'
  }
  return '○'
}
</script>

<template>
  <section class="rounded-xl border border-stone-200 bg-stone-50/70 p-4">
    <header class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-stone-900">订单进度</h2>
        <p class="mt-1 text-xs text-stone-600">从下单到评价的关键阶段一目了然</p>
      </div>
      <span
        v-if="isCanceled"
        class="rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-xs font-medium text-rose-700"
      >
        订单已取消
      </span>
    </header>

    <ol class="mt-3 grid gap-3 md:grid-cols-5">
      <li
        v-for="stage in stages"
        :key="stage.key"
        class="rounded-xl border p-3 transition"
        :class="cardClass(stage.state)"
      >
        <div class="flex items-center gap-2">
          <span
            class="inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-semibold"
            :class="dotClass(stage.state)"
          >
            {{ dotText(stage.state) }}
          </span>
          <p class="text-sm font-semibold text-stone-900">{{ stage.title }}</p>
        </div>
        <p class="mt-2 text-xs text-stone-600">{{ stage.description }}</p>
        <p class="mt-2 text-xs font-medium text-stone-700">{{ stage.timeText }}</p>
      </li>
    </ol>
  </section>
</template>
