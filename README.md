# 📖 Shiori (栞) - Campus Textbook Trading Platform

> “栞”（しおり），意为书签。一本教材的流转，就像上一位读者在书中夹入书签，交递给下一位求知者。

**Shiori** 是一个基于 **核心/边缘异构微服务（Core/Edge Polyglot Microservices）** 的校园二手教材交易平台：
- **Core（Java / Spring）**：承载交易关键链路（用户、商品/库存、订单、支付（余额托管）、权限）
- **Edge（Go / WebSocket）**：承载实时通知与长连接推送（事件消费 → 会话路由 → 推送）

项目目标：以可落地、可验证的方式实践分布式系统关键问题：**防超卖、最终一致性、超时关单、幂等、可观测性与压测资产**。

## 🧭 Kafka CDC 迁移进度（进行中）

1. 基础设施已就绪：
   1. `deploy/docker-compose.yml` 已加入 `kafka`、`kafka-connect`、Debezium Connect 初始化。
   2. `deploy/kafka/connect/*.json` 已为 `order/payment/product/user` outbox 准备 CDC connector。
2. Outbox 元数据标准化已完成：
   1. `order-service`：`o_outbox_event` 已补 `aggregate_type/message_key`
   2. `payment-service`：`p_wallet_balance_outbox` 已补 `aggregate_type/aggregate_id/message_key`
   3. `product-service`：`p_outbox_event` 已补 `aggregate_type/message_key`
   4. `user-service`：`u_outbox_event` 已补 `aggregate_type/message_key`
3. 第一条 Kafka 消费链已落地：
   1. `social-service` 新增 `ProductOutboxCdcConsumer`
   2. 读取 topic：`shiori.cdc.product.outbox.raw`
   3. 开关：`SOCIAL_KAFKA_ENABLED=true`
4. 第二条 Kafka 消费链已落地：
   1. `order-service` 新增 `WalletBalanceOutboxCdcConsumer`
   2. 读取 topic：`shiori.cdc.payment.outbox.raw`
   3. 开关：`ORDER_KAFKA_ENABLED=true`
   4. `order-service` 已不再消费 Rabbit 的 `WalletBalanceChanged` 事件
   5. `OrderTimeout` 已改为 DB 定时扫描，不再依赖 Rabbit TTL/DLX
5. 第三条 Kafka 消费链已落地：
   1. `shiori-notify` 新增 `internal/kafkaevent.Consumer`
   2. 读取 topics：`shiori.cdc.order.outbox.raw`、`shiori.cdc.user.outbox.raw`
   3. 开关：`NOTIFY_KAFKA_ENABLED=true`
   4. order/user 业务事件订阅已完成 Kafka CDC 切换
6. CDC 消费注意事项：
   1. Debezium 会输出 outbox 行后续的 `SENT/FAILED` 更新
   2. 消费端必须只处理 `status=PENDING` 的记录
   3. 当前已落地的 Kafka 消费器均已加入该过滤，避免重复消费
7. 当前策略：
   1. 已切换链路优先删除对应 Rabbit fallback，而不是长期保留双通道
   2. 当前业务事件链路已收敛到 Kafka CDC
   3. notify 多实例 chat 广播已切到 Redis Pub/Sub，仓库运行时消息基础设施收敛为 Kafka + Redis

## ✨ 核心架构与技术亮点

### 🚀 核心/边缘异构微服务 (Core & Edge Polyglot)

- **Java 核心域 (Spring Cloud 体系)**  
  采用 **JDK 21 + Spring Boot 4.0.x + Spring Cloud（Gateway / OpenFeign）** 承载核心交易链路（用户/商品/订单/支付）。  
  重点落地：订单状态机、库存扣减、幂等控制、Outbox 事件发布、超时关单等可追问能力。

- **Go 边缘域 (Gin + WebSocket)**  
  仅负责“事件消费 → 会话路由 → 推送”，将长连接与推送压力从核心域剥离。  
  **可水平扩展**：notify 多实例部署时，每个实例维护自身连接；事件广播后仅推送本机命中的会话（实现简单且有效）。

### 🛡️ 交易一致性与并发容错（可经受追问）

- **极简防超卖机制（正确性由 DB 兜底）**  
  不将 Redis 锁作为库存正确性的前提，采用 MySQL 原子更新作为并发安全根基：  
  ```sql
  UPDATE sku
  SET stock = stock - 1
  WHERE sku_id = ? AND stock >= 1;
  ```

通过检查 `affected_rows` 判断是否扣减成功，天然支持多实例并发扣减不超卖。

* **事件驱动与最终一致性（Transactional Outbox + Kafka CDC）**
  订单服务在**同一数据库事务**中写入业务数据与 Outbox 事件（如 `OrderCreated / OrderPaid / OrderCanceled / OrderDelivered / OrderFinished`），避免“写库成功但发消息失败”。
  通过 **Debezium + Kafka Connect** 订阅 Outbox 变更并投递到 Kafka，消费端按 `status=PENDING` 过滤处理，实现最终一致性而不引入重型强事务框架。

* **At-Least-Once 投递语义下的幂等闭环**
  MQ 投递采用 at-least-once（允许重复），因此系统通过幂等设计消除重复副作用：

  * **HTTP 幂等**：下单/支付回调支持 `Idempotency-Key` 或业务唯一键（如 `order_no`、`payment_no`），重复请求不重复扣库存/不重复改状态
  * **消息幂等**：事件携带 `event_id`，消费端通过（DB 唯一键/去重表/Redis SETNX）保证重复消息不重复通知

### ⏳ 超时关单（DB 扫描 + 幂等关单）

* 下单时仅写入 `o_order.timeout_at`，不再额外发送 `OrderTimeout` 延迟消息。
* `order-service` 内部定时任务按批扫描 `status=UNPAID and timeout_at<=now` 的订单号。
* 调度器逐条调用 `handleTimeout(orderNo)`：

  * 若已支付或已取消：直接忽略（幂等）
  * 若仍未支付：关单 + 回滚库存 + 写入 `OrderCanceled` Outbox 事件

> 注：该方案把超时处理收敛回订单库自身，避免 Rabbit TTL/DLX 队列堆积与重复投递干扰；多实例下即便重复扫描，也由状态更新条件保证只会成功关单一次。

### 📦 现代化工程规范（交付友好 + 易演示）

* **Monorepo (单体仓库) 治理**
  根目录按语言与生态隔离 Java/Go/前端工程，兼顾代码聚合与跨 IDE（IDEA/GoLand）的原生体验。

* **严格的数据基建（Schema Versioning）**
  摒弃 Hibernate/JPA 式 `ddl-auto` 自动建表，使用 **Flyway** 管理数据库迁移（可追溯/可回滚）。
  金额字段统一使用 `BIGINT` 存“分”，杜绝精度丢失与对账差异。

* **统一安全网关**
  基于 API Gateway 实现全局 JWT 验签、RBAC 权限拦截与基础限流；关键写接口结合 Redis 做防刷/短期幂等保护（非库存正确性依赖）。

* **可观测性（建议落地）**
  通过 Spring Boot Actuator 暴露核心指标（QPS、p95、错误率、线程/连接池），配合 Prometheus + Grafana 可视化；
  notify 服务输出在线连接数、推送成功率、消费延迟等关键指标，便于压测与复盘。

## 🔄 核心订单流转与事件流 (State & Event Flow)

本项目通过事务内 Outbox、Kafka CDC 与必要的异步总线完成事件驱动，核心链路如下：

```text
[用户下单] 
   │
   ├─▶ [Java 订单服务] MySQL 本地事务：生成订单 (Status: UNPAID) + 写入 Outbox(OrderCreated)
   │      │
   │      ├─▶ [Java 商品/库存服务] 原子扣减库存（成功/失败由 affected_rows 判断）
   │      │
   │      └─▶ 持久化 timeout_at，等待定时扫描到期处理
   │
[用户支付成功（v2 余额托管支付）]
   │
   ├─▶ [Java 订单服务] 幂等校验(Idempotency-Key)
   │      ├─▶ 调用 [Java 支付服务] 余额托管（available -> frozen）
   │      ├─▶ 更新订单状态 (Status: PAID)
   │      └─▶ 写入 Outbox(OrderPaid) → Debezium CDC → Kafka
   │
[Go 消息推送边缘服务] 
   │
   └─▶ 持续监听 MQ `OrderPaid` 事件 -> 路由本机 WebSocket 会话 -> 实时推送通知（卖家售出提醒/买家支付成功）

[15 分钟到期未支付]
   │
   └─▶ [Java 订单服务] 定时扫描到期 UNPAID 订单 -> 二次校验订单状态
          ├─ 已支付：忽略（幂等）
          └─ 未支付：关单 + 回滚库存 + Outbox(OrderCanceled) → MQ
```

---

## 🆕 v0.4-b 商品与订单扩展（`/api/v2`）

### 商品域

1. 新增商品字段：`categoryCode`、`conditionLevel`、`tradeMode`、`campusCode`。
2. 新增商品查询参数：`categoryCode/conditionLevel/tradeMode/campusCode/sortBy/sortDir`。
3. 商品列表/详情新增聚合字段：`minPriceCent/maxPriceCent/totalStock`。
4. 管理端新增批量下架：`POST /api/v2/admin/products/batch-off-shelf`。

### 订单域

1. 卖家工作台接口：
   1. `GET /api/v2/order/seller/orders`
   2. `GET /api/v2/order/seller/orders/{orderNo}`
2. 买家确认收货：
   1. `POST /api/v2/order/orders/{orderNo}/confirm-receipt`
3. 履约时间线：
   1. `GET /api/v2/order/orders/{orderNo}/timeline`
4. 管理端履约操作：
   1. `POST /api/v2/admin/orders/{orderNo}/deliver`
   2. `POST /api/v2/admin/orders/{orderNo}/finish`

### 开关、迁移与指标

1. v2 灰度开关：`feature.api-v2.enabled=true`（Nacos 模板已提供）。
2. 数据库迁移：
   1. `shiori-product-service`：`V5__add_product_v2_fields.sql`
   2. `shiori-order-service`：`V6__add_order_v2_indexes.sql`
3. 新增指标：
   1. `shiori_order_transition_total{from,to,source}`
   2. `shiori_product_query_total{filter_combo}`

---

## 🆕 v0.8-d 商品元数据治理（校区管理员化 + 分类二级化）

### 商品域（`/api/v2/product/**`）

1. 商品写接口新增必填字段：`subCategoryCode`。
2. 商品列表查询新增筛选参数：`subCategoryCode`。
3. 商品列表/详情响应新增字段：`subCategoryCode`。
4. 新增元数据读取接口：
   1. `GET /api/v2/product/meta/campuses`
   2. `GET /api/v2/product/meta/categories`（返回一级分类及其子分类树）

### 管理域（`/api/v2/admin/product-meta/**`）

1. 新增校区、一级分类、子分类管理接口（创建/更新/启停/排序，软删除策略）。
2. 新增批量迁移接口：
   1. `POST /api/v2/admin/product-meta/migrations/campuses`
   2. `POST /api/v2/admin/product-meta/migrations/sub-categories`
3. 新增权限码：`product.meta.manage`（网关已映射到 `/api/v2/admin/product-meta/**`）。

### 数据迁移

1. `shiori-product-service`：`V10__create_product_meta_tables_and_sub_category.sql`
   1. 新表：`p_product_campus`、`p_product_category`、`p_product_sub_category`
   2. `p_product` 新增列：`sub_category_code`
   3. 历史数据回填：默认子分类（`*_UNSPEC`）和兜底校区（`UNKNOWN_CAMPUS`）
2. `shiori-user-service`：`V17__add_product_meta_manage_permission.sql`
   1. 新增权限 `product.meta.manage`
   2. 默认授予 `ROLE_ADMIN`

---

## 🆕 v0.5 个人中心与资料编辑拆分

1. 路由拆分：
   1. 个人中心展示页：`/u/{userNo}`（可匿名访问）
   2. 资料编辑页：`/profile/edit`（需登录）
   3. `/profile` 作为本人中心入口，自动跳转到 `/u/{当前用户userNo}`
2. 新增公开接口：
   1. `GET /api/user/profiles/{userNo}`（公开资料）
   2. `GET /api/v2/product/users/{ownerUserId}/products`（仅在售商品）
3. 安全白名单（匿名 GET）补充：
   1. `/api/user/profiles/**`
   2. `/api/user/media/avatar/**`
   3. `/api/v2/product/users/**`

---

## 🆕 v0.5-b 未下单咨询聊天（Chat Ticket + Go Chat Core）

1. 买家在 listing 详情发起咨询流程：
   1. `POST /api/product/chat/ticket?listingId=...`（Java product-service 签发短期 Chat Ticket，RS256）
   2. 客户端 `WS /ws` 建连并发送 `join`（携带 `chatTicket`）
   3. Go `shiori-notify` 本地验票并创建/复用会话（唯一键：`listingId + buyerId + sellerId`）
   4. 客户端使用 `send/read` 进行实时消息与已读同步
2. 聊天主逻辑全部在 Go（连接管理、消息落库、会话/历史 API、推送），避免每条消息回调 Java。
3. 多实例扩展：
   1. 消息落库后本机先 `send_ack`
   2. 推送接收者本机连接
   3. 可选发布 `ChatMessageSent` 到 `shiori.chat.event`，所有实例订阅后仅推本机连接
4. 新增 API：
   1. `GET /api/chat/conversations`
   2. `GET /api/chat/conversations/{id}/messages`
   3. `POST /api/chat/conversations/{id}/read`
5. 数据表（`shiori_notify`）：
   1. `conversation`
   2. `message`（唯一键 `conversation_id + sender_id + client_msg_id`）
   3. `member_state`
6. 初始化方式：
   1. 新环境：`deploy/sql/mysql-init/002_create_notify_chat_tables.sql` 自动执行
   2. 既有环境：手工执行 `deploy/sql/manual/20260304_create_notify_chat_tables.sql`

---

## 🆕 v0.6（M1）咨询到下单转化闭环

1. 聊天页新增“去下单”入口：
   1. 会话页头部可直接跳转目标商品页并带 `conversationId`
   2. 跳转时上报点击指标：`POST /api/v2/order/orders/chat-to-order-click`
2. 下单链路新增会话来源标记：
   1. 下单请求支持 `source` 与 `conversationId`
   2. 当前支持 `source=CHAT`
   3. 订单详情/列表/卖家订单列表均返回 `source/conversationId/listingId`
3. 交易状态卡片（`trade_status_card`）：
   1. 下单后自动发送：`ORDER_CREATED`
   2. 支付后自动发送：`ORDER_PAID`
   3. 发货后自动发送：`ORDER_DELIVERED`
   4. 收货后自动发送：`ORDER_FINISHED`
4. 新增指标：
   1. `chat_to_order_click_total{source}`
   2. `chat_to_order_submit_total{source}`
   3. `chat_trade_status_card_sent_total{status}`
5. 数据库迁移：
   1. `shiori-order-service`：`V7__add_chat_source_to_order.sql`

## 🆕 v0.6（M2）会话治理与基础聊天治理能力

1. 用户侧治理能力：
   1. `POST /api/chat/blocks/{targetUserId}`、`DELETE /api/chat/blocks/{targetUserId}`、`GET /api/chat/blocks`
   2. `POST /api/chat/reports`（会话/消息维度举报）
2. 管理侧治理能力：
   1. 举报处理：`GET /api/admin/chat/reports`、`POST /api/admin/chat/reports/{reportId}/handle`
   2. 拉黑查询：`GET /api/admin/chat/blocks`
   3. 违禁词管理：`GET/POST/PUT/DELETE /api/admin/chat/forbidden-words`
3. 治理策略与落库：
   1. 拉黑双向发送拦截（保留历史消息）
   2. 违禁词策略支持 `REJECT` 与 `MASK`
   3. 发送频控（按用户+会话）返回稳定错误码与冷却秒数
4. 数据库扩展（`shiori_notify`）：
   1. `chat_block`
   2. `chat_report`
   3. `chat_forbidden_word`
   4. `chat_moderation_audit`

## 🆕 v0.6（M3）RBAC 能力级封禁

1. 新增能力封禁管理接口：
   1. `POST /api/v2/admin/users/{userId}/capability-bans`
   2. `DELETE /api/v2/admin/users/{userId}/capability-bans/{capability}`
   3. `GET /api/v2/admin/users/{userId}/capability-bans`
2. 能力项覆盖：
   1. `CHAT_SEND`
   2. `CHAT_READ`
   3. `PRODUCT_PUBLISH`
   4. `ORDER_CREATE`
3. 双重校验：
   1. 网关新增能力封禁过滤器，按路径与方法执行能力校验
   2. notify 聊天发送链路增加 `CHAT_SEND` 服务侧校验，返回稳定错误码（`40304`）
4. 审计与查询：
   1. 封禁记录支持原因、操作人、开始时间、结束时间
   2. 管理端用户页新增能力封禁操作与记录查看

## 🆕 v0.6（M4）聊天可靠性与可观测性增强

1. 聊天消息补偿拉取增强：
   1. `GET /api/chat/conversations/{conversationId}/messages?afterSeq=...`
   2. `afterSeq` 与 `before` 互斥，返回增量消息与 `nextAfterSeq`
2. 已读一致性：
   1. `member_state.last_read_msg_id` 更新使用单调递增策略（防回退、可重放）
3. 可观测指标补齐（notify）：
   1. `shiori_notify_ws_connections`
   2. `shiori_notify_chat_online_sessions`
   3. `shiori_notify_chat_delivery_latency_seconds{path}`
   4. `shiori_notify_chat_compensation_query_total{result}`
   5. `shiori_notify_chat_compensation_messages_total`
   6. `shiori_notify_chat_rate_limit_hit_total{source}`
4. perf 资产新增：
   1. `perf/k6-chat-conversation.js`（建连、发消息、断线重连、`afterSeq` 补偿）

## 🆕 v0.7 支付微服务（余额托管 + CDK）

1. 新增独立支付微服务 `shiori-payment-service`（Spring Boot）：
   1. 余额账户（可用/冻结）与资金流水
   2. 订单托管支付记录（`RESERVED/SETTLED/RELEASED`）
2. v2 支付链路切换为余额托管：
   1. `POST /api/v2/order/orders/{orderNo}/pay` 改为严格无请求体
   2. 支付时买家 `available -> frozen`，订单状态更新为 `PAID`
3. 订单完结触发结算：
   1. 订单进入 `FINISHED` 时执行托管资金转移（买家冻结扣减，卖家可用增加）
   2. 结算失败阻塞完结（状态不进入 `FINISHED`）
4. CDK 能力：
   1. 管理员支持批量创建 CDK（单码一次、可选过期）
   2. 用户支持兑换 CDK 入账余额
5. 新增支付域接口：
   1. 用户：`GET /api/v2/payment/wallet/balance`
   2. 用户：`POST /api/v2/payment/cdks/redeem`
   3. 管理员：`POST /api/v2/admin/payments/cdks/batches`
6. 详细方案文档：
   1. `docs/roadmaps/v0.7-payment-balance-plan.md`
7. v0.7.1 后端补强：
   1. 新增 `X-Shiori-Internal-Token` 内部令牌校验（order -> payment）
   2. 修复 CDK 短码掩码边界，避免明文落库
   3. 修复 `reserve` 并发冲突状态分支
   4. 补齐支付服务单测与控制器鉴权测试
8. v0.8 前端支付对接：
   1. 用户端新增独立收银台：`/checkout/{orderNo}`（PayPal 风格支付页）
   2. 用户端新增钱包页：`/wallet`（余额展示 + CDK 兑换）
   3. 订单页“立即支付”改为跳转收银台，v2 支付严格无请求体
   4. 管理端新增 CDK 管理页：`/payments/cdks`（批次创建、明文一次展示、CSV 导出）
9. v0.8-a 粉丝与关注机制（单向关注 MVP）：
   1. 用户主页支持关注/取关，接口幂等：
      1. `POST /api/user/follows/{targetUserNo}`
      2. `DELETE /api/user/follows/{targetUserNo}`
   2. 用户公开资料新增关注态字段（`GET /api/user/profiles/{userNo}`）：
      1. `followerCount`
      2. `followingCount`
      3. `followedByCurrentUser`
   3. 新增公开列表查询：
      1. `GET /api/user/profiles/{userNo}/followers?page={n}&size={n}`
      2. `GET /api/user/profiles/{userNo}/following?page={n}&size={n}`
   4. 用户端新增关注列表页面路由：
      1. `/u/:userNo/followers`
      2. `/u/:userNo/following`
   5. 数据层新增关系表：`u_user_follow`（唯一键 `follower_user_id + followed_user_id`，按创建时间倒序查询）。

---

## 🛠️ 技术栈清单 (Tech Stack)

### 核心后端 (Core Services)

* **主语言 & 框架:** Java 21 / Spring Boot 4.0.x / Spring Cloud (Gateway, OpenFeign)
* **持久层:** MySQL 8.0（单实例多库：`shiori_user`/`shiori_product`/`shiori_order`/`shiori_payment`） / MyBatis-Plus / Flyway
* **微服务大脑:** Nacos (服务注册与动态配置)
* **接口文档:** SpringDoc OpenAPI 3 (Swagger UI)
* **构建工具:** Gradle

### 边缘微服务 (Edge Services)

* **语言 & 框架:** Go 1.26 / Gin / Gorilla WebSocket
* **通信机制:** 订单/用户/商品相关业务事件走 Kafka CDC；notify 多实例 chat 广播走 Redis Pub/Sub

### 中间件与基础设施 (Infrastructure)

* **缓存:** Redis（缓存/防刷/短期幂等/热点保护）
* **消息总线:** Kafka / Kafka Connect / Debezium（业务事件 CDC 总线）
* **可观测性:** Prometheus + Grafana（指标可视化）
* **压测资产:** k6（脚本化压测，纳入仓库便于复现）
* **容器化部署:** Docker & Docker Compose (一键拉起整套基建)

### 前端与多端展示 (Frontend)

* **用户端 Web (买/卖家视角):** Vue 3 + TypeScript + Vite + Tailwind CSS + Pinia + Vue Query
* **管理端 (平台运营视角):** Vue 3 + TypeScript + Vite + Tailwind CSS + Pinia + Vue Query

---

## 📁 工程目录结构

本项目采用 Monorepo 结构，克隆后请分别使用对应的 IDE 打开子目录：

```text
shiori/
├── shiori-java/                      # ☕ [核心微服务群] 请使用 IntelliJ IDEA 打开
│   ├── build.gradle                  # 全局依赖管理
│   ├── shiori-common/                # 全局异常、统一返回体 Result<T>、错误码、事件模型
│   ├── shiori-gateway-service/       # API 网关与 JWT 统一鉴权拦截
│   ├── shiori-user-service/          # 用户服务
│   ├── shiori-product-service/       # 商品/库存服务
│   ├── shiori-order-service/         # 订单交易服务（Outbox + 超时关单）
│   └── shiori-payment-service/       # 支付服务（余额托管 + CDK）
├── shiori-notify/                    # 🐹 [推送边缘服务] 请使用 GoLand 打开
│   └── main.go                       # 监听 Kafka CDC / Redis PubSub 并通过 WebSocket 推送前端
├── shiori-app/                       # 🌐 [用户端 Web] 请使用 VSCode / WebStorm 打开
├── shiori-admin-web/                 # 💻 [管理端后台 Web] 请使用 WebStorm / VSCode 打开
├── deploy/                           # 🐳 [基础设施部署]
│   ├── docker-compose.yml            # MySQL, Redis, Kafka, Kafka Connect, (可选 Nacos/Prom/Grafana)
│   ├── kafka/                        # Kafka Connect connector 模板与注册脚本
│   ├── nacos/                        # Nacos 配置导入脚本与模板（templates/*.yml.tmpl）
│   ├── mysql/                        # MySQL 额外配置（binlog / CDC）
│   ├── observability/                # Prometheus/Grafana 配置与预置 dashboard
│   └── sql/                          # MySQL 初始化脚本（创建多库）与后续运维 SQL
└── perf/                             # ⚡ [压测资产] k6 脚本与结果记录
    ├── k6-order-hotspot.js
    ├── k6-order-realistic.js
    ├── k6-ws.js
    └── README.md
```

---

## 🚀 Quick Start

### 0) Requirements

* Docker & Docker Compose
* JDK 21 + Gradle
* Go 1.26
* Node.js + pnpm（前端）

### 1) Start Infrastructure

```bash
cd deploy
./gen-env.sh
docker compose up -d
```

如需手工填写，也可以：

```bash
cd deploy
cp .env.example .env
```

说明：
- 默认 `docker compose up -d` 只启动基础设施（MySQL/Redis/Kafka/Kafka Connect/Nacos/MinIO + 初始化容器）。
- 如需一键启动“基础设施 + Java/Go 服务”，使用：

```bash
cd deploy
docker compose --profile app up -d --build
```

如需再加上两个前端容器（用户端 + 管理端），使用：

```bash
cd deploy
docker compose --profile app --profile web up -d --build
```

如需同时拉起可观测性栈（Prometheus + Grafana），使用：

```bash
cd deploy
docker compose --profile app --profile obs up -d --build
```

若遇到 Docker Hub 网络抖动（例如 `EOF`）导致 build 失败，可先改用经典构建器重试：

```bash
cd deploy
DOCKER_BUILDKIT=0 docker compose --profile app build
DOCKER_BUILDKIT=0 docker compose --profile app --profile web build
docker compose --profile app --profile web up -d
```

全栈容器入口：
- 用户端 Web: `http://localhost:3000`
- 管理端 Web: `http://localhost:3001`
- 网关: `http://localhost:8080`
- Notify 健康检查: `http://localhost:8090/healthz`
- Prometheus: `http://localhost:9090`（`PROMETHEUS_HOST_PORT` 可改）
- Grafana: `http://localhost:3002`（`GRAFANA_HOST_PORT` 可改）

`docker-compose` 会通过 `deploy/sql/mysql-init/` 自动初始化业务数据库：
- `shiori_user`
- `shiori_product`
- `shiori_order`
- `shiori_payment`

并通过一次性容器自动渲染并导入 Nacos 配置模板（`deploy/nacos/templates/*.yml.tmpl`）：
- `nacos-config-init`：导入容器网络地址配置（默认 group 为 `SHIORI_DEV_DOCKER`，或按 `SHIORI_ENV`/`NACOS_CONFIG_GROUP` 覆盖）。
- `nacos-config-init-local`：导入本机调试地址配置（默认 group 为 `SHIORI_DEV_LOCAL`）。
导入后的 dataId 规范：
- `shiori-user-service-base.yml`
- `shiori-user-service-secret.yml`
- `shiori-product-service-base.yml`
- `shiori-product-service-secret.yml`
- `shiori-order-service-base.yml`
- `shiori-order-service-secret.yml`
- `shiori-payment-service-base.yml`
- `shiori-payment-service-secret.yml`
- `shiori-gateway-service-base.yml`
- `shiori-security-base.yml`
- `shiori-security-secret.yml`

Nacos 分组规则：
- `SHIORI_DEV_DOCKER`（容器内服务互调）
- `SHIORI_DEV_LOCAL`（IDEA/本机直启服务）
- `SHIORI_TEST`（`SHIORI_ENV=test`）
- `SHIORI_PROD`（`SHIORI_ENV=prod`）
- 未显式设置 `NACOS_CONFIG_GROUP` 时，`nacos-config-init` 会按 `SHIORI_ENV` 自动推导目标 group（`dev -> SHIORI_DEV_DOCKER`）。

可用以下命令查看导入日志：

```bash
cd deploy
docker compose logs nacos-config-init
docker compose logs nacos-config-init-local
```

如需手工重跑导入：

```bash
cd deploy
docker compose run --rm nacos-config-init
```

Kafka Connect 与 MinIO 也会通过一次性容器完成基础初始化：
- `kafka-connect-init`：向 Kafka Connect 注册 order/payment/product/user 四个 Debezium CDC connector。
- `minio-init`：创建商品桶、商品服务专用访问账号与桶级读写策略。

Kafka / CDC 相关默认入口：
- Kafka Bootstrap: `127.0.0.1:${KAFKA_HOST_PORT:-9092}`
- Kafka Connect REST: `http://127.0.0.1:${KAFKA_CONNECT_HOST_PORT:-18083}`

并启动 MinIO（商品图片对象存储）：
- S3 API: `http://localhost:9000`
- Console: `http://localhost:9001`

### 1.1) 环境配置矩阵（dev/test/prod）

| 配置项 | 敏感 | dev 来源 | test 来源 | prod 来源 | 注入位置 |
|---|---|---|---|---|---|
| `NACOS_CONFIG_GROUP` | 否 | `SHIORI_DEV_DOCKER`（容器）或 `SHIORI_DEV_LOCAL`（本机） | `SHIORI_TEST` | `SHIORI_PROD` | Compose/启动环境 |
| `NACOS_CONFIG_GROUP_LOCAL` | 否 | `SHIORI_DEV_LOCAL` | 通常不用 | 通常不用 | `nacos-config-init-local` |
| `NACOS_CONFIG_NAMESPACE` | 否 | public 或指定 namespace | 指定 namespace | 指定 namespace | Compose/启动环境 |
| `JWT_HMAC_SECRET` | 是 | `.env` 本地密钥 | CI 运行时生成/Secret | Secret 管理系统 | `nacos-config-init` 模板渲染 |
| `GATEWAY_SIGN_SECRET` | 是 | `.env` 本地密钥 | CI 运行时生成/Secret | Secret 管理系统 | `nacos-config-init` 模板渲染 |
| `ORDER_PAYMENT_INTERNAL_TOKEN` | 是 | `.env` 本地密钥 | CI Secret | Secret 管理系统 | `shiori-order-service-secret.yml` / `shiori-payment-service-secret.yml` / `shiori-user-service-secret.yml` |
| `USER_DB_USERNAME` | 否（建议最小权限） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-user-service-secret.yml` |
| `USER_DB_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-user-service-secret.yml` |
| `PRODUCT_DB_USERNAME` | 否（建议最小权限） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-product-service-secret.yml` |
| `PRODUCT_DB_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-product-service-secret.yml` |
| `ORDER_DB_USERNAME` | 否（建议最小权限） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-order-service-secret.yml` |
| `ORDER_DB_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-order-service-secret.yml` |
| `MINIO_PRODUCT_ACCESS_KEY` | 否（凭证标识） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-product-service-secret.yml` |
| `MINIO_PRODUCT_SECRET_KEY` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-product-service-secret.yml` |
| `MYSQL_OPS_USERNAME` | 否（运维/烟测账号） | `.env` | CI Secret/变量 | Secret 管理系统 | MySQL 初始化与烟测 |
| `MYSQL_OPS_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | MySQL 初始化与烟测 |
| `MYSQL_ROOT_PASSWORD` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose |
| `MINIO_ROOT_USER` | 否（MinIO 管理账号） | `.env` | CI Secret/变量 | Secret 管理系统 | docker compose |
| `MINIO_ROOT_PASSWORD` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose |
| `NACOS_AUTH_TOKEN` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose |
| `NACOS_AUTH_IDENTITY_KEY` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose / nacos init 请求头 |
| `NACOS_AUTH_IDENTITY_VALUE` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose / nacos init 请求头 |
| `NACOS_IMPORT_PASSWORD` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | `nacos-config-init` 登录 |

最小启动方式（不含明文）：

```bash
cd deploy
./gen-env.sh
# 如需自定义账号/密钥，再编辑 .env
docker compose up -d
```

IDEA / 本机调试时建议：
- `NACOS_ADDR=127.0.0.1:8848`
- `NACOS_CONFIG_GROUP=SHIORI_DEV_LOCAL`
- Redis 默认端口使用 `6379`（由 `REDIS_HOST_PORT` 控制）

### 2) Run Core Services (Java)

若你已执行 `docker compose --profile app up -d --build`，可跳过本节手工启动。

```bash
cd shiori-java
./gradlew :shiori-gateway-service:bootRun
./gradlew :shiori-user-service:bootRun
./gradlew :shiori-product-service:bootRun
./gradlew :shiori-order-service:bootRun
./gradlew :shiori-payment-service:bootRun
```

### 2.2) Nacos 数据源配置（必须）

三大业务服务的配置统一由 Nacos 下发，按 “base/secret” 拆分：
- user: `shiori-user-service-base.yml` + `shiori-user-service-secret.yml`
- product: `shiori-product-service-base.yml` + `shiori-product-service-secret.yml`
- order: `shiori-order-service-base.yml` + `shiori-order-service-secret.yml`
- payment: `shiori-payment-service-base.yml` + `shiori-payment-service-secret.yml`
- shared: `shiori-security-base.yml` + `shiori-security-secret.yml`

网关额外加载：`shiori-gateway-service-base.yml`。

模板位于：
- `deploy/nacos/templates/`

### 2.1) Gateway 鉴权（第二阶段）

当前脚手架已启用“认证闭环第二阶段”：
- 网关统一 JWT 验签（HMAC）
- 认证接口白名单：`/api/user/auth/register|login|refresh|logout`
- 业务接口默认受保护：`/api/**`
- 管理路径：`/api/admin/**` 需要 `ROLE_ADMIN`
- 网关向下游透传 `X-User-Id`、`X-User-Roles`
- 网关为 `/api/**` 写入 `X-Gateway-Ts`、`X-Gateway-Nonce`、`X-Gateway-Sign`，业务服务执行签名校验 + 防重放作为第二道防线
- 匿名 GET 白名单默认包含：`/api/product/**`、`/api/v2/product/**`、`/api/user/profiles/**`、`/api/user/media/avatar/**`、`/api/v2/product/users/**`

推荐通过 Nacos 配置中心下发安全配置（group 由 `SHIORI_ENV` 或 `NACOS_CONFIG_GROUP` 决定）：
- `shiori-security-base.yml`：`issuer`、`ttl`、`max-skew`
- `shiori-security-secret.yml`：`hmac-secret`、`internal-secret`

```yaml
security:
  jwt:
    issuer: "shiori"
    access-ttl-seconds: 900
    refresh-ttl-seconds: 604800
  gateway-sign:
    max-skew-seconds: 300
    replay-protection-enabled: true
    replay-cache-max-entries: 200000
```

密钥通过环境变量注入到模板渲染流程（不在仓库保存明文）：

```bash
export JWT_HMAC_SECRET='<your-secret>'
export GATEWAY_SIGN_SECRET='<your-secret>'
```

### 3) Run Edge Notify (Go)

```bash
# ensure Redis / Kafka / Nacos are up
cd deploy
docker compose up -d redis kafka kafka-connect nacos

cd ../shiori-notify
./gen-env.sh -f
set -a; source .env; set +a
go run .
```

常用环境变量（可选）：

```bash
export NOTIFY_HTTP_ADDR=:8090
export NOTIFY_REDIS_ADDR=127.0.0.1:6379
export NOTIFY_KAFKA_ENABLED=true
export NOTIFY_KAFKA_TOPICS=shiori.cdc.order.outbox.raw,shiori.cdc.user.outbox.raw
export NOTIFY_KAFKA_GROUP_ID=shiori-notify-cdc
export NOTIFY_STORE_DRIVER=mysql
export NOTIFY_MYSQL_DSN='<notify-mysql-dsn>'
export NOTIFY_AUTH_ENABLED=true
export NOTIFY_JWT_HMAC_SECRET='<jwt-hmac-secret>'
export NOTIFY_JWT_ISSUER=shiori
export NOTIFY_CHAT_ENABLED=true
export NOTIFY_CHAT_TICKET_ISSUER=shiori-chat-ticket
export NOTIFY_CHAT_TICKET_PUBLIC_KEY_PEM_BASE64='<public-key-pem-base64>'
export NOTIFY_CHAT_PUBSUB_ENABLED=true
export NOTIFY_CHAT_PUBSUB_CHANNEL=shiori.chat.event
```

### 3.6) Chat Ticket（RS256）密钥生成与本地验收

生成一对 RSA 密钥（OpenSSL）：

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out chat_ticket_private.pem
openssl rsa -pubout -in chat_ticket_private.pem -out chat_ticket_public.pem
```

转为“PEM 文本再 base64”用于配置：

```bash
export CHAT_TICKET_PRIVATE_KEY_PEM_BASE64="$(base64 -w0 chat_ticket_private.pem)"
export NOTIFY_CHAT_TICKET_PUBLIC_KEY_PEM_BASE64="$(base64 -w0 chat_ticket_public.pem)"
```

签发 ticket 示例：

```bash
curl -X POST "http://localhost:8080/api/product/chat/ticket?listingId=101" \
  -H "Authorization: Bearer <buyer-access-token>"
```

查询会话列表示例：

```bash
curl "http://localhost:8080/api/chat/conversations?limit=20" \
  -H "Authorization: Bearer <access-token>"
```

查询消息示例：

```bash
curl "http://localhost:8080/api/chat/conversations/11/messages?limit=20" \
  -H "Authorization: Bearer <access-token>"
```

标记已读示例：

```bash
curl -X POST "http://localhost:8080/api/chat/conversations/11/read" \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"lastReadMsgId":21}'
```

鉴权快速验证（示例）：

```bash
# 未携带 Token，应返回 401（Result.code=20002）
curl -i http://localhost:8080/api/user/me

# 登录签发（白名单接口，无需 Bearer）
curl -i -X POST http://localhost:8080/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}'

# 注册（白名单接口，无需 Bearer）
curl -i -X POST http://localhost:8080/api/user/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123","nickname":"Alice"}'

# 刷新（白名单接口，无需 Bearer）
curl -i -X POST http://localhost:8080/api/user/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<opaque-refresh-token>"}'

# 携带 Access Token 访问受保护路径（通过网关鉴权后再转发）
curl -i -H "Authorization: Bearer <access-jwt>" http://localhost:8080/api/user/me

# 上传头像（用户服务中转到对象存储，不暴露 OSS 连接）
curl -X POST http://localhost:8080/api/user/media/avatar \
  -H "Authorization: Bearer <access-jwt>" \
  -F "file=@./avatar.jpg"

# 商品匿名浏览（无需 Bearer）
curl -i http://localhost:8080/api/product/products
```

### 3.1) 商品域最小闭环（当前可用）

商品服务当前已支持：
- 图片预签名直传：`POST /api/product/media/presign-upload`
- 商品读接口（匿名）：`GET /api/product/products`、`GET /api/product/products/{id}`
- 商品写接口（需登录）：`POST /api/product/products`、`PUT /api/product/products/{id}`、`publish/off-shelf`
- 库存交易接口（供后续订单服务）：`/api/product/internal/stock/deduct|release`（`bizNo + opType` 幂等）

图片上传预签名示例：

```bash
curl -X POST http://localhost:8080/api/product/media/presign-upload \
  -H "Authorization: Bearer <access-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"book-cover.jpg","contentType":"image/jpeg"}'
```

### 3.2) 订单域交易闭环（当前可用）

订单服务当前已支持：
- 下单（幂等）：`POST /api/order/orders`（必须 `Idempotency-Key`）
- 下单规则：不允许购买自己发布的商品（违规则返回 `50015 ORDER_SELF_PURCHASE_NOT_ALLOWED`）
- 我的订单分页：`GET /api/order/orders`
- 订单详情：`GET /api/order/orders/{orderNo}`
- 模拟支付（幂等）：`POST /api/order/orders/{orderNo}/pay`（必须 `Idempotency-Key`）
- 主动取消（幂等）：`POST /api/order/orders/{orderNo}/cancel`（必须 `Idempotency-Key`）
- 卖家履约：`POST /api/order/seller/orders/{orderNo}/deliver|finish`
- 管理端履约：`POST /api/admin/orders/{orderNo}/deliver|finish`
- 状态迁移审计查询：`GET /api/admin/orders/{orderNo}/status-audits`
- 超时关单：通过 `timeout_at + 定时扫描` 自动触发，执行时二次校验状态
- Outbox 事件投递：`OrderCreated` / `OrderPaid` / `OrderCanceled`（`OrderPaid` 会分别给买家和卖家写事件）
- 状态机：`UNPAID -> PAID -> DELIVERING -> FINISHED`（`UNPAID` 可取消为 `CANCELED`）

最小调用示例：

```bash
# 1) 创建订单（多 SKU，但限制同一卖家）
curl -X POST http://localhost:8080/api/order/orders \
  -H "Authorization: Bearer <access-jwt>" \
  -H "Idempotency-Key: create-order-demo-001" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"productId": 1, "skuId": 11, "quantity": 1},
      {"productId": 1, "skuId": 12, "quantity": 2}
    ]
  }'

# 2) 支付订单（模拟）
curl -X POST http://localhost:8080/api/order/orders/<orderNo>/pay \
  -H "Authorization: Bearer <access-jwt>" \
  -H "Idempotency-Key: pay-order-demo-001" \
  -H "Content-Type: application/json" \
  -d '{"paymentNo":"pay-demo-001"}'
```

### 3.3) 一键端到端烟测（交易 + 聊天 + 通知 + 管理端）

当网关、user/product/order、notify 均已启动后，可运行：

```bash
bash scripts/smoke/e2e_trade_notify.sh
```

聊天链路烟测（Chat Ticket + 会话 API + WS join/send/read）：

```bash
bash scripts/smoke/e2e_chat_notify.sh
```

管理端闭环烟测（管理员角色、用户治理、商品下架、订单取消、审计日志）：

```bash
bash scripts/smoke/e2e_admin_console.sh
```

脚本会自动执行：
- 注册/登录 buyer + seller
- seller 创建并发布商品（2 个 SKU）
- buyer 下单并重复请求校验 `Idempotency-Key` 幂等
- buyer 支付订单
- buyer/seller 两端 WebSocket 均收到 `OrderPaid` 事件

可选环境变量（`admin` 烟测依赖 MySQL 账号）：

```bash
export GATEWAY_BASE_URL=http://localhost:8080
export NOTIFY_WS_BASE_URL=ws://localhost:8090/ws
export SMOKE_TIMEOUT_SECONDS=60
export SMOKE_PREFIX=smoke
export MYSQL_CONTAINER=shiori-mysql
export MYSQL_OPS_USERNAME=<mysql-ops-user>
export MYSQL_OPS_PASSWORD=<mysql-ops-password>
```

`ws-smoke` 探针命令（脚本内部也会调用）：

```bash
cd shiori-notify
go run ./cmd/ws-smoke -base-url ws://localhost:8090/ws -access-token '<access-jwt>' -expect-type OrderPaid -expect-aggregate Oxxxx -timeout 60s
```

`chat-smoke` 探针命令（聊天烟测脚本内部会调用）：

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

### 3.4) GitHub Actions CI（PR 全量自动化）

仓库已提供 CI workflow：
- `.github/workflows/ci.yml`
- Job 1：`java-test`（`shiori-java` 全量测试）
- Job 2：`e2e-trade-notify-admin`（基础设施 + 4 个 Java 服务 + notify + 交易通知烟测 + 聊天链路烟测 + 管理端闭环烟测 + `shiori-app` Playwright E2E + `shiori-admin-web` Playwright E2E）
- Job 3：`perf-baseline-non-blocking`（交易/通知烟测 + k6 基线，`continue-on-error` 非阻塞）

E2E 编排脚本：

```bash
bash scripts/ci/run_e2e_ci.sh
```

性能基线脚本（依赖服务已就绪）：

```bash
bash scripts/ci/run_perf_baseline.sh
```

可选环境变量：

```bash
export SERVICE_READY_TIMEOUT_SECONDS=300
export RUN_PERF_BASELINE=1
export SKIP_APP_PLAYWRIGHT=1
export SKIP_ADMIN_PLAYWRIGHT=1
# 可选：覆盖 perf 目标地址（健康检查）
export PERF_GATEWAY_BASE_URL=http://127.0.0.1:8080
export PERF_NOTIFY_WS_BASE_URL=ws://127.0.0.1:8090/ws
export PERF_NOTIFY_HTTP_BASE_URL=http://127.0.0.1:8090
# 可选：覆盖 k6 压测地址（容器化 k6 与宿主机地址不一致时使用）
export K6_GATEWAY_BASE_URL=http://host.docker.internal:8080
export K6_NOTIFY_WS_BASE_URL=ws://host.docker.internal:8090/ws
export K6_NOTIFY_HTTP_BASE_URL=http://host.docker.internal:8090
```

必填敏感变量（脚本会校验）：
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_OPS_PASSWORD`
- `USER_DB_PASSWORD`
- `PRODUCT_DB_PASSWORD`
- `ORDER_DB_PASSWORD`
- `MINIO_ROOT_PASSWORD`
- `MINIO_PRODUCT_SECRET_KEY`
- `NACOS_AUTH_TOKEN`
- `NACOS_IMPORT_PASSWORD`
- `JWT_HMAC_SECRET`
- `GATEWAY_SIGN_SECRET`

脚本执行时会：
- 自动 `docker compose up -d`
- 等待 `nacos-config-init` 成功退出
- 启动 user/product/order/gateway/notify
- 执行 `scripts/smoke/e2e_trade_notify.sh`
- 执行 `scripts/smoke/e2e_chat_notify.sh`
- 执行 `scripts/smoke/e2e_admin_console.sh`
- 可选执行 `scripts/ci/run_perf_baseline.sh`（当 `RUN_PERF_BASELINE=1`）
- 执行 `shiori-app` 前端 Playwright 端到端用例
- 执行 `shiori-admin-web` 管理端 Playwright 端到端用例
- 失败时输出关键日志并自动清理环境

CI 日志默认落盘到仓库根目录：
- `ci-logs/*.log`

### 3.5) 发布准入与回滚（v0.3）

发布准入（必须满足）：
- `java-test` 通过
- `local-regression-blocking` 通过
- `perf-stress-non-blocking` 已执行并完成趋势评估
- `e2e_trade_notify` + `e2e_chat_notify` + `e2e_admin_console` 冒烟通过

回滚触发（任一满足即评估回滚）：
- 核心交易错误率连续 5 分钟 >= 3%
- 网关限流误伤导致登录/下单/支付成功率显著下降
- 订单状态迁移出现持续非法流转

发布/回滚演练脚本：

```bash
# 发布演练
bash scripts/release/release_drill.sh drill

# 回滚演练
ROLLBACK_TO_TAG=v0.2.0 bash scripts/release/release_drill.sh rollback
```

详细排障顺序与准入细则见：
- `docs/runbooks/release-readiness-v0.3.md`

### 4) Run Frontend (Web)

```bash
cd shiori-app
cp .env.example .env
pnpm install
pnpm dev
```

默认配置：
- `VITE_API_BASE_URL=http://localhost:8080`
- `VITE_NOTIFY_WS_BASE_URL=ws://localhost:8090/ws`

前端 Playwright 冒烟（注册/登录 -> 创建商品 -> 下单 -> 收银台支付 -> 通知）：

```bash
cd shiori-app
pnpm e2e:install
pnpm e2e
```

可选环境变量：
- `E2E_BUYER_CDK`：当收银台余额不足时用于自动兑换并继续支付。

### 5) Run Admin Web

```bash
cd shiori-admin-web
cp .env.example .env
pnpm install
pnpm dev
```

默认地址：`http://localhost:5173`（仅 `ROLE_ADMIN` 可登录）。

管理端 Playwright 冒烟（登录 -> CDK 批次创建与 CSV 导出 -> 用户禁用/启用 -> 商品强制下架 -> 订单取消）：

```bash
cd shiori-admin-web
pnpm e2e:install
pnpm e2e
```

管理端 API（统一前缀）：
- `/api/admin/users/**`
- `/api/admin/roles/**`
- `/api/admin/products/**`
- `/api/admin/orders/**`
  - 订单履约：`/api/admin/orders/{orderNo}/deliver|finish`
  - 状态审计：`/api/admin/orders/{orderNo}/status-audits`

### 5.1) 手工初始化管理员

本项目不内置默认管理员账号，请按以下步骤初始化：

1. 先调用注册接口创建普通用户（`/api/user/auth/register`）。
2. 执行仓库脚本授予 `ROLE_ADMIN`（推荐）：

```bash
cd deploy
sh sql/manual/grant_admin_role.sh <username>
```

脚本路径：
- `deploy/sql/manual/grant_admin_role.sh`
- `deploy/sql/manual/grant_admin_role.sql`

---

## 📈 压测与可观测性 (Performance & Observability)

本项目建议以 **k6 + Prometheus + Grafana** 形成“可复现性能证据链”：

* `perf/k6-order-hotspot.js`：单 buyer / 单 seller / 单热点 SKU 的订单链路压测
* `perf/k6-order-realistic.js`：多 buyer / 多 seller / 多商品分布的订单链路压测
* `perf/k6-ws.js`：WebSocket 连接与推送压测
* Prometheus 抓取 Spring Boot Actuator 与 notify 指标，Grafana 面板展示 p95、错误率、队列堆积与在线连接数

可观测性栈启动（容器）：

```bash
cd deploy
docker compose --profile app --profile obs up -d --build
```

预置入口：
- Prometheus Targets: `http://localhost:9090/targets`
- Grafana: `http://localhost:3002`
- 默认 dashboard: `Shiori / Shiori Overview`

默认 Grafana 账号（可在 `deploy/.env` 覆盖）：
- 用户名：`GRAFANA_ADMIN_USER`（默认 `admin`）
- 密码：`GRAFANA_ADMIN_PASSWORD`（默认 `admin`）

运行示例：

```bash
cd perf
k6 run k6-order-hotspot.js
k6 run k6-order-realistic.js
k6 run k6-ws.js
```

CI/本机一键基线（服务就绪后）：

```bash
bash scripts/ci/run_perf_baseline.sh
```

### 基线模式（M4 固化）

为避免“本地回归验证”和“性能门禁压测”互相干扰，建议区分两套参数：

| 模式 | 目标 | 推荐参数 |
| --- | --- | --- |
| `local-regression` | 本机稳定回归、低噪声 | `K6_ORDER_VUS=1` `K6_WS_VUS=1` `K6_WS_LATENCY_P95_MS=3000` |
| `perf-stress` | 独立性能环境压测 | 按环境容量上调 `K6_ORDER_VUS`/`K6_WS_VUS`，再逐步收紧 `K6_WS_LATENCY_P95_MS` |

本地默认即 `local-regression`；如需覆盖：

```bash
K6_ORDER_VUS=5 \
K6_WS_VUS=2 \
K6_WS_LATENCY_P95_MS=2000 \
bash scripts/ci/run_perf_baseline.sh
```

### 性能变量速查

`scripts/ci/run_perf_baseline.sh` 支持以下关键变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `GATEWAY_BASE_URL` | `http://localhost:8080` | 健康检查/脚本层基础地址 |
| `NOTIFY_WS_BASE_URL` | `ws://localhost:8090/ws` | 健康检查与 WS 基础地址 |
| `PERF_NOTIFY_HTTP_BASE_URL` | 自动从 WS 推导 | notify HTTP 健康检查地址 |
| `K6_GATEWAY_BASE_URL` | 继承 `GATEWAY_BASE_URL` | k6 实际压测目标（可单独覆盖） |
| `K6_NOTIFY_WS_BASE_URL` | 继承 `NOTIFY_WS_BASE_URL` | k6 WS 压测目标 |
| `K6_NOTIFY_HTTP_BASE_URL` | 继承 `PERF_NOTIFY_HTTP_BASE_URL` | k6 notify HTTP 目标 |
| `K6_ORDER_VUS` | `1` | 订单压测并发 |
| `K6_ORDER_DURATION` | `45s` | 订单压测时长 |
| `K6_WS_VUS` | `1` | WS 压测并发 |
| `K6_WS_ITERATIONS` | `10` | WS 每 VU 迭代次数 |
| `K6_WS_TIMEOUT_MS` | `10000` | WS 单次等待超时 |
| `K6_WS_LATENCY_P95_MS` | `3000` | WS p95 阈值 |
| `K6_DEBUG_FAIL_SAMPLE` | `0` | 是否打印失败样本（1 开启） |
| `K6_DEBUG_FAIL_LIMIT` | `20` | 失败样本最多打印条数 |

> 说明：在 macOS 使用 dockerized `k6` 时，脚本会自动把 `localhost/127.0.0.1` 改写为 `host.docker.internal`，避免容器内回环地址误指向自身。
