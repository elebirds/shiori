#!/usr/bin/env bash
set -euo pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
SMOKE_PREFIX="${SMOKE_PREFIX:-adminsmoke}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-shiori-mysql}"
MYSQL_USER="${MYSQL_USER:?missing MYSQL_USER}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?missing MYSQL_PASSWORD}"

TMP_DIR="$(mktemp -d)"
START_TS="$(date +%s)"

log() {
  echo "[admin-smoke] $*"
}

fail() {
  echo "[admin-smoke][ERROR] $*" >&2
  exit 1
}

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "缺少依赖命令: $1"
  fi
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

call_api_expect_status() {
  local method="$1"
  local path="$2"
  local token="$3"
  local body="$4"
  local expected_status="$5"
  local expected_code="${6:-}"
  local response_file="${TMP_DIR}/resp.fail.$RANDOM.json"
  local -a cmd=(curl -sS -o "${response_file}" -w "%{http_code}" -X "${method}" "${GATEWAY_BASE_URL}${path}")

  if [[ -n "${token}" ]]; then
    cmd+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "${body}" ]]; then
    cmd+=(-H "Content-Type: application/json" --data "${body}")
  fi

  local status
  status="$("${cmd[@]}")" || fail "请求失败: ${method} ${path}"
  local resp
  resp="$(cat "${response_file}")"

  if [[ "${status}" != "${expected_status}" ]]; then
    fail "请求状态码不符合预期: ${method} ${path}, expect=${expected_status}, actual=${status}, body=${resp}"
  fi

  local code
  code="$(echo "${resp}" | jq -r '.code // empty' 2>/dev/null || true)"
  if [[ -n "${expected_code}" && "${code}" != "${expected_code}" ]]; then
    fail "请求业务码不符合预期: ${method} ${path}, expect=${expected_code}, actual=${code}, body=${resp}"
  fi

  echo "${resp}"
}

grant_admin_role_by_sql() {
  local username="$1"
  docker exec -i "${MYSQL_CONTAINER}" \
    mysql "-u${MYSQL_USER}" "-p${MYSQL_PASSWORD}" shiori_user <<SQL >/dev/null
INSERT INTO u_user_role (user_id, role_id, created_at)
SELECT u.id, r.id, CURRENT_TIMESTAMP(3)
FROM u_user u
JOIN u_role r ON r.role_code = 'ROLE_ADMIN' AND r.status = 1 AND r.is_deleted = 0
WHERE u.username = '${username}'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
SQL
}

query_mysql_scalar() {
  local database="$1"
  local sql="$2"
  docker exec "${MYSQL_CONTAINER}" \
    mysql "-u${MYSQL_USER}" "-p${MYSQL_PASSWORD}" -D "${database}" -Nse "${sql}"
}

require_command curl
require_command jq
require_command docker

if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
  fail "MySQL 容器未运行: ${MYSQL_CONTAINER}"
fi

log "检查网关健康状态..."
gateway_health="$(curl -fsS "${GATEWAY_BASE_URL}/actuator/health")" || fail "网关健康检查失败"
gateway_status="$(echo "${gateway_health}" | jq -r '.status // empty')"
[[ "${gateway_status}" == "UP" ]] || fail "网关未就绪: ${gateway_health}"

raw_id="$(date +%s)$RANDOM"
id_suffix="${raw_id}"
if (( ${#id_suffix} > 8 )); then
  id_suffix="${id_suffix: -8}"
fi
safe_prefix="$(echo "${SMOKE_PREFIX}" | tr -cd 'a-zA-Z0-9_' | tr '[:upper:]' '[:lower:]')"
safe_prefix="${safe_prefix:-smk}"
safe_prefix="${safe_prefix:0:8}"

admin_username="${safe_prefix}adm${id_suffix}"
seller_username="${safe_prefix}sel${id_suffix}"
buyer_username="${safe_prefix}buy${id_suffix}"
default_password="A${id_suffix}a!"

register_user() {
  local username="$1"
  local nickname="$2"
  local payload
  payload="$(jq -nc \
    --arg username "${username}" \
    --arg password "${default_password}" \
    --arg nickname "${nickname}" \
    '{username:$username,password:$password,nickname:$nickname}')"
  local resp
  resp="$(call_api POST "/api/user/auth/register" "" "${payload}")"
  extract_required "${resp}" '.data.userId | tostring' "userId(${username})"
}

login_user() {
  local username="$1"
  local payload
  payload="$(jq -nc --arg username "${username}" --arg password "${default_password}" '{username:$username,password:$password}')"
  local resp
  resp="$(call_api POST "/api/user/auth/login" "" "${payload}")"
  local access_token
  local user_id
  local roles_csv
  access_token="$(extract_required "${resp}" '.data.accessToken' "accessToken(${username})")"
  user_id="$(extract_required "${resp}" '.data.user.userId | tostring' "userId(${username})")"
  roles_csv="$(echo "${resp}" | jq -r '.data.user.roles // [] | join(",")')"
  echo "${access_token}|${user_id}|${roles_csv}"
}

log "注册 admin/seller/buyer 测试用户..."
admin_user_id="$(register_user "${admin_username}" "Admin ${id_suffix}")"
seller_user_id="$(register_user "${seller_username}" "Seller ${id_suffix}")"
buyer_user_id="$(register_user "${buyer_username}" "Buyer ${id_suffix}")"

log "admin 候选账号登录并验证未授权访问 /api/admin/users 为 403..."
admin_login_raw="$(login_user "${admin_username}")"
admin_token_before="${admin_login_raw%%|*}"
call_api_expect_status GET "/api/admin/users?page=1&size=1" "${admin_token_before}" "" 403 20003 >/dev/null

log "通过 SQL 授予 admin 候选账号 ROLE_ADMIN..."
grant_admin_role_by_sql "${admin_username}"
admin_link_count="$(query_mysql_scalar "shiori_user" "SELECT COUNT(1) FROM u_user_role ur JOIN u_user u ON ur.user_id=u.id JOIN u_role r ON ur.role_id=r.id WHERE u.username='${admin_username}' AND r.role_code='ROLE_ADMIN';")"
[[ "${admin_link_count}" =~ ^[0-9]+$ ]] || fail "管理员角色授予校验失败: ${admin_link_count}"
(( admin_link_count >= 1 )) || fail "管理员角色授予失败: ${admin_username}"

log "admin 重新登录并校验 ROLE_ADMIN..."
admin_login_after="$(login_user "${admin_username}")"
admin_token="${admin_login_after%%|*}"
admin_roles="${admin_login_after##*|}"
if ! echo "${admin_roles}" | tr ',' '\n' | grep -q '^ROLE_ADMIN$'; then
  fail "admin 登录后角色不包含 ROLE_ADMIN, roles=${admin_roles}"
fi

log "seller 创建并发布商品（用于管理员商品治理）..."
create_product_payload="$(jq -nc \
  --arg title "Admin Smoke 商品A ${id_suffix}" \
  --arg description "管理端烟测商品A" \
  '{
    title:$title,
    description:$description,
    coverObjectKey:null,
    skus:[
      {skuName:"标准版",specJson:"{\"edition\":\"std\"}",priceCent:1299,stock:30},
      {skuName:"旗舰版",specJson:"{\"edition\":\"pro\"}",priceCent:2599,stock:20}
    ]
  }')"
seller_login_raw="$(login_user "${seller_username}")"
seller_token="${seller_login_raw%%|*}"
product_a_resp="$(call_api POST "/api/product/products" "${seller_token}" "${create_product_payload}")"
product_a_id="$(extract_required "${product_a_resp}" '.data.productId | tostring' "productA.id")"
call_api POST "/api/product/products/${product_a_id}/publish" "${seller_token}" "" >/dev/null

log "admin 查询商品并执行强制下架..."
admin_product_page="$(call_api GET "/api/admin/products?page=1&size=10&ownerUserId=${seller_user_id}" "${admin_token}" "")"
contains_product_a="$(echo "${admin_product_page}" | jq -r --arg pid "${product_a_id}" '.data.items | map((.productId|tostring)==$pid) | any')"
[[ "${contains_product_a}" == "true" ]] || fail "管理员商品列表未包含目标商品 productId=${product_a_id}"
call_api POST "/api/admin/products/${product_a_id}/off-shelf" "${admin_token}" '{"reason":"运营下架烟测"}' >/dev/null
admin_product_detail="$(call_api GET "/api/admin/products/${product_a_id}" "${admin_token}" "")"
product_a_status="$(extract_required "${admin_product_detail}" '.data.status' "productA.status")"
[[ "${product_a_status}" == "OFF_SHELF" ]] || fail "商品下架状态异常，expect=OFF_SHELF actual=${product_a_status}"

log "seller 创建并发布第二个商品（用于管理员订单取消）..."
product_b_payload="$(jq -nc \
  --arg title "Admin Smoke 商品B ${id_suffix}" \
  --arg description "管理端烟测商品B" \
  '{
    title:$title,
    description:$description,
    coverObjectKey:null,
    skus:[{skuName:"普通版",specJson:"{\"edition\":\"normal\"}",priceCent:1999,stock:15}]
  }')"
product_b_resp="$(call_api POST "/api/product/products" "${seller_token}" "${product_b_payload}")"
product_b_id="$(extract_required "${product_b_resp}" '.data.productId | tostring' "productB.id")"
call_api POST "/api/product/products/${product_b_id}/publish" "${seller_token}" "" >/dev/null
product_b_detail="$(call_api GET "/api/product/products/${product_b_id}" "" "")"
product_b_sku_id="$(extract_required "${product_b_detail}" '.data.skus[0].skuId | tostring' "productB.skuId")"

log "buyer 下单（UNPAID）并由 admin 取消..."
buyer_login_raw="$(login_user "${buyer_username}")"
buyer_token="${buyer_login_raw%%|*}"
order_payload="$(jq -nc --argjson productId "${product_b_id}" --argjson skuId "${product_b_sku_id}" \
  '{items:[{productId:$productId,skuId:$skuId,quantity:1}]}')"
idem_key="admin-smoke-${id_suffix}"
order_create_resp="$(call_api POST "/api/order/orders" "${buyer_token}" "${order_payload}" -H "Idempotency-Key: ${idem_key}")"
order_no="$(extract_required "${order_create_resp}" '.data.orderNo' "orderNo")"

admin_order_page="$(call_api GET "/api/admin/orders?page=1&size=10&orderNo=${order_no}" "${admin_token}" "")"
contains_order="$(echo "${admin_order_page}" | jq -r --arg orderNo "${order_no}" '.data.items | map(.orderNo==$orderNo) | any')"
[[ "${contains_order}" == "true" ]] || fail "管理员订单列表未包含目标订单 orderNo=${order_no}"

admin_cancel_resp="$(call_api POST "/api/admin/orders/${order_no}/cancel" "${admin_token}" '{"reason":"运营取消烟测"}')"
cancel_status="$(extract_required "${admin_cancel_resp}" '.data.status' "order.cancel.status")"
[[ "${cancel_status}" == "CANCELED" ]] || fail "管理员取消订单状态异常，expect=CANCELED actual=${cancel_status}"
buyer_order_detail="$(call_api GET "/api/order/orders/${order_no}" "${buyer_token}" "")"
buyer_order_status="$(extract_required "${buyer_order_detail}" '.data.status' "buyer.order.status")"
[[ "${buyer_order_status}" == "CANCELED" ]] || fail "买家订单状态异常，expect=CANCELED actual=${buyer_order_status}"

log "admin 执行用户启用/禁用与管理员角色授予/回收..."
disable_resp="$(call_api POST "/api/admin/users/${buyer_user_id}/status" "${admin_token}" '{"status":"DISABLED","reason":"烟测禁用"}')"
[[ "$(extract_required "${disable_resp}" '.data.status' "buyer.status.disabled")" == "DISABLED" ]] || fail "禁用用户失败"
enable_resp="$(call_api POST "/api/admin/users/${buyer_user_id}/status" "${admin_token}" '{"status":"ENABLED","reason":"烟测启用"}')"
[[ "$(extract_required "${enable_resp}" '.data.status' "buyer.status.enabled")" == "ENABLED" ]] || fail "启用用户失败"

grant_resp="$(call_api PUT "/api/admin/users/${seller_user_id}/admin-role" "${admin_token}" '{"grantAdmin":true,"reason":"烟测授予管理员"}')"
[[ "$(echo "${grant_resp}" | jq -r '.data.admin')" == "true" ]] || fail "授予管理员失败"
revoke_resp="$(call_api PUT "/api/admin/users/${seller_user_id}/admin-role" "${admin_token}" '{"grantAdmin":false,"reason":"烟测回收管理员"}')"
[[ "$(echo "${revoke_resp}" | jq -r '.data.admin')" == "false" ]] || fail "回收管理员失败"

log "校验角色列表与审计日志写入..."
roles_resp="$(call_api GET "/api/admin/roles" "${admin_token}" "")"
has_admin_role="$(echo "${roles_resp}" | jq -r '.data | map(.roleCode=="ROLE_ADMIN") | any')"
[[ "${has_admin_role}" == "true" ]] || fail "角色列表中未找到 ROLE_ADMIN"

user_audit_count="$(query_mysql_scalar "shiori_user" "SELECT COUNT(1) FROM u_admin_audit_log;")"
product_audit_count="$(query_mysql_scalar "shiori_product" "SELECT COUNT(1) FROM p_admin_audit_log;")"
order_audit_count="$(query_mysql_scalar "shiori_order" "SELECT COUNT(1) FROM o_admin_audit_log;")"
[[ "${user_audit_count}" =~ ^[0-9]+$ ]] || fail "用户审计计数异常: ${user_audit_count}"
[[ "${product_audit_count}" =~ ^[0-9]+$ ]] || fail "商品审计计数异常: ${product_audit_count}"
[[ "${order_audit_count}" =~ ^[0-9]+$ ]] || fail "订单审计计数异常: ${order_audit_count}"
(( user_audit_count >= 4 )) || fail "用户审计日志数量不足: ${user_audit_count}"
(( product_audit_count >= 1 )) || fail "商品审计日志数量不足: ${product_audit_count}"
(( order_audit_count >= 1 )) || fail "订单审计日志数量不足: ${order_audit_count}"

cost_seconds="$(( $(date +%s) - START_TS ))"
log "Admin 闭环烟测成功"
log "adminUserId=${admin_user_id}, sellerUserId=${seller_user_id}, buyerUserId=${buyer_user_id}, orderNo=${order_no}, duration=${cost_seconds}s"
