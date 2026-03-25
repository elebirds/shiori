#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PERF_DIR="${ROOT_DIR}/perf"
PERF_LOG_DIR="${PERF_LOG_DIR:-${ROOT_DIR}/ci-logs/perf/diagnose}"

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
NOTIFY_HTTP_BASE_URL="${NOTIFY_HTTP_BASE_URL:-http://localhost:8090}"
NOTIFY_WS_BASE_URL="${NOTIFY_WS_BASE_URL:-ws://localhost:8090/ws}"

PERF_PREFIX="${PERF_PREFIX:-oneclick}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.49.0}"

RUN_ORDER="${RUN_ORDER:-1}"
RUN_WS="${RUN_WS:-1}"

K6_ORDER_VUS="${K6_ORDER_VUS:-2}"
K6_ORDER_DURATION="${K6_ORDER_DURATION:-10s}"
K6_WS_VUS="${K6_WS_VUS:-2}"
K6_WS_ITERATIONS="${K6_WS_ITERATIONS:-10}"
K6_WS_TIMEOUT_MS="${K6_WS_TIMEOUT_MS:-10000}"
K6_WS_LATENCY_P95_MS="${K6_WS_LATENCY_P95_MS:-3000}"
K6_DEBUG_FAIL_SAMPLE="${K6_DEBUG_FAIL_SAMPLE:-1}"
K6_DEBUG_FAIL_LIMIT="${K6_DEBUG_FAIL_LIMIT:-200}"

CDK_AMOUNT_CENT="${CDK_AMOUNT_CENT:-1000000}"
CDK_ORDER_QUANTITY="${CDK_ORDER_QUANTITY:-20}"
CDK_WS_QUANTITY="${CDK_WS_QUANTITY:-20}"

log() {
  echo "[perf-oneclick] $*"
}

fail() {
  echo "[perf-oneclick][ERROR] $*" >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "缺少依赖命令: $1"
  fi
}

env_value() {
  local key="$1"
  local value
  value="$(grep -E "^${key}=" "${ROOT_DIR}/deploy/.env" | head -n 1 | cut -d= -f2-)"
  if [[ -z "${value}" ]]; then
    fail "deploy/.env 缺少配置: ${key}"
  fi
  echo "${value}"
}

wait_http_ready() {
  local name="$1"
  local url="$2"
  local timeout_seconds="${3:-120}"
  local elapsed=0
  while (( elapsed < timeout_seconds )); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      log "${name} 已就绪: ${url}"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  fail "${name} 就绪超时(${timeout_seconds}s): ${url}"
}

rewrite_localhost_for_docker() {
  local url="$1"
  if [[ "$(uname -s)" == "Darwin" ]]; then
    echo "${url}" | sed -E 's#^(https?|wss?)://(localhost|127\.0\.0\.1)#\1://host.docker.internal#'
    return
  fi
  echo "${url}"
}

create_admin_and_login() {
  local suffix
  suffix="$(date +%s)$RANDOM"
  local admin_user="perfadm${suffix}"
  local admin_pass="Perf${suffix}Aa"

  local register_payload
  register_payload="$(jq -nc --arg u "${admin_user}" --arg p "${admin_pass}" '{username:$u,password:$p,nickname:"Perf Admin"}')"
  local register_resp
  register_resp="$(curl -fsS -X POST "${GATEWAY_BASE_URL}/api/user/auth/register" -H 'Content-Type: application/json' -d "${register_payload}")"
  echo "${register_resp}" | jq -e '.code==0' >/dev/null || fail "创建 admin 账号失败: ${register_resp}"

  local mysql_user
  local mysql_pass
  mysql_user="$(env_value MYSQL_OPS_USERNAME)"
  mysql_pass="$(env_value MYSQL_OPS_PASSWORD)"
  docker exec -i shiori-mysql mysql -u"${mysql_user}" -p"${mysql_pass}" shiori_user -e "INSERT INTO u_user_role (user_id, role_id, created_at) SELECT u.id, r.id, CURRENT_TIMESTAMP(3) FROM u_user u JOIN u_role r ON r.role_code='ROLE_ADMIN' AND r.status=1 AND r.is_deleted=0 WHERE u.username='${admin_user}' AND u.is_deleted=0 ON DUPLICATE KEY UPDATE role_id=VALUES(role_id);" >/dev/null

  local login_payload
  login_payload="$(jq -nc --arg u "${admin_user}" --arg p "${admin_pass}" '{username:$u,password:$p}')"
  local login_resp
  login_resp="$(curl -fsS -X POST "${GATEWAY_BASE_URL}/api/user/auth/login" -H 'Content-Type: application/json' -d "${login_payload}")"
  local admin_token
  admin_token="$(echo "${login_resp}" | jq -r '.data.accessToken')"
  [[ -n "${admin_token}" && "${admin_token}" != "null" ]] || fail "admin 登录失败: ${login_resp}"

  echo "${admin_token}"
}

create_cdk_batch_csv() {
  local admin_token="$1"
  local quantity="$2"
  local amount_cent="$3"

  local resp
  resp="$(curl -fsS -X POST "${GATEWAY_BASE_URL}/api/v2/admin/payments/cdks/batches" \
    -H "Authorization: Bearer ${admin_token}" \
    -H 'Content-Type: application/json' \
    -d "{\"quantity\":${quantity},\"amountCent\":${amount_cent}}")"
  echo "${resp}" | jq -e '.code==0' >/dev/null || fail "创建 CDK 批次失败: ${resp}"
  echo "${resp}" | jq -r '.data.codes | map(.code) | join(",")'
}

run_k6_order() {
  local cdk_codes="$1"
  local order_log="${PERF_LOG_DIR}/k6-order-oneclick.log"
  local order_summary_rel="ci-logs/perf/diagnose/k6-order-oneclick-summary.json"
  local gateway_for_k6
  gateway_for_k6="$(rewrite_localhost_for_docker "${GATEWAY_BASE_URL}")"

  local -a docker_args=(--rm -i -v "${ROOT_DIR}:/work" -w /work/perf)
  if [[ "$(uname -s)" == "Linux" ]]; then
    docker_args+=(--network host)
  fi

  log "执行订单压测（2VU 诊断）..."
  local rc=0
  docker run "${docker_args[@]}" \
    -e PERF_GATEWAY_BASE_URL="${gateway_for_k6}" \
    -e PERF_PREFIX="${PERF_PREFIX}-order" \
    -e K6_ORDER_VUS="${K6_ORDER_VUS}" \
    -e K6_ORDER_DURATION="${K6_ORDER_DURATION}" \
    -e K6_ORDER_BUYER_CDKS="${cdk_codes}" \
    -e K6_DEBUG_FAIL_SAMPLE="${K6_DEBUG_FAIL_SAMPLE}" \
    -e K6_DEBUG_FAIL_LIMIT="${K6_DEBUG_FAIL_LIMIT}" \
    "${K6_IMAGE}" run k6-order.js \
    --summary-export "/work/${order_summary_rel}" > "${order_log}" 2>&1 || rc=$?

  log "订单压测日志: ${order_log}"
  log "订单失败码分布:"
  (grep -Eo 'code=[0-9]+' "${order_log}" | sort | uniq -c) || true
  log "订单阈值摘要:"
  (grep -E 'http_req_failed|shiori_perf_order_biz_failed_total|thresholds on metrics' "${order_log}") || true

  return "${rc}"
}

run_k6_ws() {
  local cdk_codes="$1"
  local ws_log="${PERF_LOG_DIR}/k6-ws-oneclick.log"
  local ws_summary_rel="ci-logs/perf/diagnose/k6-ws-oneclick-summary.json"
  local gateway_for_k6
  local notify_ws_for_k6
  local notify_http_for_k6
  gateway_for_k6="$(rewrite_localhost_for_docker "${GATEWAY_BASE_URL}")"
  notify_ws_for_k6="$(rewrite_localhost_for_docker "${NOTIFY_WS_BASE_URL}")"
  notify_http_for_k6="$(rewrite_localhost_for_docker "${NOTIFY_HTTP_BASE_URL}")"

  local -a docker_args=(--rm -i -v "${ROOT_DIR}:/work" -w /work/perf)
  if [[ "$(uname -s)" == "Linux" ]]; then
    docker_args+=(--network host)
  fi

  log "执行 WS 压测..."
  local rc=0
  docker run "${docker_args[@]}" \
    -e PERF_GATEWAY_BASE_URL="${gateway_for_k6}" \
    -e PERF_NOTIFY_WS_BASE_URL="${notify_ws_for_k6}" \
    -e PERF_NOTIFY_HTTP_BASE_URL="${notify_http_for_k6}" \
    -e PERF_PREFIX="${PERF_PREFIX}-ws" \
    -e K6_WS_VUS="${K6_WS_VUS}" \
    -e K6_WS_ITERATIONS="${K6_WS_ITERATIONS}" \
    -e K6_WS_TIMEOUT_MS="${K6_WS_TIMEOUT_MS}" \
    -e K6_WS_LATENCY_P95_MS="${K6_WS_LATENCY_P95_MS}" \
    -e K6_WS_BUYER_CDKS="${cdk_codes}" \
    "${K6_IMAGE}" run k6-ws.js \
    --summary-export "/work/${ws_summary_rel}" > "${ws_log}" 2>&1 || rc=$?

  log "WS 压测日志: ${ws_log}"
  log "WS 阈值摘要:"
  (grep -E 'http_req_failed|shiori_perf_ws_notification_latency_ms|shiori_perf_ws_timeout_total|shiori_perf_ws_biz_failed_total|thresholds on metrics' "${ws_log}") || true

  return "${rc}"
}

main() {
  require_command curl
  require_command jq
  require_command docker

  [[ -f "${PERF_DIR}/k6-order.js" ]] || fail "未找到 ${PERF_DIR}/k6-order.js"
  [[ -f "${PERF_DIR}/k6-ws.js" ]] || fail "未找到 ${PERF_DIR}/k6-ws.js"

  mkdir -p "${PERF_LOG_DIR}"

  wait_http_ready "gateway" "${GATEWAY_BASE_URL}/actuator/health" 180
  if [[ "${RUN_WS}" == "1" ]]; then
    wait_http_ready "notify" "${NOTIFY_HTTP_BASE_URL}/healthz" 180
  fi

  local admin_token
  admin_token="$(create_admin_and_login)"
  log "临时 admin 准备完成"

  local order_codes=""
  local ws_codes=""
  if [[ "${RUN_ORDER}" == "1" ]]; then
    order_codes="$(create_cdk_batch_csv "${admin_token}" "${CDK_ORDER_QUANTITY}" "${CDK_AMOUNT_CENT}")"
    log "订单压测 CDK 批次已生成（${CDK_ORDER_QUANTITY}个）"
  fi
  if [[ "${RUN_WS}" == "1" ]]; then
    ws_codes="$(create_cdk_batch_csv "${admin_token}" "${CDK_WS_QUANTITY}" "${CDK_AMOUNT_CENT}")"
    log "WS 压测 CDK 批次已生成（${CDK_WS_QUANTITY}个）"
  fi

  local order_rc=0
  local ws_rc=0

  if [[ "${RUN_ORDER}" == "1" ]]; then
    run_k6_order "${order_codes}" || order_rc=$?
  fi
  if [[ "${RUN_WS}" == "1" ]]; then
    run_k6_ws "${ws_codes}" || ws_rc=$?
  fi

  log "完成，日志目录: ${PERF_LOG_DIR}"
  if (( order_rc != 0 || ws_rc != 0 )); then
    fail "压测已执行，但存在阈值失败（order_rc=${order_rc}, ws_rc=${ws_rc}）"
  fi
}

main "$@"
