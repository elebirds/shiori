import type { NotifyMessage } from '@/stores/notify'

export type NotifyCategory = 'order' | 'system' | 'other'

export interface NotifyDescriptor {
  category: NotifyCategory
  title: string
  summary: string
  aggregateLabel: string
}

const ORDER_EVENT_PREFIX = 'Order'
const SYSTEM_EVENT_PREFIX = 'User'

export function resolveNotifyCategory(type: string): NotifyCategory {
  if (!type) {
    return 'other'
  }
  if (type.startsWith(ORDER_EVENT_PREFIX)) {
    return 'order'
  }
  if (type.startsWith(SYSTEM_EVENT_PREFIX)) {
    return 'system'
  }
  return 'other'
}

export function describeNotifyMessage(message: NotifyMessage): NotifyDescriptor {
  const orderNo = pickString(message.payload, ['orderNo']) || message.aggregateId || '-'
  const reason = pickString(message.payload, ['reason'])
  const role = normalizeRole(pickString(message.payload, ['role', 'operatorType']))
  const amountCent = pickNumber(message.payload, ['totalAmountCent'])
  const itemCount = pickNumber(message.payload, ['itemCount'])
  const granted = pickBoolean(message.payload, ['granted'])
  const roleCode = pickString(message.payload, ['roleCode'])
  const beforeStatus = pickString(message.payload, ['beforeStatus'])
  const afterStatus = pickString(message.payload, ['afterStatus'])
  const mustChangePassword = pickBoolean(message.payload, ['mustChangePassword'])
  const targetUserId = pickNumber(message.payload, ['targetUserId', 'userId']) ?? Number(message.aggregateId || 0)
  const userLabel = Number.isFinite(targetUserId) && targetUserId > 0 ? `用户 ${targetUserId}` : '目标用户'

  switch (message.type) {
    case 'OrderCreated':
      return {
        category: 'order',
        title: '订单已创建',
        summary: `订单 ${orderNo} 已创建${itemCount ? `，共 ${itemCount} 件商品` : ''}。`,
        aggregateLabel: `订单号 ${orderNo}`,
      }
    case 'OrderPaid':
      return {
        category: 'order',
        title: '订单已支付',
        summary: `${role || '订单'}支付已完成${amountCent !== null ? `，金额 ${formatAmount(amountCent)}` : ''}。`,
        aggregateLabel: `订单号 ${orderNo}`,
      }
    case 'OrderCanceled':
      return {
        category: 'order',
        title: '订单已取消',
        summary: reason ? `取消原因：${reason}` : `订单 ${orderNo} 已取消。`,
        aggregateLabel: `订单号 ${orderNo}`,
      }
    case 'OrderDelivered':
      return {
        category: 'order',
        title: '订单已发货',
        summary: role ? `${role}已更新发货状态。` : '卖家已更新发货状态。',
        aggregateLabel: `订单号 ${orderNo}`,
      }
    case 'OrderFinished':
      return {
        category: 'order',
        title: '订单已完成',
        summary: role ? `${role}已确认收货，交易完成。` : '交易已完成。',
        aggregateLabel: `订单号 ${orderNo}`,
      }
    case 'UserStatusChanged':
      return {
        category: 'system',
        title: '账号状态变更',
        summary: beforeStatus && afterStatus ? `状态由 ${beforeStatus} 变更为 ${afterStatus}。` : '账号状态已更新。',
        aggregateLabel: userLabel,
      }
    case 'UserRoleChanged':
      return {
        category: 'system',
        title: '权限角色变更',
        summary: roleCode
          ? `${granted ? '已授予' : '已移除'}角色 ${roleCode}。`
          : `用户角色已${granted ? '授予' : '移除'}。`,
        aggregateLabel: userLabel,
      }
    case 'UserPasswordReset':
      return {
        category: 'system',
        title: '密码已重置',
        summary: mustChangePassword === true ? '请尽快登录并修改密码。' : '管理员已重置你的密码。',
        aggregateLabel: userLabel,
      }
    case 'UserPermissionOverrideChanged':
      return {
        category: 'system',
        title: '权限覆盖规则变更',
        summary: '你的权限覆盖规则已更新，请重新登录以刷新权限。',
        aggregateLabel: userLabel,
      }
    case 'UserRoleBindingsChanged':
      return {
        category: 'system',
        title: '角色绑定变更',
        summary: '你的角色绑定关系已更新，请重新登录以刷新权限。',
        aggregateLabel: userLabel,
      }
    default:
      return {
        category: resolveNotifyCategory(message.type),
        title: message.type || '通知更新',
        summary: '收到新的系统通知。',
        aggregateLabel: message.aggregateId ? `聚合 ${message.aggregateId}` : '系统消息',
      }
  }
}

export function formatNotifyTime(timestamp: string, receivedAt: number): string {
  const fromEvent = new Date(timestamp)
  if (!Number.isNaN(fromEvent.getTime())) {
    return fromEvent.toLocaleString('zh-CN', { hour12: false })
  }
  return new Date(receivedAt).toLocaleString('zh-CN', { hour12: false })
}

export function formatNotifyMinute(timestamp: string, receivedAt: number): string {
  const target = new Date(timestamp)
  if (Number.isNaN(target.getTime())) {
    const fallback = new Date(receivedAt)
    return formatMinute(fallback)
  }
  return formatMinute(target)
}

function formatMinute(date: Date): string {
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${date.getHours()}:${minute}`
}

function normalizeRole(value: string | null): string {
  if (!value) {
    return ''
  }
  const normalized = value.toUpperCase()
  if (normalized === 'BUYER') {
    return '买家'
  }
  if (normalized === 'SELLER') {
    return '卖家'
  }
  if (normalized === 'ADMIN') {
    return '管理员'
  }
  return value
}

function formatAmount(amountCent: number): string {
  return `¥ ${(amountCent / 100).toFixed(2)}`
}

function pickString(payload: Record<string, unknown>, keys: string[]): string | null {
  for (const key of keys) {
    const raw = payload[key]
    if (typeof raw === 'string' && raw.trim()) {
      return raw.trim()
    }
  }
  return null
}

function pickNumber(payload: Record<string, unknown>, keys: string[]): number | null {
  for (const key of keys) {
    const raw = payload[key]
    if (typeof raw === 'number' && Number.isFinite(raw)) {
      return raw
    }
    if (typeof raw === 'string' && raw.trim()) {
      const parsed = Number(raw)
      if (Number.isFinite(parsed)) {
        return parsed
      }
    }
  }
  return null
}

function pickBoolean(payload: Record<string, unknown>, keys: string[]): boolean | null {
  for (const key of keys) {
    const raw = payload[key]
    if (typeof raw === 'boolean') {
      return raw
    }
    if (typeof raw === 'string') {
      const normalized = raw.trim().toLowerCase()
      if (normalized === 'true') {
        return true
      }
      if (normalized === 'false') {
        return false
      }
    }
  }
  return null
}
