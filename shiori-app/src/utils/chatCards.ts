const TRADE_STATUS_CARD_PREFIX = '[SHIORI_TRADE_STATUS_CARD]'
const PRODUCT_CARD_PREFIX = '[SHIORI_PRODUCT_CARD]'

export type TradeStatusCardType = 'ORDER_CREATED' | 'ORDER_PAID' | 'ORDER_DELIVERED' | 'ORDER_FINISHED'

export interface TradeStatusCardPayload {
  kind: 'trade_status_card'
  status: TradeStatusCardType
  text: string
  orderNo?: string
}

export interface ProductCardPayload {
  kind: 'product_card'
  listingId: number
  title: string
  priceCent: number
  coverImageUrl?: string
}

const TRADE_STATUS_TEXT: Record<TradeStatusCardType, string> = {
  ORDER_CREATED: '我已下单，正在等待支付',
  ORDER_PAID: '我已支付，请尽快发货',
  ORDER_DELIVERED: '我已发货，请注意查收',
  ORDER_FINISHED: '我已确认收货，本次交易完成',
}

export function resolveTradeStatusText(status: TradeStatusCardType): string {
  return TRADE_STATUS_TEXT[status]
}

export function buildTradeStatusCardContent(status: TradeStatusCardType, orderNo?: string): string {
  const payload: TradeStatusCardPayload = {
    kind: 'trade_status_card',
    status,
    text: resolveTradeStatusText(status),
    orderNo: orderNo?.trim() || undefined,
  }
  return `${TRADE_STATUS_CARD_PREFIX}${JSON.stringify(payload)}`
}

export function buildProductCardContent(payload: {
  listingId: number
  title: string
  priceCent: number
  coverImageUrl?: string
}): string {
  const card: ProductCardPayload = {
    kind: 'product_card',
    listingId: payload.listingId,
    title: String(payload.title || '').trim(),
    priceCent: Math.max(0, Math.floor(Number(payload.priceCent) || 0)),
    coverImageUrl: String(payload.coverImageUrl || '').trim() || undefined,
  }
  return `${PRODUCT_CARD_PREFIX}${JSON.stringify(card)}`
}

export function parseTradeStatusCardContent(content: string): TradeStatusCardPayload | null {
  const raw = String(content || '').trim()
  if (!raw.startsWith(TRADE_STATUS_CARD_PREFIX)) {
    return null
  }
  const json = raw.slice(TRADE_STATUS_CARD_PREFIX.length)
  if (!json) {
    return null
  }
  try {
    const parsed = JSON.parse(json) as Partial<TradeStatusCardPayload>
    if (parsed?.kind !== 'trade_status_card') {
      return null
    }
    if (!parsed.status || !TRADE_STATUS_TEXT[parsed.status as TradeStatusCardType]) {
      return null
    }
    const text = typeof parsed.text === 'string' && parsed.text.trim() ? parsed.text.trim() : TRADE_STATUS_TEXT[parsed.status as TradeStatusCardType]
    return {
      kind: 'trade_status_card',
      status: parsed.status as TradeStatusCardType,
      text,
      orderNo: typeof parsed.orderNo === 'string' && parsed.orderNo.trim() ? parsed.orderNo.trim() : undefined,
    }
  } catch {
    return null
  }
}

export function parseProductCardContent(content: string): ProductCardPayload | null {
  const raw = String(content || '').trim()
  if (!raw.startsWith(PRODUCT_CARD_PREFIX)) {
    return null
  }
  const json = raw.slice(PRODUCT_CARD_PREFIX.length)
  if (!json) {
    return null
  }
  try {
    const parsed = JSON.parse(json) as Partial<ProductCardPayload>
    const listingId = Number(parsed.listingId || 0)
    const title = String(parsed.title || '').trim()
    const priceCent = Math.max(0, Math.floor(Number(parsed.priceCent) || 0))
    if (parsed.kind !== 'product_card' || listingId <= 0 || !title) {
      return null
    }
    return {
      kind: 'product_card',
      listingId,
      title,
      priceCent,
      coverImageUrl: typeof parsed.coverImageUrl === 'string' && parsed.coverImageUrl.trim() ? parsed.coverImageUrl.trim() : undefined,
    }
  } catch {
    return null
  }
}

export function formatMessagePreview(content: string): string {
  const productCard = parseProductCardContent(content)
  if (productCard) {
    return `[商品卡片] ${productCard.title}`
  }
  const card = parseTradeStatusCardContent(content)
  if (!card) {
    return content
  }
  return `[交易状态] ${card.text}`
}
