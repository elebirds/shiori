import { httpGet, httpPost, httpPut } from '@/api/http'

export interface SkuResponse {
  skuId: number
  skuNo: string
  skuName: string
  specJson?: string
  priceCent: number
  stock: number
}

export type ProductStatus = 'DRAFT' | 'ON_SALE' | 'OFF_SHELF'
export type ProductCategoryCode = 'TEXTBOOK' | 'EXAM_MATERIAL' | 'NOTE' | 'OTHER'
export type ProductConditionLevel = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR'
export type ProductTradeMode = 'MEETUP' | 'DELIVERY' | 'BOTH'
export type ProductSortBy = 'CREATED_AT' | 'MIN_PRICE' | 'MAX_PRICE'
export type ProductSortDir = 'ASC' | 'DESC'

export interface ProductV2SummaryResponse {
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

export interface ProductV2PageResponse {
  total: number
  page: number
  size: number
  items: ProductV2SummaryResponse[]
}

export interface ProductV2DetailResponse {
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

export interface ProductV2Query {
  page?: number
  size?: number
  keyword?: string
  categoryCode?: ProductCategoryCode
  conditionLevel?: ProductConditionLevel
  tradeMode?: ProductTradeMode
  campusCode?: string
  sortBy?: ProductSortBy
  sortDir?: ProductSortDir
}

export interface MyProductV2Query extends ProductV2Query {
  status?: ProductStatus
}

export interface SkuInput {
  id?: number
  skuName: string
  specJson?: string
  priceCent: number
  stock: number
}

export interface ProductV2WriteRequest {
  title: string
  description?: string
  coverObjectKey?: string
  categoryCode: ProductCategoryCode
  conditionLevel: ProductConditionLevel
  tradeMode: ProductTradeMode
  campusCode: string
  skus: SkuInput[]
}

export interface ProductWriteResponse {
  productId: number
  productNo: string
  status: ProductStatus
}

export function listProductsV2(query: ProductV2Query): Promise<ProductV2PageResponse> {
  return httpGet<ProductV2PageResponse>('/api/v2/product/products', { params: query })
}

export function getProductDetailV2(productId: number): Promise<ProductV2DetailResponse> {
  return httpGet<ProductV2DetailResponse>(`/api/v2/product/products/${productId}`)
}

export function listMyProductsV2(query: MyProductV2Query): Promise<ProductV2PageResponse> {
  return httpGet<ProductV2PageResponse>('/api/v2/product/my/products', { params: query })
}

export function getMyProductDetailV2(productId: number): Promise<ProductV2DetailResponse> {
  return httpGet<ProductV2DetailResponse>(`/api/v2/product/my/products/${productId}`)
}

export function createProductV2(payload: ProductV2WriteRequest): Promise<ProductWriteResponse> {
  return httpPost<ProductWriteResponse>('/api/v2/product/products', payload)
}

export function updateProductV2(productId: number, payload: ProductV2WriteRequest): Promise<ProductWriteResponse> {
  return httpPut<ProductWriteResponse>(`/api/v2/product/products/${productId}`, payload)
}

export function publishProductV2(productId: number): Promise<ProductWriteResponse> {
  return httpPost<ProductWriteResponse>(`/api/v2/product/products/${productId}/publish`)
}

export function offShelfProductV2(productId: number): Promise<ProductWriteResponse> {
  return httpPost<ProductWriteResponse>(`/api/v2/product/products/${productId}/off-shelf`)
}

