import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const gatewayBaseUrl = __ENV.PERF_GATEWAY_BASE_URL || 'http://localhost:8080';
const notifyWsBaseUrl = __ENV.PERF_NOTIFY_WS_BASE_URL || 'ws://localhost:8090/ws';
const perfPrefix = __ENV.PERF_PREFIX || 'perf';
const vus = Number(__ENV.K6_CHAT_VUS || 1);
const iterations = Number(__ENV.K6_CHAT_ITERATIONS || 5);
const wsTimeoutMs = Number(__ENV.K6_CHAT_TIMEOUT_MS || 8000);

const chatFailedTotal = new Counter('shiori_perf_chat_conversation_failed_total');
const chatReconnectTotal = new Counter('shiori_perf_chat_reconnect_total');
const chatCompensationHitTotal = new Counter('shiori_perf_chat_compensation_hit_total');
const chatSendAckLatency = new Trend('shiori_perf_chat_send_ack_latency_ms', true);

export const options = {
  scenarios: {
    chat_conversation: {
      executor: 'per-vu-iterations',
      vus,
      iterations,
      maxDuration: '10m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    shiori_perf_chat_conversation_failed_total: ['count==0'],
    shiori_perf_chat_send_ack_latency_ms: ['p(95)<2000'],
  },
};

function apiRequest(method, path, token, payload, extraHeaders = {}) {
  const headers = { 'Content-Type': 'application/json' };
  for (const key in extraHeaders) {
    if (Object.prototype.hasOwnProperty.call(extraHeaders, key)) {
      headers[key] = extraHeaders[key];
    }
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const body = payload === null || payload === undefined ? null : JSON.stringify(payload);
  const res = http.request(method, `${gatewayBaseUrl}${path}`, body, { headers });

  let parsed = null;
  try {
    parsed = res.json();
  } catch (_) {
    parsed = null;
  }
  const ok = res.status >= 200
    && res.status < 300
    && parsed
    && parsed.code === 0
    && parsed.data !== undefined
    && parsed.data !== null;
  return { ok, res, parsed };
}

function mustOk(result, hint) {
  if (!result.ok) {
    throw new Error(`${hint} failed, status=${result.res.status}, body=${result.res.body}`);
  }
  return result.parsed.data;
}

function uniqueText(prefix) {
  return `${prefix}_${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
}

function uniqueUsername(prefix) {
  const safePrefix = String(prefix).replace(/[^A-Za-z0-9_]/g, '_');
  const seed = `${Date.now().toString().slice(-8)}${Math.floor(Math.random() * 1000)}`;
  return `${safePrefix}_${seed}`.slice(0, 32);
}

function wsJoinAndSend(token, chatTicket, fixedConversationId, content) {
  const state = {
    joined: false,
    acked: false,
    conversationId: Number(fixedConversationId || 0),
    messageId: 0,
    failed: false,
    sendStartAt: 0,
  };

  const result = ws.connect(
    `${notifyWsBaseUrl}?accessToken=${encodeURIComponent(token)}`,
    null,
    (socket) => {
      socket.on('open', () => {
        socket.send(JSON.stringify({
          type: 'join',
          chatTicket,
        }));
      });

      socket.on('message', (raw) => {
        let data = null;
        try {
          data = JSON.parse(raw);
        } catch (_) {
          return;
        }

        if (!data) {
          return;
        }
        if (data.type === 'error') {
          state.failed = true;
          socket.close();
          return;
        }
        if (data.type === 'join_ack') {
          state.joined = true;
          if (!state.conversationId) {
            state.conversationId = Number(data.conversationId || 0);
          }
          if (!state.conversationId) {
            state.failed = true;
            socket.close();
            return;
          }
          state.sendStartAt = Date.now();
          socket.send(JSON.stringify({
            type: 'send',
            conversationId: state.conversationId,
            clientMsgId: uniqueText('k6-chat-client'),
            content,
          }));
          return;
        }
        if (data.type === 'send_ack') {
          state.acked = true;
          state.messageId = Number(data.messageId || 0);
          if (state.sendStartAt > 0) {
            chatSendAckLatency.add(Date.now() - state.sendStartAt);
          }
          socket.close();
        }
      });

      socket.setTimeout(() => {
        if (!state.joined || !state.acked) {
          state.failed = true;
        }
        socket.close();
      }, wsTimeoutMs);
    }
  );

  const upgradeOk = check(result, { 'chat ws upgrade is 101': (r) => r && r.status === 101 });
  if (!upgradeOk || state.failed || !state.joined || !state.acked || state.conversationId <= 0 || state.messageId <= 0) {
    return { ok: false, conversationId: state.conversationId, messageId: state.messageId };
  }
  return { ok: true, conversationId: state.conversationId, messageId: state.messageId };
}

function wsJoinOnly(token, chatTicket, conversationId) {
  const state = { joined: false, failed: false };
  const result = ws.connect(
    `${notifyWsBaseUrl}?accessToken=${encodeURIComponent(token)}`,
    null,
    (socket) => {
      socket.on('open', () => {
        socket.send(JSON.stringify({
          type: 'join',
          chatTicket,
        }));
      });

      socket.on('message', (raw) => {
        let data = null;
        try {
          data = JSON.parse(raw);
        } catch (_) {
          return;
        }
        if (!data) {
          return;
        }
        if (data.type === 'error') {
          state.failed = true;
          socket.close();
          return;
        }
        if (data.type === 'join_ack') {
          if (Number(data.conversationId || 0) !== Number(conversationId || 0)) {
            state.failed = true;
          } else {
            state.joined = true;
          }
          socket.close();
        }
      });

      socket.setTimeout(() => {
        if (!state.joined) {
          state.failed = true;
        }
        socket.close();
      }, wsTimeoutMs);
    }
  );
  return check(result, { 'chat reconnect ws upgrade is 101': (r) => r && r.status === 101 }) && !state.failed && state.joined;
}

function containsMessageId(items, messageId) {
  if (!Array.isArray(items) || items.length === 0) {
    return false;
  }
  return items.some((item) => Number(item.messageId || 0) === Number(messageId || 0));
}

export function setup() {
  const gatewayHealth = http.get(`${gatewayBaseUrl}/actuator/health`);
  check(gatewayHealth, { 'gateway health is UP': (r) => r.status === 200 && r.json('status') === 'UP' });

  const sellerUsername = uniqueUsername(`${perfPrefix}_chat_seller`);
  const buyerUsername = uniqueUsername(`${perfPrefix}_chat_buyer`);
  const sellerPassword = uniqueText('SellerChat');
  const buyerPassword = uniqueText('BuyerChat');

  const sellerRegister = mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username: sellerUsername,
      password: sellerPassword,
      nickname: `Chat Seller ${perfPrefix}`,
    }),
    'register chat seller'
  );
  const buyerRegister = mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username: buyerUsername,
      password: buyerPassword,
      nickname: `Chat Buyer ${perfPrefix}`,
    }),
    'register chat buyer'
  );

  const sellerLogin = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username: sellerUsername,
      password: sellerPassword,
    }),
    'login chat seller'
  );
  const buyerLogin = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username: buyerUsername,
      password: buyerPassword,
    }),
    'login chat buyer'
  );

  const productCreate = mustOk(
    apiRequest('POST', '/api/product/products', sellerLogin.accessToken, {
      title: `Perf Chat 商品 ${perfPrefix}`,
      description: 'k6 chat conversation baseline',
      coverObjectKey: null,
      skus: [
        { skuName: '标准版', specJson: '{"edition":"std"}', priceCent: 1999, stock: 200000 },
      ],
    }),
    'create chat product'
  );
  mustOk(
    apiRequest('POST', `/api/product/products/${productCreate.productId}/publish`, sellerLogin.accessToken, null),
    'publish chat product'
  );

  const ticket = mustOk(
    apiRequest('POST', `/api/product/chat/ticket?listingId=${productCreate.productId}`, buyerLogin.accessToken, null),
    'issue chat ticket'
  );

  return {
    buyerToken: buyerLogin.accessToken,
    sellerToken: sellerLogin.accessToken,
    buyerUserId: `${buyerRegister.userId}`,
    sellerUserId: `${sellerRegister.userId}`,
    chatTicket: ticket.ticket,
  };
}

export default function (data) {
  const startData = mustOk(
    apiRequest('POST', '/api/chat/conversations/start', data.buyerToken, {
      chatTicket: data.chatTicket,
    }),
    'start conversation'
  );
  const conversationId = Number(startData.conversationId || 0);
  if (conversationId <= 0) {
    chatFailedTotal.add(1);
    return;
  }

  const firstSend = wsJoinAndSend(
    data.buyerToken,
    data.chatTicket,
    conversationId,
    `k6 chat message 1 ${__VU}-${__ITER}-${Date.now()}`
  );
  if (!firstSend.ok) {
    chatFailedTotal.add(1);
    return;
  }

  const compensateFirst = apiRequest(
    'GET',
    `/api/chat/conversations/${conversationId}/messages?afterSeq=0&limit=20`,
    data.sellerToken,
    null
  );
  if (!compensateFirst.ok || !containsMessageId(compensateFirst.parsed.data.items, firstSend.messageId)) {
    chatFailedTotal.add(1);
    return;
  }
  chatCompensationHitTotal.add(1);

  chatReconnectTotal.add(1);
  if (!wsJoinOnly(data.sellerToken, data.chatTicket, conversationId)) {
    chatFailedTotal.add(1);
    return;
  }

  const secondSend = wsJoinAndSend(
    data.buyerToken,
    data.chatTicket,
    conversationId,
    `k6 chat message 2 ${__VU}-${__ITER}-${Date.now()}`
  );
  if (!secondSend.ok) {
    chatFailedTotal.add(1);
    return;
  }

  const compensateSecond = apiRequest(
    'GET',
    `/api/chat/conversations/${conversationId}/messages?afterSeq=${firstSend.messageId}&limit=20`,
    data.sellerToken,
    null
  );
  if (!compensateSecond.ok || !containsMessageId(compensateSecond.parsed.data.items, secondSend.messageId)) {
    chatFailedTotal.add(1);
    return;
  }
  chatCompensationHitTotal.add(1);

  sleep(0.2);
}
