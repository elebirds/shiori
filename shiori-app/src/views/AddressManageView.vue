<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, reactive, ref, watch } from 'vue'

import ResultState from '@/components/ResultState.vue'
import {
  createMyAddress,
  deleteMyAddress,
  listMyAddresses,
  setMyAddressDefault,
  updateMyAddress,
  type UpsertUserAddressRequest,
} from '@/api/auth'
import { ApiBizError } from '@/types/result'
import { CN_MAINLAND_REGIONS } from '@/constants/cnRegions'

const queryClient = useQueryClient()

const form = reactive<UpsertUserAddressRequest>({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  isDefault: false,
})

const editingAddressId = ref<number | null>(null)
const submitMessage = ref('')
const submitError = ref('')

const addressQuery = useQuery({
  queryKey: ['my-addresses'],
  queryFn: listMyAddresses,
})

const addresses = computed(() => addressQuery.data.value || [])
const errorMessage = computed(() => (addressQuery.error.value instanceof Error ? addressQuery.error.value.message : ''))

const provinceOptions = computed(() => CN_MAINLAND_REGIONS.map((item) => item.name))
const cityOptions = computed(() => {
  const province = CN_MAINLAND_REGIONS.find((item) => item.name === form.province)
  return province ? province.cities.map((item) => item.name) : []
})
const districtOptions = computed(() => {
  const province = CN_MAINLAND_REGIONS.find((item) => item.name === form.province)
  const city = province?.cities.find((item) => item.name === form.city)
  return city ? city.districts : []
})

watch(
  () => form.province,
  (value) => {
    if (!value || !cityOptions.value.includes(form.city)) {
      form.city = cityOptions.value[0] || ''
    }
    if (!districtOptions.value.includes(form.district)) {
      form.district = districtOptions.value[0] || ''
    }
  },
)

watch(
  () => form.city,
  () => {
    if (!districtOptions.value.includes(form.district)) {
      form.district = districtOptions.value[0] || ''
    }
  },
)

const saveMutation = useMutation({
  mutationFn: async () => {
    const payload: UpsertUserAddressRequest = {
      receiverName: form.receiverName.trim(),
      receiverPhone: form.receiverPhone.trim(),
      province: form.province,
      city: form.city,
      district: form.district,
      detailAddress: form.detailAddress.trim(),
      isDefault: Boolean(form.isDefault),
    }
    if (editingAddressId.value) {
      return updateMyAddress(editingAddressId.value, payload)
    }
    return createMyAddress(payload)
  },
  onSuccess: async () => {
    submitError.value = ''
    submitMessage.value = editingAddressId.value ? '地址更新成功' : '地址新增成功'
    clearForm()
    await queryClient.invalidateQueries({ queryKey: ['my-addresses'] })
  },
  onError: (error) => {
    submitMessage.value = ''
    submitError.value = error instanceof ApiBizError ? error.message : '保存地址失败'
  },
})

const deleteMutation = useMutation({
  mutationFn: (addressId: number) => deleteMyAddress(addressId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['my-addresses'] })
  },
})

const defaultMutation = useMutation({
  mutationFn: (addressId: number) => setMyAddressDefault(addressId),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['my-addresses'] })
  },
})

function clearForm(): void {
  editingAddressId.value = null
  form.receiverName = ''
  form.receiverPhone = ''
  form.province = provinceOptions.value[0] || ''
  form.city = cityOptions.value[0] || ''
  form.district = districtOptions.value[0] || ''
  form.detailAddress = ''
  form.isDefault = false
}

function editAddress(addressId: number): void {
  const target = addresses.value.find((item) => item.addressId === addressId)
  if (!target) {
    return
  }
  editingAddressId.value = addressId
  form.receiverName = target.receiverName
  form.receiverPhone = target.receiverPhone
  form.province = target.province
  form.city = target.city
  form.district = target.district
  form.detailAddress = target.detailAddress
  form.isDefault = target.isDefault
  submitMessage.value = ''
  submitError.value = ''
}

function validateForm(): boolean {
  if (!form.receiverName.trim()) {
    submitError.value = '请填写收件人'
    return false
  }
  if (!/^1\d{10}$/.test(form.receiverPhone.trim())) {
    submitError.value = '手机号格式不正确'
    return false
  }
  if (!form.province || !form.city || !form.district) {
    submitError.value = '请选择完整省市区'
    return false
  }
  if (!form.detailAddress.trim()) {
    submitError.value = '请填写详细地址'
    return false
  }
  return true
}

function submitForm(): void {
  submitError.value = ''
  submitMessage.value = ''
  if (!validateForm()) {
    return
  }
  saveMutation.mutate()
}

function removeAddress(addressId: number): void {
  if (!window.confirm('确认删除该地址？')) {
    return
  }
  deleteMutation.mutate(addressId)
}

function markDefault(addressId: number): void {
  defaultMutation.mutate(addressId)
}

clearForm()
</script>

<template>
  <section class="space-y-4">
    <header class="rounded-2xl border border-stone-200 bg-white/95 p-4">
      <h1 class="font-display text-2xl text-stone-900">收货地址</h1>
      <p class="mt-1 text-sm text-stone-600">管理用于邮寄订单的中国大陆收货地址。</p>
    </header>

    <article class="rounded-2xl border border-stone-200 bg-white/95 p-4">
      <h2 class="text-base font-semibold text-stone-900">{{ editingAddressId ? '编辑地址' : '新增地址' }}</h2>

      <div class="mt-3 grid gap-3 sm:grid-cols-2">
        <label class="text-sm text-stone-700">
          收件人
          <input
            v-model.trim="form.receiverName"
            maxlength="32"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          />
        </label>
        <label class="text-sm text-stone-700">
          手机号
          <input
            v-model.trim="form.receiverPhone"
            maxlength="11"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          />
        </label>
        <label class="text-sm text-stone-700">
          省份
          <select
            v-model="form.province"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          >
            <option v-for="item in provinceOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label class="text-sm text-stone-700">
          城市
          <select
            v-model="form.city"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          >
            <option v-for="item in cityOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label class="text-sm text-stone-700">
          区县
          <select
            v-model="form.district"
            class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          >
            <option v-for="item in districtOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label class="flex items-center gap-2 text-sm text-stone-700">
          <input v-model="form.isDefault" type="checkbox" />
          设为默认地址
        </label>
      </div>

      <label class="mt-3 block text-sm text-stone-700">
        详细地址
        <input
          v-model.trim="form.detailAddress"
          maxlength="128"
          class="mt-1 w-full rounded-xl border border-stone-300 px-3 py-2 text-sm outline-none transition focus:border-amber-500"
          placeholder="街道、门牌号、楼栋房间等"
        />
      </label>

      <div class="mt-4 flex flex-wrap gap-2">
        <button
          type="button"
          class="rounded-xl bg-stone-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-70"
          :disabled="saveMutation.isPending.value"
          @click="submitForm"
        >
          {{ saveMutation.isPending.value ? '保存中...' : editingAddressId ? '更新地址' : '新增地址' }}
        </button>
        <button
          type="button"
          class="rounded-xl border border-stone-300 px-4 py-2 text-sm text-stone-700 transition hover:bg-stone-100"
          @click="clearForm"
        >
          清空
        </button>
      </div>
      <p v-if="submitMessage" class="mt-2 text-sm text-emerald-600">{{ submitMessage }}</p>
      <p v-if="submitError" class="mt-2 text-sm text-rose-600">{{ submitError }}</p>
    </article>

    <ResultState
      :loading="addressQuery.isLoading.value"
      :error="errorMessage"
      :empty="!addressQuery.isLoading.value && addresses.length === 0"
      empty-text="还没有收货地址，先新增一个吧。"
    >
      <ul class="space-y-3">
        <li
          v-for="item in addresses"
          :key="item.addressId"
          class="rounded-2xl border border-stone-200 bg-white/95 p-4"
        >
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-sm font-semibold text-stone-900">
              {{ item.receiverName }} {{ item.receiverPhone }}
              <span v-if="item.isDefault" class="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700">默认</span>
            </p>
            <div class="flex gap-2 text-xs">
              <button
                type="button"
                class="rounded-lg border border-stone-300 px-2 py-1 text-stone-700 transition hover:bg-stone-100"
                @click="editAddress(item.addressId)"
              >
                编辑
              </button>
              <button
                v-if="!item.isDefault"
                type="button"
                class="rounded-lg border border-amber-300 px-2 py-1 text-amber-700 transition hover:bg-amber-50"
                @click="markDefault(item.addressId)"
              >
                设默认
              </button>
              <button
                type="button"
                class="rounded-lg border border-rose-300 px-2 py-1 text-rose-700 transition hover:bg-rose-50"
                @click="removeAddress(item.addressId)"
              >
                删除
              </button>
            </div>
          </div>
          <p class="mt-1 text-sm text-stone-600">{{ item.province }} {{ item.city }} {{ item.district }} {{ item.detailAddress }}</p>
        </li>
      </ul>
    </ResultState>
  </section>
</template>
