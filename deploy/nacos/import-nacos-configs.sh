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
  echo "[nacos-import] $*" >&2
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

nacos_login_once() {
  local_login_response="$(curl -fsS -X POST "${NACOS_URL}/nacos/v3/auth/user/login" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
    --data-urlencode "username=${NACOS_IMPORT_USERNAME}" \
    --data-urlencode "password=${NACOS_IMPORT_PASSWORD}")" || return 1

  token="$(printf '%s' "${local_login_response}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  if [ -z "${token}" ]; then
    token="$(printf '%s' "${local_login_response}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
  fi
  printf '%s' "${token}"
}

ensure_admin_password_initialized() {
  log "确保 Nacos 管理员密码已初始化"
  response="$(curl -sS -w '\n%{http_code}' -X POST "${NACOS_URL}/nacos/v3/auth/user/admin" \
    -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
    --data-urlencode "password=${NACOS_IMPORT_PASSWORD}")" || die "调用管理员初始化接口失败"

  http_status="$(printf '%s' "${response}" | tail -n1)"
  body="$(printf '%s' "${response}" | sed '$d')"

  if [ "${http_status#2}" != "${http_status}" ]; then
    log "管理员密码初始化成功"
    return 0
  fi

  if printf '%s' "${body}" | grep -Eqi 'initialized|already|exist'; then
    log "管理员密码已初始化，跳过"
    return 0
  fi

  die "管理员密码初始化失败，status=${http_status}, body=${body}"
}

nacos_login_with_retry() {
  log "等待 Nacos 登录接口可用"
  i=0
  while [ "$i" -lt 60 ]; do
    token="$(nacos_login_once || true)"
    if [ -n "${token}" ]; then
      printf '%s' "${token}"
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  return 1
}

publish_config() {
  file="$1"
  data_id="$(basename "${file}")"

  if [ -n "${NACOS_IMPORT_NAMESPACE}" ]; then
    response="$(curl -sS -w '\n%{http_code}' -X POST "${NACOS_URL}/nacos/v3/admin/cs/config" \
      -H "accessToken: ${ACCESS_TOKEN}" \
      -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "groupName=${NACOS_IMPORT_GROUP}" \
      --data-urlencode "namespaceId=${NACOS_IMPORT_NAMESPACE}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${file}")"
  else
    response="$(curl -sS -w '\n%{http_code}' -X POST "${NACOS_URL}/nacos/v3/admin/cs/config" \
      -H "accessToken: ${ACCESS_TOKEN}" \
      -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "groupName=${NACOS_IMPORT_GROUP}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${file}")"
  fi

  http_status="$(printf '%s' "${response}" | tail -n1)"
  body="$(printf '%s' "${response}" | sed '$d')"
  if [ "${http_status#2}" = "${http_status}" ]; then
    die "导入失败 dataId=${data_id}, status=${http_status}, body=${body}"
  fi
  if printf '%s' "${body}" | grep -Eq '"code"[[:space:]]*:[[:space:]]*0'; then
    log "导入成功 dataId=${data_id}"
    return 0
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
ensure_admin_password_initialized

ACCESS_TOKEN="$(nacos_login_with_retry || true)"
[ -n "${ACCESS_TOKEN}" ] || die "Nacos 登录失败，请检查账号或鉴权配置"

count=0
for file in /deploy/nacos/*.yml; do
  [ -f "${file}" ] || continue
  publish_config "${file}"
  count=$((count + 1))
done

[ "${count}" -gt 0 ] || die "未找到可导入的配置文件"
log "导入完成，共 ${count} 个配置"
