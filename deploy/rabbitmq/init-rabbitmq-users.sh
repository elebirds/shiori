#!/bin/sh
set -eu

RABBITMQ_API_URL="${RABBITMQ_API_URL:-http://rabbitmq:15672}"

log() {
  echo "[rabbitmq-init] $*" >&2
}

die() {
  echo "[rabbitmq-init][ERROR] $*" >&2
  exit 1
}

require_env() {
  key="$1"
  eval "value=\${${key}:-}"
  if [ -z "${value}" ]; then
    die "missing env: ${key}"
  fi
}

upsert_user() {
  username="$1"
  password="$2"

  curl -fsS -u "${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}" \
    -H "content-type: application/json" \
    -X PUT "${RABBITMQ_API_URL}/api/users/${username}" \
    -d "{\"password\":\"${password}\",\"tags\":\"\"}" >/dev/null
}

upsert_permissions() {
  username="$1"
  configure_pattern="$2"
  write_pattern="$3"
  read_pattern="$4"

  curl -fsS -u "${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}" \
    -H "content-type: application/json" \
    -X PUT "${RABBITMQ_API_URL}/api/permissions/%2F/${username}" \
    -d "{\"configure\":\"${configure_pattern}\",\"write\":\"${write_pattern}\",\"read\":\"${read_pattern}\"}" >/dev/null
}

wait_for_rabbitmq() {
  log "waiting rabbitmq management api: ${RABBITMQ_API_URL}"
  i=0
  while [ "$i" -lt 90 ]; do
    if curl -fsS -u "${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}" "${RABBITMQ_API_URL}/api/overview" >/dev/null 2>&1; then
      log "rabbitmq is ready"
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  die "wait rabbitmq timeout"
}

require_env RABBITMQ_DEFAULT_USER
require_env RABBITMQ_DEFAULT_PASS
require_env ORDER_RMQ_USERNAME
require_env ORDER_RMQ_PASSWORD
require_env NOTIFY_RMQ_USERNAME
require_env NOTIFY_RMQ_PASSWORD

wait_for_rabbitmq

# Ensure default vhost exists.
curl -fsS -u "${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}" \
  -H "content-type: application/json" \
  -X PUT "${RABBITMQ_API_URL}/api/vhosts/%2F" >/dev/null

upsert_user "${ORDER_RMQ_USERNAME}" "${ORDER_RMQ_PASSWORD}"
upsert_user "${NOTIFY_RMQ_USERNAME}" "${NOTIFY_RMQ_PASSWORD}"

# order-service: only order exchanges/queues
upsert_permissions \
  "${ORDER_RMQ_USERNAME}" \
  "^(shiori[.]order[.].*|q[.]order[.].*)$" \
  "^(shiori[.]order[.].*|q[.]order[.].*)$" \
  "^(shiori[.]order[.].*|q[.]order[.].*)$"

# notify-service: only consume from order-paid exchange/queue
upsert_permissions \
  "${NOTIFY_RMQ_USERNAME}" \
  "^(shiori[.]order[.]event|notify[.]order[.]paid)$" \
  "^(shiori[.]order[.]event|notify[.]order[.]paid)$" \
  "^(shiori[.]order[.]event|notify[.]order[.]paid)$"

log "rabbitmq users and permissions initialized"
