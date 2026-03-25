#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export RUN_ORDER="${RUN_ORDER:-1}"
export RUN_WS="${RUN_WS:-0}"
export PERF_PREFIX="${PERF_PREFIX:-order-hotspot}"
export PERF_LOG_DIR="${PERF_LOG_DIR:-${ROOT_DIR}/ci-logs/perf/order-hotspot}"
export K6_ORDER_SCRIPT="${K6_ORDER_SCRIPT:-k6-order-hotspot.js}"
export K6_ORDER_LOG_BASENAME="${K6_ORDER_LOG_BASENAME:-k6-order-hotspot}"

exec "${ROOT_DIR}/scripts/ci/run_perf_oneclick.sh"
