#!/bin/sh
set -eu

CONNECTOR_DIR="${CONNECTOR_DIR:-/deploy/kafka/connect}"
KAFKA_CONNECT_URL="${KAFKA_CONNECT_URL:-http://kafka-connect:8083}"

require_env() {
  key="$1"
  eval "value=\${${key}:-}"
  if [ -z "${value}" ]; then
    echo "[kafka-connect-init][ERROR] missing env: ${key}" >&2
    exit 1
  fi
}

escape_sed() {
  printf "%s" "$1" | sed 's/[\\/&]/\\&/g'
}

require_env CDC_DB_USERNAME
require_env CDC_DB_PASSWORD

CDC_DB_USERNAME_ESC="$(escape_sed "${CDC_DB_USERNAME}")"
CDC_DB_PASSWORD_ESC="$(escape_sed "${CDC_DB_PASSWORD}")"

echo "[kafka-connect-init] waiting for Kafka Connect at ${KAFKA_CONNECT_URL}"
attempts=0
while [ "${attempts}" -lt 60 ]; do
  if curl -fsS "${KAFKA_CONNECT_URL}/connectors" >/dev/null 2>&1; then
    break
  fi
  attempts=$((attempts + 1))
  sleep 2
done

curl -fsS "${KAFKA_CONNECT_URL}/connectors" >/dev/null

for file in "${CONNECTOR_DIR}"/*-connector.json; do
  [ -f "${file}" ] || continue

  connector_name="$(basename "${file}" .json)"
  tmp_file="$(mktemp)"

  sed \
    -e "s/__CDC_DB_USERNAME__/${CDC_DB_USERNAME_ESC}/g" \
    -e "s/__CDC_DB_PASSWORD__/${CDC_DB_PASSWORD_ESC}/g" \
    "${file}" > "${tmp_file}"

  echo "[kafka-connect-init] applying connector ${connector_name}"
  curl -fsS -X PUT \
    -H "Content-Type: application/json" \
    --data-binary @"${tmp_file}" \
    "${KAFKA_CONNECT_URL}/connectors/${connector_name}/config" >/dev/null

  rm -f "${tmp_file}"
done

echo "[kafka-connect-init] done"
