#!/bin/sh
set -eu

NACOS_URL="${NACOS_URL:-http://nacos:8848}"
NACOS_IMPORT_USERNAME="${NACOS_IMPORT_USERNAME:-}"
NACOS_IMPORT_PASSWORD="${NACOS_IMPORT_PASSWORD:-}"
NACOS_AUTH_IDENTITY_KEY="${NACOS_AUTH_IDENTITY_KEY:-}"
NACOS_AUTH_IDENTITY_VALUE="${NACOS_AUTH_IDENTITY_VALUE:-}"
SHIORI_ENV="${SHIORI_ENV:-dev}"
NACOS_CONFIG_GROUP="${NACOS_CONFIG_GROUP:-}"
NACOS_CONFIG_NAMESPACE="${NACOS_CONFIG_NAMESPACE:-}"
TEMPLATE_DIR="${NACOS_TEMPLATE_DIR:-/deploy/nacos/templates}"

log() {
  echo "[nacos-import] $*" >&2
}

die() {
  echo "[nacos-import][ERROR] $*" >&2
  exit 1
}

require_env_non_empty() {
  var_name="$1"
  eval "var_value=\${${var_name}:-}"
  if [ -z "${var_value}" ]; then
    die "缺少必填环境变量: ${var_name}"
  fi
}

resolve_group_by_env() {
  env_name="$(printf '%s' "${SHIORI_ENV}" | tr '[:upper:]' '[:lower:]')"
  case "${env_name}" in
    dev)
      printf '%s' "SHIORI_DEV_DOCKER"
      ;;
    test)
      printf '%s' "SHIORI_TEST"
      ;;
    prod)
      printf '%s' "SHIORI_PROD"
      ;;
    *)
      die "不支持的 SHIORI_ENV: ${SHIORI_ENV}，仅支持 dev/test/prod"
      ;;
  esac
}

collect_required_vars() {
  grep -h -o '\${[A-Za-z_][A-Za-z0-9_]*}' "${TEMPLATE_DIR}"/*.yml.tmpl 2>/dev/null \
    | tr -d '${}' \
    | sort -u \
    || true
}

render_template() {
  template_file="$1"
  output_file="$2"

  awk '
  {
    line = $0
    while (match(line, /\$\{[A-Za-z_][A-Za-z0-9_]*\}/)) {
      var = substr(line, RSTART + 2, RLENGTH - 3)
      val = ENVIRON[var]
      line = substr(line, 1, RSTART - 1) val substr(line, RSTART + RLENGTH)
    }
    print line
  }
  ' "${template_file}" > "${output_file}"
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
  rendered_file="$1"
  data_id="$(basename "${rendered_file}")"

  if [ -n "${NACOS_CONFIG_NAMESPACE}" ]; then
    response="$(curl -sS -w '\n%{http_code}' -X POST "${NACOS_URL}/nacos/v3/admin/cs/config" \
      -H "accessToken: ${ACCESS_TOKEN}" \
      -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "groupName=${TARGET_GROUP}" \
      --data-urlencode "namespaceId=${NACOS_CONFIG_NAMESPACE}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${rendered_file}")"
  else
    response="$(curl -sS -w '\n%{http_code}' -X POST "${NACOS_URL}/nacos/v3/admin/cs/config" \
      -H "accessToken: ${ACCESS_TOKEN}" \
      -H "${NACOS_AUTH_IDENTITY_KEY}: ${NACOS_AUTH_IDENTITY_VALUE}" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "groupName=${TARGET_GROUP}" \
      --data-urlencode "type=yaml" \
      --data-urlencode "content@${rendered_file}")"
  fi

  http_status="$(printf '%s' "${response}" | tail -n1)"
  body="$(printf '%s' "${response}" | sed '$d')"
  if [ "${http_status#2}" = "${http_status}" ]; then
    die "导入失败 dataId=${data_id}, status=${http_status}, body=${body}"
  fi
  if printf '%s' "${body}" | grep -Eq '"code"[[:space:]]*:[[:space:]]*0'; then
    log "导入成功 group=${TARGET_GROUP} dataId=${data_id}"
    return 0
  fi

  case "${body}" in
    true|\"true\"|ok|OK)
      log "导入成功 group=${TARGET_GROUP} dataId=${data_id}"
      ;;
    *)
      die "导入返回非成功体 dataId=${data_id}, body=${body}"
      ;;
  esac
}

require_env_non_empty NACOS_IMPORT_USERNAME
require_env_non_empty NACOS_IMPORT_PASSWORD
require_env_non_empty NACOS_AUTH_IDENTITY_KEY
require_env_non_empty NACOS_AUTH_IDENTITY_VALUE

if [ ! -d "${TEMPLATE_DIR}" ]; then
  die "模板目录不存在: ${TEMPLATE_DIR}"
fi

TARGET_GROUP="${NACOS_CONFIG_GROUP}"
if [ -z "${TARGET_GROUP}" ]; then
  TARGET_GROUP="$(resolve_group_by_env)"
fi

required_vars="$(collect_required_vars)"
if [ -n "${required_vars}" ]; then
  for var_name in ${required_vars}; do
    require_env_non_empty "${var_name}"
  done
fi

RENDER_DIR="$(mktemp -d)"
cleanup_render_dir() {
  rm -rf "${RENDER_DIR}"
}
trap cleanup_render_dir EXIT

wait_nacos_ready
ensure_admin_password_initialized

ACCESS_TOKEN="$(nacos_login_with_retry || true)"
[ -n "${ACCESS_TOKEN}" ] || die "Nacos 登录失败，请检查账号或鉴权配置"

count=0
for template_file in "${TEMPLATE_DIR}"/*.yml.tmpl; do
  [ -f "${template_file}" ] || continue
  data_id="$(basename "${template_file}" .tmpl)"
  output_file="${RENDER_DIR}/${data_id}"
  render_template "${template_file}" "${output_file}"
  publish_config "${output_file}"
  count=$((count + 1))
done

[ "${count}" -gt 0 ] || die "未找到可导入的配置模板"
log "导入完成，group=${TARGET_GROUP}，共 ${count} 个配置"
