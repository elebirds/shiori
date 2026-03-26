import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

export const gatewayBaseUrl = __ENV.PERF_GATEWAY_BASE_URL || 'http://localhost:8080';
export const perfPrefix = __ENV.PERF_PREFIX || 'perf';
export const vus = Number(__ENV.K6_ORDER_VUS || 5);
export const duration = __ENV.K6_ORDER_DURATION || '45s';
const buyerCdk = __ENV.K6_ORDER_BUYER_CDK || '';
const buyerCdks = __ENV.K6_ORDER_BUYER_CDKS || '';
const debugFailSample = __ENV.K6_DEBUG_FAIL_SAMPLE === '1';
const debugFailLimit = Number(__ENV.K6_DEBUG_FAIL_LIMIT || 20);

let failLogCount = 0;
const perfUserPassword = 'PerfPwd123!';

export const orderCreateDuration = new Trend('shiori_perf_order_create_duration_ms', true);
export const orderPayDuration = new Trend('shiori_perf_order_pay_duration_ms', true);
export const orderDeliverDuration = new Trend('shiori_perf_order_deliver_duration_ms', true);
export const orderConfirmDuration = new Trend('shiori_perf_order_confirm_duration_ms', true);
export const orderDetailDuration = new Trend('shiori_perf_order_detail_duration_ms', true);
export const bizFailedTotal = new Counter('shiori_perf_order_biz_failed_total');

export function buildOptions() {
  return {
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
}

export function apiRequest(method, path, token, payload, extraHeaders = {}) {
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

export function mustOk(result, hint) {
  if (!result.ok) {
    throw new Error(`${hint} failed, status=${result.res.status}, body=${result.res.body}`);
  }
  return result.parsed.data;
}

function formatFailureContext(context) {
  if (!context) {
    return '';
  }

  const parts = [];
  for (const key in context) {
    if (Object.prototype.hasOwnProperty.call(context, key) && context[key] !== undefined && context[key] !== null) {
      parts.push(`${key}=${context[key]}`);
    }
  }
  return parts.length > 0 ? ` ${parts.join(' ')}` : '';
}

export function markBizFailure(scriptTag, stage, result, context) {
  const statusTag = result && result.res && result.res.status !== undefined ? String(result.res.status) : 'n/a';
  const codeTag = result && result.parsed && result.parsed.code !== undefined
    ? String(result.parsed.code)
    : 'n/a';
  const metricTags = { script: scriptTag, stage, status: statusTag, code: codeTag };
  if (context && context.buyerIndex !== undefined) {
    metricTags.buyerIndex = String(context.buyerIndex);
  }
  if (context && context.offerIndex !== undefined) {
    metricTags.offerIndex = String(context.offerIndex);
  }
  bizFailedTotal.add(1, metricTags);

  if (debugFailSample && failLogCount < debugFailLimit) {
    failLogCount += 1;
    const body = result && result.res && result.res.body ? String(result.res.body).slice(0, 400) : '';
    console.error(
      `[${scriptTag}][${stage}] status=${statusTag} code=${codeTag}${formatFailureContext(context)} body=${body}`,
    );
  }
}

export function uniqueText(prefix) {
  return `${prefix}_${Date.now()}_${Math.floor(Math.random() * 1000000)}`;
}

export function uniqueUsername(prefix) {
  const safePrefix = String(prefix).replace(/[^A-Za-z0-9_]/g, '_');
  const seed = `${Date.now().toString().slice(-8)}${String(Math.floor(Math.random() * 1000)).padStart(3, '0')}`;
  const suffix = `_${seed}`;
  const prefixLimit = Math.max(1, 32 - suffix.length);
  return `${safePrefix.slice(0, prefixLimit)}${suffix}`;
}

export function splitCsv(value) {
  if (!value) {
    return [];
  }
  return String(value)
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

export function collectBuyerCdkCodes() {
  return [...splitCsv(buyerCdks), ...splitCsv(buyerCdk)];
}

export function redeemCodeList(token, codes) {
  for (const code of codes) {
    mustOk(
      apiRequest('POST', '/api/v2/payment/cdks/redeem', token, { code }),
      `redeem buyer cdk ${code}`,
    );
  }
}

export function ensureGatewayHealth() {
  const health = http.get(`${gatewayBaseUrl}/actuator/health`);
  const ok = check(health, {
    'gateway health is UP': (response) => response.status === 200 && response.json('status') === 'UP',
  });
  if (!ok) {
    throw new Error(`gateway health check failed, status=${health.status}, body=${health.body}`);
  }
}

export function registerAndLoginUser(usernamePrefix, nickname) {
  const username = uniqueUsername(usernamePrefix);
  const password = perfUserPassword;

  const register = mustOk(
    apiRequest('POST', '/api/user/auth/register', '', {
      username,
      password,
      nickname,
    }),
    `register ${usernamePrefix}`,
  );
  const login = mustOk(
    apiRequest('POST', '/api/user/auth/login', '', {
      username,
      password,
    }),
    `login ${usernamePrefix}`,
  );

  return {
    userId: `${register.userId}`,
    accessToken: login.accessToken,
    username,
  };
}

export function createPublishedProduct(sellerToken, config) {
  const productCreate = mustOk(
    apiRequest('POST', '/api/v2/product/products', sellerToken, {
      title: config.title,
      description: config.description || 'k6 order flow',
      coverObjectKey: null,
      categoryCode: config.categoryCode || 'TEXTBOOK',
      subCategoryCode: config.subCategoryCode || 'TEXTBOOK_UNSPEC',
      conditionLevel: config.conditionLevel || 'GOOD',
      tradeMode: config.tradeMode || 'MEETUP',
      campusCode: config.campusCode || 'UNKNOWN_CAMPUS',
      skus: config.skus,
    }),
    `create product ${config.title}`,
  );

  mustOk(
    apiRequest('POST', `/api/v2/product/products/${productCreate.productId}/publish`, sellerToken, null),
    `publish product ${config.title}`,
  );

  const detail = mustOk(
    apiRequest('GET', `/api/v2/product/products/${productCreate.productId}`, '', null),
    `get product detail ${config.title}`,
  );

  return {
    productId: productCreate.productId,
    skus: detail.skus,
  };
}

function buildIdempotencyKey(flowPrefix, stage) {
  return `${flowPrefix}-${stage}-${__VU}-${__ITER}-${Date.now()}`;
}

export function executeOrderLifecycle(context) {
  const flowPrefix = context.flowPrefix || perfPrefix;
  const failureContext = {
    buyerIndex: context.buyerIndex,
    offerIndex: context.offerIndex,
    flowPrefix,
  };

  const createOrder = apiRequest(
    'POST',
    '/api/v2/order/orders',
    context.buyerToken,
    {
      items: [
        { productId: context.productId, skuId: context.skuId, quantity: 1 },
      ],
    },
    { 'Idempotency-Key': buildIdempotencyKey(flowPrefix, 'create') },
  );
  orderCreateDuration.add(createOrder.res.timings.duration, { script: context.scriptTag });
  if (!createOrder.ok) {
    markBizFailure(context.scriptTag, 'create', createOrder, failureContext);
    return false;
  }

  const orderNo = createOrder.parsed.data.orderNo;
  const payOrder = apiRequest(
    'POST',
    `/api/v2/order/orders/${orderNo}/pay`,
    context.buyerToken,
    null,
    { 'Idempotency-Key': buildIdempotencyKey(flowPrefix, 'pay') },
  );
  orderPayDuration.add(payOrder.res.timings.duration, { script: context.scriptTag });
  if (!payOrder.ok) {
    markBizFailure(context.scriptTag, 'pay', payOrder, failureContext);
    return false;
  }

  const deliverOrder = apiRequest(
    'POST',
    `/api/v2/order/seller/orders/${orderNo}/deliver`,
    context.sellerToken,
    { reason: 'k6 seller deliver' },
  );
  orderDeliverDuration.add(deliverOrder.res.timings.duration, { script: context.scriptTag });
  if (!deliverOrder.ok) {
    markBizFailure(context.scriptTag, 'deliver', deliverOrder, failureContext);
    return false;
  }

  const confirmReceipt = apiRequest(
    'POST',
    `/api/v2/order/orders/${orderNo}/confirm-receipt`,
    context.buyerToken,
    { reason: 'k6 buyer confirm' },
  );
  orderConfirmDuration.add(confirmReceipt.res.timings.duration, { script: context.scriptTag });
  if (!confirmReceipt.ok) {
    markBizFailure(context.scriptTag, 'confirm', confirmReceipt, failureContext);
    return false;
  }

  const orderDetail = apiRequest(
    'GET',
    `/api/v2/order/orders/${orderNo}`,
    context.buyerToken,
    null,
  );
  orderDetailDuration.add(orderDetail.res.timings.duration, { script: context.scriptTag });
  if (!orderDetail.ok || orderDetail.parsed.data.status !== 'FINISHED') {
    markBizFailure(context.scriptTag, 'detail', orderDetail, failureContext);
    return false;
  }

  return true;
}
