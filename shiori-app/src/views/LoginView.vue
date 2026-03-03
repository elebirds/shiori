<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiBizError } from '@/types/result'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const pending = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: '',
})

async function handleSubmit(): Promise<void> {
  if (!form.username || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  pending.value = true
  errorMessage.value = ''
  try {
    await authStore.loginWithPassword(form)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/products'
    await router.push(redirect)
  } catch (error) {
    if (error instanceof ApiBizError) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '登录失败，请稍后重试'
    }
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="mx-auto max-w-md rounded-3xl border border-stone-200/80 bg-white/90 p-6 shadow-xl shadow-stone-400/10">
    <h1 class="font-display text-2xl text-stone-900">登录 Shiori</h1>
    <p class="mt-2 text-sm text-stone-600">继续访问订单与个人中心</p>

    <form class="mt-6 space-y-4" @submit.prevent="handleSubmit">
      <label class="block text-sm text-stone-700">
        用户名
        <input
          v-model.trim="form.username"
          type="text"
          autocomplete="username"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="请输入用户名"
        />
      </label>

      <label class="block text-sm text-stone-700">
        密码
        <input
          v-model="form.password"
          type="password"
          autocomplete="current-password"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="请输入密码"
        />
      </label>

      <p v-if="errorMessage" class="text-sm text-rose-600">{{ errorMessage }}</p>

      <button
        type="submit"
        class="w-full rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
        :disabled="pending"
      >
        {{ pending ? '登录中...' : '登录' }}
      </button>
    </form>

    <p class="mt-4 text-sm text-stone-600">
      还没有账号？
      <RouterLink class="text-amber-700 hover:text-amber-800" to="/register">立即注册</RouterLink>
    </p>
  </section>
</template>
