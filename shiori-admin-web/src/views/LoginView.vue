<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { ApiBizError } from '@/types/result'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const form = reactive({
  username: '',
  password: '',
})

const loading = ref(false)
const errorMessage = ref('')

async function onSubmit() {
  errorMessage.value = ''
  loading.value = true
  try {
    await authStore.loginWithPassword({
      username: form.username.trim(),
      password: form.password,
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/users'
    await router.replace(redirect)
  } catch (error) {
    if (error instanceof ApiBizError) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '登录失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="mx-auto mt-20 w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
    <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Shiori Admin</p>
    <h1 class="mt-2 text-2xl font-semibold text-slate-900">管理员登录</h1>
    <p class="mt-2 text-sm text-slate-500">仅拥有 ROLE_ADMIN 的账号可进入后台</p>

    <form class="mt-8 space-y-4" @submit.prevent="onSubmit">
      <label class="block text-sm text-slate-700">
        用户名
        <input
          v-model="form.username"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 outline-none ring-blue-500 focus:ring"
          autocomplete="username"
          required
        />
      </label>

      <label class="block text-sm text-slate-700">
        密码
        <input
          v-model="form.password"
          class="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 outline-none ring-blue-500 focus:ring"
          type="password"
          autocomplete="current-password"
          required
        />
      </label>

      <p v-if="errorMessage" class="text-sm text-rose-600">{{ errorMessage }}</p>

      <button
        class="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:cursor-not-allowed disabled:bg-blue-300"
        type="submit"
        :disabled="loading"
      >
        {{ loading ? '登录中...' : '登录' }}
      </button>
    </form>
  </div>
</template>
