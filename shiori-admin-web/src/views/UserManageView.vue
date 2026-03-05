<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  getAdminUser,
  listAdminUserCapabilityBans,
  listAdminRoles,
  listAdminUserAudits,
  listAdminUsers,
  lockAdminUser,
  removeAdminUserCapabilityBan,
  resetAdminUserPassword,
  unlockAdminUser,
  updateAdminRole,
  upsertAdminUserCapabilityBan,
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

const lockDurationMinutes = ref(15)
const lockReason = ref('后台手动锁定用户')
const unlockReason = ref('后台手动解锁用户')
const resetPassword = ref('')
const resetReason = ref('后台重置密码')
const forceChangePassword = ref(true)
const capability = ref<'CHAT_SEND' | 'CHAT_READ' | 'PRODUCT_PUBLISH' | 'ORDER_CREATE'>('CHAT_SEND')
const capabilityReason = ref('后台能力封禁')
const capabilityEndAt = ref('')

const auditPage = ref(1)
const auditSize = ref(10)
const auditAction = ref('')

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

const auditsQuery = useQuery({
  queryKey: computed(() => ['admin-user-audits', selectedUserId.value, auditPage.value, auditSize.value, auditAction.value]),
  queryFn: () =>
    listAdminUserAudits(selectedUserId.value as number, {
      page: auditPage.value,
      size: auditSize.value,
      action: auditAction.value || undefined,
    }),
  enabled: computed(() => selectedUserId.value !== null),
})

const capabilityBansQuery = useQuery({
  queryKey: computed(() => ['admin-user-capability-bans', selectedUserId.value]),
  queryFn: () => listAdminUserCapabilityBans(selectedUserId.value as number),
  enabled: computed(() => selectedUserId.value !== null),
})

const statusMutation = useMutation({
  mutationFn: ({ user, nextStatus }: { user: AdminUserSummary; nextStatus: 'ENABLED' | 'DISABLED' }) =>
    updateAdminUserStatus(user.userId, {
      status: nextStatus,
      reason: `后台手动${nextStatus === 'DISABLED' ? '禁用' : '启用'}用户`,
    }),
  onSuccess: () => handleMutationSuccess(),
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const roleMutation = useMutation({
  mutationFn: ({ user, grantAdmin }: { user: AdminUserSummary; grantAdmin: boolean }) =>
    updateAdminRole(user.userId, {
      grantAdmin,
      reason: grantAdmin ? '后台授予管理员角色' : '后台回收管理员角色',
    }),
  onSuccess: () => handleMutationSuccess(),
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const lockMutation = useMutation({
  mutationFn: (userId: number) =>
    lockAdminUser(userId, {
      durationMinutes: lockDurationMinutes.value,
      reason: lockReason.value || undefined,
    }),
  onSuccess: () => handleMutationSuccess(),
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const unlockMutation = useMutation({
  mutationFn: (userId: number) =>
    unlockAdminUser(userId, {
      reason: unlockReason.value || undefined,
    }),
  onSuccess: () => handleMutationSuccess(),
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const resetPasswordMutation = useMutation({
  mutationFn: (userId: number) =>
    resetAdminUserPassword(userId, {
      newPassword: resetPassword.value,
      forceChangePassword: forceChangePassword.value,
      reason: resetReason.value || undefined,
    }),
  onSuccess: () => {
    resetPassword.value = ''
    handleMutationSuccess()
  },
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const upsertCapabilityBanMutation = useMutation({
  mutationFn: (userId: number) =>
    upsertAdminUserCapabilityBan(userId, {
      capability: capability.value,
      reason: capabilityReason.value || undefined,
      endAt: capabilityEndAt.value || undefined,
    }),
  onSuccess: () => handleMutationSuccess(),
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const removeCapabilityBanMutation = useMutation({
  mutationFn: ({ userId, capabilityCode }: { userId: number; capabilityCode: 'CHAT_SEND' | 'CHAT_READ' | 'PRODUCT_PUBLISH' | 'ORDER_CREATE' }) =>
    removeAdminUserCapabilityBan(userId, capabilityCode, capabilityReason.value || undefined),
  onSuccess: () => handleMutationSuccess(),
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const totalPage = computed(() => {
  const total = usersQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / size.value), 1)
})

const auditTotalPage = computed(() => {
  const total = auditsQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / auditSize.value), 1)
})

const selectedUser = computed(() => selectedUserQuery.data.value)
const currentSelectedUserId = computed(() => selectedUserId.value)

function onSearch() {
  page.value = 1
}

function onAuditSearch() {
  auditPage.value = 1
}

function selectUser(userId: number) {
  selectedUserId.value = userId
  auditPage.value = 1
  auditAction.value = ''
  actionError.value = ''
}

function toggleUserStatus(user: AdminUserSummary) {
  const nextStatus = user.status === 'DISABLED' ? 'ENABLED' : 'DISABLED'
  statusMutation.mutate({ user, nextStatus })
}

function toggleAdminRole(user: AdminUserSummary) {
  const hasAdmin = user.roles.some((item) => item.toUpperCase() === 'ROLE_ADMIN')
  roleMutation.mutate({ user, grantAdmin: !hasAdmin })
}

function executeLock() {
  if (!currentSelectedUserId.value) {
    return
  }
  lockMutation.mutate(currentSelectedUserId.value)
}

function executeUnlock() {
  if (!currentSelectedUserId.value) {
    return
  }
  unlockMutation.mutate(currentSelectedUserId.value)
}

function executeResetPassword() {
  if (!currentSelectedUserId.value) {
    return
  }
  if (!resetPassword.value || resetPassword.value.length < 8) {
    actionError.value = '重置密码长度至少 8 位'
    return
  }
  resetPasswordMutation.mutate(currentSelectedUserId.value)
}

function executeUpsertCapabilityBan() {
  if (!currentSelectedUserId.value) {
    return
  }
  upsertCapabilityBanMutation.mutate(currentSelectedUserId.value)
}

function executeRemoveCapabilityBan(capabilityCode: 'CHAT_SEND' | 'CHAT_READ' | 'PRODUCT_PUBLISH' | 'ORDER_CREATE') {
  if (!currentSelectedUserId.value) {
    return
  }
  removeCapabilityBanMutation.mutate({
    userId: currentSelectedUserId.value,
    capabilityCode,
  })
}

function resolveError(error: unknown): string {
  return error instanceof ApiBizError ? error.message : '操作失败'
}

function handleMutationSuccess() {
  actionError.value = ''
  void queryClient.invalidateQueries({ queryKey: ['admin-users'] })
  if (selectedUserId.value != null) {
    void queryClient.invalidateQueries({ queryKey: ['admin-user-detail', selectedUserId.value] })
    void queryClient.invalidateQueries({ queryKey: ['admin-user-audits', selectedUserId.value] })
    void queryClient.invalidateQueries({ queryKey: ['admin-user-capability-bans', selectedUserId.value] })
  }
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">用户管理</h1>
      <p class="mt-1 text-sm text-slate-500">检索用户、启用/禁用、锁定/解锁、重置密码与审计查询。</p>
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

      <aside class="space-y-4">
        <section class="rounded-xl border border-slate-200 bg-white p-4">
          <h2 class="text-lg font-semibold text-slate-900">用户详情</h2>
          <div v-if="selectedUser" class="mt-3 space-y-2 text-sm text-slate-700">
            <p>用户名：{{ selectedUser.username }}</p>
            <p>昵称：{{ selectedUser.nickname }}</p>
            <p>状态：{{ selectedUser.status }}</p>
            <p>角色：{{ selectedUser.roles.join(', ') || '-' }}</p>
            <p>最后登录IP：{{ selectedUser.lastLoginIp || '-' }}</p>
            <p>最后登录时间：{{ selectedUser.lastLoginAt || '-' }}</p>
            <p>必须改密：{{ selectedUser.mustChangePassword ? '是' : '否' }}</p>
          </div>
          <p v-else class="mt-3 text-sm text-slate-400">点击左侧用户名查看详情</p>
        </section>

        <section v-if="selectedUser" class="rounded-xl border border-slate-200 bg-white p-4">
          <h2 class="text-base font-semibold text-slate-900">治理操作</h2>

          <div class="mt-3 space-y-2">
            <label class="block text-xs text-slate-600">锁定时长（分钟）</label>
            <input v-model.number="lockDurationMinutes" type="number" min="1" class="w-full rounded border border-slate-300 px-2 py-1 text-sm" />
            <input v-model.trim="lockReason" type="text" placeholder="锁定原因" class="w-full rounded border border-slate-300 px-2 py-1 text-sm" />
            <button class="w-full rounded bg-amber-600 px-2 py-1.5 text-xs text-white" @click="executeLock">锁定用户</button>
          </div>

          <div class="mt-3 space-y-2">
            <input v-model.trim="unlockReason" type="text" placeholder="解锁原因" class="w-full rounded border border-slate-300 px-2 py-1 text-sm" />
            <button class="w-full rounded bg-emerald-600 px-2 py-1.5 text-xs text-white" @click="executeUnlock">解锁用户</button>
          </div>

          <div class="mt-3 space-y-2">
            <input
              v-model="resetPassword"
              type="text"
              placeholder="新密码（至少 8 位）"
              class="w-full rounded border border-slate-300 px-2 py-1 text-sm"
            />
            <input v-model.trim="resetReason" type="text" placeholder="重置原因" class="w-full rounded border border-slate-300 px-2 py-1 text-sm" />
            <label class="flex items-center gap-2 text-xs text-slate-600">
              <input v-model="forceChangePassword" type="checkbox" />
              强制下次登录改密
            </label>
            <button class="w-full rounded bg-rose-600 px-2 py-1.5 text-xs text-white" @click="executeResetPassword">重置密码</button>
          </div>

          <div class="mt-4 space-y-2 border-t border-slate-200 pt-3">
            <label class="block text-xs text-slate-600">能力封禁</label>
            <select v-model="capability" class="w-full rounded border border-slate-300 px-2 py-1 text-sm">
              <option value="CHAT_SEND">CHAT_SEND</option>
              <option value="CHAT_READ">CHAT_READ</option>
              <option value="PRODUCT_PUBLISH">PRODUCT_PUBLISH</option>
              <option value="ORDER_CREATE">ORDER_CREATE</option>
            </select>
            <input
              v-model.trim="capabilityReason"
              type="text"
              placeholder="封禁/解封原因"
              class="w-full rounded border border-slate-300 px-2 py-1 text-sm"
            />
            <input
              v-model="capabilityEndAt"
              type="datetime-local"
              class="w-full rounded border border-slate-300 px-2 py-1 text-sm"
            />
            <button class="w-full rounded bg-indigo-600 px-2 py-1.5 text-xs text-white" @click="executeUpsertCapabilityBan">
              新增/更新能力封禁
            </button>

            <div class="space-y-1 pt-2">
              <p class="text-xs text-slate-500">当前能力封禁：</p>
              <div
                v-for="item in capabilityBansQuery.data.value || []"
                :key="`${item.userId}-${item.capability}`"
                class="flex items-center justify-between rounded border border-slate-200 px-2 py-1 text-xs"
              >
                <div>
                  <p class="font-medium text-slate-700">{{ item.capability }} · {{ item.banned ? 'BANNED' : 'UNBANNED' }}</p>
                  <p class="text-slate-500">{{ item.reason || '-' }}</p>
                </div>
                <button
                  class="rounded bg-emerald-600 px-2 py-1 text-white"
                  :disabled="!item.banned"
                  @click="executeRemoveCapabilityBan(item.capability)"
                >
                  解封
                </button>
              </div>
              <p v-if="(capabilityBansQuery.data.value || []).length === 0" class="text-xs text-slate-400">暂无能力封禁记录</p>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <section v-if="selectedUser" class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="flex flex-wrap items-center gap-2">
        <h2 class="text-base font-semibold text-slate-900">治理审计日志</h2>
        <input
          v-model.trim="auditAction"
          type="text"
          placeholder="按 action 过滤，如 USER_LOCK"
          class="rounded border border-slate-300 px-2 py-1 text-sm"
        />
        <button class="rounded bg-slate-800 px-2 py-1 text-xs text-white" @click="onAuditSearch">查询</button>
      </div>

      <div class="mt-3 overflow-x-auto">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-3 py-2 text-left">时间</th>
              <th class="px-3 py-2 text-left">动作</th>
              <th class="px-3 py-2 text-left">操作人</th>
              <th class="px-3 py-2 text-left">原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in auditsQuery.data.value?.items || []" :key="item.id" class="border-t border-slate-100">
              <td class="px-3 py-2">{{ item.createdAt }}</td>
              <td class="px-3 py-2">{{ item.action }}</td>
              <td class="px-3 py-2">{{ item.operatorUserId }}</td>
              <td class="px-3 py-2">{{ item.reason || '-' }}</td>
            </tr>
            <tr v-if="(auditsQuery.data.value?.items || []).length === 0">
              <td colspan="4" class="px-3 py-6 text-center text-slate-400">暂无审计记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="mt-2 flex items-center justify-end gap-2 text-sm">
        <button class="rounded border px-2 py-1" :disabled="auditPage <= 1" @click="auditPage -= 1">上一页</button>
        <span>{{ auditPage }} / {{ auditTotalPage }}</span>
        <button class="rounded border px-2 py-1" :disabled="auditPage >= auditTotalPage" @click="auditPage += 1">下一页</button>
      </div>
    </section>
  </section>
</template>
