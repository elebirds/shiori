#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
INPUT_FILE="${SCRIPT_DIR}/.env.example"
OUTPUT_FILE="${SCRIPT_DIR}/.env"
FORCE_OVERWRITE=0

usage() {
  cat <<'EOF'
用法:
  ./gen-env.sh [-f] [-o 输出文件]

说明:
  - 从 deploy/.env.example 生成 .env
  - 自动替换所有 __CHANGE_ME__ 为随机值
  - 默认输出 deploy/.env

参数:
  -f           覆盖已存在的输出文件
  -o <file>    指定输出文件路径
EOF
}

while getopts "fo:h" opt; do
  case "${opt}" in
    f) FORCE_OVERWRITE=1 ;;
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
  echo "[gen-env][ERROR] 模板不存在: ${INPUT_FILE}" >&2
  exit 1
fi

if [ -f "${OUTPUT_FILE}" ] && [ "${FORCE_OVERWRITE}" -ne 1 ]; then
  echo "[gen-env][ERROR] 输出文件已存在: ${OUTPUT_FILE}" >&2
  echo "[gen-env] 使用 -f 覆盖，或 -o 指定其他文件" >&2
  exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
  echo "[gen-env][ERROR] 需要 openssl 来生成随机密钥" >&2
  exit 1
fi

rand_hex() {
  bytes="$1"
  openssl rand -hex "${bytes}"
}

rand_b64url() {
  bytes="$1"
  openssl rand -base64 "${bytes}" | tr -d '\n' | tr '+/' '-_' | tr -d '='
}

gen_value() {
  key="$1"
  case "${key}" in
    JWT_HMAC_SECRET|GATEWAY_SIGN_SECRET)
      rand_hex 32
      ;;
    NACOS_AUTH_TOKEN)
      rand_b64url 48
      ;;
    NACOS_AUTH_IDENTITY_KEY)
      printf 'idk_%s' "$(rand_hex 6)"
      ;;
    NACOS_AUTH_IDENTITY_VALUE)
      rand_hex 16
      ;;
    *PASSWORD|*SECRET*)
      rand_hex 24
      ;;
    *)
      rand_hex 16
      ;;
  esac
}

TMP_FILE="$(mktemp)"
cleanup() {
  rm -f "${TMP_FILE}"
}
trap cleanup EXIT

replaced_count=0
while IFS= read -r line || [ -n "${line}" ]; do
  case "${line}" in
    *"__CHANGE_ME__"*)
      key="${line%%=*}"
      value="$(gen_value "${key}")"
      printf '%s=%s\n' "${key}" "${value}" >> "${TMP_FILE}"
      replaced_count=$((replaced_count + 1))
      ;;
    *)
      printf '%s\n' "${line}" >> "${TMP_FILE}"
      ;;
  esac
done < "${INPUT_FILE}"

if [ "${FORCE_OVERWRITE}" -eq 1 ] && [ -f "${OUTPUT_FILE}" ]; then
  rm -f "${OUTPUT_FILE}"
fi

mv "${TMP_FILE}" "${OUTPUT_FILE}"
trap - EXIT

echo "[gen-env] 生成完成: ${OUTPUT_FILE}"
echo "[gen-env] 共替换 ${replaced_count} 个 __CHANGE_ME__"
