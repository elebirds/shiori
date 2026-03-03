#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_DIR="${ROOT_DIR}/deploy"
JAVA_DIR="${ROOT_DIR}/shiori-java"
NOTIFY_DIR="${ROOT_DIR}/shiori-notify"
APP_DIR="${ROOT_DIR}/shiori-app"
ADMIN_WEB_DIR="${ROOT_DIR}/shiori-admin-web"
SMOKE_SCRIPT="${ROOT_DIR}/scripts/smoke/e2e_trade_notify.sh"
ADMIN_SMOKE_SCRIPT="${ROOT_DIR}/scripts/smoke/e2e_admin_console.sh"
PERF_BASELINE_SCRIPT="${ROOT_DIR}/scripts/ci/run_perf_baseline.sh"
CI_LOG_DIR="${ROOT_DIR}/ci-logs"
SERVICE_READY_TIMEOUT_SECONDS="${SERVICE_READY_TIMEOUT_SECONDS:-300}"
REDIS_LOCAL_PORT="${REDIS_HOST_PORT:-6379}"
RUN_PERF_BASELINE="${RUN_PERF_BASELINE:-0}"
SKIP_APP_PLAYWRIGHT="${SKIP_APP_PLAYWRIGHT:-0}"
SKIP_ADMIN_PLAYWRIGHT="${SKIP_ADMIN_PLAYWRIGHT:-0}"

SERVICE_NAMES=()
SERVICE_PIDS=()

log() {
  echo "[ci-e2e] $*"
}

dump_tail() {
  local file="$1"
  if [[ -f "${file}" ]]; then
    echo "----- tail -n 120 ${file} -----"
    tail -n 120 "${file}" || true
  fi
}

print_key_logs() {
  echo "[ci-e2e] 输出关键日志片段..."
  dump_tail "${CI_LOG_DIR}/user-service.log"
  dump_tail "${CI_LOG_DIR}/product-service.log"
  dump_tail "${CI_LOG_DIR}/order-service.log"
  dump_tail "${CI_LOG_DIR}/gateway-service.log"
  dump_tail "${CI_LOG_DIR}/notify.log"
  dump_tail "${CI_LOG_DIR}/admin-smoke.log"
  dump_tail "${CI_LOG_DIR}/perf-baseline.log"
  dump_tail "${CI_LOG_DIR}/perf/k6-order.log"
  dump_tail "${CI_LOG_DIR}/perf/k6-ws.log"
  dump_tail "${CI_LOG_DIR}/app-e2e.log"
  dump_tail "${CI_LOG_DIR}/admin-web-e2e.log"

  if docker ps -a --format '{{.Names}}' | grep -q '^shiori-nacos-config-init$'; then
    echo "----- docker logs shiori-nacos-config-init -----"
    docker logs shiori-nacos-config-init || true
  fi
  if docker ps -a --format '{{.Names}}' | grep -q '^shiori-rabbitmq-auth-init$'; then
    echo "----- docker logs shiori-rabbitmq-auth-init -----"
    docker logs shiori-rabbitmq-auth-init || true
  fi

  echo "----- docker compose ps -----"
  docker compose -f "${DEPLOY_DIR}/docker-compose.yml" ps || true
}

fail() {
  echo "[ci-e2e][ERROR] $*" >&2
  print_key_logs
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "缺少依赖命令: $1"
  fi
}

require_env() {
  local var_name="$1"
  if [[ -z "${!var_name:-}" ]]; then
    fail "缺少必填环境变量: ${var_name}"
  fi
}

add_service_pid() {
  SERVICE_NAMES+=("$1")
  SERVICE_PIDS+=("$2")
}

assert_services_alive() {
  local i
  for (( i = 0; i < ${#SERVICE_PIDS[@]}; i++ )); do
    if ! kill -0 "${SERVICE_PIDS[$i]}" 2>/dev/null; then
      dump_tail "${CI_LOG_DIR}/${SERVICE_NAMES[$i]}.log"
      fail "服务进程异常退出: ${SERVICE_NAMES[$i]}(pid=${SERVICE_PIDS[$i]})"
    fi
  done
}

cleanup() {
  local i
  log "开始清理后台服务与容器..."
  for (( i = 0; i < ${#SERVICE_PIDS[@]}; i++ )); do
    if kill -0 "${SERVICE_PIDS[$i]}" 2>/dev/null; then
      kill "${SERVICE_PIDS[$i]}" 2>/dev/null || true
      wait "${SERVICE_PIDS[$i]}" 2>/dev/null || true
    fi
  done
  docker compose -f "${DEPLOY_DIR}/docker-compose.yml" down -v >"${CI_LOG_DIR}/docker-compose-down.log" 2>&1 || true
}
trap cleanup EXIT

wait_nacos_init() {
  local timeout_seconds=240
  local elapsed=0
  local status=""
  local exit_code=""

  log "等待 nacos-config-init 执行完成..."
  while (( elapsed < timeout_seconds )); do
    if docker inspect shiori-nacos-config-init >/dev/null 2>&1; then
      status="$(docker inspect -f '{{.State.Status}}' shiori-nacos-config-init 2>/dev/null || true)"
      exit_code="$(docker inspect -f '{{.State.ExitCode}}' shiori-nacos-config-init 2>/dev/null || true)"

      if [[ "${status}" == "exited" && "${exit_code}" == "0" ]]; then
        docker logs shiori-nacos-config-init >"${CI_LOG_DIR}/nacos-config-init.log" 2>&1 || true
        log "nacos-config-init 成功退出"
        return 0
      fi

      if [[ "${status}" == "exited" && "${exit_code}" != "0" ]]; then
        docker logs shiori-nacos-config-init >"${CI_LOG_DIR}/nacos-config-init.log" 2>&1 || true
        fail "nacos-config-init 执行失败，exitCode=${exit_code}"
      fi
    fi

    sleep 2
    elapsed=$((elapsed + 2))
  done

  docker logs shiori-nacos-config-init >"${CI_LOG_DIR}/nacos-config-init.log" 2>&1 || true
  fail "等待 nacos-config-init 超时(${timeout_seconds}s)"
}

wait_rabbitmq_auth_init() {
  local timeout_seconds=180
  local elapsed=0
  local status=""
  local exit_code=""

  log "等待 rabbitmq-auth-init 执行完成..."
  while (( elapsed < timeout_seconds )); do
    if docker inspect shiori-rabbitmq-auth-init >/dev/null 2>&1; then
      status="$(docker inspect -f '{{.State.Status}}' shiori-rabbitmq-auth-init 2>/dev/null || true)"
      exit_code="$(docker inspect -f '{{.State.ExitCode}}' shiori-rabbitmq-auth-init 2>/dev/null || true)"

      if [[ "${status}" == "exited" && "${exit_code}" == "0" ]]; then
        docker logs shiori-rabbitmq-auth-init >"${CI_LOG_DIR}/rabbitmq-auth-init.log" 2>&1 || true
        log "rabbitmq-auth-init 成功退出"
        return 0
      fi

      if [[ "${status}" == "exited" && "${exit_code}" != "0" ]]; then
        docker logs shiori-rabbitmq-auth-init >"${CI_LOG_DIR}/rabbitmq-auth-init.log" 2>&1 || true
        fail "rabbitmq-auth-init 执行失败，exitCode=${exit_code}"
      fi
    fi

    sleep 2
    elapsed=$((elapsed + 2))
  done

  docker logs shiori-rabbitmq-auth-init >"${CI_LOG_DIR}/rabbitmq-auth-init.log" 2>&1 || true
  fail "等待 rabbitmq-auth-init 超时(${timeout_seconds}s)"
}

assert_container_running() {
  local container_name="$1"
  local status
  status="$(docker inspect -f '{{.State.Status}}' "${container_name}" 2>/dev/null || true)"
  if [[ "${status}" != "running" ]]; then
    fail "容器未运行: ${container_name}, status=${status:-unknown}"
  fi
}

wait_http_ready() {
  local service_name="$1"
  local url="$2"
  local timeout_seconds="${3:-120}"
  local elapsed=0

  log "等待 ${service_name} 就绪: ${url}"
  while (( elapsed < timeout_seconds )); do
    assert_services_alive
    if curl -fsS "${url}" >/dev/null 2>&1; then
      log "${service_name} 已就绪"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done

  fail "${service_name} 就绪超时(${timeout_seconds}s): ${url}"
}

start_user_service() {
  (
    cd "${JAVA_DIR}"
    NACOS_ADDR=localhost:8848 \
    NACOS_CONFIG_GROUP="${NACOS_CONFIG_GROUP}" \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/shiori_user?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" \
    SPRING_DATASOURCE_USERNAME="${USER_DB_USERNAME}" \
    SPRING_DATASOURCE_PASSWORD="${USER_DB_PASSWORD}" \
    SPRING_DATA_REDIS_HOST=localhost \
    SPRING_DATA_REDIS_PORT="${REDIS_LOCAL_PORT}" \
    NACOS_USERNAME="${NACOS_IMPORT_USERNAME}" \
    NACOS_PASSWORD="${NACOS_IMPORT_PASSWORD}" \
    ./gradlew :shiori-user-service:bootRun --no-daemon
  ) >"${CI_LOG_DIR}/user-service.log" 2>&1 &
  add_service_pid "user-service" "$!"
}

start_product_service() {
  (
    cd "${JAVA_DIR}"
    NACOS_ADDR=localhost:8848 \
    NACOS_CONFIG_GROUP="${NACOS_CONFIG_GROUP}" \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/shiori_product?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" \
    SPRING_DATASOURCE_USERNAME="${PRODUCT_DB_USERNAME}" \
    SPRING_DATASOURCE_PASSWORD="${PRODUCT_DB_PASSWORD}" \
    SPRING_DATA_REDIS_HOST=localhost \
    SPRING_DATA_REDIS_PORT="${REDIS_LOCAL_PORT}" \
    STORAGE_OSS_ENDPOINT=http://localhost:9000 \
    NACOS_USERNAME="${NACOS_IMPORT_USERNAME}" \
    NACOS_PASSWORD="${NACOS_IMPORT_PASSWORD}" \
    ./gradlew :shiori-product-service:bootRun --no-daemon
  ) >"${CI_LOG_DIR}/product-service.log" 2>&1 &
  add_service_pid "product-service" "$!"
}

start_order_service() {
  (
    cd "${JAVA_DIR}"
    NACOS_ADDR=localhost:8848 \
    NACOS_CONFIG_GROUP="${NACOS_CONFIG_GROUP}" \
    SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/shiori_order?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" \
    SPRING_DATASOURCE_USERNAME="${ORDER_DB_USERNAME}" \
    SPRING_DATASOURCE_PASSWORD="${ORDER_DB_PASSWORD}" \
    SPRING_RABBITMQ_HOST=localhost \
    SPRING_RABBITMQ_PORT=5672 \
    SPRING_RABBITMQ_USERNAME="${ORDER_RMQ_USERNAME}" \
    SPRING_RABBITMQ_PASSWORD="${ORDER_RMQ_PASSWORD}" \
    SPRING_DATA_REDIS_HOST=localhost \
    SPRING_DATA_REDIS_PORT="${REDIS_LOCAL_PORT}" \
    ORDER_PRODUCT_SERVICE_BASE_URL=http://shiori-product-service \
    NACOS_USERNAME="${NACOS_IMPORT_USERNAME}" \
    NACOS_PASSWORD="${NACOS_IMPORT_PASSWORD}" \
    ./gradlew :shiori-order-service:bootRun --no-daemon
  ) >"${CI_LOG_DIR}/order-service.log" 2>&1 &
  add_service_pid "order-service" "$!"
}

start_gateway_service() {
  (
    cd "${JAVA_DIR}"
    NACOS_ADDR=localhost:8848 \
    NACOS_CONFIG_GROUP="${NACOS_CONFIG_GROUP}" \
    SPRING_DATA_REDIS_HOST=localhost \
    SPRING_DATA_REDIS_PORT="${REDIS_LOCAL_PORT}" \
    NACOS_USERNAME="${NACOS_IMPORT_USERNAME}" \
    NACOS_PASSWORD="${NACOS_IMPORT_PASSWORD}" \
    ./gradlew :shiori-gateway-service:bootRun --no-daemon
  ) >"${CI_LOG_DIR}/gateway-service.log" 2>&1 &
  add_service_pid "gateway-service" "$!"
}

start_notify_service() {
  (
    cd "${NOTIFY_DIR}"
    NOTIFY_HTTP_ADDR=:8090 \
    RABBITMQ_ADDR="amqp://${NOTIFY_RMQ_USERNAME}:${NOTIFY_RMQ_PASSWORD}@localhost:5672/" \
    go run .
  ) >"${CI_LOG_DIR}/notify.log" 2>&1 &
  add_service_pid "notify" "$!"
}

main() {
  require_command docker
  require_command jq
  require_command curl
  require_command go
  if [[ "${SKIP_APP_PLAYWRIGHT}" != "1" || "${SKIP_ADMIN_PLAYWRIGHT}" != "1" ]]; then
    require_command node
    require_command pnpm
  fi
  if [[ "${RUN_PERF_BASELINE}" == "1" ]]; then
    require_command k6
  fi

  export SHIORI_ENV="${SHIORI_ENV:-test}"
  export NACOS_CONFIG_GROUP="${NACOS_CONFIG_GROUP:-SHIORI_TEST}"

  require_env MYSQL_ROOT_PASSWORD
  require_env MYSQL_OPS_USERNAME
  require_env MYSQL_OPS_PASSWORD
  require_env RABBITMQ_DEFAULT_USER
  require_env RABBITMQ_DEFAULT_PASS
  require_env ORDER_RMQ_USERNAME
  require_env ORDER_RMQ_PASSWORD
  require_env NOTIFY_RMQ_USERNAME
  require_env NOTIFY_RMQ_PASSWORD
  require_env MINIO_ROOT_USER
  require_env MINIO_ROOT_PASSWORD
  require_env MINIO_PRODUCT_ACCESS_KEY
  require_env MINIO_PRODUCT_SECRET_KEY
  require_env NACOS_AUTH_TOKEN
  require_env NACOS_AUTH_IDENTITY_KEY
  require_env NACOS_AUTH_IDENTITY_VALUE
  require_env NACOS_IMPORT_USERNAME
  require_env NACOS_IMPORT_PASSWORD
  require_env USER_DB_URL
  require_env USER_DB_USERNAME
  require_env USER_DB_PASSWORD
  require_env PRODUCT_DB_URL
  require_env PRODUCT_DB_USERNAME
  require_env PRODUCT_DB_PASSWORD
  require_env ORDER_DB_URL
  require_env ORDER_DB_USERNAME
  require_env ORDER_DB_PASSWORD
  require_env JWT_HMAC_SECRET
  require_env GATEWAY_SIGN_SECRET

  docker compose version >/dev/null 2>&1 || fail "docker compose 不可用"
  [[ -x "${SMOKE_SCRIPT}" ]] || fail "烟测脚本不存在或不可执行: ${SMOKE_SCRIPT}"
  [[ -x "${ADMIN_SMOKE_SCRIPT}" ]] || fail "管理端烟测脚本不存在或不可执行: ${ADMIN_SMOKE_SCRIPT}"
  if [[ "${RUN_PERF_BASELINE}" == "1" ]]; then
    [[ -x "${PERF_BASELINE_SCRIPT}" ]] || fail "性能脚本不存在或不可执行: ${PERF_BASELINE_SCRIPT}"
  fi

  rm -rf "${CI_LOG_DIR}"
  mkdir -p "${CI_LOG_DIR}"

  log "启动基础设施容器..."
  docker compose -f "${DEPLOY_DIR}/docker-compose.yml" up -d >"${CI_LOG_DIR}/docker-compose-up.log" 2>&1 || fail "docker compose up -d 失败"
  docker compose -f "${DEPLOY_DIR}/docker-compose.yml" ps >"${CI_LOG_DIR}/docker-compose-ps-initial.log" 2>&1 || true

  wait_nacos_init
  wait_rabbitmq_auth_init

  assert_container_running shiori-mysql
  assert_container_running shiori-rabbitmq
  assert_container_running shiori-redis
  assert_container_running shiori-nacos
  assert_container_running shiori-minio

  log "启动应用服务..."
  start_user_service
  start_product_service
  start_order_service
  start_gateway_service
  start_notify_service

  wait_http_ready "gateway" "http://localhost:8080/actuator/health" "${SERVICE_READY_TIMEOUT_SECONDS}"
  wait_http_ready "user-service" "http://localhost:8081/actuator/health" "${SERVICE_READY_TIMEOUT_SECONDS}"
  wait_http_ready "product-service" "http://localhost:8082/actuator/health" "${SERVICE_READY_TIMEOUT_SECONDS}"
  wait_http_ready "order-service" "http://localhost:8083/actuator/health" "${SERVICE_READY_TIMEOUT_SECONDS}"
  wait_http_ready "notify" "http://localhost:8090/healthz" "${SERVICE_READY_TIMEOUT_SECONDS}"

  log "执行交易通知 E2E 烟测..."
  if ! bash "${SMOKE_SCRIPT}" >"${CI_LOG_DIR}/smoke.log" 2>&1; then
    dump_tail "${CI_LOG_DIR}/smoke.log"
    fail "烟测执行失败"
  fi

  log "交易通知烟测执行成功"
  dump_tail "${CI_LOG_DIR}/smoke.log"

  log "执行管理端闭环烟测..."
  if ! bash "${ADMIN_SMOKE_SCRIPT}" >"${CI_LOG_DIR}/admin-smoke.log" 2>&1; then
    dump_tail "${CI_LOG_DIR}/admin-smoke.log"
    fail "管理端烟测执行失败"
  fi

  log "管理端烟测执行成功"
  dump_tail "${CI_LOG_DIR}/admin-smoke.log"

  if [[ "${RUN_PERF_BASELINE}" == "1" ]]; then
    log "执行性能基线..."
    if ! PERF_LOG_DIR="${CI_LOG_DIR}/perf" \
      GATEWAY_BASE_URL=http://127.0.0.1:8080 \
      NOTIFY_WS_BASE_URL=ws://127.0.0.1:8090/ws \
      bash "${PERF_BASELINE_SCRIPT}" >"${CI_LOG_DIR}/perf-baseline.log" 2>&1; then
      dump_tail "${CI_LOG_DIR}/perf-baseline.log"
      fail "性能基线执行失败"
    fi
    log "性能基线执行成功"
    dump_tail "${CI_LOG_DIR}/perf-baseline.log"
  fi

  if [[ "${SKIP_APP_PLAYWRIGHT}" != "1" ]]; then
    log "执行前端 Playwright E2E..."
    if ! (
      cd "${APP_DIR}"
      pnpm install --frozen-lockfile
      pnpm e2e:install
      E2E_GATEWAY_BASE_URL=http://127.0.0.1:8080 \
      E2E_NOTIFY_HTTP_BASE_URL=http://127.0.0.1:8090 \
      pnpm e2e
    ) >"${CI_LOG_DIR}/app-e2e.log" 2>&1; then
      dump_tail "${CI_LOG_DIR}/app-e2e.log"
      fail "前端 Playwright E2E 执行失败"
    fi
    log "前端 Playwright E2E 执行成功"
    dump_tail "${CI_LOG_DIR}/app-e2e.log"
  else
    log "按配置跳过前端 Playwright E2E"
  fi

  if [[ "${SKIP_ADMIN_PLAYWRIGHT}" != "1" ]]; then
    log "执行管理端 Playwright E2E..."
    if ! (
      cd "${ADMIN_WEB_DIR}"
      pnpm install --frozen-lockfile
      pnpm e2e:install
      E2E_GATEWAY_BASE_URL=http://127.0.0.1:8080 \
      E2E_MYSQL_CONTAINER=shiori-mysql \
      E2E_MYSQL_USER="${MYSQL_OPS_USERNAME}" \
      E2E_MYSQL_PASSWORD="${MYSQL_OPS_PASSWORD}" \
      pnpm e2e
    ) >"${CI_LOG_DIR}/admin-web-e2e.log" 2>&1; then
      dump_tail "${CI_LOG_DIR}/admin-web-e2e.log"
      fail "管理端 Playwright E2E 执行失败"
    fi
    log "管理端 Playwright E2E 执行成功"
    dump_tail "${CI_LOG_DIR}/admin-web-e2e.log"
  else
    log "按配置跳过管理端 Playwright E2E"
  fi
}

main "$@"
