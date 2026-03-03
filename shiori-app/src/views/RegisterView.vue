<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiBizError, extractValidationMessage, isValidationErrorPayload } from '@/types/result'
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

type RegisterField = 'username' | 'nickname' | 'password' | 'confirmPassword'
const fieldErrors = reactive<Record<RegisterField, string>>({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

function clearFieldErrors(): void {
  fieldErrors.username = ''
  fieldErrors.nickname = ''
  fieldErrors.password = ''
  fieldErrors.confirmPassword = ''
}

function firstFieldError(): string {
  return fieldErrors.username || fieldErrors.nickname || fieldErrors.password || fieldErrors.confirmPassword
}

function validateRegisterForm(): boolean {
  clearFieldErrors()

  if (!form.username) {
    fieldErrors.username = '用户名不能为空'
  } else if (form.username.length < 4 || form.username.length > 32) {
    fieldErrors.username = '用户名长度必须在4-32之间'
  } else if (!/^[a-zA-Z0-9_]+$/.test(form.username)) {
    fieldErrors.username = '用户名仅支持字母数字下划线'
  }

  if (form.nickname && form.nickname.length > 64) {
    fieldErrors.nickname = '昵称长度不能超过64'
  }

  if (!form.password) {
    fieldErrors.password = '密码不能为空'
  } else if (form.password.length < 8 || form.password.length > 100) {
    fieldErrors.password = '密码长度必须在8-100之间'
  }

  if (!form.confirmPassword) {
    fieldErrors.confirmPassword = '请再次输入密码'
  } else if (form.password !== form.confirmPassword) {
    fieldErrors.confirmPassword = '两次输入的密码不一致'
  }

  return !firstFieldError()
}

function applyBackendFieldErrors(data: unknown): void {
  if (!isValidationErrorPayload(data)) {
    return
  }

  for (const item of data.errors) {
    if (item.field === 'username') {
      fieldErrors.username = item.message
      continue
    }
    if (item.field === 'nickname') {
      fieldErrors.nickname = item.message
      continue
    }
    if (item.field === 'password') {
      fieldErrors.password = item.message
    }
  }
}

async function handleSubmit(): Promise<void> {
  if (!validateRegisterForm()) {
    errorMessage.value = firstFieldError()
    return
  }

  pending.value = true
  errorMessage.value = ''
  successMessage.value = ''
  clearFieldErrors()

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
      applyBackendFieldErrors(error.data)
      errorMessage.value = extractValidationMessage(error.data) || error.message
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
          class="mt-1 w-full rounded-xl border px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          :class="fieldErrors.username ? 'border-rose-400' : 'border-stone-300'"
          placeholder="4-32位字母数字下划线"
        />
        <p v-if="fieldErrors.username" class="mt-1 text-xs text-rose-600">{{ fieldErrors.username }}</p>
      </label>

      <label class="block text-sm text-stone-700">
        昵称
        <input
          v-model.trim="form.nickname"
          type="text"
          class="mt-1 w-full rounded-xl border px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          :class="fieldErrors.nickname ? 'border-rose-400' : 'border-stone-300'"
          placeholder="选填"
        />
        <p v-if="fieldErrors.nickname" class="mt-1 text-xs text-rose-600">{{ fieldErrors.nickname }}</p>
      </label>

      <label class="block text-sm text-stone-700">
        密码
        <input
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          class="mt-1 w-full rounded-xl border px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          :class="fieldErrors.password ? 'border-rose-400' : 'border-stone-300'"
          placeholder="至少8位"
        />
        <p v-if="fieldErrors.password" class="mt-1 text-xs text-rose-600">{{ fieldErrors.password }}</p>
      </label>

      <label class="block text-sm text-stone-700">
        确认密码
        <input
          v-model="form.confirmPassword"
          type="password"
          autocomplete="new-password"
          class="mt-1 w-full rounded-xl border px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          :class="fieldErrors.confirmPassword ? 'border-rose-400' : 'border-stone-300'"
          placeholder="再次输入密码"
        />
        <p v-if="fieldErrors.confirmPassword" class="mt-1 text-xs text-rose-600">{{ fieldErrors.confirmPassword }}</p>
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
