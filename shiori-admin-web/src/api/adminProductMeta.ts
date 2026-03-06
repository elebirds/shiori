import { httpGet, httpPost, httpPut } from '@/api/http'

export interface AdminProductMetaCampusResponse {
  id: number
  campusCode: string
  campusName: string
  status: number
  sortOrder: number
}

export interface AdminProductMetaSubCategoryResponse {
  id: number
  categoryCode: string
  subCategoryCode: string
  subCategoryName: string
  status: number
  sortOrder: number
}

export interface AdminProductMetaCategoryResponse {
  id: number
  categoryCode: string
  categoryName: string
  status: number
  sortOrder: number
  subCategories: AdminProductMetaSubCategoryResponse[]
}

export interface ProductMetaMigrationResponse {
  affected: number
}

export function listAdminProductMetaCampuses(): Promise<AdminProductMetaCampusResponse[]> {
  return httpGet('/api/v2/admin/product-meta/campuses')
}

export function listAdminProductMetaCategories(): Promise<AdminProductMetaCategoryResponse[]> {
  return httpGet('/api/v2/admin/product-meta/categories')
}

export function createAdminProductCampus(payload: {
  campusCode: string
  campusName: string
  sortOrder?: number
}): Promise<AdminProductMetaCampusResponse> {
  return httpPost('/api/v2/admin/product-meta/campuses', payload)
}

export function updateAdminProductCampus(
  campusCode: string,
  payload: { campusName: string; status?: number; sortOrder?: number },
): Promise<AdminProductMetaCampusResponse> {
  return httpPut(`/api/v2/admin/product-meta/campuses/${campusCode}`, payload)
}

export function createAdminProductCategory(payload: {
  categoryCode: string
  categoryName: string
  sortOrder?: number
}): Promise<AdminProductMetaCategoryResponse> {
  return httpPost('/api/v2/admin/product-meta/categories', payload)
}

export function updateAdminProductCategory(
  categoryCode: string,
  payload: { categoryName: string; status?: number; sortOrder?: number },
): Promise<AdminProductMetaCategoryResponse> {
  return httpPut(`/api/v2/admin/product-meta/categories/${categoryCode}`, payload)
}

export function createAdminProductSubCategory(payload: {
  categoryCode: string
  subCategoryCode: string
  subCategoryName: string
  sortOrder?: number
}): Promise<AdminProductMetaSubCategoryResponse> {
  return httpPost('/api/v2/admin/product-meta/sub-categories', payload)
}

export function updateAdminProductSubCategory(
  subCategoryCode: string,
  payload: { subCategoryName: string; status?: number; sortOrder?: number },
): Promise<AdminProductMetaSubCategoryResponse> {
  return httpPut(`/api/v2/admin/product-meta/sub-categories/${subCategoryCode}`, payload)
}

export function migrateAdminProductCampus(payload: {
  fromCampusCode: string
  toCampusCode: string
}): Promise<ProductMetaMigrationResponse> {
  return httpPost('/api/v2/admin/product-meta/migrations/campuses', payload)
}

export function migrateAdminProductSubCategory(payload: {
  fromSubCategoryCode: string
  toSubCategoryCode: string
}): Promise<ProductMetaMigrationResponse> {
  return httpPost('/api/v2/admin/product-meta/migrations/sub-categories', payload)
}
