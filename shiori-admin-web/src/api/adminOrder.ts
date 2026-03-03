import { httpGet, httpPost } from '@/api/http'

export interface OrderSummary {
  orderNo: string
  status: string
  totalAmountCent: number
  itemCount: number
  createdAt: string
  paidAt?: string
}

export interface OrderItem {
  productId: number
  productNo: string
  skuId: number
  skuNo: string
  skuName: string
  specJson?: string
  priceCent: number
  quantity: number
  subtotalCent: number
}

export interface OrderDetail {
  orderNo: string
  buyerUserId: number
  sellerUserId: number
  status: string
  totalAmountCent: number
  createdAt: string
  paidAt?: string
  timeoutAt?: string
  items: OrderItem[]
}

export interface OrderPageResponse {
  total: number
  page: number
  size: number
  items: OrderSummary[]
}

export interface OrderOperateResponse {
  orderNo: string
  status: string
  idempotent: boolean
}

export function listAdminOrders(params: {
  page?: number
  size?: number
  orderNo?: string
  status?: string
  buyerUserId?: number
  sellerUserId?: number
}): Promise<OrderPageResponse> {
  return httpGet('/api/admin/orders', { params })
}

export function getAdminOrder(orderNo: string): Promise<OrderDetail> {
  return httpGet(`/api/admin/orders/${orderNo}`)
}

export function cancelAdminOrder(orderNo: string, reason?: string): Promise<OrderOperateResponse> {
  return httpPost(`/api/admin/orders/${orderNo}/cancel`, { reason })
}
