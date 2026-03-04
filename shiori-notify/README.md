# shiori-notify

Go 通知服务（Gin + WebSocket + RabbitMQ + MySQL）。

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
  - 多实例推送：RabbitMQ fanout 广播（`shiori.chat.event`）

## 运行

```bash
cd shiori-notify
./gen-env.sh -f            # 默认生成 memory 模式 .env
# ./gen-env.sh -f -m mysql # 需要本地可用的 shiori_notify 库
go run .
```

> 若 `deploy/.env` 不在默认路径，可使用 `./gen-env.sh -i <path-to-env> -f`。
> `go run .` 会自动尝试读取当前目录 `.env`。如需指定文件，可设置 `NOTIFY_ENV_FILE=/path/to/file.env`。

## 环境变量

| 变量名 | 默认值 | 说明 |
|---|---|---|
| `NOTIFY_HTTP_ADDR` | `:8090` | HTTP 监听地址 |
| `RABBITMQ_ADDR` | `amqp://localhost:5672/` | RabbitMQ 连接串 |
| `RABBITMQ_EXCHANGES` | `shiori.order.event,shiori.user.event` | 订阅 exchange 列表 |
| `RABBITMQ_QUEUE` | `notify.order.event` | 消费队列 |
| `RABBITMQ_ROUTING_KEYS` | `order.created,order.paid,order.canceled,order.delivered,order.finished,user.status.changed,user.role.changed,user.password.reset` | 绑定 routing key 列表 |
| `NOTIFY_STORE_DRIVER` | `memory` | `memory` 或 `mysql` |
| `NOTIFY_MYSQL_DSN` | 空 | `mysql` 驱动必填 |
| `NOTIFY_AUTH_ENABLED` | `true` | 是否启用 JWT 鉴权 |
| `NOTIFY_JWT_HMAC_SECRET` | 空 | JWT HMAC 密钥 |
| `NOTIFY_JWT_ISSUER` | `shiori` | JWT issuer |
| `NOTIFY_EVENT_STORE_MAX_PER_USER` | `1000` | 每个用户最大保留事件数 |
| `NOTIFY_REPLAY_DEFAULT_LIMIT` | `50` | 补偿拉取默认条数 |
| `NOTIFY_REPLAY_MAX_LIMIT` | `200` | 补偿拉取最大条数 |
| `NOTIFY_WS_REPLAY_DEFAULT_LIMIT` | `100` | WS 建连补偿上限 |
| `NOTIFY_WS_PATH` | `/ws` | WebSocket 路径 |
| `NOTIFY_CHAT_ENABLED` | `false` | 是否启用聊天能力 |
| `NOTIFY_CHAT_DEFAULT_LIMIT` | `20` | 聊天分页默认条数 |
| `NOTIFY_CHAT_MAX_LIMIT` | `100` | 聊天分页最大条数 |
| `NOTIFY_CHAT_TICKET_ISSUER` | `shiori-chat-ticket` | Chat Ticket issuer |
| `NOTIFY_CHAT_TICKET_PUBLIC_KEY_PEM_BASE64` | 空 | RS256 公钥（PEM 文本再 base64） |
| `NOTIFY_CHAT_MQ_ENABLED` | `true` | 是否开启跨实例聊天广播 |
| `NOTIFY_CHAT_MQ_EXCHANGE` | `shiori.chat.event` | 广播 exchange |
| `NOTIFY_INSTANCE_ID` | `<hostname>-<pid>` | 实例标识，用于广播去重 |

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
