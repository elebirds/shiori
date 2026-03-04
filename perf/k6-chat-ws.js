import ws from 'k6/ws'
import { check } from 'k6'
import { Counter } from 'k6/metrics'

const wsURL = __ENV.K6_CHAT_WS_URL || 'ws://localhost:8090/ws'
const accessToken = __ENV.K6_CHAT_ACCESS_TOKEN || ''
const chatTicket = __ENV.K6_CHAT_TICKET || ''
const fixedConversationID = Number(__ENV.K6_CHAT_CONVERSATION_ID || 0)
const iterations = Number(__ENV.K6_CHAT_ITERATIONS || 1)

const chatFailed = new Counter('shiori_perf_chat_ws_failed_total')

export const options = {
  vus: 1,
  iterations,
  thresholds: {
    shiori_perf_chat_ws_failed_total: ['count==0'],
  },
}

export default function () {
  if (!accessToken || !chatTicket) {
    chatFailed.add(1)
    return
  }

  const target = `${wsURL}?accessToken=${encodeURIComponent(accessToken)}`
  const result = ws.connect(target, null, (socket) => {
    let conversationID = fixedConversationID
    let joined = false
    let acked = false

    socket.on('open', () => {
      socket.send(JSON.stringify({
        type: 'join',
        chatTicket,
      }))
    })

    socket.on('message', (raw) => {
      let data = null
      try {
        data = JSON.parse(raw)
      } catch (_) {
        return
      }

      if (data.type === 'join_ack') {
        joined = true
        if (!conversationID) {
          conversationID = Number(data.conversationId || 0)
        }
        if (!conversationID) {
          chatFailed.add(1)
          socket.close()
          return
        }
        socket.send(JSON.stringify({
          type: 'send',
          conversationId: conversationID,
          clientMsgId: `k6-${Date.now()}`,
          content: 'hello from k6 chat skeleton',
        }))
        return
      }

      if (data.type === 'send_ack') {
        acked = true
        socket.close()
      }

      if (data.type === 'error') {
        chatFailed.add(1)
        socket.close()
      }
    })

    socket.setTimeout(() => {
      if (!joined || !acked) {
        chatFailed.add(1)
      }
      socket.close()
    }, 5000)
  })

  if (!check(result, { 'chat ws upgrade 101': (r) => r && r.status === 101 })) {
    chatFailed.add(1)
  }
}
