import { httpGet, httpPost } from '@/api/http'

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
  status: string
  totalAmountCent: number
  itemCount: number
  idempotent: boolean
}

export interface OrderOperateResponse {
  orderNo: string
  status: string
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
  status: string
  totalAmountCent: number
  createdAt: string
  paidAt?: string
  timeoutAt?: string
  items: OrderItemResponse[]
}

export interface OrderSummaryResponse {
  orderNo: string
  status: string
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

export interface OrderQuery {
  page?: number
  size?: number
}

function buildOperateIdempotencyKey(prefix: string, orderNo: string): string {
  const randomPart = Math.random().toString(36).slice(2, 10)
  return `${prefix}-${orderNo}-${Date.now()}-${randomPart}`
}

export function createOrder(payload: CreateOrderRequest, idempotencyKey: string): Promise<CreateOrderResponse> {
  return httpPost<CreateOrderResponse>('/api/order/orders', payload, {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  })
}

export function listMyOrders(query: OrderQuery): Promise<OrderPageResponse> {
  return httpGet<OrderPageResponse>('/api/order/orders', { params: query })
}

export function getOrderDetail(orderNo: string): Promise<OrderDetailResponse> {
  return httpGet<OrderDetailResponse>(`/api/order/orders/${orderNo}`)
}

export function payOrder(orderNo: string, payload: PayOrderRequest, idempotencyKey?: string): Promise<OrderOperateResponse> {
  const key = idempotencyKey || buildOperateIdempotencyKey('pay', orderNo)
  return httpPost<OrderOperateResponse>(`/api/order/orders/${orderNo}/pay`, payload, {
    headers: {
      'Idempotency-Key': key,
    },
  })
}

export function cancelOrder(orderNo: string, payload?: CancelOrderRequest, idempotencyKey?: string): Promise<OrderOperateResponse> {
  const key = idempotencyKey || buildOperateIdempotencyKey('cancel', orderNo)
  return httpPost<OrderOperateResponse>(`/api/order/orders/${orderNo}/cancel`, payload, {
    headers: {
      'Idempotency-Key': key,
    },
  })
}
