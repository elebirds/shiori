<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Cropper } from 'vue-advanced-cropper'
import 'vue-advanced-cropper/dist/style.css'

import { getAccessToken } from '@/api/http'
import ResultState from '@/components/ResultState.vue'
import { ApiBizError } from '@/types/result'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const profileForm = reactive({
  nickname: '',
  gender: 0,
  birthDate: '',
  bio: '',
})

const cropperRef = ref<any>(null)
const avatarSource = ref('')
const avatarPreviewUrl = ref('')
const selectedAvatarFileName = ref('')
const avatarModalOpen = ref(false)
const avatarMessage = ref('')
const profileMessage = ref('')
let avatarBlobUrl: string | null = null

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
    profileForm.gender = profile.gender ?? 0
    profileForm.birthDate = profile.birthDate || ''
    profileForm.bio = profile.bio || ''
    void hydrateAvatarPreview(profile.avatarUrl)
  },
  { immediate: true },
)

const updateProfileMutation = useMutation({
  mutationFn: () =>
    authStore.updateMyProfile({
      nickname: profileForm.nickname,
      gender: profileForm.gender,
      birthDate: profileForm.birthDate || undefined,
      bio: profileForm.bio.trim() || undefined,
    }),
  onSuccess: () => {
    profileMessage.value = '资料更新成功'
  },
  onError: (error) => {
    profileMessage.value = error instanceof Error ? error.message : '资料更新失败'
  },
})

const uploadAvatarMutation = useMutation({
  mutationFn: (file: File) => authStore.uploadMyAvatar(file),
  onSuccess: async (data) => {
    avatarMessage.value = '头像上传成功'
    if (profileQuery.data.value) {
      profileQuery.data.value.avatarUrl = data.avatarUrl
    }
    await hydrateAvatarPreview(data.avatarUrl)
    clearAvatarSelection()
  },
  onError: (error) => {
    avatarMessage.value = error instanceof Error ? error.message : '头像上传失败'
  },
})

const errorMessage = profileQuery.error.value instanceof Error ? profileQuery.error.value.message : ''
const bioLength = computed(() => profileForm.bio.length)

function clearAvatarSelection(): void {
  if (avatarSource.value.startsWith('blob:')) {
    URL.revokeObjectURL(avatarSource.value)
  }
  avatarSource.value = ''
  selectedAvatarFileName.value = ''
  avatarModalOpen.value = false
}

async function hydrateAvatarPreview(avatarUrl?: string): Promise<void> {
  if (avatarBlobUrl) {
    URL.revokeObjectURL(avatarBlobUrl)
    avatarBlobUrl = null
  }
  avatarPreviewUrl.value = ''
  if (!avatarUrl) {
    return
  }
  if (avatarUrl.startsWith('http://') || avatarUrl.startsWith('https://')) {
    avatarPreviewUrl.value = avatarUrl
    return
  }

  const token = getAccessToken()
  if (!token) {
    return
  }

  try {
    const response = await fetch(avatarUrl, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })
    if (!response.ok) {
      return
    }
    const blob = await response.blob()
    avatarBlobUrl = URL.createObjectURL(blob)
    avatarPreviewUrl.value = avatarBlobUrl
  } catch {
    avatarPreviewUrl.value = ''
  }
}

function onAvatarSelected(event: Event): void {
  avatarMessage.value = ''
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) {
    return
  }
  if (!file.type.startsWith('image/')) {
    avatarMessage.value = '请选择图片文件'
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    avatarMessage.value = '头像大小不能超过 2MB'
    return
  }
  clearAvatarSelection()
  avatarSource.value = URL.createObjectURL(file)
  selectedAvatarFileName.value = file.name
  avatarModalOpen.value = true
}

async function submitAvatar(): Promise<void> {
  avatarMessage.value = ''
  const result = cropperRef.value?.getResult?.()
  const canvas = result?.canvas as HTMLCanvasElement | undefined
  if (!canvas) {
    avatarMessage.value = '请先选择并裁剪头像'
    return
  }

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.92))
  if (!blob) {
    avatarMessage.value = '头像裁剪失败，请重试'
    return
  }

  try {
    await uploadAvatarMutation.mutateAsync(new File([blob], 'avatar.jpg', { type: 'image/jpeg' }))
  } catch (error) {
    if (error instanceof ApiBizError) {
      return
    }
  }
}

async function submitProfile(): Promise<void> {
  profileMessage.value = ''
  if (!profileForm.nickname.trim()) {
    profileMessage.value = '昵称不能为空'
    return
  }
  if (![0, 1, 2, 9].includes(profileForm.gender)) {
    profileMessage.value = '请选择有效性别'
    return
  }
  if (bioLength.value > 280) {
    profileMessage.value = '个人简介长度不能超过 280'
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

onBeforeUnmount(() => {
  clearAvatarSelection()
  if (avatarBlobUrl) {
    URL.revokeObjectURL(avatarBlobUrl)
    avatarBlobUrl = null
  }
})
</script>

<template>
  <section class="space-y-4">
    <header class="mx-auto max-w-3xl rounded-2xl border border-stone-200 bg-white/90 p-4">
      <h1 class="font-display text-2xl text-stone-900">编辑资料</h1>
    </header>

    <ResultState :loading="profileQuery.isLoading.value" :error="errorMessage" :empty="!profileQuery.isLoading.value && !profileQuery.data.value" empty-text="未获取到用户资料">
      <div class="mx-auto max-w-3xl">
        <form class="rounded-2xl border border-stone-200 bg-white/95 p-5" @submit.prevent="submitProfile">
          <h2 class="text-base font-semibold text-stone-900">资料信息</h2>

          <div class="mt-4 flex items-center gap-4">
            <div class="h-16 w-16 overflow-hidden rounded-full border border-stone-200 bg-stone-100">
              <img v-if="avatarPreviewUrl" :src="avatarPreviewUrl" alt="avatar" class="h-full w-full object-cover" />
              <div v-else class="flex h-full w-full items-center justify-center text-xs text-stone-500">暂无头像</div>
            </div>
            <label class="rounded-xl border border-stone-300 px-3 py-2 text-sm text-stone-700 transition hover:border-amber-500">
              选择头像
              <input type="file" accept="image/png,image/jpeg,image/webp" class="hidden" @change="onAvatarSelected" />
            </label>
          </div>

          <p v-if="avatarMessage" class="mt-2 text-sm" :class="avatarMessage.includes('成功') ? 'text-emerald-600' : 'text-rose-600'">
            {{ avatarMessage }}
          </p>

          <label class="mt-4 block text-sm text-stone-700">
            昵称
            <input
              v-model.trim="profileForm.nickname"
              type="text"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            />
          </label>

          <label class="mt-3 block text-sm text-stone-700">
            性别
            <select
              v-model.number="profileForm.gender"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            >
              <option :value="0">未设置</option>
              <option :value="1">男</option>
              <option :value="2">女</option>
              <option :value="9">保密</option>
            </select>
          </label>

          <label class="mt-3 block text-sm text-stone-700">
            出生日期
            <input
              v-model="profileForm.birthDate"
              type="date"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            />
          </label>

          <label class="mt-3 block text-sm text-stone-700">
            个人简介
            <textarea
              v-model="profileForm.bio"
              rows="4"
              maxlength="280"
              class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
            />
            <span class="mt-1 block text-right text-xs text-stone-500">{{ bioLength }}/280</span>
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
      </div>
    </ResultState>

    <div
      v-if="avatarModalOpen && avatarSource"
      class="fixed inset-0 z-40 flex items-center justify-center bg-stone-900/60 px-4"
      @click.self="clearAvatarSelection"
    >
      <div class="w-full max-w-2xl rounded-2xl border border-stone-200 bg-white p-5 shadow-xl">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h3 class="text-lg font-semibold text-stone-900">裁剪头像</h3>
            <p class="mt-1 text-sm text-stone-600">建议保留清晰的人像主体，上传后立即生效。</p>
            <p v-if="selectedAvatarFileName" class="mt-1 text-xs text-stone-500">{{ selectedAvatarFileName }}</p>
          </div>
          <button
            type="button"
            class="rounded-lg border border-stone-300 px-2 py-1 text-xs text-stone-600 transition hover:bg-stone-100"
            @click="clearAvatarSelection"
          >
            关闭
          </button>
        </div>

        <Cropper
          ref="cropperRef"
          class="mt-4 h-80 w-full rounded-xl border border-stone-200 bg-stone-50"
          :src="avatarSource"
          image-restriction="stencil"
          :stencil-props="{ aspectRatio: 1 }"
        />

        <div class="mt-4 grid gap-2 sm:grid-cols-2">
          <button
            type="button"
            class="rounded-xl border border-stone-300 px-4 py-2 text-sm font-medium text-stone-800 transition hover:border-amber-500"
            @click="clearAvatarSelection"
          >
            取消
          </button>
          <button
            type="button"
            class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
            :disabled="uploadAvatarMutation.isPending.value"
            @click="submitAvatar"
          >
            {{ uploadAvatarMutation.isPending.value ? '上传中...' : '确认并上传' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
