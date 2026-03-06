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
require_env USER_RMQ_USERNAME
require_env USER_RMQ_PASSWORD
require_env NOTIFY_RMQ_USERNAME
require_env NOTIFY_RMQ_PASSWORD
require_env PAYMENT_RMQ_USERNAME
require_env PAYMENT_RMQ_PASSWORD
require_env PRODUCT_RMQ_USERNAME
require_env PRODUCT_RMQ_PASSWORD
require_env SOCIAL_RMQ_USERNAME
require_env SOCIAL_RMQ_PASSWORD

wait_for_rabbitmq

# Ensure default vhost exists.
curl -fsS -u "${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}" \
  -H "content-type: application/json" \
  -X PUT "${RABBITMQ_API_URL}/api/vhosts/%2F" >/dev/null

upsert_user "${ORDER_RMQ_USERNAME}" "${ORDER_RMQ_PASSWORD}"
upsert_user "${USER_RMQ_USERNAME}" "${USER_RMQ_PASSWORD}"
upsert_user "${NOTIFY_RMQ_USERNAME}" "${NOTIFY_RMQ_PASSWORD}"
upsert_user "${PAYMENT_RMQ_USERNAME}" "${PAYMENT_RMQ_PASSWORD}"
upsert_user "${PRODUCT_RMQ_USERNAME}" "${PRODUCT_RMQ_PASSWORD}"
upsert_user "${SOCIAL_RMQ_USERNAME}" "${SOCIAL_RMQ_PASSWORD}"

# order-service: own exchanges/queues + subscribe payment event exchange
upsert_permissions \
  "${ORDER_RMQ_USERNAME}" \
  "^(shiori[.]order[.].*|q[.]order[.].*|shiori[.]payment[.]event)$" \
  "^(shiori[.]order[.].*|q[.]order[.].*)$" \
  "^(shiori[.]order[.].*|q[.]order[.].*|shiori[.]payment[.]event)$"

# user-service: publish user governance events
upsert_permissions \
  "${USER_RMQ_USERNAME}" \
  "^(shiori[.]user[.]event)$" \
  "^(shiori[.]user[.]event)$" \
  "^(shiori[.]user[.]event)$"

# notify-service: consume order/user events + chat fanout broadcast
upsert_permissions \
  "${NOTIFY_RMQ_USERNAME}" \
  "^(shiori[.]order[.]event|shiori[.]user[.]event|shiori[.]chat[.]event|notify[.]order[.]event|amq[.]gen.*)$" \
  "^(shiori[.]order[.]event|shiori[.]user[.]event|shiori[.]chat[.]event|notify[.]order[.]event|amq[.]gen.*)$" \
  "^(shiori[.]order[.]event|shiori[.]user[.]event|shiori[.]chat[.]event|notify[.]order[.]event|amq[.]gen.*)$"

# payment-service: publish wallet and payment events
upsert_permissions \
  "${PAYMENT_RMQ_USERNAME}" \
  "^(shiori[.]payment[.].*|q[.]payment[.].*)$" \
  "^(shiori[.]payment[.].*|q[.]payment[.].*)$" \
  "^(shiori[.]payment[.].*|q[.]payment[.].*)$"

# product-service: publish product events
upsert_permissions \
  "${PRODUCT_RMQ_USERNAME}" \
  "^(shiori[.]product[.]event)$" \
  "^(shiori[.]product[.]event)$" \
  "^(shiori[.]product[.]event)$"

# social-service: consume product published events and own queue
upsert_permissions \
  "${SOCIAL_RMQ_USERNAME}" \
  "^(shiori[.]product[.]event|shiori[.]social[.]product[.]dlx|q[.]social[.]product[.]published(?:[.]dlq)?)$" \
  "^(shiori[.]product[.]event|shiori[.]social[.]product[.]dlx|q[.]social[.]product[.]published(?:[.]dlq)?)$" \
  "^(shiori[.]product[.]event|shiori[.]social[.]product[.]dlx|q[.]social[.]product[.]published(?:[.]dlq)?)$"

log "rabbitmq users and permissions initialized"
