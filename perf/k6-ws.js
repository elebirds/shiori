import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const gatewayBaseUrl = __ENV.PERF_GATEWAY_BASE_URL || 'http://localhost:8080';
const notifyWsBaseUrl = __ENV.PERF_NOTIFY_WS_BASE_URL || 'ws://localhost:8090/ws';
const perfPrefix = __ENV.PERF_PREFIX || 'perf';
const vus = Number(__ENV.K6_WS_VUS || 2);
const iterations = Number(__ENV.K6_WS_ITERATIONS || 10);
const wsTimeoutMs = Number(__ENV.K6_WS_TIMEOUT_MS || 10000);
const wsLatencyP95Ms = Number(__ENV.K6_WS_LATENCY_P95_MS || 3000);

const wsNotificationLatency = new Trend('shiori_perf_ws_notification_latency_ms', true);
const wsTimeoutTotal = new Counter('shiori_perf_ws_timeout_total');
const wsBizFailedTotal = new Counter('shiori_perf_ws_biz_failed_total');

export const options = {
  scenarios: {
    ws_notify: {
      executor: 'per-vu-iterations',
      vus,
      iterations,
      maxDuration: '10m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    shiori_perf_ws_notification_latency_ms: [`p(95)<${wsLatencyP95Ms}`],
    shiori_perf_ws_timeout_total: ['count==0'],
    shiori_perf_ws_biz_failed_total: ['count==0'],
  },
};

function toNotifyHttpBase(wsBaseUrl) {
  return wsBaseUrl.replace(/^ws:\/\//, 'http://')
    .replace(/^wss:\/\//, 'https://')
    .replace(/\/ws$/, '');
}

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
  } catch (error) {
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

export function setup() {
  const notifyHttpBaseUrl = __ENV.PERF_NOTIFY_HTTP_BASE_URL || toNotifyHttpBase(notifyWsBaseUrl);

  const gatewayHealth = http.get(`${gatewayBaseUrl}/actuator/health`);
  check(gatewayHealth, { 'gateway health is UP': (r) => r.status === 200 && r.json('status') === 'UP' });

  const notifyHealth = http.get(`${notifyHttpBaseUrl}/healthz`);
  check(notifyHealth, { 'notify health is ok': (r) => r.status === 200 && r.json('status') === 'ok' });

  const sellerUsername = uniqueUsername(`${perfPrefix}_ws_seller`);
  const buyerUsername = uniqueUsername(`${perfPrefix}_ws_buyer`);
  const sellerPassword = uniqueText('SellerWS');
  const buyerPassword = uniqueText('BuyerWS');

  mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username: sellerUsername,
      password: sellerPassword,
      nickname: `WS Seller ${perfPrefix}`,
    }),
    'register ws seller'
  );
  const buyerRegister = mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username: buyerUsername,
      password: buyerPassword,
      nickname: `WS Buyer ${perfPrefix}`,
    }),
    'register ws buyer'
  );

  const sellerLogin = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username: sellerUsername,
      password: sellerPassword,
    }),
    'login ws seller'
  );
  const buyerLogin = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username: buyerUsername,
      password: buyerPassword,
    }),
    'login ws buyer'
  );

  const productCreate = mustOk(
    apiRequest('POST', '/api/v2/product/products', sellerLogin.accessToken, {
      title: `Perf WS 商品 ${perfPrefix}`,
      description: 'k6 ws baseline',
      coverObjectKey: null,
      skus: [
        { skuName: '标准版', specJson: '{"edition":"std"}', priceCent: 1999, stock: 200000 },
        { skuName: '豪华版', specJson: '{"edition":"pro"}', priceCent: 2999, stock: 200000 },
      ],
    }),
    'create ws product'
  );

  mustOk(
    apiRequest('POST', `/api/v2/product/products/${productCreate.productId}/publish`, sellerLogin.accessToken, null),
    'publish ws product'
  );

  const detail = mustOk(
    apiRequest('GET', `/api/v2/product/products/${productCreate.productId}`, '', null),
    'get ws product detail'
  );

  return {
    buyerToken: buyerLogin.accessToken,
    buyerUserId: `${buyerRegister.userId}`,
    productId: productCreate.productId,
    skuId1: detail.skus[0].skuId,
    skuId2: detail.skus[1].skuId,
  };
}

export default function (data) {
  const idemKey = `${perfPrefix}-ws-idem-${__VU}-${__ITER}-${Date.now()}`;
  const createOrder = apiRequest(
    'POST',
    '/api/v2/order/orders',
    data.buyerToken,
    {
      items: [
        { productId: data.productId, skuId: data.skuId1, quantity: 1 },
        { productId: data.productId, skuId: data.skuId2, quantity: 1 },
      ],
    },
    { 'Idempotency-Key': idemKey }
  );
  if (!createOrder.ok) {
    wsBizFailedTotal.add(1);
    return;
  }
  const orderNo = createOrder.parsed.data.orderNo;
  const payIdemKey = `${perfPrefix}-ws-pay-idem-${__VU}-${__ITER}-${Date.now()}`;

  let paySucceeded = false;
  let notified = false;
  let payAt = 0;

  const wsResult = ws.connect(
    `${notifyWsBaseUrl}?accessToken=${encodeURIComponent(data.buyerToken)}&userId=${encodeURIComponent(data.buyerUserId)}`,
    null,
    (socket) => {
      socket.on('open', () => {
        payAt = Date.now();
        const payOrder = apiRequest(
          'POST',
          `/api/v2/order/orders/${orderNo}/pay`,
          data.buyerToken,
          null,
          { 'Idempotency-Key': payIdemKey }
        );
        paySucceeded = payOrder.ok;
        if (!paySucceeded) {
          wsBizFailedTotal.add(1);
          socket.close();
        }
      });

      socket.on('message', (message) => {
        let env = null;
        try {
          env = JSON.parse(message);
        } catch (error) {
          return;
        }
        if (!env || env.type !== 'OrderPaid' || env.aggregateId !== orderNo) {
          return;
        }
        notified = true;
        wsNotificationLatency.add(Date.now() - payAt);
        socket.close();
      });

      socket.setTimeout(() => {
        socket.close();
      }, wsTimeoutMs);
    }
  );

  if (!check(wsResult, { 'ws upgrade status is 101': (r) => r && r.status === 101 })) {
    wsBizFailedTotal.add(1);
    return;
  }

  if (paySucceeded && !notified) {
    wsTimeoutTotal.add(1);
    wsBizFailedTotal.add(1);
  }

  sleep(0.2);
}
