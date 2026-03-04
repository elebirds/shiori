import { httpGet, httpPost } from '@/api/http'

export type OrderStatus = 'UNPAID' | 'PAID' | 'CANCELED' | 'DELIVERING' | 'FINISHED'

export interface CreateOrderItem {
  productId: number
  skuId: number
  quantity: number
}

export interface CreateOrderRequest {
  items: CreateOrderItem[]
}

export interface CreateOrderResponse {
  orderNo: string
  status: OrderStatus
  totalAmountCent: number
  itemCount: number
  idempotent: boolean
}

export interface OrderOperateResponse {
  orderNo: string
  status: OrderStatus
  idempotent: boolean
}

export interface OrderItemResponse {
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

export interface OrderDetailResponse {
  orderNo: string
  buyerUserId: number
  sellerUserId: number
  status: OrderStatus
  totalAmountCent: number
  createdAt: string
  paidAt?: string
  timeoutAt?: string
  items: OrderItemResponse[]
}

export interface OrderSummaryResponse {
  orderNo: string
  status: OrderStatus
  totalAmountCent: number
  itemCount: number
  createdAt: string
  paidAt?: string
}

export interface OrderPageResponse {
  total: number
  page: number
  size: number
  items: OrderSummaryResponse[]
}

export interface PayOrderRequest {
  paymentNo: string
}

export interface CancelOrderRequest {
  reason?: string
}

export interface ConfirmReceiptRequest {
  reason?: string
}

export interface OrderTransitionRequest {
  reason?: string
}

export interface OrderQuery {
  page?: number
  size?: number
}

export interface SellerOrderQuery {
  page?: number
  size?: number
  orderNo?: string
  status?: OrderStatus
  createdFrom?: string
  createdTo?: string
}

export interface SellerOrderSummaryResponse {
  orderNo: string
  buyerUserId: number
  status: OrderStatus
  totalAmountCent: number
  itemCount: number
  createdAt: string
  paidAt?: string
  updatedAt?: string
}

export interface SellerOrderPageResponse {
  total: number
  page: number
  size: number
  items: SellerOrderSummaryResponse[]
}

export interface OrderTimelineItemResponse {
  source: string
  operatorUserId?: number
  fromStatus: OrderStatus
  toStatus: OrderStatus
  reason?: string
  createdAt: string
}

export interface OrderTimelineResponse {
  total: number
  page: number
  size: number
  items: OrderTimelineItemResponse[]
}

function buildOperateIdempotencyKey(prefix: string, orderNo: string): string {
  const randomPart = Math.random().toString(36).slice(2, 10)
  return `${prefix}-${orderNo}-${Date.now()}-${randomPart}`
}

export function createOrderV2(payload: CreateOrderRequest, idempotencyKey: string): Promise<CreateOrderResponse> {
  return httpPost<CreateOrderResponse>('/api/v2/order/orders', payload, {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  })
}

export function listMyOrdersV2(query: OrderQuery): Promise<OrderPageResponse> {
  return httpGet<OrderPageResponse>('/api/v2/order/orders', { params: query })
}

export function getOrderDetailV2(orderNo: string): Promise<OrderDetailResponse> {
  return httpGet<OrderDetailResponse>(`/api/v2/order/orders/${orderNo}`)
}

export function payOrderV2(orderNo: string, payload: PayOrderRequest, idempotencyKey?: string): Promise<OrderOperateResponse> {
  const key = idempotencyKey || buildOperateIdempotencyKey('pay', orderNo)
  return httpPost<OrderOperateResponse>(`/api/v2/order/orders/${orderNo}/pay`, payload, {
    headers: {
      'Idempotency-Key': key,
    },
  })
}

export function cancelOrderV2(orderNo: string, payload?: CancelOrderRequest, idempotencyKey?: string): Promise<OrderOperateResponse> {
  const key = idempotencyKey || buildOperateIdempotencyKey('cancel', orderNo)
  return httpPost<OrderOperateResponse>(`/api/v2/order/orders/${orderNo}/cancel`, payload, {
    headers: {
      'Idempotency-Key': key,
    },
  })
}

export function confirmReceiptV2(orderNo: string, payload?: ConfirmReceiptRequest): Promise<OrderOperateResponse> {
  return httpPost<OrderOperateResponse>(`/api/v2/order/orders/${orderNo}/confirm-receipt`, payload)
}

export function getOrderTimelineV2(orderNo: string, query?: OrderQuery): Promise<OrderTimelineResponse> {
  return httpGet<OrderTimelineResponse>(`/api/v2/order/orders/${orderNo}/timeline`, { params: query })
}

export function listSellerOrdersV2(query: SellerOrderQuery): Promise<SellerOrderPageResponse> {
  return httpGet<SellerOrderPageResponse>('/api/v2/order/seller/orders', { params: query })
}

export function getSellerOrderDetailV2(orderNo: string): Promise<OrderDetailResponse> {
  return httpGet<OrderDetailResponse>(`/api/v2/order/seller/orders/${orderNo}`)
}

export function deliverSellerOrderV2(orderNo: string, payload?: OrderTransitionRequest): Promise<OrderOperateResponse> {
  return httpPost<OrderOperateResponse>(`/api/v2/order/seller/orders/${orderNo}/deliver`, payload)
}

export function finishSellerOrderV2(orderNo: string, payload?: OrderTransitionRequest): Promise<OrderOperateResponse> {
  return httpPost<OrderOperateResponse>(`/api/v2/order/seller/orders/${orderNo}/finish`, payload)
}

