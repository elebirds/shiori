# shiori-notify

Go 通知服务（Gin + WebSocket + Kafka CDC + MySQL）。

## 已实现能力

- `GET /healthz`
- `GET /metrics`（可关闭）
- `GET /ws?accessToken=<jwt>&lastEventId=<id>`
- `GET /api/notify/events?afterEventId=<id>&limit=<n>`
- `POST /api/notify/events/{eventId}/read`
- `POST /api/notify/events/read-all`
- `GET /api/notify/summary`
- `GET /api/chat/conversations?cursor=<id>&limit=<n>`
- `GET /api/chat/conversations/{conversationId}/messages?before=<id>&limit=<n>`
- `POST /api/chat/conversations/{conversationId}/read`
- `WebSocket` 入站：`join` / `send` / `read`
- `WebSocket` 出站：`join_ack` / `send_ack` / `chat_message` / `read_ack` / `error`
- 支持事件类型：
  - `OrderCreated`
  - `OrderPaid`
  - `OrderCanceled`
  - `OrderDelivered`
  - `OrderFinished`
  - `UserStatusChanged`
  - `UserRoleChanged`
  - `UserPasswordReset`
- 存储驱动：
  - `memory`
  - `mysql`（表：`n_notification_event`）
- Chat（可选，默认关闭）：
  - Ticket：RS256 本地验票（Java 签发）
  - 会话表：`conversation` / `message` / `member_state`
  - 多实例推送：Redis Pub/Sub 广播（channel 默认 `shiori.chat.event`）

## 运行

`shiori-notify` 采用 Nacos 作为配置中心，业务配置不再从环境变量直接读取。

```bash
cd shiori-notify
./gen-env.sh -f
go run .
```

`go run .` 会自动尝试读取当前目录 `.env`。如需指定文件，可设置 `NOTIFY_ENV_FILE=/path/to/file.env`。

`gen-env.sh` 只会生成 Nacos 连接参数：`NACOS_ADDR`、`NACOS_USERNAME`、`NACOS_PASSWORD`、`NACOS_CONFIG_GROUP`、`NACOS_CONFIG_NAMESPACE`。

## 启动必需环境变量（仅 Nacos 连接）

| 变量名 | 默认值 | 说明 |
|---|---|---|
| `NACOS_ADDR` | `nacos:8848` | Nacos 地址 |
| `NACOS_USERNAME` | 空 | Nacos 用户名（必填） |
| `NACOS_PASSWORD` | 空 | Nacos 密码（必填） |
| `NACOS_CONFIG_GROUP` | `SHIORI_DEV_DOCKER` | Nacos 配置组 |
| `NACOS_CONFIG_NAMESPACE` | 空 | Nacos 命名空间（可选） |

## Nacos DataId

启动时按顺序拉取并合并：

1. `shiori-notify-service-base.yml`
2. `shiori-notify-service-secret.yml`
3. `shiori-security-base.yml`
4. `shiori-security-secret.yml`

## ws-smoke

```bash
cd shiori-notify
go run ./cmd/ws-smoke \
  -base-url ws://localhost:8090/ws \
  -access-token '<access-jwt>' \
  -expect-type OrderPaid \
  -expect-aggregate Oxxxx \
  -timeout 60s
```

## chat-smoke

```bash
cd shiori-notify
go run ./cmd/chat-smoke \
  -base-url ws://localhost:8090/ws \
  -buyer-access-token '<buyer-access-jwt>' \
  -seller-access-token '<seller-access-jwt>' \
  -chat-ticket '<chat-ticket>' \
  -conversation-id 11 \
  -client-msg-id smoke-msg-1 \
  -content 'hello from smoke' \
  -timeout 60s
```

## Chat WS 协议示例

```json
{"type":"join","chatTicket":"<chat-ticket>"}
{"type":"send","conversationId":11,"clientMsgId":"m-1","content":"你好，教材还在吗？"}
{"type":"read","conversationId":11,"lastReadMsgId":21}
```

返回示例：

```json
{"type":"join_ack","conversationId":11,"listingId":101,"buyerId":1001,"sellerId":2002}
{"type":"send_ack","conversationId":11,"clientMsgId":"m-1","messageId":21,"deduplicated":false}
{"type":"chat_message","conversationId":11,"messageId":21,"senderId":1001,"receiverId":2002,"content":"你好，教材还在吗？"}
{"type":"read_ack","conversationId":11,"lastReadMsgId":21}
```
