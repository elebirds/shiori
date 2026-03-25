#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "[test-perf-order-scripts][ERROR] $*" >&2
  exit 1
}

assert_file() {
  local path="$1"
  [[ -f "${ROOT_DIR}/${path}" ]] || fail "缺少文件: ${path}"
}

assert_contains() {
  local path="$1"
  local pattern="$2"
  if ! rg -n --fixed-strings "${pattern}" "${ROOT_DIR}/${path}" >/dev/null 2>&1; then
    fail "文件 ${path} 未包含预期内容: ${pattern}"
  fi
}

assert_not_contains() {
  local path="$1"
  local pattern="$2"
  if rg -n --fixed-strings "${pattern}" "${ROOT_DIR}/${path}" >/dev/null 2>&1; then
    fail "文件 ${path} 不应再包含内容: ${pattern}"
  fi
}

main() {
  command -v rg >/dev/null 2>&1 || fail "缺少依赖命令: rg"

  assert_file "perf/k6-order-hotspot.js"
  assert_file "perf/k6-order-realistic.js"
  assert_file "scripts/ci/run_perf_order_hotspot.sh"
  assert_file "scripts/ci/run_perf_order_realistic.sh"

  assert_contains "scripts/ci/run_perf_oneclick.sh" "k6-order-hotspot.js"
  assert_contains "scripts/ci/run_perf_baseline.sh" "k6-order-hotspot.js"

  assert_contains "perf/README.md" "k6-order-hotspot.js"
  assert_contains "perf/README.md" "k6-order-realistic.js"
  assert_contains "perf/README.md" "run_perf_order_hotspot.sh"
  assert_contains "perf/README.md" "run_perf_order_realistic.sh"
  assert_not_contains "perf/README.md" "k6-order.js"
}

main "$@"
