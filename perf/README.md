# Shiori Perf Baseline (k6)

本目录提供交易链路性能基线脚本：

1. `k6-order.js`：下单 -> 支付 -> 卖家发货 -> 买家确认收货 -> 订单详情 的 v2 履约链路压测。
2. `k6-ws.js`：WebSocket 建连后触发支付并统计通知到达时延。
3. `k6-chat-ws.js`：咨询聊天建连 + join + send 骨架脚本（v0.5-b）。

## 前置条件

1. 网关与 notify 可访问：
   1. `http://localhost:8080`
   2. `ws://localhost:8090/ws`
2. user/product/order/gateway/notify 已启动并可通过健康检查。
3. 已安装 `k6`。

## 快速执行

```bash
cd perf
k6 run k6-order.js
k6 run k6-ws.js
k6 run k6-chat-ws.js
```

## 常用参数

### 通用

1. `PERF_GATEWAY_BASE_URL`：网关地址，默认 `http://localhost:8080`
2. `PERF_NOTIFY_WS_BASE_URL`：notify WS 地址，默认 `ws://localhost:8090/ws`
3. `PERF_NOTIFY_HTTP_BASE_URL`：notify HTTP 地址（可选，默认由 WS 地址推导）
4. `PERF_PREFIX`：测试数据前缀，默认 `perf`

### `k6-order.js`

1. `K6_ORDER_VUS`：并发 VU，默认 `5`
2. `K6_ORDER_DURATION`：压测时长，默认 `45s`

### `k6-ws.js`

1. `K6_WS_VUS`：并发 VU，默认 `2`
2. `K6_WS_ITERATIONS`：总迭代次数，默认 `10`
3. `K6_WS_TIMEOUT_MS`：单次 WS 等待超时，默认 `10000`

### `k6-chat-ws.js`

1. `K6_CHAT_WS_URL`：聊天 WS 地址，默认 `ws://localhost:8090/ws`
2. `K6_CHAT_ACCESS_TOKEN`：登录 access token（必填）
3. `K6_CHAT_TICKET`：`/api/product/chat/ticket` 签发的 chat ticket（必填）
4. `K6_CHAT_CONVERSATION_ID`：可选；不填则使用 `join_ack` 返回值
5. `K6_CHAT_ITERATIONS`：每个 VU 发送次数，默认 `1`

## 默认阈值

1. HTTP 失败率：`http_req_failed < 1%`
2. 订单链路：
   1. `shiori_perf_order_create_duration_ms p95 < 400ms`
   2. `shiori_perf_order_pay_duration_ms p95 < 400ms`
   3. `shiori_perf_order_deliver_duration_ms p95 < 400ms`
   4. `shiori_perf_order_confirm_duration_ms p95 < 400ms`
   5. `shiori_perf_order_detail_duration_ms p95 < 400ms`
3. WS 通知：
   1. `shiori_perf_ws_notification_latency_ms p95 < 2000ms`
   2. `shiori_perf_ws_timeout_total == 0`

阈值失败时 k6 退出码非 0，可直接用于 CI 判定（建议先非阻塞运行）。
