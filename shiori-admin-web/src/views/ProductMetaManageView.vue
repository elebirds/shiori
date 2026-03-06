<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'

import {
  createAdminProductCampus,
  createAdminProductCategory,
  createAdminProductSubCategory,
  listAdminProductMetaCampuses,
  listAdminProductMetaCategories,
  migrateAdminProductCampus,
  migrateAdminProductSubCategory,
  updateAdminProductCampus,
  updateAdminProductCategory,
  updateAdminProductSubCategory,
  type AdminProductMetaCampusResponse,
  type AdminProductMetaCategoryResponse,
  type AdminProductMetaSubCategoryResponse,
} from '@/api/adminProductMeta'
import { ApiBizError } from '@/types/result'

interface CampusDraft {
  campusName: string
  status: number
  sortOrder: number
}

interface CategoryDraft {
  categoryName: string
  status: number
  sortOrder: number
}

interface SubCategoryDraft {
  subCategoryName: string
  status: number
  sortOrder: number
}

const queryClient = useQueryClient()
const actionError = ref('')
const actionMessage = ref('')

const newCampus = reactive({
  campusCode: '',
  campusName: '',
  sortOrder: 100,
})

const newCategory = reactive({
  categoryCode: '',
  categoryName: '',
  sortOrder: 100,
})

const newSubCategory = reactive({
  categoryCode: '',
  subCategoryCode: '',
  subCategoryName: '',
  sortOrder: 100,
})

const migrateCampusForm = reactive({
  fromCampusCode: '',
  toCampusCode: '',
})

const migrateSubCategoryForm = reactive({
  fromSubCategoryCode: '',
  toSubCategoryCode: '',
})

const campusDrafts = reactive<Record<string, CampusDraft>>({})
const categoryDrafts = reactive<Record<string, CategoryDraft>>({})
const subCategoryDrafts = reactive<Record<string, SubCategoryDraft>>({})

const campusesQuery = useQuery({
  queryKey: ['admin-product-meta-campuses'],
  queryFn: () => listAdminProductMetaCampuses(),
})

const categoriesQuery = useQuery({
  queryKey: ['admin-product-meta-categories'],
  queryFn: () => listAdminProductMetaCategories(),
})

const campuses = computed(() => campusesQuery.data.value || [])
const categories = computed(() => categoriesQuery.data.value || [])
const allSubCategories = computed(() => categories.value.flatMap((category) => category.subCategories || []))

function setSuccess(message: string): void {
  actionError.value = ''
  actionMessage.value = message
}

function setError(error: unknown, fallback: string): void {
  actionMessage.value = ''
  actionError.value = error instanceof ApiBizError ? error.message : fallback
}

async function refreshMeta(): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['admin-product-meta-campuses'] }),
    queryClient.invalidateQueries({ queryKey: ['admin-product-meta-categories'] }),
  ])
}

function campusDraftOf(item: AdminProductMetaCampusResponse): CampusDraft {
  const existed = campusDrafts[item.campusCode]
  if (existed) {
    return existed
  }
  const created: CampusDraft = {
    campusName: item.campusName,
    status: item.status,
    sortOrder: item.sortOrder,
  }
  campusDrafts[item.campusCode] = created
  return created
}

function categoryDraftOf(item: AdminProductMetaCategoryResponse): CategoryDraft {
  const existed = categoryDrafts[item.categoryCode]
  if (existed) {
    return existed
  }
  const created: CategoryDraft = {
    categoryName: item.categoryName,
    status: item.status,
    sortOrder: item.sortOrder,
  }
  categoryDrafts[item.categoryCode] = created
  return created
}

function subCategoryDraftOf(item: AdminProductMetaSubCategoryResponse): SubCategoryDraft {
  const existed = subCategoryDrafts[item.subCategoryCode]
  if (existed) {
    return existed
  }
  const created: SubCategoryDraft = {
    subCategoryName: item.subCategoryName,
    status: item.status,
    sortOrder: item.sortOrder,
  }
  subCategoryDrafts[item.subCategoryCode] = created
  return created
}

function normalizeSortOrder(value: number | string): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return 100
  }
  return Math.max(0, Math.floor(parsed))
}

const createCampusMutation = useMutation({
  mutationFn: () =>
    createAdminProductCampus({
      campusCode: newCampus.campusCode.trim(),
      campusName: newCampus.campusName.trim(),
      sortOrder: normalizeSortOrder(newCampus.sortOrder),
    }),
  onSuccess: async () => {
    setSuccess('校区已创建')
    newCampus.campusCode = ''
    newCampus.campusName = ''
    newCampus.sortOrder = 100
    await refreshMeta()
  },
  onError: (error) => setError(error, '创建校区失败'),
})

const updateCampusMutation = useMutation({
  mutationFn: ({ campusCode, draft }: { campusCode: string; draft: CampusDraft }) =>
    updateAdminProductCampus(campusCode, {
      campusName: draft.campusName.trim(),
      status: draft.status,
      sortOrder: normalizeSortOrder(draft.sortOrder),
    }),
  onSuccess: async (_, variables) => {
    setSuccess(`校区 ${variables.campusCode} 已更新`)
    await refreshMeta()
  },
  onError: (error) => setError(error, '更新校区失败'),
})

const createCategoryMutation = useMutation({
  mutationFn: () =>
    createAdminProductCategory({
      categoryCode: newCategory.categoryCode.trim(),
      categoryName: newCategory.categoryName.trim(),
      sortOrder: normalizeSortOrder(newCategory.sortOrder),
    }),
  onSuccess: async () => {
    setSuccess('一级分类已创建')
    newCategory.categoryCode = ''
    newCategory.categoryName = ''
    newCategory.sortOrder = 100
    await refreshMeta()
  },
  onError: (error) => setError(error, '创建一级分类失败'),
})

const updateCategoryMutation = useMutation({
  mutationFn: ({ categoryCode, draft }: { categoryCode: string; draft: CategoryDraft }) =>
    updateAdminProductCategory(categoryCode, {
      categoryName: draft.categoryName.trim(),
      status: draft.status,
      sortOrder: normalizeSortOrder(draft.sortOrder),
    }),
  onSuccess: async (_, variables) => {
    setSuccess(`分类 ${variables.categoryCode} 已更新`)
    await refreshMeta()
  },
  onError: (error) => setError(error, '更新一级分类失败'),
})

const createSubCategoryMutation = useMutation({
  mutationFn: () =>
    createAdminProductSubCategory({
      categoryCode: newSubCategory.categoryCode,
      subCategoryCode: newSubCategory.subCategoryCode.trim(),
      subCategoryName: newSubCategory.subCategoryName.trim(),
      sortOrder: normalizeSortOrder(newSubCategory.sortOrder),
    }),
  onSuccess: async () => {
    setSuccess('子分类已创建')
    newSubCategory.categoryCode = ''
    newSubCategory.subCategoryCode = ''
    newSubCategory.subCategoryName = ''
    newSubCategory.sortOrder = 100
    await refreshMeta()
  },
  onError: (error) => setError(error, '创建子分类失败'),
})

const updateSubCategoryMutation = useMutation({
  mutationFn: ({ subCategoryCode, draft }: { subCategoryCode: string; draft: SubCategoryDraft }) =>
    updateAdminProductSubCategory(subCategoryCode, {
      subCategoryName: draft.subCategoryName.trim(),
      status: draft.status,
      sortOrder: normalizeSortOrder(draft.sortOrder),
    }),
  onSuccess: async (_, variables) => {
    setSuccess(`子分类 ${variables.subCategoryCode} 已更新`)
    await refreshMeta()
  },
  onError: (error) => setError(error, '更新子分类失败'),
})

const migrateCampusMutation = useMutation({
  mutationFn: () =>
    migrateAdminProductCampus({
      fromCampusCode: migrateCampusForm.fromCampusCode,
      toCampusCode: migrateCampusForm.toCampusCode,
    }),
  onSuccess: async (result) => {
    setSuccess(`校区迁移完成，影响商品 ${result.affected} 条`)
    migrateCampusForm.fromCampusCode = ''
    migrateCampusForm.toCampusCode = ''
    await queryClient.invalidateQueries({ queryKey: ['admin-products-v2'] })
  },
  onError: (error) => setError(error, '校区迁移失败'),
})

const migrateSubCategoryMutation = useMutation({
  mutationFn: () =>
    migrateAdminProductSubCategory({
      fromSubCategoryCode: migrateSubCategoryForm.fromSubCategoryCode,
      toSubCategoryCode: migrateSubCategoryForm.toSubCategoryCode,
    }),
  onSuccess: async (result) => {
    setSuccess(`子分类迁移完成，影响商品 ${result.affected} 条`)
    migrateSubCategoryForm.fromSubCategoryCode = ''
    migrateSubCategoryForm.toSubCategoryCode = ''
    await queryClient.invalidateQueries({ queryKey: ['admin-products-v2'] })
  },
  onError: (error) => setError(error, '子分类迁移失败'),
})
</script>

<template>
  <section class="space-y-6">
    <div>
      <h1 class="text-2xl font-semibold text-slate-900">商品元数据管理</h1>
      <p class="mt-1 text-sm text-slate-500">维护交易校区、一级分类和子分类，并支持停用后批量迁移商品。</p>
    </div>

    <div class="rounded-xl border border-slate-200 bg-white p-4">
      <div class="grid gap-3 md:grid-cols-3">
        <div class="rounded-lg border border-slate-200 p-3">
          <p class="text-sm font-medium text-slate-900">新增校区</p>
          <div class="mt-2 space-y-2">
            <input v-model="newCampus.campusCode" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="CAMPUS_CODE" />
            <input v-model="newCampus.campusName" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="校区名称" />
            <input v-model.number="newCampus.sortOrder" type="number" min="0" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="排序" />
            <button
              type="button"
              class="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="createCampusMutation.isPending.value"
              @click="createCampusMutation.mutate()"
            >
              新增校区
            </button>
          </div>
        </div>

        <div class="rounded-lg border border-slate-200 p-3">
          <p class="text-sm font-medium text-slate-900">新增一级分类</p>
          <div class="mt-2 space-y-2">
            <input v-model="newCategory.categoryCode" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="CATEGORY_CODE" />
            <input v-model="newCategory.categoryName" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="分类名称" />
            <input v-model.number="newCategory.sortOrder" type="number" min="0" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="排序" />
            <button
              type="button"
              class="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="createCategoryMutation.isPending.value"
              @click="createCategoryMutation.mutate()"
            >
              新增一级分类
            </button>
          </div>
        </div>

        <div class="rounded-lg border border-slate-200 p-3">
          <p class="text-sm font-medium text-slate-900">新增子分类</p>
          <div class="mt-2 space-y-2">
            <select v-model="newSubCategory.categoryCode" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">选择一级分类</option>
              <option v-for="item in categories" :key="item.categoryCode" :value="item.categoryCode">{{ item.categoryName }}</option>
            </select>
            <input v-model="newSubCategory.subCategoryCode" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="SUB_CATEGORY_CODE" />
            <input v-model="newSubCategory.subCategoryName" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="子分类名称" />
            <input v-model.number="newSubCategory.sortOrder" type="number" min="0" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" placeholder="排序" />
            <button
              type="button"
              class="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="createSubCategoryMutation.isPending.value"
              @click="createSubCategoryMutation.mutate()"
            >
              新增子分类
            </button>
          </div>
        </div>
      </div>
      <p v-if="actionMessage" class="mt-3 text-sm text-emerald-700">{{ actionMessage }}</p>
      <p v-if="actionError" class="mt-2 text-sm text-rose-600">{{ actionError }}</p>
    </div>

    <div class="grid gap-4 lg:grid-cols-2">
      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-slate-900">校区列表</h2>
          <span class="text-xs text-slate-500">共 {{ campuses.length }} 项</span>
        </div>
        <div v-if="campusesQuery.isLoading.value" class="text-sm text-slate-500">加载中...</div>
        <div v-else class="space-y-2">
          <article v-for="item in campuses" :key="item.campusCode" class="rounded-lg border border-slate-200 p-3">
            <div class="grid gap-2 sm:grid-cols-[1fr_1fr_90px_90px_100px] sm:items-center">
              <div>
                <p class="text-xs text-slate-500">编码</p>
                <p class="text-sm font-medium text-slate-900">{{ item.campusCode }}</p>
              </div>
              <input
                v-model="campusDraftOf(item).campusName"
                class="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                placeholder="校区名称"
              />
              <select v-model.number="campusDraftOf(item).status" class="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
              <input
                v-model.number="campusDraftOf(item).sortOrder"
                type="number"
                min="0"
                class="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              />
              <button
                type="button"
                class="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="updateCampusMutation.isPending.value"
                @click="updateCampusMutation.mutate({ campusCode: item.campusCode, draft: campusDraftOf(item) })"
              >
                保存
              </button>
            </div>
          </article>
        </div>
      </section>

      <section class="rounded-xl border border-slate-200 bg-white p-4">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-slate-900">分类结构</h2>
          <span class="text-xs text-slate-500">共 {{ categories.length }} 个一级分类</span>
        </div>
        <div v-if="categoriesQuery.isLoading.value" class="text-sm text-slate-500">加载中...</div>
        <div v-else class="space-y-3">
          <article v-for="category in categories" :key="category.categoryCode" class="rounded-lg border border-slate-200 p-3">
            <div class="grid gap-2 sm:grid-cols-[1fr_1fr_90px_90px_100px] sm:items-center">
              <div>
                <p class="text-xs text-slate-500">一级编码</p>
                <p class="text-sm font-medium text-slate-900">{{ category.categoryCode }}</p>
              </div>
              <input
                v-model="categoryDraftOf(category).categoryName"
                class="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                placeholder="分类名称"
              />
              <select v-model.number="categoryDraftOf(category).status" class="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
              <input
                v-model.number="categoryDraftOf(category).sortOrder"
                type="number"
                min="0"
                class="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
              />
              <button
                type="button"
                class="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="updateCategoryMutation.isPending.value"
                @click="updateCategoryMutation.mutate({ categoryCode: category.categoryCode, draft: categoryDraftOf(category) })"
              >
                保存
              </button>
            </div>

            <div class="mt-3 space-y-2 rounded-md border border-slate-100 bg-slate-50 p-2">
              <p class="text-xs font-medium text-slate-600">子分类</p>
              <article
                v-for="sub in category.subCategories"
                :key="sub.subCategoryCode"
                class="grid gap-2 rounded-md border border-slate-200 bg-white p-2 sm:grid-cols-[1fr_1fr_90px_90px_100px] sm:items-center"
              >
                <div>
                  <p class="text-xs text-slate-500">子类编码</p>
                  <p class="text-sm font-medium text-slate-900">{{ sub.subCategoryCode }}</p>
                </div>
                <input
                  v-model="subCategoryDraftOf(sub).subCategoryName"
                  class="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                  placeholder="子分类名称"
                />
                <select v-model.number="subCategoryDraftOf(sub).status" class="rounded-md border border-slate-300 px-2 py-1.5 text-sm">
                  <option :value="1">启用</option>
                  <option :value="0">停用</option>
                </select>
                <input
                  v-model.number="subCategoryDraftOf(sub).sortOrder"
                  type="number"
                  min="0"
                  class="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                />
                <button
                  type="button"
                  class="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="updateSubCategoryMutation.isPending.value"
                  @click="updateSubCategoryMutation.mutate({ subCategoryCode: sub.subCategoryCode, draft: subCategoryDraftOf(sub) })"
                >
                  保存
                </button>
              </article>
              <p v-if="category.subCategories.length === 0" class="text-xs text-slate-500">该一级分类暂无子分类。</p>
            </div>
          </article>
        </div>
      </section>
    </div>

    <section class="rounded-xl border border-slate-200 bg-white p-4">
      <h2 class="text-lg font-semibold text-slate-900">批量迁移</h2>
      <p class="mt-1 text-xs text-slate-500">用于停用后将历史商品迁移到新校区/新子分类。</p>
      <div class="mt-3 grid gap-3 lg:grid-cols-2">
        <div class="rounded-lg border border-slate-200 p-3">
          <p class="text-sm font-medium text-slate-900">校区迁移</p>
          <div class="mt-2 space-y-2">
            <select v-model="migrateCampusForm.fromCampusCode" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">来源校区</option>
              <option v-for="item in campuses" :key="`from-${item.campusCode}`" :value="item.campusCode">{{ item.campusName }}（{{ item.campusCode }}）</option>
            </select>
            <select v-model="migrateCampusForm.toCampusCode" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">目标校区</option>
              <option v-for="item in campuses" :key="`to-${item.campusCode}`" :value="item.campusCode">{{ item.campusName }}（{{ item.campusCode }}）</option>
            </select>
            <button
              type="button"
              class="w-full rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="migrateCampusMutation.isPending.value"
              @click="migrateCampusMutation.mutate()"
            >
              执行校区迁移
            </button>
          </div>
        </div>

        <div class="rounded-lg border border-slate-200 p-3">
          <p class="text-sm font-medium text-slate-900">子分类迁移</p>
          <div class="mt-2 space-y-2">
            <select v-model="migrateSubCategoryForm.fromSubCategoryCode" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">来源子分类</option>
              <option
                v-for="item in allSubCategories"
                :key="`from-sub-${item.subCategoryCode}`"
                :value="item.subCategoryCode"
              >
                {{ item.subCategoryName }}（{{ item.subCategoryCode }}）
              </option>
            </select>
            <select v-model="migrateSubCategoryForm.toSubCategoryCode" class="w-full rounded-md border border-slate-300 px-3 py-2 text-sm">
              <option value="">目标子分类</option>
              <option
                v-for="item in allSubCategories"
                :key="`to-sub-${item.subCategoryCode}`"
                :value="item.subCategoryCode"
              >
                {{ item.subCategoryName }}（{{ item.subCategoryCode }}）
              </option>
            </select>
            <button
              type="button"
              class="w-full rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="migrateSubCategoryMutation.isPending.value"
              @click="migrateSubCategoryMutation.mutate()"
            >
              执行子分类迁移
            </button>
          </div>
        </div>
      </div>
    </section>
  </section>
</template>
