import { httpGet, httpPost, httpPut } from '@/api/http'

export interface SkuResponse {
  skuId: number
  skuNo: string
  skuName: string
  specJson?: string
  priceCent: number
  stock: number
}

export interface ProductSummaryResponse {
  productId: number
  productNo: string
  title: string
  description?: string
  coverObjectKey?: string
  coverImageUrl?: string
  status: string
}

export interface ProductPageResponse {
  total: number
  page: number
  size: number
  items: ProductSummaryResponse[]
}

export interface ProductDetailResponse {
  productId: number
  productNo: string
  ownerUserId: number
  title: string
  description?: string
  coverObjectKey?: string
  coverImageUrl?: string
  status: string
  skus: SkuResponse[]
}

export interface ProductQuery {
  page?: number
  size?: number
  keyword?: string
}

export type ProductStatus = 'DRAFT' | 'ON_SALE' | 'OFF_SHELF'

export interface MyProductQuery extends ProductQuery {
  status?: ProductStatus
}

export interface SkuInput {
  id?: number
  skuName: string
  specJson?: string
  priceCent: number
  stock: number
}

export interface ProductWriteRequest {
  title: string
  description?: string
  coverObjectKey?: string
  skus: SkuInput[]
}

export interface ProductWriteResponse {
  productId: number
  productNo: string
  status: string
}

export function listProducts(query: ProductQuery): Promise<ProductPageResponse> {
  return httpGet<ProductPageResponse>('/api/product/products', { params: query })
}

export function getProductDetail(productId: number): Promise<ProductDetailResponse> {
  return httpGet<ProductDetailResponse>(`/api/product/products/${productId}`)
}

export function listMyProducts(query: MyProductQuery): Promise<ProductPageResponse> {
  return httpGet<ProductPageResponse>('/api/product/my/products', { params: query })
}

export function getMyProductDetail(productId: number): Promise<ProductDetailResponse> {
  return httpGet<ProductDetailResponse>(`/api/product/my/products/${productId}`)
}

export function createProduct(payload: ProductWriteRequest): Promise<ProductWriteResponse> {
  return httpPost<ProductWriteResponse>('/api/product/products', payload)
}

export function updateProduct(productId: number, payload: ProductWriteRequest): Promise<ProductWriteResponse> {
  return httpPut<ProductWriteResponse>(`/api/product/products/${productId}`, payload)
}

export function publishProduct(productId: number): Promise<ProductWriteResponse> {
  return httpPost<ProductWriteResponse>(`/api/product/products/${productId}/publish`)
}

export function offShelfProduct(productId: number): Promise<ProductWriteResponse> {
  return httpPost<ProductWriteResponse>(`/api/product/products/${productId}/off-shelf`)
}
