#!/usr/bin/env bash
set -euo pipefail

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
NOTIFY_WS_BASE_URL="${NOTIFY_WS_BASE_URL:-ws://localhost:8090/ws}"
SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-60}"
SMOKE_PREFIX="${SMOKE_PREFIX:-chatsmoke}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CHAT_SMOKE_CMD_DIR="${ROOT_DIR}/shiori-notify/cmd/chat-smoke"
TMP_DIR="$(mktemp -d)"
START_TS="$(date +%s)"

log() {
  echo "[chat-smoke] $*"
}

fail() {
  echo "[chat-smoke][ERROR] $*" >&2
  exit 1
}

cleanup() {
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "缺少依赖命令: $1"
  fi
}

require_command curl
require_command jq
require_command go

if [[ ! -d "${CHAT_SMOKE_CMD_DIR}" ]]; then
  fail "未找到 chat-smoke 命令目录: ${CHAT_SMOKE_CMD_DIR}"
fi

NOTIFY_HTTP_BASE_URL="${NOTIFY_HTTP_BASE_URL:-}"
if [[ -z "${NOTIFY_HTTP_BASE_URL}" ]]; then
  NOTIFY_HTTP_BASE_URL="${NOTIFY_WS_BASE_URL/ws:\/\//http://}"
  NOTIFY_HTTP_BASE_URL="${NOTIFY_HTTP_BASE_URL/wss:\/\//https://}"
  NOTIFY_HTTP_BASE_URL="${NOTIFY_HTTP_BASE_URL%/ws}"
fi

call_api() {
  local method="$1"
  local path="$2"
  local token="$3"
  local body="$4"
  shift 4
  local extra_headers=("$@")
  local response_file="${TMP_DIR}/resp.$RANDOM.json"
  local -a cmd=(curl -sS -o "${response_file}" -w "%{http_code}" -X "${method}" "${GATEWAY_BASE_URL}${path}")

  if [[ -n "${token}" ]]; then
    cmd+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "${body}" ]]; then
    cmd+=(-H "Content-Type: application/json" --data "${body}")
  fi
  if (( ${#extra_headers[@]} > 0 )); then
    cmd+=("${extra_headers[@]}")
  fi

  local status
  status="$("${cmd[@]}")" || fail "请求失败: ${method} ${path}"
  local resp
  resp="$(cat "${response_file}")"
  if [[ "${status:0:1}" != "2" ]]; then
    fail "请求返回非 2xx: ${method} ${path}, status=${status}, body=${resp}"
  fi

  local code
  code="$(echo "${resp}" | jq -r '.code // empty' 2>/dev/null || true)"
  if [[ "${code}" != "0" ]]; then
    fail "请求业务码非 0: ${method} ${path}, code=${code}, body=${resp}"
  fi
  echo "${resp}"
}

extract_required() {
  local json="$1"
  local expr="$2"
  local name="$3"
  local value
  value="$(echo "${json}" | jq -r "${expr}")"
  if [[ -z "${value}" || "${value}" == "null" ]]; then
    fail "提取字段失败: ${name}, expr=${expr}, body=${json}"
  fi
  echo "${value}"
}

log "检查网关健康状态..."
gateway_health="$(curl -fsS "${GATEWAY_BASE_URL}/actuator/health")" || fail "网关健康检查失败"
gateway_status="$(echo "${gateway_health}" | jq -r '.status // empty')"
[[ "${gateway_status}" == "UP" ]] || fail "网关未就绪: ${gateway_health}"

log "检查 notify 健康状态..."
notify_health="$(curl -fsS "${NOTIFY_HTTP_BASE_URL}/healthz")" || fail "notify 健康检查失败"
notify_status="$(echo "${notify_health}" | jq -r '.status // empty')"
[[ "${notify_status}" == "ok" ]] || fail "notify 未就绪: ${notify_health}"

raw_id="$(date +%s)$RANDOM"
id_suffix="${raw_id}"
if (( ${#id_suffix} > 8 )); then
  id_suffix="${id_suffix: -8}"
fi
safe_prefix="$(echo "${SMOKE_PREFIX}" | tr -cd 'a-zA-Z0-9_' | tr '[:upper:]' '[:lower:]')"
safe_prefix="${safe_prefix:-smk}"
safe_prefix="${safe_prefix:0:8}"
run_id="${raw_id}"

seller_username="${safe_prefix}chs${id_suffix}"
buyer_username="${safe_prefix}chb${id_suffix}"
seller_password="S${id_suffix}b!"
buyer_password="B${id_suffix}b!"

log "注册 seller/buyer..."
seller_register_payload="$(jq -nc \
  --arg username "${seller_username}" \
  --arg password "${seller_password}" \
  --arg nickname "ChatSeller ${run_id}" \
  '{username:$username,password:$password,nickname:$nickname}')"
seller_register_resp="$(call_api POST "/api/user/auth/register" "" "${seller_register_payload}")"
seller_user_id="$(extract_required "${seller_register_resp}" '.data.userId | tostring' "seller.userId")"

buyer_register_payload="$(jq -nc \
  --arg username "${buyer_username}" \
  --arg password "${buyer_password}" \
  --arg nickname "ChatBuyer ${run_id}" \
  '{username:$username,password:$password,nickname:$nickname}')"
buyer_register_resp="$(call_api POST "/api/user/auth/register" "" "${buyer_register_payload}")"
buyer_user_id="$(extract_required "${buyer_register_resp}" '.data.userId | tostring' "buyer.userId")"

log "登录 seller/buyer..."
seller_login_payload="$(jq -nc --arg username "${seller_username}" --arg password "${seller_password}" '{username:$username,password:$password}')"
seller_login_resp="$(call_api POST "/api/user/auth/login" "" "${seller_login_payload}")"
seller_access_token="$(extract_required "${seller_login_resp}" '.data.accessToken' "seller.accessToken")"

buyer_login_payload="$(jq -nc --arg username "${buyer_username}" --arg password "${buyer_password}" '{username:$username,password:$password}')"
buyer_login_resp="$(call_api POST "/api/user/auth/login" "" "${buyer_login_payload}")"
buyer_access_token="$(extract_required "${buyer_login_resp}" '.data.accessToken' "buyer.accessToken")"

log "seller 创建并发布商品..."
product_payload="$(jq -nc \
  --arg title "Chat Smoke 商品 ${run_id}" \
  --arg description "聊天链路烟测商品" \
  '{
    title:$title,
    description:$description,
    coverObjectKey:null,
    skus:[
      {skuName:"咨询版",specJson:"{\"edition\":\"chat\"}",priceCent:1099,stock:20}
    ]
  }')"
product_resp="$(call_api POST "/api/product/products" "${seller_access_token}" "${product_payload}")"
product_id="$(extract_required "${product_resp}" '.data.productId | tostring' "product.id")"
call_api POST "/api/product/products/${product_id}/publish" "${seller_access_token}" "" >/dev/null

log "buyer 签发 Chat Ticket..."
ticket_resp="$(call_api POST "/api/product/chat/ticket?listingId=${product_id}" "${buyer_access_token}" "")"
chat_ticket="$(extract_required "${ticket_resp}" '.data.ticket' "chat.ticket")"
ticket_buyer_id="$(extract_required "${ticket_resp}" '.data.buyerId | tostring' "chat.ticket.buyerId")"
ticket_seller_id="$(extract_required "${ticket_resp}" '.data.sellerId | tostring' "chat.ticket.sellerId")"
ticket_listing_id="$(extract_required "${ticket_resp}" '.data.listingId | tostring' "chat.ticket.listingId")"
[[ "${ticket_buyer_id}" == "${buyer_user_id}" ]] || fail "ticket buyerId 不匹配: ${ticket_buyer_id} vs ${buyer_user_id}"
[[ "${ticket_seller_id}" == "${seller_user_id}" ]] || fail "ticket sellerId 不匹配: ${ticket_seller_id} vs ${seller_user_id}"
[[ "${ticket_listing_id}" == "${product_id}" ]] || fail "ticket listingId 不匹配: ${ticket_listing_id} vs ${product_id}"

log "buyer 通过 HTTP 启动会话..."
start_payload="$(jq -nc --arg chatTicket "${chat_ticket}" '{chatTicket:$chatTicket}')"
start_resp="$(call_api POST "/api/chat/conversations/start" "${buyer_access_token}" "${start_payload}")"
conversation_id="$(extract_required "${start_resp}" '.data.conversationId | tostring' "chat.start.conversationId")"

client_msg_id="${SMOKE_PREFIX}-chat-${run_id}"
message_content="chat smoke message ${run_id}"
ws_log="${TMP_DIR}/chat-smoke.ws.log"
log "执行 WS 聊天探针（join/send/chat_message）..."
if ! (
  cd "${ROOT_DIR}/shiori-notify"
  go run ./cmd/chat-smoke \
    -base-url "${NOTIFY_WS_BASE_URL}" \
    -buyer-access-token "${buyer_access_token}" \
    -seller-access-token "${seller_access_token}" \
    -chat-ticket "${chat_ticket}" \
    -conversation-id "${conversation_id}" \
    -client-msg-id "${client_msg_id}" \
    -content "${message_content}" \
    -timeout "${SMOKE_TIMEOUT_SECONDS}s"
) >"${ws_log}" 2>&1; then
  cat "${ws_log}" >&2 || true
  fail "WS 聊天探针失败"
fi
cat "${ws_log}"

log "校验 seller 会话与消息列表..."
seller_conversations_resp="$(call_api GET "/api/chat/conversations?limit=20" "${seller_access_token}" "")"
seller_contains_conversation="$(echo "${seller_conversations_resp}" | jq -r --arg cid "${conversation_id}" '.data.items | map((.conversationId|tostring)==$cid) | any')"
[[ "${seller_contains_conversation}" == "true" ]] || fail "seller 会话列表缺少 conversationId=${conversation_id}"
seller_has_unread="$(echo "${seller_conversations_resp}" | jq -r --arg cid "${conversation_id}" '.data.items[] | select((.conversationId|tostring)==$cid) | .hasUnread')"
[[ "${seller_has_unread}" == "true" ]] || fail "seller 会话未标记未读"

seller_messages_resp="$(call_api GET "/api/chat/conversations/${conversation_id}/messages?limit=20" "${seller_access_token}" "")"
first_client_msg_id="$(extract_required "${seller_messages_resp}" '.data.items[0].clientMsgId' "seller.messages[0].clientMsgId")"
first_content="$(extract_required "${seller_messages_resp}" '.data.items[0].content' "seller.messages[0].content")"
first_message_id="$(extract_required "${seller_messages_resp}" '.data.items[0].messageId | tostring' "seller.messages[0].messageId")"
[[ "${first_client_msg_id}" == "${client_msg_id}" ]] || fail "消息 clientMsgId 不匹配: ${first_client_msg_id} vs ${client_msg_id}"
[[ "${first_content}" == "${message_content}" ]] || fail "消息内容不匹配: ${first_content} vs ${message_content}"

log "校验 seller summary 未读统计..."
seller_summary_resp="$(call_api GET "/api/chat/summary" "${seller_access_token}" "")"
seller_unread_msg_count="$(extract_required "${seller_summary_resp}" '.data.unreadMessageCount | tostring' "seller.summary.unreadMessageCount")"
seller_unread_conv_count="$(extract_required "${seller_summary_resp}" '.data.unreadConversationCount | tostring' "seller.summary.unreadConversationCount")"
[[ "${seller_unread_msg_count}" =~ ^[0-9]+$ ]] || fail "seller unreadMessageCount 非数字: ${seller_unread_msg_count}"
[[ "${seller_unread_conv_count}" =~ ^[0-9]+$ ]] || fail "seller unreadConversationCount 非数字: ${seller_unread_conv_count}"
(( seller_unread_msg_count >= 1 )) || fail "seller unreadMessageCount 期望 >=1，实际 ${seller_unread_msg_count}"
(( seller_unread_conv_count >= 1 )) || fail "seller unreadConversationCount 期望 >=1，实际 ${seller_unread_conv_count}"

log "seller 标记已读并复核 summary..."
read_payload="$(jq -nc --argjson lastReadMsgId "${first_message_id}" '{lastReadMsgId:$lastReadMsgId}')"
read_resp="$(call_api POST "/api/chat/conversations/${conversation_id}/read" "${seller_access_token}" "${read_payload}")"
read_msg_id="$(extract_required "${read_resp}" '.data.lastReadMsgId | tostring' "chat.read.lastReadMsgId")"
[[ "${read_msg_id}" == "${first_message_id}" ]] || fail "lastReadMsgId 不匹配: ${read_msg_id} vs ${first_message_id}"

seller_summary_after_resp="$(call_api GET "/api/chat/summary" "${seller_access_token}" "")"
seller_unread_msg_after="$(extract_required "${seller_summary_after_resp}" '.data.unreadMessageCount | tostring' "seller.summary.after.unreadMessageCount")"
[[ "${seller_unread_msg_after}" == "0" ]] || fail "seller 已读后 unreadMessageCount 非 0: ${seller_unread_msg_after}"

cost_seconds="$(( $(date +%s) - START_TS ))"
log "聊天链路烟测成功"
log "buyerUserId=${buyer_user_id}, sellerUserId=${seller_user_id}, productId=${product_id}, conversationId=${conversation_id}, duration=${cost_seconds}s"
