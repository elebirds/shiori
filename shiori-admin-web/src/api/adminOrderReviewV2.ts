import { httpGet, httpPost } from '@/api/http'

export interface AdminOrderReviewItem {
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

export interface AdminOrderReviewPageResponse {
  total: number
  page: number
  size: number
  items: AdminOrderReviewItem[]
}

export interface AdminOrderReviewQuery {
  page?: number
  size?: number
  reviewedUserId?: number
  reviewerUserId?: number
  reviewerRole?: 'BUYER' | 'SELLER'
  visibilityStatus?: 'VISIBLE' | 'HIDDEN_BY_ADMIN'
  minOverallStar?: number
  maxOverallStar?: number
  createdFrom?: string
  createdTo?: string
}

export function listAdminOrderReviewsV2(params: AdminOrderReviewQuery): Promise<AdminOrderReviewPageResponse> {
  return httpGet('/api/v2/admin/orders/reviews', { params })
}

export function updateAdminOrderReviewVisibilityV2(
  reviewId: number,
  payload: { visible: boolean; reason?: string },
): Promise<{ reviewId: number; visibilityStatus: 'VISIBLE' | 'HIDDEN_BY_ADMIN' }> {
  return httpPost(`/api/v2/admin/orders/reviews/${reviewId}/visibility`, payload)
}

