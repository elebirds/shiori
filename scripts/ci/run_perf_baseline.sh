#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PERF_DIR="${ROOT_DIR}/perf"
PERF_LOG_DIR="${PERF_LOG_DIR:-${ROOT_DIR}/ci-logs/perf}"

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
NOTIFY_WS_BASE_URL="${NOTIFY_WS_BASE_URL:-ws://localhost:8090/ws}"
PERF_NOTIFY_HTTP_BASE_URL="${PERF_NOTIFY_HTTP_BASE_URL:-}"
PERF_PREFIX="${PERF_PREFIX:-ci-perf}"

K6_ORDER_VUS="${K6_ORDER_VUS:-5}"
K6_ORDER_DURATION="${K6_ORDER_DURATION:-45s}"
K6_WS_VUS="${K6_WS_VUS:-2}"
K6_WS_ITERATIONS="${K6_WS_ITERATIONS:-10}"
K6_WS_TIMEOUT_MS="${K6_WS_TIMEOUT_MS:-10000}"

log() {
  echo "[perf] $*"
}

fail() {
  echo "[perf][ERROR] $*" >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "缺少依赖命令: $1"
  fi
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

summarize_order() {
  local summary_file="$1"
  jq -r '
    "order_create_p95_ms=\(.metrics.shiori_perf_order_create_duration_ms.values["p(95)"] // "n/a")",
    "order_pay_p95_ms=\(.metrics.shiori_perf_order_pay_duration_ms.values["p(95)"] // "n/a")",
    "order_detail_p95_ms=\(.metrics.shiori_perf_order_detail_duration_ms.values["p(95)"] // "n/a")",
    "http_failed_rate=\(.metrics.http_req_failed.values.rate // "n/a")"
  ' "${summary_file}"
}

summarize_ws() {
  local summary_file="$1"
  jq -r '
    "ws_notify_p95_ms=\(.metrics.shiori_perf_ws_notification_latency_ms.values["p(95)"] // "n/a")",
    "ws_timeout_count=\(.metrics.shiori_perf_ws_timeout_total.values.count // "n/a")",
    "http_failed_rate=\(.metrics.http_req_failed.values.rate // "n/a")"
  ' "${summary_file}"
}

main() {
  require_command curl
  require_command jq
  require_command k6

  [[ -f "${PERF_DIR}/k6-order.js" ]] || fail "未找到 k6-order.js"
  [[ -f "${PERF_DIR}/k6-ws.js" ]] || fail "未找到 k6-ws.js"

  if [[ -z "${PERF_NOTIFY_HTTP_BASE_URL}" ]]; then
    PERF_NOTIFY_HTTP_BASE_URL="${NOTIFY_WS_BASE_URL/ws:\/\//http://}"
    PERF_NOTIFY_HTTP_BASE_URL="${PERF_NOTIFY_HTTP_BASE_URL/wss:\/\//https://}"
    PERF_NOTIFY_HTTP_BASE_URL="${PERF_NOTIFY_HTTP_BASE_URL%/ws}"
  fi

  mkdir -p "${PERF_LOG_DIR}"
  log "性能结果目录: ${PERF_LOG_DIR}"

  wait_http_ready "gateway" "${GATEWAY_BASE_URL}/actuator/health" 180
  wait_http_ready "notify" "${PERF_NOTIFY_HTTP_BASE_URL}/healthz" 180

  log "执行订单链路基线..."
  k6 run "${PERF_DIR}/k6-order.js" \
    -e PERF_GATEWAY_BASE_URL="${GATEWAY_BASE_URL}" \
    -e PERF_PREFIX="${PERF_PREFIX}-order" \
    -e K6_ORDER_VUS="${K6_ORDER_VUS}" \
    -e K6_ORDER_DURATION="${K6_ORDER_DURATION}" \
    --summary-export "${PERF_LOG_DIR}/k6-order-summary.json" \
    > "${PERF_LOG_DIR}/k6-order.log" 2>&1

  log "执行 WS 通知基线..."
  k6 run "${PERF_DIR}/k6-ws.js" \
    -e PERF_GATEWAY_BASE_URL="${GATEWAY_BASE_URL}" \
    -e PERF_NOTIFY_WS_BASE_URL="${NOTIFY_WS_BASE_URL}" \
    -e PERF_NOTIFY_HTTP_BASE_URL="${PERF_NOTIFY_HTTP_BASE_URL}" \
    -e PERF_PREFIX="${PERF_PREFIX}-ws" \
    -e K6_WS_VUS="${K6_WS_VUS}" \
    -e K6_WS_ITERATIONS="${K6_WS_ITERATIONS}" \
    -e K6_WS_TIMEOUT_MS="${K6_WS_TIMEOUT_MS}" \
    --summary-export "${PERF_LOG_DIR}/k6-ws-summary.json" \
    > "${PERF_LOG_DIR}/k6-ws.log" 2>&1

  log "性能摘要（order）:"
  summarize_order "${PERF_LOG_DIR}/k6-order-summary.json"
  log "性能摘要（ws）:"
  summarize_ws "${PERF_LOG_DIR}/k6-ws-summary.json"
  log "性能基线执行完成"
}

main "$@"
