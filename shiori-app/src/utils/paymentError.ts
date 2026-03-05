import { ApiBizError } from '@/types/result'

const PAYMENT_ERROR_MESSAGE: Record<number, string> = {
  10004: '无权限访问该支付能力',
  50019: '余额不足，请先兑换 CDK 或等待入账',
  60000: '余额不足，请先兑换 CDK 或等待入账',
  60004: 'CDK 无效，请核对后重试',
  60005: 'CDK 已兑换，不能重复使用',
  60006: 'CDK 已过期，请更换后重试',
}

export function resolvePaymentErrorMessage(error: unknown, fallback = '支付失败，请稍后重试'): string {
  if (error instanceof ApiBizError) {
    return PAYMENT_ERROR_MESSAGE[error.code] || error.message || fallback
  }
  if (error instanceof Error) {
    return error.message || fallback
  }
  return fallback
}
