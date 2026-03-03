import { httpGet, httpPost } from '@/api/http'

export interface SkuResponse {
  skuId: number
  skuNo: string
  skuName: string
  specJson?: string
  priceCent: number
  stock: number
}

export interface ProductSummary {
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
  items: ProductSummary[]
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

export interface ProductWriteResponse {
  productId: number
  productNo: string
  status: string
}

export function listAdminProducts(params: {
  page?: number
  size?: number
  keyword?: string
  status?: string
  ownerUserId?: number
}): Promise<ProductPageResponse> {
  return httpGet('/api/admin/products', { params })
}

export function getAdminProduct(productId: number): Promise<ProductDetailResponse> {
  return httpGet(`/api/admin/products/${productId}`)
}

export function forceOffShelf(productId: number, reason?: string): Promise<ProductWriteResponse> {
  return httpPost(`/api/admin/products/${productId}/off-shelf`, { reason })
}
