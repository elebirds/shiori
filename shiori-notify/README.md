# shiori-notify

Go 边缘通知服务初始化骨架（Gin + WebSocket + RabbitMQ）。

## 已实现

- `GET /healthz` 健康检查
- `GET /metrics` Prometheus 指标导出（可通过配置关闭）
- `GET /ws?userId=<id>` WebSocket 接入与会话管理
- `GET /api/notify/events?userId=<id>&afterEventId=<id>&limit=<n>` 补偿拉取接口
- RabbitMQ 消费骨架（拓扑声明、消费、重连、事件解析）
- 事件路由骨架（仅处理 `OrderPaid`）
- 事件存储与补偿回放：
  - 按用户保存事件窗口（内存实现）
  - WS 建连支持 `lastEventId` 回放（`/ws?userId=<id>&lastEventId=<id>`）
  - MQ 重投场景按 `eventId` 去重，避免重复推送
- 基础业务指标：
  - `shiori_notify_ws_connections`
  - `shiori_notify_ws_push_total{result,event_type}`
  - `shiori_notify_mq_consume_total{result,event_type}`
  - `shiori_notify_mq_route_duration_seconds{event_type}`
  - `shiori_notify_replay_query_total{source,result}`
  - `shiori_notify_replay_events_total{source}`

## 运行

```bash
cd shiori-notify
go run .
```

## 环境变量

| 变量名 | 默认值 | 说明 |
|---|---|---|
| `NOTIFY_HTTP_ADDR` | `:8090` | HTTP 监听地址 |
| `RABBITMQ_ADDR` | `amqp://localhost:5672/` | RabbitMQ 连接串 |
| `RABBITMQ_EXCHANGE` | `shiori.order.event` | topic exchange 名称 |
| `RABBITMQ_QUEUE` | `notify.order.paid` | 消费队列名称 |
| `RABBITMQ_ROUTING_KEY` | `order.paid` | 绑定 routing key |
| `LOG_LEVEL` | `info` | `debug/info/warn/error` |
| `WS_WRITE_TIMEOUT` | `5s` | WS 写超时 |
| `WS_PING_INTERVAL` | `30s` | WS ping 间隔 |
| `NOTIFY_METRICS_ENABLED` | `true` | 是否启用 `/metrics` 指标导出 |
| `NOTIFY_EVENT_STORE_MAX_PER_USER` | `1000` | 每个用户保留的事件窗口大小 |
| `NOTIFY_REPLAY_DEFAULT_LIMIT` | `50` | 补偿拉取默认分页大小 |
| `NOTIFY_REPLAY_MAX_LIMIT` | `200` | 补偿拉取单次最大分页大小 |
| `NOTIFY_WS_REPLAY_DEFAULT_LIMIT` | `100` | WS 建连补偿回放的默认上限 |

## 事件 Envelope

```json
{
  "eventId": "uuid",
  "type": "OrderPaid",
  "aggregateId": "orderNo",
  "createdAt": "2026-03-02T00:00:00Z",
  "payload": {
    "userId": "u1"
  }
}
```

## 当前边界

- 当前只路由 `OrderPaid` 事件。
- 未实现业务通知模板、离线消息、分布式会话共享。
