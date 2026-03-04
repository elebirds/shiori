#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DEFAULT_INPUT="${SCRIPT_DIR}/../deploy/.env"
INPUT_FILE="${DEFAULT_INPUT}"
OUTPUT_FILE="${SCRIPT_DIR}/.env"
STORE_DRIVER="memory"
FORCE_OVERWRITE=0

usage() {
  cat <<'EOF'
用法:
  ./gen-env.sh [-f] [-m memory|mysql] [-i 输入env] [-o 输出文件]

说明:
  - 从 deploy/.env 生成 shiori-notify/.env
  - 自动注入 notify 本地运行所需变量
  - 默认存储驱动为 memory

参数:
  -f                 覆盖已存在输出文件
  -m <driver>        存储驱动: memory 或 mysql（默认 memory）
  -i <file>          输入 env 文件（默认 ../deploy/.env）
  -o <file>          输出文件（默认 ./ .env）
EOF
}

while getopts "fm:i:o:h" opt; do
  case "${opt}" in
    f) FORCE_OVERWRITE=1 ;;
    m) STORE_DRIVER="${OPTARG}" ;;
    i) INPUT_FILE="${OPTARG}" ;;
    o) OUTPUT_FILE="${OPTARG}" ;;
    h)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
done

if [ ! -f "${INPUT_FILE}" ]; then
  echo "[notify-gen-env][ERROR] 输入文件不存在: ${INPUT_FILE}" >&2
  exit 1
fi

if [ -f "${OUTPUT_FILE}" ] && [ "${FORCE_OVERWRITE}" -ne 1 ]; then
  echo "[notify-gen-env][ERROR] 输出文件已存在: ${OUTPUT_FILE}" >&2
  echo "[notify-gen-env] 使用 -f 覆盖，或 -o 指定其他文件" >&2
  exit 1
fi

case "${STORE_DRIVER}" in
  memory|mysql) ;;
  *)
    echo "[notify-gen-env][ERROR] 不支持的驱动: ${STORE_DRIVER}，仅支持 memory/mysql" >&2
    exit 1
    ;;
esac

trim_spaces() {
  printf '%s' "$1" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

strip_quotes() {
  value="$(trim_spaces "$1")"
  case "${value}" in
    \"*\")
      value="${value#\"}"
      value="${value%\"}"
      ;;
    \'*\')
      value="${value#\'}"
      value="${value%\'}"
      ;;
  esac
  printf '%s' "${value}"
}

read_env() {
  key="$1"
  raw="$(grep "^${key}=" "${INPUT_FILE}" | tail -n1 | cut -d= -f2- || true)"
  strip_quotes "${raw}"
}

coalesce() {
  for candidate in "$@"; do
    if [ -n "${candidate}" ]; then
      printf '%s' "${candidate}"
      return 0
    fi
  done
  printf ''
}

notify_rmq_username="$(coalesce "$(read_env NOTIFY_RMQ_USERNAME)" "$(read_env ORDER_RMQ_USERNAME)" "notify_service")"
notify_rmq_password="$(coalesce "$(read_env NOTIFY_RMQ_PASSWORD)" "$(read_env ORDER_RMQ_PASSWORD)")"
notify_jwt_hmac_secret="$(coalesce "$(read_env NOTIFY_JWT_HMAC_SECRET)" "$(read_env JWT_HMAC_SECRET)")"
notify_jwt_issuer="$(coalesce "$(read_env NOTIFY_JWT_ISSUER)" "shiori")"
notify_db_username="$(coalesce "$(read_env NOTIFY_DB_USERNAME)" "notify_service")"
notify_db_password="$(coalesce "$(read_env NOTIFY_DB_PASSWORD)" "$(read_env ORDER_DB_PASSWORD)")"
notify_db_name="$(coalesce "$(read_env NOTIFY_DB_NAME_LOCAL)" "shiori_notify")"
notify_ws_path="$(coalesce "$(read_env NOTIFY_WS_PATH)" "/ws")"
notify_chat_enabled="$(coalesce "$(read_env NOTIFY_CHAT_ENABLED)" "false")"
notify_chat_default_limit="$(coalesce "$(read_env NOTIFY_CHAT_DEFAULT_LIMIT)" "20")"
notify_chat_max_limit="$(coalesce "$(read_env NOTIFY_CHAT_MAX_LIMIT)" "100")"
notify_chat_ticket_issuer="$(coalesce "$(read_env CHAT_TICKET_ISSUER)" "shiori-chat-ticket")"
notify_chat_ticket_public_key="$(coalesce "$(read_env NOTIFY_CHAT_TICKET_PUBLIC_KEY_PEM_BASE64)" "")"
notify_chat_mq_enabled="$(coalesce "$(read_env NOTIFY_CHAT_MQ_ENABLED)" "true")"
notify_chat_mq_exchange="$(coalesce "$(read_env NOTIFY_CHAT_MQ_EXCHANGE)" "shiori.chat.event")"
notify_instance_id="$(coalesce "$(read_env NOTIFY_INSTANCE_ID)" "")"

if [ -z "${notify_rmq_password}" ]; then
  echo "[notify-gen-env][ERROR] 缺少 RabbitMQ 密码（NOTIFY_RMQ_PASSWORD 或 ORDER_RMQ_PASSWORD）" >&2
  exit 1
fi

if [ -z "${notify_jwt_hmac_secret}" ]; then
  echo "[notify-gen-env][ERROR] 缺少 JWT HMAC 密钥（NOTIFY_JWT_HMAC_SECRET 或 JWT_HMAC_SECRET）" >&2
  exit 1
fi

if [ "${STORE_DRIVER}" = "mysql" ] && [ -z "${notify_db_password}" ]; then
  echo "[notify-gen-env][ERROR] mysql 模式缺少 DB 密码（NOTIFY_DB_PASSWORD 或 ORDER_DB_PASSWORD）" >&2
  exit 1
fi

tmp_file="$(mktemp)"
cleanup() {
  rm -f "${tmp_file}"
}
trap cleanup EXIT

cat > "${tmp_file}" <<EOF
NOTIFY_HTTP_ADDR=:8090
RABBITMQ_ADDR=amqp://${notify_rmq_username}:${notify_rmq_password}@localhost:5672/
RABBITMQ_EXCHANGES=shiori.order.event,shiori.user.event
RABBITMQ_QUEUE=notify.order.event
RABBITMQ_ROUTING_KEYS=order.created,order.paid,order.canceled,order.delivered,order.finished,user.status.changed,user.role.changed,user.password.reset
NOTIFY_AUTH_ENABLED=true
NOTIFY_JWT_HMAC_SECRET=${notify_jwt_hmac_secret}
NOTIFY_JWT_ISSUER=${notify_jwt_issuer}
NOTIFY_WS_PATH=${notify_ws_path}
NOTIFY_CHAT_ENABLED=${notify_chat_enabled}
NOTIFY_CHAT_DEFAULT_LIMIT=${notify_chat_default_limit}
NOTIFY_CHAT_MAX_LIMIT=${notify_chat_max_limit}
NOTIFY_CHAT_TICKET_ISSUER=${notify_chat_ticket_issuer}
NOTIFY_CHAT_TICKET_PUBLIC_KEY_PEM_BASE64=${notify_chat_ticket_public_key}
NOTIFY_CHAT_MQ_ENABLED=${notify_chat_mq_enabled}
NOTIFY_CHAT_MQ_EXCHANGE=${notify_chat_mq_exchange}
NOTIFY_INSTANCE_ID=${notify_instance_id}
NOTIFY_STORE_DRIVER=${STORE_DRIVER}
EOF

if [ "${STORE_DRIVER}" = "mysql" ]; then
  cat >> "${tmp_file}" <<EOF
NOTIFY_MYSQL_DSN=${notify_db_username}:${notify_db_password}@tcp(127.0.0.1:3306)/${notify_db_name}?charset=utf8mb4&parseTime=true&loc=UTC
NOTIFY_MYSQL_MAX_OPEN_CONNS=20
NOTIFY_MYSQL_MAX_IDLE_CONNS=10
NOTIFY_MYSQL_CONN_MAX_LIFETIME=30m
EOF
fi

if [ "${FORCE_OVERWRITE}" -eq 1 ] && [ -f "${OUTPUT_FILE}" ]; then
  rm -f "${OUTPUT_FILE}"
fi

mv "${tmp_file}" "${OUTPUT_FILE}"
trap - EXIT

echo "[notify-gen-env] 生成完成: ${OUTPUT_FILE}"
echo "[notify-gen-env] STORE_DRIVER=${STORE_DRIVER}"
