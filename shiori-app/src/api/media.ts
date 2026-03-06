import { httpPost } from '@/api/http'

export interface PresignUploadRequest {
  fileName: string
  contentType?: string
}

export interface PresignUploadResponse {
  objectKey: string
  uploadUrl: string
  expireAt: number
  requiredHeaders: Record<string, string>
}

export interface ResolveMediaUrlsResponse {
  urls: Record<string, string>
}

export function presignProductUpload(payload: PresignUploadRequest): Promise<PresignUploadResponse> {
  return httpPost<PresignUploadResponse>('/api/v2/product/media/presign-upload', payload)
}

export function resolveProductMediaUrls(objectKeys: string[]): Promise<ResolveMediaUrlsResponse> {
  const normalized = Array.from(new Set(objectKeys.map((item) => item.trim()).filter((item) => item)))
  return httpPost<ResolveMediaUrlsResponse>('/api/v2/product/media/resolve-urls', {
    objectKeys: normalized,
  })
}

export async function uploadByPresignedUrl(
  uploadUrl: string,
  file: File,
  requiredHeaders: Record<string, string> = {},
): Promise<void> {
  const headers = new Headers()

  Object.entries(requiredHeaders).forEach(([key, value]) => {
    if (value) {
      headers.set(key, value)
    }
  })

  if (!headers.has('Content-Type') && file.type) {
    headers.set('Content-Type', file.type)
  }

  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers,
    body: file,
  })

  if (!response.ok) {
    throw new Error(`对象上传失败（${response.status}）`)
  }
}
