import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'

import {
  listProductMetaCampuses,
  listProductMetaCategories,
  type ProductMetaCampusResponse,
  type ProductMetaCategoryResponse,
  type ProductMetaSubCategoryResponse,
} from '@/api/productMeta'

export function useProductMeta() {
  const campusesQuery = useQuery({
    queryKey: ['product-meta-campuses'],
    queryFn: () => listProductMetaCampuses(),
    staleTime: 5 * 60 * 1000,
  })

  const categoriesQuery = useQuery({
    queryKey: ['product-meta-categories'],
    queryFn: () => listProductMetaCategories(),
    staleTime: 5 * 60 * 1000,
  })

  const campusOptions = computed<ProductMetaCampusResponse[]>(() => campusesQuery.data.value || [])
  const categoryOptions = computed<ProductMetaCategoryResponse[]>(() => categoriesQuery.data.value || [])

  const subCategoriesByCategory = computed<Map<string, ProductMetaSubCategoryResponse[]>>(() => {
    const map = new Map<string, ProductMetaSubCategoryResponse[]>()
    categoryOptions.value.forEach((category) => {
      map.set(category.categoryCode, category.subCategories || [])
    })
    return map
  })

  const categoryNameMap = computed<Map<string, string>>(() => {
    const map = new Map<string, string>()
    categoryOptions.value.forEach((category) => {
      map.set(category.categoryCode, category.categoryName)
    })
    return map
  })

  const subCategoryNameMap = computed<Map<string, string>>(() => {
    const map = new Map<string, string>()
    categoryOptions.value.forEach((category) => {
      ;(category.subCategories || []).forEach((subCategory) => {
        map.set(subCategory.subCategoryCode, subCategory.subCategoryName)
      })
    })
    return map
  })

  const campusNameMap = computed<Map<string, string>>(() => {
    const map = new Map<string, string>()
    campusOptions.value.forEach((campus) => {
      map.set(campus.campusCode, campus.campusName)
    })
    return map
  })

  return {
    campusesQuery,
    categoriesQuery,
    campusOptions,
    categoryOptions,
    subCategoriesByCategory,
    categoryNameMap,
    subCategoryNameMap,
    campusNameMap,
  }
}
