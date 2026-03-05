# Shiori v0.6 发布准入与排障 Runbook（M1：咨询到下单转化）

## 1. 范围

1. 覆盖 `v0.6` 当前已落地范围：聊天到下单转化链路（M1）。
2. 本文不覆盖 M2/M3/M4（治理、能力封禁、补偿链路增强）。

## 2. 发布前检查

### 2.1 后端

1. 测试通过：`./gradlew :shiori-order-service:test`。
2. Flyway 迁移已执行：`V7__add_chat_source_to_order.sql`。
3. `/api/v2/order/**` 路由已在网关生效。
4. 订单服务指标可采集：
   1. `chat_to_order_click_total{source}`
   2. `chat_to_order_submit_total{source}`
   3. `chat_trade_status_card_sent_total{status}`

### 2.2 前端

1. 用户端类型检查通过：`pnpm typecheck`（目录：`shiori-app`）。
2. 聊天页存在“去下单”按钮，点击会跳转到目标商品详情并带 `conversationId`。
3. 商品详情下单后会携带 `source=CHAT` 与 `conversationId`。

## 3. 核心验收路径

1. 买家在商品详情点击“咨询卖家”，进入聊天会话。
2. 在聊天页点击“去下单”：
   1. 观察 `chat_to_order_click_total{source="CHAT"}` 增长。
3. 在商品页提交订单：
   1. 观察 `chat_to_order_submit_total{source="CHAT"}` 增长。
   2. 订单详情返回 `source=CHAT`、`conversationId`、`listingId`。
4. 完成支付 / 发货 / 收货：
   1. 会话中出现交易状态卡片。
   2. `chat_trade_status_card_sent_total{status=...}` 增长。

## 4. 常见故障排查

1. 聊天页“去下单”无跳转：
   1. 检查会话是否包含 `listingId` 与 `conversationId`。
   2. 检查前端路由是否被守卫拦截。
2. 下单后没有关联会话：
   1. 检查下单请求体是否包含 `source=CHAT` 与 `conversationId`。
   2. 检查 `o_order` 新列是否存在：`biz_source/chat_conversation_id/chat_listing_id`。
3. 交易状态卡片未显示：
   1. 检查客户端是否成功发送状态卡片消息。
   2. 检查聊天会话成员权限和 WebSocket 连接状态。
4. 指标不增长：
   1. 检查 `/actuator/prometheus` 是否暴露新指标。
   2. 检查 Prometheus 抓取目标与标签过滤规则。

## 5. 回滚策略

1. 前端紧急回滚：隐藏聊天“去下单”入口（仅保留原下单入口）。
2. 后端兼容回滚：保留 `source/conversationId` 字段但不依赖其强逻辑。
3. 指标侧回滚：Grafana 面板临时下线 v0.6 转化指标，避免误告警。

## 6. 注意事项

1. 当前版本仅做了参数合法性校验，未完成服务端“会话归属强校验”。
2. `product_card` 消息类型尚未落地，不影响当前转化主链路。
