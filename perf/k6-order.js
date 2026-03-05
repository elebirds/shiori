import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const gatewayBaseUrl = __ENV.PERF_GATEWAY_BASE_URL || 'http://localhost:8080';
const perfPrefix = __ENV.PERF_PREFIX || 'perf';
const vus = Number(__ENV.K6_ORDER_VUS || 5);
const duration = __ENV.K6_ORDER_DURATION || '45s';
const debugFailSample = __ENV.K6_DEBUG_FAIL_SAMPLE === '1';
const debugFailLimit = Number(__ENV.K6_DEBUG_FAIL_LIMIT || 20);

let failLogCount = 0;

const orderCreateDuration = new Trend('shiori_perf_order_create_duration_ms', true);
const orderPayDuration = new Trend('shiori_perf_order_pay_duration_ms', true);
const orderDeliverDuration = new Trend('shiori_perf_order_deliver_duration_ms', true);
const orderConfirmDuration = new Trend('shiori_perf_order_confirm_duration_ms', true);
const orderDetailDuration = new Trend('shiori_perf_order_detail_duration_ms', true);
const bizFailedTotal = new Counter('shiori_perf_order_biz_failed_total');

export const options = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    shiori_perf_order_create_duration_ms: ['p(95)<400'],
    shiori_perf_order_pay_duration_ms: ['p(95)<400'],
    shiori_perf_order_deliver_duration_ms: ['p(95)<400'],
    shiori_perf_order_confirm_duration_ms: ['p(95)<400'],
    shiori_perf_order_detail_duration_ms: ['p(95)<400'],
    shiori_perf_order_biz_failed_total: ['count==0'],
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

function markBizFailure(stage, result) {
  const statusTag = result && result.res && result.res.status !== undefined ? String(result.res.status) : 'n/a';
  const codeTag = result && result.parsed && result.parsed.code !== undefined
    ? String(result.parsed.code)
    : 'n/a';
  bizFailedTotal.add(1, { stage, status: statusTag, code: codeTag });

  if (debugFailSample && failLogCount < debugFailLimit) {
    failLogCount += 1;
    const body = result && result.res && result.res.body ? String(result.res.body).slice(0, 400) : '';
    console.error(`[k6-order][${stage}] status=${statusTag} code=${codeTag} body=${body}`);
  }
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
  const health = http.get(`${gatewayBaseUrl}/actuator/health`);
  check(health, { 'gateway health is UP': (r) => r.status === 200 && r.json('status') === 'UP' });

  const sellerUsername = uniqueUsername(`${perfPrefix}_seller`);
  const buyerUsername = uniqueUsername(`${perfPrefix}_buyer`);
  const sellerPassword = uniqueText('SellerA');
  const buyerPassword = uniqueText('BuyerA');

  const sellerRegister = mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username: sellerUsername,
      password: sellerPassword,
      nickname: `Seller ${perfPrefix}`,
    }),
    'register seller',
  );
  const buyerRegister = mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username: buyerUsername,
      password: buyerPassword,
      nickname: `Buyer ${perfPrefix}`,
    }),
    'register buyer',
  );

  const sellerLogin = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username: sellerUsername,
      password: sellerPassword,
    }),
    'login seller',
  );
  const buyerLogin = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username: buyerUsername,
      password: buyerPassword,
    }),
    'login buyer',
  );

  const productCreate = mustOk(
    apiRequest('POST', '/api/v2/product/products', sellerLogin.accessToken, {
      title: `Perf 商品 ${perfPrefix}`,
      description: 'k6 v0.4-b order flow',
      coverObjectKey: null,
      categoryCode: 'TEXTBOOK',
      conditionLevel: 'GOOD',
      tradeMode: 'MEETUP',
      campusCode: 'perf_campus',
      skus: [
        { skuName: '标准版', specJson: '{"edition":"std"}', priceCent: 1999, stock: 200000 },
        { skuName: '豪华版', specJson: '{"edition":"pro"}', priceCent: 2999, stock: 200000 },
      ],
    }),
    'create product',
  );

  mustOk(
    apiRequest('POST', `/api/v2/product/products/${productCreate.productId}/publish`, sellerLogin.accessToken, null),
    'publish product',
  );

  const detail = mustOk(
    apiRequest('GET', `/api/v2/product/products/${productCreate.productId}`, '', null),
    'get product detail',
  );

  return {
    sellerToken: sellerLogin.accessToken,
    sellerUserId: `${sellerRegister.userId}`,
    buyerToken: buyerLogin.accessToken,
    buyerUserId: `${buyerRegister.userId}`,
    productId: productCreate.productId,
    skuId1: detail.skus[0].skuId,
    skuId2: detail.skus[1].skuId,
  };
}

export default function (data) {
  const createIdemKey = `${perfPrefix}-create-${__VU}-${__ITER}-${Date.now()}`;
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
    { 'Idempotency-Key': createIdemKey },
  );
  orderCreateDuration.add(createOrder.res.timings.duration);
  if (!createOrder.ok) {
    markBizFailure('create', createOrder);
    return;
  }

  const orderNo = createOrder.parsed.data.orderNo;
  const payOrder = apiRequest(
    'POST',
    `/api/v2/order/orders/${orderNo}/pay`,
    data.buyerToken,
    null,
    { 'Idempotency-Key': `${perfPrefix}-pay-${__VU}-${__ITER}-${Date.now()}` },
  );
  orderPayDuration.add(payOrder.res.timings.duration);
  if (!payOrder.ok) {
    markBizFailure('pay', payOrder);
    return;
  }

  const deliverOrder = apiRequest(
    'POST',
    `/api/v2/order/seller/orders/${orderNo}/deliver`,
    data.sellerToken,
    { reason: 'k6 seller deliver' },
  );
  orderDeliverDuration.add(deliverOrder.res.timings.duration);
  if (!deliverOrder.ok) {
    markBizFailure('deliver', deliverOrder);
    return;
  }

  const confirmReceipt = apiRequest(
    'POST',
    `/api/v2/order/orders/${orderNo}/confirm-receipt`,
    data.buyerToken,
    { reason: 'k6 buyer confirm' },
  );
  orderConfirmDuration.add(confirmReceipt.res.timings.duration);
  if (!confirmReceipt.ok) {
    markBizFailure('confirm', confirmReceipt);
    return;
  }

  const orderDetail = apiRequest(
    'GET',
    `/api/v2/order/orders/${orderNo}`,
    data.buyerToken,
    null,
  );
  orderDetailDuration.add(orderDetail.res.timings.duration);
  if (!orderDetail.ok || orderDetail.parsed.data.status !== 'FINISHED') {
    markBizFailure('detail', orderDetail);
  }

  sleep(0.2);
}
