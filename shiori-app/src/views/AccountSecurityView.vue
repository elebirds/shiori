<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiBizError } from '@/types/result'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
})

const message = ref('')
const mustChangePassword = computed(() => Boolean(authStore.user?.mustChangePassword) || route.query.forceChangePassword === '1')

const changePasswordMutation = useMutation({
  mutationFn: () => authStore.changeMyPassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword }),
  onSuccess: async () => {
    message.value = '密码更新成功'
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    if (mustChangePassword.value) {
      await router.replace('/products')
    }
  },
  onError: (error) => {
    message.value = error instanceof Error ? error.message : '密码更新失败'
  },
})

async function submitPassword(): Promise<void> {
  message.value = ''
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    message.value = '请填写旧密码和新密码'
    return
  }
  try {
    await changePasswordMutation.mutateAsync()
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}
</script>

<template>
  <section class="space-y-4">
    <div v-if="mustChangePassword" class="rounded-2xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-800">
      当前账号需先修改密码后才能访问其他页面。
    </div>

    <header class="rounded-2xl border border-stone-200 bg-white/90 p-4">
      <h1 class="font-display text-2xl text-stone-900">登录与安全</h1>
      <p class="mt-1 text-sm text-stone-600">管理登录密码与账号安全设置</p>
    </header>

    <form class="rounded-2xl border border-stone-200 bg-white/95 p-5" @submit.prevent="submitPassword">
      <h2 class="text-base font-semibold text-stone-900">修改密码</h2>

      <label class="mt-4 block text-sm text-stone-700">
        旧密码
        <input
          v-model="passwordForm.oldPassword"
          type="password"
          autocomplete="current-password"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
        />
      </label>

      <label class="mt-3 block text-sm text-stone-700">
        新密码
        <input
          v-model="passwordForm.newPassword"
          type="password"
          autocomplete="new-password"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
        />
      </label>

      <button
        type="submit"
        class="mt-4 w-full rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
        :disabled="changePasswordMutation.isPending.value"
      >
        {{ changePasswordMutation.isPending.value ? '提交中...' : '更新密码' }}
      </button>

      <RouterLink
        to="/profile"
        class="mt-3 inline-flex w-full items-center justify-center rounded-xl border border-stone-300 px-4 py-2 text-sm font-medium text-stone-800 transition hover:border-amber-500"
      >
        返回个人中心
      </RouterLink>

      <p v-if="message" class="mt-3 text-sm" :class="message.includes('成功') ? 'text-emerald-600' : 'text-rose-600'">
        {{ message }}
      </p>
    </form>
  </section>
</template>
