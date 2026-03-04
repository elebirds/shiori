<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import ResultState from '@/components/ResultState.vue'
import { ApiBizError } from '@/types/result'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const route = useRoute()

const profileForm = reactive({
  nickname: '',
  avatarUrl: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
})

const profileMessage = ref('')
const passwordMessage = ref('')

const profileQuery = useQuery({
  queryKey: ['profile'],
  queryFn: () => authStore.fetchMyProfile(),
})

watch(
  () => profileQuery.data.value,
  (profile) => {
    if (!profile) {
      return
    }
    profileForm.nickname = profile.nickname || ''
    profileForm.avatarUrl = profile.avatarUrl || ''
  },
  { immediate: true },
)

const updateProfileMutation = useMutation({
  mutationFn: () => authStore.updateMyProfile({ nickname: profileForm.nickname, avatarUrl: profileForm.avatarUrl || undefined }),
  onSuccess: () => {
    profileMessage.value = '资料更新成功'
  },
  onError: (error) => {
    profileMessage.value = error instanceof Error ? error.message : '资料更新失败'
  },
})

const changePasswordMutation = useMutation({
  mutationFn: () => authStore.changeMyPassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword }),
  onSuccess: () => {
    passwordMessage.value = '密码更新成功'
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
  },
  onError: (error) => {
    passwordMessage.value = error instanceof Error ? error.message : '密码更新失败'
  },
})

const errorMessage = profileQuery.error.value instanceof Error ? profileQuery.error.value.message : ''
const mustChangePassword = computed(() => Boolean(authStore.user?.mustChangePassword) || route.query.forceChangePassword === '1')

async function submitProfile(): Promise<void> {
  profileMessage.value = ''
  if (!profileForm.nickname.trim()) {
    profileMessage.value = '昵称不能为空'
    return
  }

  try {
    await updateProfileMutation.mutateAsync()
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function submitPassword(): Promise<void> {
  passwordMessage.value = ''
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    passwordMessage.value = '请填写旧密码和新密码'
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
      <h1 class="font-display text-2xl text-stone-900">个人中心</h1>
      <p class="mt-1 text-sm text-stone-600">支持查看与更新个人资料，修改登录密码</p>
    </header>

    <ResultState :loading="profileQuery.isLoading.value" :error="errorMessage" :empty="!profileQuery.isLoading.value && !profileQuery.data.value" empty-text="未获取到用户资料">
      <div class="grid gap-4 lg:grid-cols-2">
        <form class="rounded-2xl border border-stone-200 bg-white/95 p-5" @submit.prevent="submitProfile">
          <h2 class="text-base font-semibold text-stone-900">资料信息</h2>

          <label class="mt-4 block text-sm text-stone-700">
            昵称
            <input
              v-model.trim="profileForm.nickname"
              type="text"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            />
          </label>

          <label class="mt-3 block text-sm text-stone-700">
            头像地址
            <input
              v-model.trim="profileForm.avatarUrl"
              type="url"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            />
          </label>

          <button
            type="submit"
            class="mt-4 w-full rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="updateProfileMutation.isPending.value"
          >
            {{ updateProfileMutation.isPending.value ? '保存中...' : '保存资料' }}
          </button>

          <p v-if="profileMessage" class="mt-3 text-sm" :class="profileMessage.includes('成功') ? 'text-emerald-600' : 'text-rose-600'">
            {{ profileMessage }}
          </p>
        </form>

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

          <p v-if="passwordMessage" class="mt-3 text-sm" :class="passwordMessage.includes('成功') ? 'text-emerald-600' : 'text-rose-600'">
            {{ passwordMessage }}
          </p>
        </form>
      </div>
    </ResultState>
  </section>
</template>
