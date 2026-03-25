#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export RUN_ORDER="${RUN_ORDER:-1}"
export RUN_WS="${RUN_WS:-0}"
export PERF_PREFIX="${PERF_PREFIX:-order-realistic}"
export PERF_LOG_DIR="${PERF_LOG_DIR:-${ROOT_DIR}/ci-logs/perf/order-realistic}"
export K6_ORDER_SCRIPT="${K6_ORDER_SCRIPT:-k6-order-realistic.js}"
export K6_ORDER_LOG_BASENAME="${K6_ORDER_LOG_BASENAME:-k6-order-realistic}"
export K6_ORDER_REAL_BUYERS="${K6_ORDER_REAL_BUYERS:-80}"
export K6_ORDER_REAL_SELLERS="${K6_ORDER_REAL_SELLERS:-20}"
export K6_ORDER_REAL_PRODUCTS_PER_SELLER="${K6_ORDER_REAL_PRODUCTS_PER_SELLER:-2}"
export CDK_ORDER_QUANTITY="${CDK_ORDER_QUANTITY:-${K6_ORDER_REAL_BUYERS}}"

exec "${ROOT_DIR}/scripts/ci/run_perf_oneclick.sh"
