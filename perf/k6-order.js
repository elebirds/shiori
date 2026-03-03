import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const gatewayBaseUrl = __ENV.PERF_GATEWAY_BASE_URL || 'http://localhost:8080';
const perfPrefix = __ENV.PERF_PREFIX || 'perf';
const vus = Number(__ENV.K6_ORDER_VUS || 5);
const duration = __ENV.K6_ORDER_DURATION || '45s';

const orderCreateDuration = new Trend('shiori_perf_order_create_duration_ms', true);
const orderPayDuration = new Trend('shiori_perf_order_pay_duration_ms', true);
const orderDetailDuration = new Trend('shiori_perf_order_detail_duration_ms', true);
const bizFailedTotal = new Counter('shiori_perf_order_biz_failed_total');

export const options = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    shiori_perf_order_create_duration_ms: ['p(95)<300'],
    shiori_perf_order_pay_duration_ms: ['p(95)<300'],
    shiori_perf_order_detail_duration_ms: ['p(95)<300'],
    shiori_perf_order_biz_failed_total: ['count==0'],
  },
};

function apiRequest(method, path, token, payload, extraHeaders = {}) {
  const headers = { 'Content-Type': 'application/json', ...extraHeaders };
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

export function setup() {
  const health = http.get(`${gatewayBaseUrl}/actuator/health`);
  check(health, { 'gateway health is UP': (r) => r.status === 200 && r.json('status') === 'UP' });

  const sellerUsername = uniqueText(`${perfPrefix}_seller`);
  const buyerUsername = uniqueText(`${perfPrefix}_buyer`);
  const sellerPassword = uniqueText('SellerA');
  const buyerPassword = uniqueText('BuyerA');

  mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username: sellerUsername,
      password: sellerPassword,
      nickname: `Seller ${perfPrefix}`,
    }),
    'register seller'
  );
  const buyerRegister = mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username: buyerUsername,
      password: buyerPassword,
      nickname: `Buyer ${perfPrefix}`,
    }),
    'register buyer'
  );

  const sellerLogin = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username: sellerUsername,
      password: sellerPassword,
    }),
    'login seller'
  );
  const buyerLogin = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username: buyerUsername,
      password: buyerPassword,
    }),
    'login buyer'
  );

  const productCreate = mustOk(
    apiRequest('POST', '/api/product/products', sellerLogin.accessToken, {
      title: `Perf 商品 ${perfPrefix}`,
      description: 'k6 order baseline',
      coverObjectKey: null,
      skus: [
        { skuName: '标准版', specJson: '{"edition":"std"}', priceCent: 1999, stock: 200000 },
        { skuName: '豪华版', specJson: '{"edition":"pro"}', priceCent: 2999, stock: 200000 },
      ],
    }),
    'create product'
  );

  mustOk(
    apiRequest('POST', `/api/product/products/${productCreate.productId}/publish`, sellerLogin.accessToken, null),
    'publish product'
  );

  const detail = mustOk(
    apiRequest('GET', `/api/product/products/${productCreate.productId}`, '', null),
    'get product detail'
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
  const idemKey = `${perfPrefix}-idem-${__VU}-${__ITER}-${Date.now()}`;
  const createOrder = apiRequest(
    'POST',
    '/api/order/orders',
    data.buyerToken,
    {
      items: [
        { productId: data.productId, skuId: data.skuId1, quantity: 1 },
        { productId: data.productId, skuId: data.skuId2, quantity: 1 },
      ],
    },
    { 'Idempotency-Key': idemKey }
  );
  orderCreateDuration.add(createOrder.res.timings.duration);
  if (!createOrder.ok) {
    bizFailedTotal.add(1);
    return;
  }

  const orderNo = createOrder.parsed.data.orderNo;
  const payOrder = apiRequest(
    'POST',
    `/api/order/orders/${orderNo}/pay`,
    data.buyerToken,
    { paymentNo: `${perfPrefix}-pay-${__VU}-${__ITER}-${Date.now()}` }
  );
  orderPayDuration.add(payOrder.res.timings.duration);
  if (!payOrder.ok) {
    bizFailedTotal.add(1);
    return;
  }

  const orderDetail = apiRequest(
    'GET',
    `/api/order/orders/${orderNo}`,
    data.buyerToken,
    null
  );
  orderDetailDuration.add(orderDetail.res.timings.duration);
  if (!orderDetail.ok || orderDetail.parsed.data.status !== 'PAID') {
    bizFailedTotal.add(1);
  }

  sleep(0.2);
}
