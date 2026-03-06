import { httpDelete, httpGet, httpPost, httpPut } from '@/api/http'

export type OrderStatus = 'UNPAID' | 'PAID' | 'CANCELED' | 'DELIVERING' | 'FINISHED'
export type OrderRefundStatus = 'REQUESTED' | 'REJECTED' | 'PENDING_FUNDS' | 'SUCCEEDED'

export interface CreateOrderItem {
  productId: number
  skuId: number
  quantity: number
}

export interface CreateOrderRequest {
  items: CreateOrderItem[]
  source?: string
  conversationId?: number
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
  source?: string
  conversationId?: number
  listingId?: number
  createdAt: string
  paidAt?: string
  finishedAt?: string
  timeoutAt?: string
  refundStatus?: OrderRefundStatus
  refundNo?: string
  refundAmountCent?: number
  refundUpdatedAt?: string
  items: OrderItemResponse[]
  myReviewSubmitted: boolean
  counterpartyReviewSubmitted: boolean
  canCreateReview: boolean
  canEditReview: boolean
  reviewDeadlineAt?: string
}

export interface OrderSummaryResponse {
  orderNo: string
  status: OrderStatus
  totalAmountCent: number
  itemCount: number
  source?: string
  conversationId?: number
  listingId?: number
  createdAt: string
  paidAt?: string
  refundStatus?: OrderRefundStatus
  refundNo?: string
  refundAmountCent?: number
  refundUpdatedAt?: string
}

export interface OrderPageResponse {
  total: number
  page: number
  size: number
  items: OrderSummaryResponse[]
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

export interface ChatToOrderClickRequest {
  source: 'CHAT'
  conversationId: number
  listingId: number
}

export interface SellerOrderSummaryResponse {
  orderNo: string
  buyerUserId: number
  status: OrderStatus
  totalAmountCent: number
  itemCount: number
  source?: string
  conversationId?: number
  listingId?: number
  createdAt: string
  paidAt?: string
  updatedAt?: string
  refundStatus?: OrderRefundStatus
  refundNo?: string
  refundAmountCent?: number
  refundUpdatedAt?: string
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

export interface OrderReviewUpsertRequest {
  communicationStar: number
  timelinessStar: number
  credibilityStar: number
  comment?: string
}

export interface OrderReviewItemResponse {
  reviewId: number
  orderNo: string
  reviewerUserId: number
  reviewedUserId: number
  reviewerRole: 'BUYER' | 'SELLER'
  communicationStar: number
  timelinessStar: number
  credibilityStar: number
  overallStar: number
  comment?: string
  visibilityStatus: 'VISIBLE' | 'HIDDEN_BY_ADMIN'
  visibilityReason?: string
  visibilityOperatorUserId?: number
  visibilityUpdatedAt?: string
  editCount: number
  lastEditedAt?: string
  createdAt: string
  updatedAt: string
}

export interface OrderReviewContextResponse {
  orderNo: string
  myReviewSubmitted: boolean
  counterpartyReviewSubmitted: boolean
  canCreateReview: boolean
  canEditReview: boolean
  reviewDeadlineAt?: string
  myReview?: OrderReviewItemResponse
  counterpartyReview?: OrderReviewItemResponse
}

export interface UserCreditRoleProfileResponse {
  role: 'buyer' | 'seller'
  reviewCount: number
  avgStar: number
  positiveRate: number
}

export interface UserCreditCompositeResponse {
  totalReviewCount: number
  compositeAvgStar: number
  compositeScore100: number
  creditGrade: 'NEW' | 'S' | 'A' | 'B' | 'C' | 'D'
}

export interface UserCreditProfileResponse {
  userId: number
  buyerProfile: UserCreditRoleProfileResponse
  sellerProfile: UserCreditRoleProfileResponse
  composite: UserCreditCompositeResponse
}

export interface PraiseWallItemResponse {
  reviewId: number
  orderNo: string
  reviewerUserId: number
  reviewerRole: 'BUYER' | 'SELLER'
  communicationStar: number
  timelinessStar: number
  credibilityStar: number
  overallStar: number
  comment?: string
  createdAt: string
}

export interface PraiseWallPageResponse {
  total: number
  page: number
  size: number
  items: PraiseWallItemResponse[]
}

export interface UserReviewItemResponse {
  reviewId: number
  orderNo: string
  reviewerUserId: number
  reviewerRole: 'BUYER' | 'SELLER'
  communicationStar: number
  timelinessStar: number
  credibilityStar: number
  overallStar: number
  comment?: string
  createdAt: string
}

export interface UserReviewPageResponse {
  total: number
  page: number
  size: number
  items: UserReviewItemResponse[]
}

export interface CreateOrderRefundRequest {
  reason?: string
}

export interface ReviewOrderRefundRequest {
  reason?: string
}

export interface OrderRefundResponse {
  refundNo: string
  orderNo: string
  status: OrderRefundStatus
  amountCent: number
  applyReason?: string
  rejectReason?: string
  reviewedByUserId?: number
  reviewDeadlineAt?: string
  reviewedAt?: string
  autoApproved: boolean
  paymentNo?: string
  lastError?: string
  retryCount?: number
  createdAt: string
  updatedAt: string
  idempotent: boolean
}

export interface OrderRefundPageResponse {
  total: number
  page: number
  size: number
  items: OrderRefundResponse[]
}

export interface SellerRefundQuery {
  status?: OrderRefundStatus
  page?: number
  size?: number
}

export interface CartSpecItemResponse {
  name: string
  value: string
}

export interface CartItemResponse {
  itemId: number
  productId: number
  productNo?: string
  productTitle?: string
  coverImageUrl?: string
  skuId: number
  skuNo?: string
  displayName: string
  specItems: CartSpecItemResponse[]
  priceCent?: number
  stock?: number
  quantity: number
  subtotalCent: number
}

export interface CartResponse {
  cartId?: number
  sellerUserId?: number
  totalItemCount: number
  totalAmountCent: number
  items: CartItemResponse[]
}

export interface CartAddItemRequest {
  productId: number
  skuId: number
  quantity: number
}

export interface CartUpdateItemRequest {
  quantity: number
}

export interface CartCheckoutRequest {
  itemIds?: number[]
  source?: string
  conversationId?: number
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

export function recordChatToOrderClickV2(payload: ChatToOrderClickRequest): Promise<void> {
  return httpPost<void>('/api/v2/order/orders/chat-to-order-click', payload)
}

export function getOrderDetailV2(orderNo: string): Promise<OrderDetailResponse> {
  return httpGet<OrderDetailResponse>(`/api/v2/order/orders/${orderNo}`)
}

export function applyOrderRefundV2(orderNo: string, payload?: CreateOrderRefundRequest): Promise<OrderRefundResponse> {
  return httpPost<OrderRefundResponse>(`/api/v2/order/orders/${orderNo}/refunds`, payload)
}

export function getLatestOrderRefundV2(orderNo: string): Promise<OrderRefundResponse> {
  return httpGet<OrderRefundResponse>(`/api/v2/order/orders/${orderNo}/refunds/latest`)
}

export function payOrderV2(orderNo: string, idempotencyKey?: string): Promise<OrderOperateResponse> {
  const key = idempotencyKey || buildOperateIdempotencyKey('pay', orderNo)
  return httpPost<OrderOperateResponse>(`/api/v2/order/orders/${orderNo}/pay`, undefined, {
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

export function createOrderReviewV2(orderNo: string, payload: OrderReviewUpsertRequest): Promise<OrderReviewItemResponse> {
  return httpPost<OrderReviewItemResponse>(`/api/v2/order/orders/${orderNo}/reviews`, payload)
}

export function updateMyOrderReviewV2(orderNo: string, payload: OrderReviewUpsertRequest): Promise<OrderReviewItemResponse> {
  return httpPut<OrderReviewItemResponse>(`/api/v2/order/orders/${orderNo}/reviews/me`, payload)
}

export function getOrderReviewContextV2(orderNo: string): Promise<OrderReviewContextResponse> {
  return httpGet<OrderReviewContextResponse>(`/api/v2/order/orders/${orderNo}/reviews`)
}

export function getUserCreditProfileV2(userId: number): Promise<UserCreditProfileResponse> {
  return httpGet<UserCreditProfileResponse>(`/api/v2/order/reviews/users/${userId}/credit-profile`)
}

export function listUserPraiseWallV2(
  userId: number,
  query?: { page?: number; size?: number },
): Promise<PraiseWallPageResponse> {
  return httpGet<PraiseWallPageResponse>(`/api/v2/order/reviews/users/${userId}/praise-wall`, { params: query })
}

export function listUserReviewsV2(
  userId: number,
  query?: { page?: number; size?: number },
): Promise<UserReviewPageResponse> {
  return httpGet<UserReviewPageResponse>(`/api/v2/order/reviews/users/${userId}/reviews`, { params: query })
}

export function listSellerOrdersV2(query: SellerOrderQuery): Promise<SellerOrderPageResponse> {
  return httpGet<SellerOrderPageResponse>('/api/v2/order/seller/orders', { params: query })
}

export function listSellerRefundsV2(query: SellerRefundQuery): Promise<OrderRefundPageResponse> {
  return httpGet<OrderRefundPageResponse>('/api/v2/order/seller/refunds', { params: query })
}

export function approveSellerRefundV2(refundNo: string, payload?: ReviewOrderRefundRequest): Promise<OrderRefundResponse> {
  return httpPost<OrderRefundResponse>(`/api/v2/order/seller/refunds/${refundNo}/approve`, payload)
}

export function rejectSellerRefundV2(refundNo: string, payload?: ReviewOrderRefundRequest): Promise<OrderRefundResponse> {
  return httpPost<OrderRefundResponse>(`/api/v2/order/seller/refunds/${refundNo}/reject`, payload)
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

export function getCartV2(): Promise<CartResponse> {
  return httpGet<CartResponse>('/api/v2/order/cart')
}

export function addCartItemV2(payload: CartAddItemRequest): Promise<CartResponse> {
  return httpPost<CartResponse>('/api/v2/order/cart/items', payload)
}

export function updateCartItemV2(itemId: number, payload: CartUpdateItemRequest): Promise<CartResponse> {
  return httpPut<CartResponse>(`/api/v2/order/cart/items/${itemId}`, payload)
}

export function removeCartItemV2(itemId: number): Promise<CartResponse> {
  return httpDelete<CartResponse>(`/api/v2/order/cart/items/${itemId}`)
}

export function checkoutCartV2(payload: CartCheckoutRequest, idempotencyKey?: string): Promise<CreateOrderResponse> {
  return httpPost<CreateOrderResponse>('/api/v2/order/cart/checkout', payload, {
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  })
}
