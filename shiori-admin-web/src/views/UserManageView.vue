<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  getAdminUser,
  listAdminRoles,
  listAdminUsers,
  updateAdminRole,
  updateAdminUserStatus,
  type AdminUserSummary,
} from '@/api/adminUser'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const status = ref('')
const role = ref('')
const selectedUserId = ref<number | null>(null)
const actionError = ref('')

const usersQuery = useQuery({
  queryKey: computed(() => ['admin-users', page.value, size.value, keyword.value, status.value, role.value]),
  queryFn: () =>
    listAdminUsers({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      role: role.value || undefined,
    }),
})

const rolesQuery = useQuery({
  queryKey: ['admin-roles'],
  queryFn: () => listAdminRoles(),
})

const selectedUserQuery = useQuery({
  queryKey: computed(() => ['admin-user-detail', selectedUserId.value]),
  queryFn: () => getAdminUser(selectedUserId.value as number),
  enabled: computed(() => selectedUserId.value !== null),
})

const statusMutation = useMutation({
  mutationFn: ({ user, nextStatus }: { user: AdminUserSummary; nextStatus: 'ENABLED' | 'DISABLED' }) =>
    updateAdminUserStatus(user.userId, {
      status: nextStatus,
      reason: `后台手动${nextStatus === 'DISABLED' ? '禁用' : '启用'}用户`,
    }),
  onSuccess: async () => {
    actionError.value = ''
    await queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    if (selectedUserId.value != null) {
      await queryClient.invalidateQueries({ queryKey: ['admin-user-detail', selectedUserId.value] })
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '操作失败'
  },
})

const roleMutation = useMutation({
  mutationFn: ({ user, grantAdmin }: { user: AdminUserSummary; grantAdmin: boolean }) =>
    updateAdminRole(user.userId, {
      grantAdmin,
      reason: grantAdmin ? '后台授予管理员角色' : '后台回收管理员角色',
    }),
  onSuccess: async () => {
    actionError.value = ''
    await queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    if (selectedUserId.value != null) {
      await queryClient.invalidateQueries({ queryKey: ['admin-user-detail', selectedUserId.value] })
    }
  },
  onError: (error) => {
    actionError.value = error instanceof ApiBizError ? error.message : '操作失败'
  },
})

const totalPage = computed(() => {
  const total = usersQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / size.value), 1)
})

function onSearch() {
  page.value = 1
}

function selectUser(userId: number) {
  selectedUserId.value = userId
}

function toggleUserStatus(user: AdminUserSummary) {
  const nextStatus = user.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  statusMutation.mutate({ user, nextStatus })
}

function toggleAdminRole(user: AdminUserSummary) {
  const hasAdmin = user.roles.some((item) => item.toUpperCase() === 'ROLE_ADMIN')
  roleMutation.mutate({ user, grantAdmin: !hasAdmin })
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">用户管理</h1>
      <p class="mt-1 text-sm text-slate-500">检索用户、启用/禁用账号、授予或回收管理员角色。</p>
    </div>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-5">
        <input v-model="keyword" placeholder="用户名/昵称/userNo" class="rounded-md border border-slate-300 px-3 py-2 text-sm" />
        <select v-model="status" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部状态</option>
          <option value="ENABLED">ENABLED</option>
          <option value="DISABLED">DISABLED</option>
          <option value="LOCKED">LOCKED</option>
        </select>
        <select v-model="role" class="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">全部角色</option>
          <option v-for="item in rolesQuery.data.value || []" :key="item.roleCode" :value="item.roleCode">{{ item.roleCode }}</option>
        </select>
        <button class="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white" @click="onSearch">查询</button>
      </div>
    </div>

    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-[2fr_1fr]">
      <div class="overflow-hidden rounded-xl border border-slate-200 bg-white">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-4 py-3 text-left">用户</th>
              <th class="px-4 py-3 text-left">状态</th>
              <th class="px-4 py-3 text-left">角色</th>
              <th class="px-4 py-3 text-left">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in usersQuery.data.value?.items || []" :key="user.userId" class="border-t border-slate-100">
              <td class="px-4 py-3">
                <button class="text-left text-blue-600 hover:underline" @click="selectUser(user.userId)">
                  {{ user.username }} ({{ user.userNo }})
                </button>
                <p class="text-xs text-slate-500">{{ user.nickname }}</p>
              </td>
              <td class="px-4 py-3">{{ user.status }}</td>
              <td class="px-4 py-3">{{ user.roles.join(', ') || '-' }}</td>
              <td class="px-4 py-3">
                <div class="flex flex-wrap gap-2">
                  <button class="rounded bg-slate-900 px-2 py-1 text-xs text-white" @click="toggleUserStatus(user)">
                    {{ user.status === 'DISABLED' ? '启用' : '禁用' }}
                  </button>
                  <button class="rounded bg-blue-600 px-2 py-1 text-xs text-white" @click="toggleAdminRole(user)">
                    {{ user.roles.includes('ROLE_ADMIN') ? '回收管理员' : '授予管理员' }}
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="(usersQuery.data.value?.items || []).length === 0">
              <td colspan="4" class="px-4 py-10 text-center text-slate-400">暂无数据</td>
            </tr>
          </tbody>
        </table>
        <div class="flex items-center justify-end gap-2 border-t border-slate-100 px-4 py-3 text-sm">
          <button class="rounded border px-2 py-1" :disabled="page <= 1" @click="page -= 1">上一页</button>
          <span>{{ page }} / {{ totalPage }}</span>
          <button class="rounded border px-2 py-1" :disabled="page >= totalPage" @click="page += 1">下一页</button>
        </div>
      </div>

      <aside class="rounded-xl border border-slate-200 bg-white p-4">
        <h2 class="text-lg font-semibold text-slate-900">用户详情</h2>
        <div v-if="selectedUserQuery.data.value" class="mt-3 space-y-2 text-sm text-slate-700">
          <p>用户名：{{ selectedUserQuery.data.value.username }}</p>
          <p>昵称：{{ selectedUserQuery.data.value.nickname }}</p>
          <p>状态：{{ selectedUserQuery.data.value.status }}</p>
          <p>角色：{{ selectedUserQuery.data.value.roles.join(', ') || '-' }}</p>
          <p>最后登录IP：{{ selectedUserQuery.data.value.lastLoginIp || '-' }}</p>
        </div>
        <p v-else class="mt-3 text-sm text-slate-400">点击左侧用户名查看详情</p>
      </aside>
    </div>
  </section>
</template>
