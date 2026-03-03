#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DEPLOY_DIR="$(CDPATH= cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${DEPLOY_DIR}/.env}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-shiori-mysql}"

usage() {
  cat <<USAGE
用法:
  sh deploy/sql/manual/grant_admin_role.sh <username>

可选环境变量:
  ENV_FILE          .env 文件路径（默认: deploy/.env）
  MYSQL_CONTAINER   MySQL 容器名（默认: shiori-mysql）
  MYSQL_OPS_USER    MySQL 操作账号（默认取 MYSQL_OPS_USERNAME）
  MYSQL_OPS_PASSWORD MySQL 操作密码（默认取 MYSQL_OPS_PASSWORD）
USAGE
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

TARGET_USERNAME="${1:-}"
if [ -z "${TARGET_USERNAME}" ]; then
  echo "[grant-admin][ERROR] 缺少用户名" >&2
  usage >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[grant-admin][ERROR] 未找到 docker 命令" >&2
  exit 1
fi

if [ -f "${ENV_FILE}" ]; then
  # shellcheck disable=SC1090
  set -a
  . "${ENV_FILE}"
  set +a
fi

MYSQL_OPS_USER="${MYSQL_OPS_USER:-${MYSQL_OPS_USERNAME:-${MYSQL_USER:-}}}"
MYSQL_OPS_PASSWORD="${MYSQL_OPS_PASSWORD:-${MYSQL_PASSWORD:-}}"

if [ -z "${MYSQL_OPS_USER}" ]; then
  echo "[grant-admin][ERROR] 缺少 MYSQL_OPS_USER（或 MYSQL_OPS_USERNAME）" >&2
  exit 1
fi
if [ -z "${MYSQL_OPS_PASSWORD}" ]; then
  echo "[grant-admin][ERROR] 缺少 MYSQL_OPS_PASSWORD（或 MYSQL_PASSWORD）" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
  echo "[grant-admin][ERROR] MySQL 容器未运行: ${MYSQL_CONTAINER}" >&2
  exit 1
fi

sql_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

TARGET_USERNAME_ESC="$(sql_escape "${TARGET_USERNAME}")"

echo "[grant-admin] 开始授予 ROLE_ADMIN: username=${TARGET_USERNAME}" >&2

docker exec -i "${MYSQL_CONTAINER}" \
  mysql "-u${MYSQL_OPS_USER}" "-p${MYSQL_OPS_PASSWORD}" shiori_user <<SQL
INSERT INTO u_user_role (user_id, role_id, created_at)
SELECT u.id, r.id, CURRENT_TIMESTAMP(3)
FROM u_user u
JOIN u_role r ON r.role_code = 'ROLE_ADMIN' AND r.status = 1 AND r.is_deleted = 0
WHERE u.username = '${TARGET_USERNAME_ESC}'
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
SQL

COUNT_RESULT="$(docker exec "${MYSQL_CONTAINER}" \
  mysql "-u${MYSQL_OPS_USER}" "-p${MYSQL_OPS_PASSWORD}" -D shiori_user -Nse "
SELECT COUNT(1)
FROM u_user_role ur
JOIN u_user u ON ur.user_id = u.id
JOIN u_role r ON ur.role_id = r.id
WHERE u.username = '${TARGET_USERNAME_ESC}'
  AND u.is_deleted = 0
  AND r.role_code = 'ROLE_ADMIN'
  AND r.status = 1
  AND r.is_deleted = 0;")"

case "${COUNT_RESULT}" in
  ''|*[!0-9]*)
    echo "[grant-admin][ERROR] 校验失败，返回值异常: ${COUNT_RESULT}" >&2
    exit 1
    ;;
esac

if [ "${COUNT_RESULT}" -ge 1 ]; then
  echo "[grant-admin] 授权成功: username=${TARGET_USERNAME}, role=ROLE_ADMIN" >&2
  exit 0
fi

echo "[grant-admin][ERROR] 未找到目标用户或授权未生效: ${TARGET_USERNAME}" >&2
exit 1
