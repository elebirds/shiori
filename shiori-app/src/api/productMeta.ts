import { httpGet } from '@/api/http'

export interface ProductMetaCampusResponse {
  campusCode: string
  campusName: string
}

export interface ProductMetaSubCategoryResponse {
  subCategoryCode: string
  subCategoryName: string
}

export interface ProductMetaCategoryResponse {
  categoryCode: string
  categoryName: string
  subCategories: ProductMetaSubCategoryResponse[]
}

export function listProductMetaCampuses(): Promise<ProductMetaCampusResponse[]> {
  return httpGet<ProductMetaCampusResponse[]>('/api/v2/product/meta/campuses')
}

export function listProductMetaCategories(): Promise<ProductMetaCategoryResponse[]> {
  return httpGet<ProductMetaCategoryResponse[]>('/api/v2/product/meta/categories')
}
