#!/bin/sh
set -eu

NACOS_URL="${NACOS_URL:-http://nacos:8848}"
NACOS_IMPORT_USERNAME="${NACOS_IMPORT_USERNAME:-nacos}"
NACOS_IMPORT_PASSWORD="${NACOS_IMPORT_PASSWORD:-nacos}"
NACOS_IMPORT_GROUP="${NACOS_IMPORT_GROUP:-DEFAULT_GROUP}"
NACOS_IMPORT_NAMESPACE="${NACOS_IMPORT_NAMESPACE:-}"
NACOS_AUTH_IDENTITY_KEY="${NACOS_AUTH_IDENTITY_KEY:-shiori}"
NACOS_AUTH_IDENTITY_VALUE="${NACOS_AUTH_IDENTITY_VALUE:-shiori}"

log() {
  echo "[nacos-import] $*"
}

die() {
  echo "[nacos-import][ERROR] $*" >&2
  exit 1
}

wait_nacos_ready() {
  log "等待 Nacos 就绪: ${NACOS_URL}"
  i=0
  while [ "$i" -lt 60 ]; do
    if curl -fsS "${NACOS_URL}/nacos/" >/dev/null 2>&1; then
      log "Nacos 已就绪"
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  die "等待 Nacos 超时"
}

nacos_login() {
  local_login_response="$(curl -fsS -X POST "${NACOS_URL}/nacos/v1/auth/users/login" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
    --data-urlencode "username=${NACOS_IMPORT_USERNAME}" \
    --data-urlencode "password=${NACOS_IMPORT_PASSWORD}")" || return 1

  printf '%s' "${local_login_response}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

publish_config() {
  file="$1"
  data_id="$(basename "${file}")"

  if [ -n "${NACOS_IMPORT_NAMESPACE}" ]; then
    response="$(curl -sS -w '\n%{http_code}' -X POST "${NACOS_URL}/nacos/v1/cs/configs" \
      -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
      --data-urlencode "accessToken=${ACCESS_TOKEN}" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "group=${NACOS_IMPORT_GROUP}" \
      --data-urlencode "tenant=${NACOS_IMPORT_NAMESPACE}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${file}")"
  else
    response="$(curl -sS -w '\n%{http_code}' -X POST "${NACOS_URL}/nacos/v1/cs/configs" \
      -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
      --data-urlencode "accessToken=${ACCESS_TOKEN}" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "group=${NACOS_IMPORT_GROUP}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${file}")"
  fi

  http_status="$(printf '%s' "${response}" | tail -n1)"
  body="$(printf '%s' "${response}" | sed '$d')"
  if [ "${http_status#2}" = "${http_status}" ]; then
    die "导入失败 dataId=${data_id}, status=${http_status}, body=${body}"
  fi
  case "${body}" in
    true|\"true\"|ok|OK)
      log "导入成功 dataId=${data_id}"
      ;;
    *)
      die "导入返回非成功体 dataId=${data_id}, body=${body}"
      ;;
  esac
}

wait_nacos_ready

ACCESS_TOKEN="$(nacos_login || true)"
[ -n "${ACCESS_TOKEN}" ] || die "Nacos 登录失败，请检查账号或鉴权配置"

count=0
for file in /deploy/nacos/*.yml; do
  [ -f "${file}" ] || continue
  publish_config "${file}"
  count=$((count + 1))
done

[ "${count}" -gt 0 ] || die "未找到可导入的配置文件"
log "导入完成，共 ${count} 个配置"
