import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useNotifyStore } from '@/stores/notify'

vi.mock('@/api/notify', () => ({
  getNotifySummary: vi.fn(async () => ({ unreadCount: 0 })),
  listNotifyEvents: vi.fn(async () => ({ items: [], hasMore: false, nextEventId: '', afterEventId: '', limit: 100 })),
  markAllNotifyEventsRead: vi.fn(async () => ({ affected: 0 })),
  markNotifyEventRead: vi.fn(async () => ({ eventId: 'x', marked: true })),
}))

class MockWebSocket {
  static instances: MockWebSocket[] = []
  static OPEN = 1

  public readyState = 0
  public onopen: (() => void) | null = null
  public onmessage: ((evt: { data: string }) => void) | null = null
  public onerror: (() => void) | null = null
  public onclose: (() => void) | null = null
  public sent: string[] = []

  constructor(public url: string) {
    MockWebSocket.instances.push(this)
  }

  send(payload: string): void {
    this.sent.push(payload)
  }

  close(): void {
    this.onclose?.()
  }

  open(): void {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
  }

  message(payload: unknown): void {
    this.onmessage?.({ data: JSON.stringify(payload) })
  }
}

describe('notify store ws bus', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    MockWebSocket.instances = []
    vi.stubGlobal('WebSocket', MockWebSocket as unknown as typeof WebSocket)
  })

  it('should dispatch non-notify frame to listeners', () => {
    const store = useNotifyStore()
    const listener = vi.fn()
    store.registerFrameListener(listener)
    store.connect('token')
    const ws = MockWebSocket.instances[0]
    ws.open()

    ws.message({ type: 'chat_message', conversationId: 11, content: 'hi' })

    expect(listener).toHaveBeenCalledTimes(1)
    expect(store.messages).toHaveLength(0)
  })

  it('should keep notify envelope in notify messages and allow sendFrame', () => {
    const store = useNotifyStore()
    store.connect('token')
    const ws = MockWebSocket.instances[0]
    ws.open()

    ws.message({
      eventId: 'E1',
      type: 'OrderPaid',
      aggregateId: 'O001',
      createdAt: new Date().toISOString(),
      payload: {},
    })

    expect(store.messages).toHaveLength(1)
    expect(store.sendFrame({ type: 'read', conversationId: 11 })).toBe(true)
    expect(ws.sent).toHaveLength(1)
  })
})
