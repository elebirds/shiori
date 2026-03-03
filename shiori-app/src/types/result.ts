export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface ValidationErrorItem {
  field: string
  message: string
  rejectedValue: unknown
}

export interface ValidationErrorPayload {
  errors: ValidationErrorItem[]
}

export class ApiBizError extends Error {
  code: number
  status: number
  data: unknown

  constructor(code: number, message: string, status = 500, data: unknown = null) {
    super(message)
    this.name = 'ApiBizError'
    this.code = code
    this.status = status
    this.data = data
  }
}

export function isResultPayload(value: unknown): value is Result<unknown> {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Partial<Result<unknown>>
  return (
    typeof candidate.code === 'number' &&
    typeof candidate.message === 'string' &&
    Object.prototype.hasOwnProperty.call(candidate, 'timestamp')
  )
}

export function unwrapResult<T>(payload: unknown, status = 200): T {
  if (!isResultPayload(payload)) {
    throw new ApiBizError(-1, '响应格式不合法', status, payload)
  }

  if (payload.code !== 0) {
    throw new ApiBizError(payload.code, payload.message || '请求失败', status, payload.data)
  }

  return payload.data as T
}

export function isValidationErrorPayload(value: unknown): value is ValidationErrorPayload {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Partial<ValidationErrorPayload>
  if (!Array.isArray(candidate.errors)) {
    return false
  }

  return candidate.errors.every((item: unknown) => {
    if (typeof item !== 'object' || item === null) {
      return false
    }
    const errorItem = item as Partial<ValidationErrorItem>
    return typeof errorItem.field === 'string' && typeof errorItem.message === 'string'
  })
}

export function extractValidationMessage(value: unknown): string | null {
  if (!isValidationErrorPayload(value) || value.errors.length === 0) {
    return null
  }
  return value.errors[0]?.message || null
}
