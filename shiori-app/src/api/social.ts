import { httpDelete, httpGet, httpPost } from '@/api/http'

export type PostSourceType = 'MANUAL' | 'AUTO_PRODUCT'

export interface CreatePostV2Request {
  contentHtml: string
}

export interface PostRelatedProductResponse {
  productId: number
  productNo: string
  title?: string
  coverObjectKey?: string
  coverImageUrl?: string
  minPriceCent?: number
  maxPriceCent?: number
  campusCode?: string
}

export interface PostV2ItemResponse {
  postId: number
  postNo: string
  authorUserId: number
  sourceType: PostSourceType | string
  contentHtml?: string
  relatedProduct?: PostRelatedProductResponse
  createdAt: string
}

export interface PostV2PageResponse {
  total: number
  page: number
  size: number
  items: PostV2ItemResponse[]
}

export function createPostV2(payload: CreatePostV2Request): Promise<PostV2ItemResponse> {
  return httpPost<PostV2ItemResponse>('/api/v2/social/posts', payload)
}

export function deletePostV2(postId: number): Promise<{ success: boolean }> {
  return httpDelete<{ success: boolean }>(`/api/v2/social/posts/${postId}`)
}

export function listUserPostsV2(
  authorUserId: number,
  params?: { page?: number; size?: number },
): Promise<PostV2PageResponse> {
  return httpGet<PostV2PageResponse>(`/api/v2/social/users/${authorUserId}/posts`, { params })
}

export function listSquareFeedPostsV2(params?: { page?: number; size?: number }): Promise<PostV2PageResponse> {
  return httpGet<PostV2PageResponse>('/api/v2/social/square/feed', {
    params: {
      page: params?.page,
      size: params?.size,
    },
  })
}
