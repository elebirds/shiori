import { httpGet, httpPost } from '@/api/http'

export type ProductStatus = 'DRAFT' | 'ON_SALE' | 'OFF_SHELF'
export type ProductCategoryCode = 'TEXTBOOK' | 'EXAM_MATERIAL' | 'NOTE' | 'OTHER'
export type ProductConditionLevel = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR'
export type ProductTradeMode = 'MEETUP' | 'DELIVERY' | 'BOTH'
export type ProductSortBy = 'CREATED_AT' | 'MIN_PRICE' | 'MAX_PRICE'
export type ProductSortDir = 'ASC' | 'DESC'

export interface SkuResponse {
  skuId: number
  skuNo: string
  skuName: string
  specJson?: string
  priceCent: number
  stock: number
}

export interface AdminProductSummaryV2 {
  productId: number
  productNo: string
  title: string
  description?: string
  coverObjectKey?: string
  coverImageUrl?: string
  status: ProductStatus
  categoryCode: ProductCategoryCode
  conditionLevel: ProductConditionLevel
  tradeMode: ProductTradeMode
  campusCode: string
  minPriceCent?: number
  maxPriceCent?: number
  totalStock?: number
}

export interface AdminProductPageV2 {
  total: number
  page: number
  size: number
  items: AdminProductSummaryV2[]
}

export interface AdminProductDetailV2 {
  productId: number
  productNo: string
  ownerUserId: number
  title: string
  description?: string
  coverObjectKey?: string
  coverImageUrl?: string
  status: ProductStatus
  categoryCode: ProductCategoryCode
  conditionLevel: ProductConditionLevel
  tradeMode: ProductTradeMode
  campusCode: string
  minPriceCent?: number
  maxPriceCent?: number
  totalStock?: number
  skus: SkuResponse[]
}

export interface ProductWriteResponse {
  productId: number
  productNo: string
  status: ProductStatus
}

export interface BatchOffShelfResponse {
  total: number
  successCount: number
  failedProductIds: number[]
  successItems: ProductWriteResponse[]
}

export interface AdminProductQueryV2 {
  page?: number
  size?: number
  keyword?: string
  status?: ProductStatus
  ownerUserId?: number
  categoryCode?: ProductCategoryCode
  conditionLevel?: ProductConditionLevel
  tradeMode?: ProductTradeMode
  campusCode?: string
  sortBy?: ProductSortBy
  sortDir?: ProductSortDir
}

export function listAdminProductsV2(params: AdminProductQueryV2): Promise<AdminProductPageV2> {
  return httpGet('/api/v2/admin/products', { params })
}

export function getAdminProductV2(productId: number): Promise<AdminProductDetailV2> {
  return httpGet(`/api/v2/admin/products/${productId}`)
}

export function forceOffShelfV2(productId: number, reason?: string): Promise<ProductWriteResponse> {
  return httpPost(`/api/v2/admin/products/${productId}/off-shelf`, { reason })
}

export function batchOffShelfV2(productIds: number[], reason?: string): Promise<BatchOffShelfResponse> {
  return httpPost('/api/v2/admin/products/batch-off-shelf', { productIds, reason })
}

