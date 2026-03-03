#!/usr/bin/env bash
set -euo pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
NOTIFY_WS_BASE_URL="${NOTIFY_WS_BASE_URL:-ws://localhost:8090/ws}"
SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-60}"
SMOKE_PREFIX="${SMOKE_PREFIX:-smoke}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WS_SMOKE_CMD="${ROOT_DIR}/shiori-notify/cmd/ws-smoke"
TMP_DIR="$(mktemp -d)"
START_TS="$(date +%s)"

BUYER_WS_PID=""
SELLER_WS_PID=""

log() {
  echo "[smoke] $*"
}

fail() {
  echo "[smoke][ERROR] $*" >&2
  exit 1
}

cleanup() {
  if [[ -n "${BUYER_WS_PID}" ]] && kill -0 "${BUYER_WS_PID}" 2>/dev/null; then
    kill "${BUYER_WS_PID}" 2>/dev/null || true
  fi
  if [[ -n "${SELLER_WS_PID}" ]] && kill -0 "${SELLER_WS_PID}" 2>/dev/null; then
    kill "${SELLER_WS_PID}" 2>/dev/null || true
  fi
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "缺少依赖命令: $1"
  fi
}

require_command curl
require_command jq
require_command go

if [[ ! -d "${WS_SMOKE_CMD}" ]]; then
  fail "未找到 ws-smoke 命令目录: ${WS_SMOKE_CMD}"
fi

NOTIFY_HTTP_BASE_URL="${NOTIFY_HTTP_BASE_URL:-}"
if [[ -z "${NOTIFY_HTTP_BASE_URL}" ]]; then
  NOTIFY_HTTP_BASE_URL="${NOTIFY_WS_BASE_URL/ws:\/\//http://}"
  NOTIFY_HTTP_BASE_URL="${NOTIFY_HTTP_BASE_URL/wss:\/\//https://}"
  NOTIFY_HTTP_BASE_URL="${NOTIFY_HTTP_BASE_URL%/ws}"
fi

call_api() {
  local method="$1"
  local path="$2"
  local token="$3"
  local body="$4"
  shift 4
  local extra_headers=("$@")
  local response_file="${TMP_DIR}/resp.$RANDOM.json"
  local status=""
  local -a cmd=(curl -sS -o "${response_file}" -w "%{http_code}" -X "${method}" "${GATEWAY_BASE_URL}${path}")

  if [[ -n "${token}" ]]; then
    cmd+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "${body}" ]]; then
    cmd+=(-H "Content-Type: application/json" --data "${body}")
  fi
  if (( ${#extra_headers[@]} > 0 )); then
    cmd+=("${extra_headers[@]}")
  fi

  status="$("${cmd[@]}")" || fail "请求失败: ${method} ${path}"
  local resp
  resp="$(cat "${response_file}")"
  if [[ "${status:0:1}" != "2" ]]; then
    fail "请求返回非 2xx: ${method} ${path}, status=${status}, body=${resp}"
  fi

  local code
  code="$(echo "${resp}" | jq -r '.code // empty' 2>/dev/null || true)"
  if [[ "${code}" != "0" ]]; then
    fail "请求业务码非 0: ${method} ${path}, code=${code}, body=${resp}"
  fi
  echo "${resp}"
}

extract_required() {
  local json="$1"
  local expr="$2"
  local name="$3"
  local value
  value="$(echo "${json}" | jq -r "${expr}")"
  if [[ -z "${value}" || "${value}" == "null" ]]; then
    fail "提取字段失败: ${name}, expr=${expr}, body=${json}"
  fi
  echo "${value}"
}

log "检查网关健康状态..."
gateway_health="$(curl -fsS "${GATEWAY_BASE_URL}/actuator/health")" || fail "网关健康检查失败"
gateway_status="$(echo "${gateway_health}" | jq -r '.status // empty')"
[[ "${gateway_status}" == "UP" ]] || fail "网关未就绪: ${gateway_health}"

log "检查 notify 健康状态..."
notify_health="$(curl -fsS "${NOTIFY_HTTP_BASE_URL}/healthz")" || fail "notify 健康检查失败"
notify_status="$(echo "${notify_health}" | jq -r '.status // empty')"
[[ "${notify_status}" == "ok" ]] || fail "notify 未就绪: ${notify_health}"

run_id="$(date +%s)$RANDOM"
seller_username="${SMOKE_PREFIX}_seller_${run_id}"
buyer_username="${SMOKE_PREFIX}_buyer_${run_id}"
seller_password="S${run_id}a!"
buyer_password="B${run_id}a!"

log "注册 seller 用户: ${seller_username}"
seller_register_payload="$(jq -nc \
  --arg username "${seller_username}" \
  --arg password "${seller_password}" \
  --arg nickname "Seller ${run_id}" \
  '{username:$username,password:$password,nickname:$nickname}')"
seller_register_resp="$(call_api POST "/api/user/auth/register" "" "${seller_register_payload}")"
seller_user_id="$(extract_required "${seller_register_resp}" '.data.userId | tostring' "seller.userId")"

log "注册 buyer 用户: ${buyer_username}"
buyer_register_payload="$(jq -nc \
  --arg username "${buyer_username}" \
  --arg password "${buyer_password}" \
  --arg nickname "Buyer ${run_id}" \
  '{username:$username,password:$password,nickname:$nickname}')"
buyer_register_resp="$(call_api POST "/api/user/auth/register" "" "${buyer_register_payload}")"
buyer_user_id="$(extract_required "${buyer_register_resp}" '.data.userId | tostring' "buyer.userId")"

log "登录 seller..."
seller_login_payload="$(jq -nc --arg username "${seller_username}" --arg password "${seller_password}" '{username:$username,password:$password}')"
seller_login_resp="$(call_api POST "/api/user/auth/login" "" "${seller_login_payload}")"
seller_access_token="$(extract_required "${seller_login_resp}" '.data.accessToken' "seller.accessToken")"

log "登录 buyer..."
buyer_login_payload="$(jq -nc --arg username "${buyer_username}" --arg password "${buyer_password}" '{username:$username,password:$password}')"
buyer_login_resp="$(call_api POST "/api/user/auth/login" "" "${buyer_login_payload}")"
buyer_access_token="$(extract_required "${buyer_login_resp}" '.data.accessToken' "buyer.accessToken")"

log "seller 创建商品（2 个 SKU）..."
create_product_payload="$(jq -nc \
  --arg title "Smoke 商品 ${run_id}" \
  --arg description "用于端到端烟测" \
  '{
    title:$title,
    description:$description,
    coverObjectKey:null,
    skus:[
      {skuName:"标准版",specJson:"{\"edition\":\"std\"}",priceCent:1999,stock:50},
      {skuName:"豪华版",specJson:"{\"edition\":\"pro\"}",priceCent:2999,stock:50}
    ]
  }')"
product_create_resp="$(call_api POST "/api/product/products" "${seller_access_token}" "${create_product_payload}")"
product_id="$(extract_required "${product_create_resp}" '.data.productId | tostring' "productId")"

log "seller 发布商品: productId=${product_id}"
call_api POST "/api/product/products/${product_id}/publish" "${seller_access_token}" ""

log "读取商品详情，提取 SKU..."
product_detail_resp="$(call_api GET "/api/product/products/${product_id}" "" "")"
sku_id_1="$(extract_required "${product_detail_resp}" '.data.skus[0].skuId | tostring' "sku_id_1")"
sku_id_2="$(extract_required "${product_detail_resp}" '.data.skus[1].skuId | tostring' "sku_id_2")"

idempotency_key="${SMOKE_PREFIX}-idem-${run_id}"
create_order_payload="$(jq -nc --argjson productId "${product_id}" --argjson sku1 "${sku_id_1}" --argjson sku2 "${sku_id_2}" \
  '{items:[{productId:$productId,skuId:$sku1,quantity:1},{productId:$productId,skuId:$sku2,quantity:1}]}')"

log "buyer 创建订单（第一次）..."
order_create_resp_1="$(call_api POST "/api/order/orders" "${buyer_access_token}" "${create_order_payload}" -H "Idempotency-Key: ${idempotency_key}")"
order_no_1="$(extract_required "${order_create_resp_1}" '.data.orderNo' "orderNo_1")"

log "buyer 创建订单（重复请求，验证幂等）..."
order_create_resp_2="$(call_api POST "/api/order/orders" "${buyer_access_token}" "${create_order_payload}" -H "Idempotency-Key: ${idempotency_key}")"
order_no_2="$(extract_required "${order_create_resp_2}" '.data.orderNo' "orderNo_2")"
[[ "${order_no_1}" == "${order_no_2}" ]] || fail "幂等失败，两次 orderNo 不一致: ${order_no_1} vs ${order_no_2}"

buyer_ws_log="${TMP_DIR}/buyer.ws.log"
seller_ws_log="${TMP_DIR}/seller.ws.log"

log "启动 WS 探针（buyer/seller）等待 OrderPaid..."
(
  cd "${ROOT_DIR}/shiori-notify"
  go run ./cmd/ws-smoke \
    -base-url "${NOTIFY_WS_BASE_URL}" \
    -user-id "${buyer_user_id}" \
    -expect-type "OrderPaid" \
    -expect-aggregate "${order_no_1}" \
    -timeout "${SMOKE_TIMEOUT_SECONDS}s"
) >"${buyer_ws_log}" 2>&1 &
BUYER_WS_PID=$!

(
  cd "${ROOT_DIR}/shiori-notify"
  go run ./cmd/ws-smoke \
    -base-url "${NOTIFY_WS_BASE_URL}" \
    -user-id "${seller_user_id}" \
    -expect-type "OrderPaid" \
    -expect-aggregate "${order_no_1}" \
    -timeout "${SMOKE_TIMEOUT_SECONDS}s"
) >"${seller_ws_log}" 2>&1 &
SELLER_WS_PID=$!

sleep 1

payment_no="${SMOKE_PREFIX}-pay-${run_id}"
pay_order_payload="$(jq -nc --arg paymentNo "${payment_no}" '{paymentNo:$paymentNo}')"
pay_idempotency_key="${SMOKE_PREFIX}-pay-idem-${run_id}"
log "buyer 支付订单: orderNo=${order_no_1}"
call_api POST "/api/order/orders/${order_no_1}/pay" "${buyer_access_token}" "${pay_order_payload}" \
  -H "Idempotency-Key: ${pay_idempotency_key}" >/dev/null

if ! wait "${BUYER_WS_PID}"; then
  cat "${buyer_ws_log}" >&2 || true
  fail "buyer WS 未在超时内收到 OrderPaid"
fi
if ! wait "${SELLER_WS_PID}"; then
  cat "${seller_ws_log}" >&2 || true
  fail "seller WS 未在超时内收到 OrderPaid"
fi
BUYER_WS_PID=""
SELLER_WS_PID=""

order_detail_resp="$(call_api GET "/api/order/orders/${order_no_1}" "${buyer_access_token}" "")"
order_status="$(extract_required "${order_detail_resp}" '.data.status' "order.status")"
[[ "${order_status}" == "PAID" ]] || fail "订单状态异常，预期 PAID，实际 ${order_status}"

cost_seconds="$(( $(date +%s) - START_TS ))"
log "E2E 烟测成功"
log "buyerUserId=${buyer_user_id}, sellerUserId=${seller_user_id}, productId=${product_id}, orderNo=${order_no_1}, duration=${cost_seconds}s"
