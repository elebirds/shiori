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
