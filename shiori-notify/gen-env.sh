#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DEFAULT_INPUT="${SCRIPT_DIR}/../deploy/.env"
INPUT_FILE="${DEFAULT_INPUT}"
OUTPUT_FILE="${SCRIPT_DIR}/.env"
FORCE_OVERWRITE=0
OVERRIDE_GROUP=""

usage() {
  cat <<'EOF'
用法:
  ./gen-env.sh [-f] [-i 输入env] [-o 输出文件] [-g nacos_group]

说明:
  - 从 deploy/.env 生成 shiori-notify/.env（仅 Nacos 连接相关变量）
  - 业务配置不再写入本地 .env，统一从 Nacos DataId 读取

参数:
  -f                 覆盖已存在输出文件
  -i <file>          输入 env 文件（默认 ../deploy/.env）
  -o <file>          输出文件（默认 ./.env）
  -g <group>         覆盖 NACOS_CONFIG_GROUP
EOF
}

while getopts "fi:o:g:h" opt; do
  case "${opt}" in
    f) FORCE_OVERWRITE=1 ;;
    i) INPUT_FILE="${OPTARG}" ;;
    o) OUTPUT_FILE="${OPTARG}" ;;
    g) OVERRIDE_GROUP="${OPTARG}" ;;
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

nacos_addr="$(coalesce "$(read_env NACOS_ADDR)" "127.0.0.1:8848")"
nacos_username="$(coalesce "$(read_env NACOS_USERNAME)" "$(read_env NACOS_IMPORT_USERNAME)")"
nacos_password="$(coalesce "$(read_env NACOS_PASSWORD)" "$(read_env NACOS_IMPORT_PASSWORD)")"
nacos_group="$(coalesce "${OVERRIDE_GROUP}" "$(read_env NACOS_CONFIG_GROUP_LOCAL)" "$(read_env NACOS_CONFIG_GROUP)" "SHIORI_DEV_LOCAL")"
nacos_namespace="$(coalesce "$(read_env NACOS_CONFIG_NAMESPACE)" "")"

if [ -z "${nacos_username}" ]; then
  echo "[notify-gen-env][ERROR] 缺少 Nacos 用户名（NACOS_USERNAME 或 NACOS_IMPORT_USERNAME）" >&2
  exit 1
fi

if [ -z "${nacos_password}" ]; then
  echo "[notify-gen-env][ERROR] 缺少 Nacos 密码（NACOS_PASSWORD 或 NACOS_IMPORT_PASSWORD）" >&2
  exit 1
fi

if [ -z "${nacos_addr}" ]; then
  echo "[notify-gen-env][ERROR] 缺少 Nacos 地址（NACOS_ADDR）" >&2
  exit 1
fi

if [ -z "${nacos_group}" ]; then
  echo "[notify-gen-env][ERROR] 缺少 Nacos 配置组（NACOS_CONFIG_GROUP）" >&2
  exit 1
fi

tmp_file="$(mktemp)"
cleanup() {
  rm -f "${tmp_file}"
}
trap cleanup EXIT

cat > "${tmp_file}" <<EOF
NACOS_ADDR=${nacos_addr}
NACOS_USERNAME=${nacos_username}
NACOS_PASSWORD=${nacos_password}
NACOS_CONFIG_GROUP=${nacos_group}
NACOS_CONFIG_NAMESPACE=${nacos_namespace}
EOF

if [ "${FORCE_OVERWRITE}" -eq 1 ] && [ -f "${OUTPUT_FILE}" ]; then
  rm -f "${OUTPUT_FILE}"
fi

mv "${tmp_file}" "${OUTPUT_FILE}"
trap - EXIT

echo "[notify-gen-env] 生成完成: ${OUTPUT_FILE}"
echo "[notify-gen-env] NACOS_ADDR=${nacos_addr}"
echo "[notify-gen-env] NACOS_CONFIG_GROUP=${nacos_group}"
