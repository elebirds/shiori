#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ACTION="${1:-drill}"
RELEASE_TAG="${RELEASE_TAG:-}"
ROLLBACK_TO_TAG="${ROLLBACK_TO_TAG:-}"
LOG_DIR="${ROOT_DIR}/ci-logs/release"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
DRILL_LOG="${LOG_DIR}/drill-${TIMESTAMP}.log"

log() {
  echo "[release-drill] $*"
}

fail() {
  echo "[release-drill][ERROR] $*" >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "缺少依赖命令: $1"
  fi
}

run_drill() {
  require_command git
  require_command bash

  mkdir -p "${LOG_DIR}"

  if [[ "${SKIP_GIT_CLEAN_CHECK:-0}" != "1" ]]; then
    if [[ -n "$(git -C "${ROOT_DIR}" status --porcelain)" ]]; then
      fail "工作区存在未提交改动。可先提交，或设置 SKIP_GIT_CLEAN_CHECK=1 跳过。"
    fi
  fi

  log "开始发布演练（阻塞基线 + 非阻塞 perf 观察）"
  {
    echo "== release drill start: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "== root: ${ROOT_DIR}"
    echo "== release tag: ${RELEASE_TAG:-unset}"

    echo "-- step1: java tests"
    (cd "${ROOT_DIR}/shiori-java" && ./gradlew clean test --no-daemon)

    echo "-- step2: local regression (blocking)"
    (cd "${ROOT_DIR}" && \
      SKIP_APP_PLAYWRIGHT="${SKIP_APP_PLAYWRIGHT:-1}" \
      SKIP_ADMIN_PLAYWRIGHT="${SKIP_ADMIN_PLAYWRIGHT:-1}" \
      RUN_PERF_BASELINE=0 \
      bash scripts/ci/run_e2e_ci.sh)

    echo "-- step3: perf stress (non-blocking)"
    if ! (cd "${ROOT_DIR}" && \
      SKIP_APP_PLAYWRIGHT=1 \
      SKIP_ADMIN_PLAYWRIGHT=1 \
      RUN_PERF_BASELINE=1 \
      PERF_MODE=perf-stress \
      bash scripts/ci/run_e2e_ci.sh); then
      echo "WARN: perf stress failed, but drill keeps going (non-blocking)."
    fi

    if [[ -n "${RELEASE_TAG}" ]]; then
      local drill_tag
      drill_tag="${RELEASE_TAG}-drill-${TIMESTAMP}"
      echo "-- step4: create local drill tag: ${drill_tag}"
      git -C "${ROOT_DIR}" tag -a "${drill_tag}" -m "release drill for ${RELEASE_TAG} at ${TIMESTAMP}"
      echo "drillTag=${drill_tag}"
    fi

    echo "== release drill success: $(date '+%Y-%m-%d %H:%M:%S')"
  } | tee "${DRILL_LOG}"

  log "演练完成，日志: ${DRILL_LOG}"
  log "如需演练失败回滚，请执行: ROLLBACK_TO_TAG=<稳定版本tag> bash scripts/release/release_drill.sh rollback"
}

run_rollback() {
  require_command git

  if [[ -z "${ROLLBACK_TO_TAG}" ]]; then
    fail "请设置 ROLLBACK_TO_TAG，例如: ROLLBACK_TO_TAG=v0.2.0"
  fi

  log "开始回滚演练，目标版本: ${ROLLBACK_TO_TAG}"
  git -C "${ROOT_DIR}" fetch --tags >/dev/null 2>&1 || true
  git -C "${ROOT_DIR}" rev-parse "${ROLLBACK_TO_TAG}" >/dev/null 2>&1 \
    || fail "目标 tag 不存在: ${ROLLBACK_TO_TAG}"

  cat <<STEP
[release-drill] 回滚步骤（演练）
1. 将部署版本切换到 tag: ${ROLLBACK_TO_TAG}
2. 重新执行 smoke：
   - bash scripts/smoke/e2e_trade_notify.sh
   - bash scripts/smoke/e2e_chat_notify.sh
   - bash scripts/smoke/e2e_admin_console.sh
3. 若当前发布 tag 已创建但未生效，可删除远端 tag（按需执行）：
   - git push origin :refs/tags/<failed-tag>
4. 若网关治理策略导致误伤，可先临时回退：
   - security.rate-limit.enabled=false
   - security.gateway-sign.replay-protection-enabled=false
STEP

  log "回滚演练说明已输出。"
}

case "${ACTION}" in
  drill)
    run_drill
    ;;
  rollback)
    run_rollback
    ;;
  *)
    fail "未知动作: ${ACTION}，支持 drill|rollback"
    ;;
esac
