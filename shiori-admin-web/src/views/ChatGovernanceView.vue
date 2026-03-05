<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  createForbiddenWord,
  deleteForbiddenWord,
  handleChatReport,
  listChatBlocks,
  listChatReports,
  listForbiddenWords,
  updateForbiddenWord,
  type ForbiddenWordItem,
  type UpsertForbiddenWordPayload,
} from '@/api/adminChatGovernance'
import { ApiBizError } from '@/types/result'

const queryClient = useQueryClient()
const actionError = ref('')

const reportPage = ref(1)
const reportSize = ref(10)
const reportStatus = ref('')
const reportRemark = ref('')

const blockPage = ref(1)
const blockSize = ref(10)
const blockerUserId = ref<number | null>(null)
const targetUserId = ref<number | null>(null)

const includeDisabledWords = ref(true)
const editingWord = ref<ForbiddenWordItem | null>(null)
const wordForm = ref<UpsertForbiddenWordPayload>({
  word: '',
  matchType: 'KEYWORD',
  policy: 'MASK',
  mask: '***',
  status: 'ACTIVE',
})

const reportsQuery = useQuery({
  queryKey: computed(() => ['chat-reports', reportPage.value, reportSize.value, reportStatus.value]),
  queryFn: () =>
    listChatReports({
      page: reportPage.value,
      size: reportSize.value,
      status: reportStatus.value || undefined,
    }),
})

const blocksQuery = useQuery({
  queryKey: computed(() => ['chat-blocks-admin', blockPage.value, blockSize.value, blockerUserId.value, targetUserId.value]),
  queryFn: () =>
    listChatBlocks({
      page: blockPage.value,
      size: blockSize.value,
      blockerUserId: blockerUserId.value || undefined,
      targetUserId: targetUserId.value || undefined,
    }),
})

const forbiddenWordsQuery = useQuery({
  queryKey: computed(() => ['chat-forbidden-words', includeDisabledWords.value]),
  queryFn: () => listForbiddenWords({ includeDisabled: includeDisabledWords.value }),
})

const handleReportMutation = useMutation({
  mutationFn: ({ reportId, status }: { reportId: number; status: 'RESOLVED' | 'REJECTED' | 'IGNORED' }) =>
    handleChatReport(reportId, { status, remark: reportRemark.value || undefined }),
  onSuccess: () => {
    actionError.value = ''
    reportRemark.value = ''
    void queryClient.invalidateQueries({ queryKey: ['chat-reports'] })
  },
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const createWordMutation = useMutation({
  mutationFn: () => createForbiddenWord(wordForm.value),
  onSuccess: () => {
    actionError.value = ''
    resetWordForm()
    void queryClient.invalidateQueries({ queryKey: ['chat-forbidden-words'] })
  },
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const updateWordMutation = useMutation({
  mutationFn: (ruleId: number) => updateForbiddenWord(ruleId, wordForm.value),
  onSuccess: () => {
    actionError.value = ''
    resetWordForm()
    void queryClient.invalidateQueries({ queryKey: ['chat-forbidden-words'] })
  },
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const deleteWordMutation = useMutation({
  mutationFn: (ruleId: number) => deleteForbiddenWord(ruleId),
  onSuccess: () => {
    actionError.value = ''
    if (editingWord.value) {
      resetWordForm()
    }
    void queryClient.invalidateQueries({ queryKey: ['chat-forbidden-words'] })
  },
  onError: (error) => {
    actionError.value = resolveError(error)
  },
})

const reportTotalPage = computed(() => {
  const total = reportsQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / reportSize.value), 1)
})

const blockTotalPage = computed(() => {
  const total = blocksQuery.data.value?.total || 0
  return Math.max(Math.ceil(total / blockSize.value), 1)
})

function resolveError(error: unknown): string {
  return error instanceof ApiBizError ? error.message : '操作失败'
}

function onReportSearch(): void {
  reportPage.value = 1
}

function onBlockSearch(): void {
  blockPage.value = 1
}

function handleReportAction(reportId: number, status: 'RESOLVED' | 'REJECTED' | 'IGNORED'): void {
  handleReportMutation.mutate({ reportId, status })
}

function selectWord(item: ForbiddenWordItem): void {
  editingWord.value = item
  wordForm.value = {
    word: item.word,
    matchType: item.matchType === 'EXACT' ? 'EXACT' : 'KEYWORD',
    policy: item.policy === 'REJECT' ? 'REJECT' : 'MASK',
    mask: item.mask || '***',
    status: item.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE',
  }
}

function submitWordForm(): void {
  if (!wordForm.value.word?.trim()) {
    actionError.value = '违禁词不能为空'
    return
  }
  if (editingWord.value) {
    updateWordMutation.mutate(editingWord.value.id)
    return
  }
  createWordMutation.mutate()
}

function resetWordForm(): void {
  editingWord.value = null
  wordForm.value = {
    word: '',
    matchType: 'KEYWORD',
    policy: 'MASK',
    mask: '***',
    status: 'ACTIVE',
  }
}
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">聊天治理</h1>
      <p class="mt-1 text-sm text-slate-500">处理举报、查询拉黑关系、维护违禁词策略。</p>
    </div>

    <p v-if="actionError" class="text-sm text-rose-600">{{ actionError }}</p>

    <section class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="flex flex-wrap items-center gap-2">
        <h2 class="text-lg font-semibold text-slate-900">举报处理</h2>
        <select v-model="reportStatus" class="rounded border border-slate-300 px-2 py-1 text-sm">
          <option value="">全部状态</option>
          <option value="PENDING">PENDING</option>
          <option value="RESOLVED">RESOLVED</option>
          <option value="REJECTED">REJECTED</option>
          <option value="IGNORED">IGNORED</option>
        </select>
        <input v-model.trim="reportRemark" class="rounded border border-slate-300 px-2 py-1 text-sm" placeholder="处理备注（可选）" />
        <button class="rounded bg-blue-600 px-3 py-1.5 text-xs text-white" @click="onReportSearch">查询</button>
      </div>

      <div class="mt-3 overflow-x-auto">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-3 py-2 text-left">ID</th>
              <th class="px-3 py-2 text-left">举报人</th>
              <th class="px-3 py-2 text-left">被举报人</th>
              <th class="px-3 py-2 text-left">原因</th>
              <th class="px-3 py-2 text-left">状态</th>
              <th class="px-3 py-2 text-left">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in reportsQuery.data.value?.items || []" :key="item.id" class="border-t border-slate-100">
              <td class="px-3 py-2">{{ item.id }}</td>
              <td class="px-3 py-2">{{ item.reporterUserId }}</td>
              <td class="px-3 py-2">{{ item.targetUserId }}</td>
              <td class="px-3 py-2">{{ item.reason }}</td>
              <td class="px-3 py-2">{{ item.status }}</td>
              <td class="px-3 py-2">
                <div class="flex flex-wrap gap-1">
                  <button class="rounded bg-emerald-600 px-2 py-1 text-xs text-white" @click="handleReportAction(item.id, 'RESOLVED')">通过</button>
                  <button class="rounded bg-rose-600 px-2 py-1 text-xs text-white" @click="handleReportAction(item.id, 'REJECTED')">驳回</button>
                  <button class="rounded bg-slate-700 px-2 py-1 text-xs text-white" @click="handleReportAction(item.id, 'IGNORED')">忽略</button>
                </div>
              </td>
            </tr>
            <tr v-if="(reportsQuery.data.value?.items || []).length === 0">
              <td colspan="6" class="px-3 py-6 text-center text-slate-400">暂无举报记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="mt-2 flex items-center justify-end gap-2 text-sm">
        <button class="rounded border px-2 py-1" :disabled="reportPage <= 1" @click="reportPage -= 1">上一页</button>
        <span>{{ reportPage }} / {{ reportTotalPage }}</span>
        <button class="rounded border px-2 py-1" :disabled="reportPage >= reportTotalPage" @click="reportPage += 1">下一页</button>
      </div>
    </section>

    <section class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="flex flex-wrap items-center gap-2">
        <h2 class="text-lg font-semibold text-slate-900">拉黑关系</h2>
        <input v-model.number="blockerUserId" type="number" class="rounded border border-slate-300 px-2 py-1 text-sm" placeholder="blockerUserId" />
        <input v-model.number="targetUserId" type="number" class="rounded border border-slate-300 px-2 py-1 text-sm" placeholder="targetUserId" />
        <button class="rounded bg-blue-600 px-3 py-1.5 text-xs text-white" @click="onBlockSearch">查询</button>
      </div>

      <div class="mt-3 overflow-x-auto">
        <table class="min-w-full text-sm">
          <thead class="bg-slate-50 text-slate-600">
            <tr>
              <th class="px-3 py-2 text-left">拉黑人</th>
              <th class="px-3 py-2 text-left">目标用户</th>
              <th class="px-3 py-2 text-left">创建时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in blocksQuery.data.value?.items || []" :key="`${item.blockerUserId}-${item.targetUserId}`" class="border-t border-slate-100">
              <td class="px-3 py-2">{{ item.blockerUserId }}</td>
              <td class="px-3 py-2">{{ item.targetUserId }}</td>
              <td class="px-3 py-2">{{ item.createdAt }}</td>
            </tr>
            <tr v-if="(blocksQuery.data.value?.items || []).length === 0">
              <td colspan="3" class="px-3 py-6 text-center text-slate-400">暂无拉黑记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="mt-2 flex items-center justify-end gap-2 text-sm">
        <button class="rounded border px-2 py-1" :disabled="blockPage <= 1" @click="blockPage -= 1">上一页</button>
        <span>{{ blockPage }} / {{ blockTotalPage }}</span>
        <button class="rounded border px-2 py-1" :disabled="blockPage >= blockTotalPage" @click="blockPage += 1">下一页</button>
      </div>
    </section>

    <section class="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_2fr]">
      <div class="rounded-xl border border-slate-200 bg-white p-4">
        <h2 class="text-lg font-semibold text-slate-900">{{ editingWord ? '编辑违禁词' : '新增违禁词' }}</h2>
        <div class="mt-3 space-y-2">
          <input v-model.trim="wordForm.word" class="w-full rounded border border-slate-300 px-2 py-1 text-sm" placeholder="词条" />
          <select v-model="wordForm.matchType" class="w-full rounded border border-slate-300 px-2 py-1 text-sm">
            <option value="KEYWORD">KEYWORD</option>
            <option value="EXACT">EXACT</option>
          </select>
          <select v-model="wordForm.policy" class="w-full rounded border border-slate-300 px-2 py-1 text-sm">
            <option value="MASK">MASK</option>
            <option value="REJECT">REJECT</option>
          </select>
          <input v-model.trim="wordForm.mask" class="w-full rounded border border-slate-300 px-2 py-1 text-sm" placeholder="掩码（MASK 策略生效）" />
          <select v-model="wordForm.status" class="w-full rounded border border-slate-300 px-2 py-1 text-sm">
            <option value="ACTIVE">ACTIVE</option>
            <option value="DISABLED">DISABLED</option>
          </select>
          <div class="flex gap-2">
            <button class="rounded bg-blue-600 px-3 py-1.5 text-xs text-white" @click="submitWordForm">
              {{ editingWord ? '保存修改' : '新增' }}
            </button>
            <button class="rounded border border-slate-300 px-3 py-1.5 text-xs" @click="resetWordForm">重置</button>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-slate-200 bg-white p-4">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold text-slate-900">违禁词列表</h2>
          <label class="flex items-center gap-2 text-sm text-slate-600">
            <input v-model="includeDisabledWords" type="checkbox" />
            包含 DISABLED
          </label>
        </div>

        <div class="mt-3 overflow-x-auto">
          <table class="min-w-full text-sm">
            <thead class="bg-slate-50 text-slate-600">
              <tr>
                <th class="px-3 py-2 text-left">词条</th>
                <th class="px-3 py-2 text-left">匹配</th>
                <th class="px-3 py-2 text-left">策略</th>
                <th class="px-3 py-2 text-left">状态</th>
                <th class="px-3 py-2 text-left">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in forbiddenWordsQuery.data.value?.items || []" :key="item.id" class="border-t border-slate-100">
                <td class="px-3 py-2">{{ item.word }}</td>
                <td class="px-3 py-2">{{ item.matchType }}</td>
                <td class="px-3 py-2">{{ item.policy }} <span v-if="item.policy === 'MASK'">({{ item.mask }})</span></td>
                <td class="px-3 py-2">{{ item.status }}</td>
                <td class="px-3 py-2">
                  <div class="flex gap-2">
                    <button class="rounded bg-slate-800 px-2 py-1 text-xs text-white" @click="selectWord(item)">编辑</button>
                    <button class="rounded bg-rose-600 px-2 py-1 text-xs text-white" @click="deleteWordMutation.mutate(item.id)">删除</button>
                  </div>
                </td>
              </tr>
              <tr v-if="(forbiddenWordsQuery.data.value?.items || []).length === 0">
                <td colspan="5" class="px-3 py-6 text-center text-slate-400">暂无词库配置</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </section>
</template>
