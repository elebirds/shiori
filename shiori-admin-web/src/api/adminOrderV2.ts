import { httpGet, httpPost } from '@/api/http'

export type OrderStatus = 'UNPAID' | 'PAID' | 'CANCELED' | 'DELIVERING' | 'FINISHED'

export interface OrderSummary {
  orderNo: string
  status: OrderStatus
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
  status: OrderStatus
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
  status: OrderStatus
  idempotent: boolean
}

export interface OrderStatusAuditItem {
  operatorUserId?: number
  source: string
  fromStatus: OrderStatus
  toStatus: OrderStatus
  reason?: string
  createdAt: string
}

export interface OrderStatusAuditPageResponse {
  total: number
  page: number
  size: number
  items: OrderStatusAuditItem[]
}

export interface AdminOrderQueryV2 {
  page?: number
  size?: number
  orderNo?: string
  status?: OrderStatus
  buyerUserId?: number
  sellerUserId?: number
}

export function listAdminOrdersV2(params: AdminOrderQueryV2): Promise<OrderPageResponse> {
  return httpGet('/api/v2/admin/orders', { params })
}

export function getAdminOrderV2(orderNo: string): Promise<OrderDetail> {
  return httpGet(`/api/v2/admin/orders/${orderNo}`)
}

export function cancelAdminOrderV2(orderNo: string, reason?: string): Promise<OrderOperateResponse> {
  return httpPost(`/api/v2/admin/orders/${orderNo}/cancel`, { reason })
}

export function deliverAdminOrderV2(orderNo: string, reason?: string): Promise<OrderOperateResponse> {
  return httpPost(`/api/v2/admin/orders/${orderNo}/deliver`, { reason })
}

export function finishAdminOrderV2(orderNo: string, reason?: string): Promise<OrderOperateResponse> {
  return httpPost(`/api/v2/admin/orders/${orderNo}/finish`, { reason })
}

export function listAdminOrderStatusAuditsV2(orderNo: string, page = 1, size = 20): Promise<OrderStatusAuditPageResponse> {
  return httpGet(`/api/v2/admin/orders/${orderNo}/status-audits`, {
    params: { page, size },
  })
}

