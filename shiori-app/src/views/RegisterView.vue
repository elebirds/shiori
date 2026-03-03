<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiBizError } from '@/types/result'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const pending = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

async function handleSubmit(): Promise<void> {
  if (!form.username || !form.password) {
    errorMessage.value = '请填写用户名和密码'
    return
  }
  if (form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  pending.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await authStore.registerAccount({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
    })
    successMessage.value = '注册成功，请登录'
    setTimeout(() => {
      void router.push('/login')
    }, 600)
  } catch (error) {
    if (error instanceof ApiBizError) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '注册失败，请稍后重试'
    }
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="mx-auto max-w-md rounded-3xl border border-stone-200/80 bg-white/90 p-6 shadow-xl shadow-stone-400/10">
    <h1 class="font-display text-2xl text-stone-900">创建账号</h1>
    <p class="mt-2 text-sm text-stone-600">注册后即可下单与查看订单进度</p>

    <form class="mt-6 space-y-4" @submit.prevent="handleSubmit">
      <label class="block text-sm text-stone-700">
        用户名
        <input
          v-model.trim="form.username"
          type="text"
          autocomplete="username"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="4-32位字母数字下划线"
        />
      </label>

      <label class="block text-sm text-stone-700">
        昵称
        <input
          v-model.trim="form.nickname"
          type="text"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="选填"
        />
      </label>

      <label class="block text-sm text-stone-700">
        密码
        <input
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="至少8位"
        />
      </label>

      <label class="block text-sm text-stone-700">
        确认密码
        <input
          v-model="form.confirmPassword"
          type="password"
          autocomplete="new-password"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="再次输入密码"
        />
      </label>

      <p v-if="errorMessage" class="text-sm text-rose-600">{{ errorMessage }}</p>
      <p v-if="successMessage" class="text-sm text-emerald-600">{{ successMessage }}</p>

      <button
        type="submit"
        class="w-full rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
        :disabled="pending"
      >
        {{ pending ? '提交中...' : '注册' }}
      </button>
    </form>
  </section>
</template>
