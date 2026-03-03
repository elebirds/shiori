#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PERF_DIR="${ROOT_DIR}/perf"
PERF_LOG_DIR="${PERF_LOG_DIR:-${ROOT_DIR}/ci-logs/perf}"

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
NOTIFY_WS_BASE_URL="${NOTIFY_WS_BASE_URL:-ws://localhost:8090/ws}"
PERF_NOTIFY_HTTP_BASE_URL="${PERF_NOTIFY_HTTP_BASE_URL:-}"
K6_GATEWAY_BASE_URL="${K6_GATEWAY_BASE_URL:-}"
K6_NOTIFY_WS_BASE_URL="${K6_NOTIFY_WS_BASE_URL:-}"
K6_NOTIFY_HTTP_BASE_URL="${K6_NOTIFY_HTTP_BASE_URL:-}"
PERF_PREFIX="${PERF_PREFIX:-ci-perf}"

K6_ORDER_VUS="${K6_ORDER_VUS:-1}"
K6_ORDER_DURATION="${K6_ORDER_DURATION:-45s}"
K6_WS_VUS="${K6_WS_VUS:-1}"
K6_WS_ITERATIONS="${K6_WS_ITERATIONS:-10}"
K6_WS_TIMEOUT_MS="${K6_WS_TIMEOUT_MS:-10000}"
K6_WS_LATENCY_P95_MS="${K6_WS_LATENCY_P95_MS:-3000}"
K6_DEBUG_FAIL_SAMPLE="${K6_DEBUG_FAIL_SAMPLE:-0}"
K6_DEBUG_FAIL_LIMIT="${K6_DEBUG_FAIL_LIMIT:-20}"

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

is_dockerized_k6_on_macos() {
  if [[ "$(uname -s)" != "Darwin" ]]; then
    return 1
  fi

  local k6_bin
  k6_bin="$(command -v k6 || true)"
  if [[ -z "${k6_bin}" || ! -f "${k6_bin}" ]]; then
    return 1
  fi

  if grep -q "docker run" "${k6_bin}" 2>/dev/null; then
    return 0
  fi
  return 1
}

rewrite_localhost_to_docker_host() {
  local url="$1"
  echo "${url}" | sed -E 's#^(https?|wss?)://(localhost|127\.0\.0\.1)#\1://host.docker.internal#'
}

summarize_order() {
  local summary_file="$1"
  jq -r '
    def p95(name):
      (.metrics[name].values["p(95)"] // .metrics[name]["p(95)"] // "n/a");
    def rate(name):
      (.metrics[name].values.rate // .metrics[name].value // "n/a");
    "order_create_p95_ms=\(p95("shiori_perf_order_create_duration_ms"))",
    "order_pay_p95_ms=\(p95("shiori_perf_order_pay_duration_ms"))",
    "order_detail_p95_ms=\(p95("shiori_perf_order_detail_duration_ms"))",
    "http_failed_rate=\(rate("http_req_failed"))"
  ' "${summary_file}"
}

summarize_ws() {
  local summary_file="$1"
  jq -r '
    def p95(name):
      (.metrics[name].values["p(95)"] // .metrics[name]["p(95)"] // "n/a");
    def count(name):
      (.metrics[name].values.count // .metrics[name].count // "n/a");
    def rate(name):
      (.metrics[name].values.rate // .metrics[name].value // "n/a");
    "ws_notify_p95_ms=\(p95("shiori_perf_ws_notification_latency_ms"))",
    "ws_timeout_count=\(count("shiori_perf_ws_timeout_total"))",
    "http_failed_rate=\(rate("http_req_failed"))"
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
  if [[ -z "${K6_GATEWAY_BASE_URL}" ]]; then
    K6_GATEWAY_BASE_URL="${GATEWAY_BASE_URL}"
  fi
  if [[ -z "${K6_NOTIFY_WS_BASE_URL}" ]]; then
    K6_NOTIFY_WS_BASE_URL="${NOTIFY_WS_BASE_URL}"
  fi
  if [[ -z "${K6_NOTIFY_HTTP_BASE_URL}" ]]; then
    K6_NOTIFY_HTTP_BASE_URL="${PERF_NOTIFY_HTTP_BASE_URL}"
  fi
  if is_dockerized_k6_on_macos; then
    K6_GATEWAY_BASE_URL="$(rewrite_localhost_to_docker_host "${K6_GATEWAY_BASE_URL}")"
    K6_NOTIFY_WS_BASE_URL="$(rewrite_localhost_to_docker_host "${K6_NOTIFY_WS_BASE_URL}")"
    K6_NOTIFY_HTTP_BASE_URL="$(rewrite_localhost_to_docker_host "${K6_NOTIFY_HTTP_BASE_URL}")"
  fi

  mkdir -p "${PERF_LOG_DIR}"
  log "性能结果目录: ${PERF_LOG_DIR}"

  wait_http_ready "gateway" "${GATEWAY_BASE_URL}/actuator/health" 180
  wait_http_ready "notify" "${PERF_NOTIFY_HTTP_BASE_URL}/healthz" 180

  log "执行订单链路基线..."
  k6 run "${PERF_DIR}/k6-order.js" \
    -e PERF_GATEWAY_BASE_URL="${K6_GATEWAY_BASE_URL}" \
    -e PERF_PREFIX="${PERF_PREFIX}-order" \
    -e K6_ORDER_VUS="${K6_ORDER_VUS}" \
    -e K6_ORDER_DURATION="${K6_ORDER_DURATION}" \
    -e K6_DEBUG_FAIL_SAMPLE="${K6_DEBUG_FAIL_SAMPLE}" \
    -e K6_DEBUG_FAIL_LIMIT="${K6_DEBUG_FAIL_LIMIT}" \
    --summary-export "${PERF_LOG_DIR}/k6-order-summary.json" \
    > "${PERF_LOG_DIR}/k6-order.log" 2>&1

  log "执行 WS 通知基线..."
  k6 run "${PERF_DIR}/k6-ws.js" \
    -e PERF_GATEWAY_BASE_URL="${K6_GATEWAY_BASE_URL}" \
    -e PERF_NOTIFY_WS_BASE_URL="${K6_NOTIFY_WS_BASE_URL}" \
    -e PERF_NOTIFY_HTTP_BASE_URL="${K6_NOTIFY_HTTP_BASE_URL}" \
    -e PERF_PREFIX="${PERF_PREFIX}-ws" \
    -e K6_WS_VUS="${K6_WS_VUS}" \
    -e K6_WS_ITERATIONS="${K6_WS_ITERATIONS}" \
    -e K6_WS_TIMEOUT_MS="${K6_WS_TIMEOUT_MS}" \
    -e K6_WS_LATENCY_P95_MS="${K6_WS_LATENCY_P95_MS}" \
    --summary-export "${PERF_LOG_DIR}/k6-ws-summary.json" \
    > "${PERF_LOG_DIR}/k6-ws.log" 2>&1

  log "性能摘要（order）:"
  summarize_order "${PERF_LOG_DIR}/k6-order-summary.json"
  log "性能摘要（ws）:"
  summarize_ws "${PERF_LOG_DIR}/k6-ws-summary.json"
  log "性能基线执行完成"
}

main "$@"
