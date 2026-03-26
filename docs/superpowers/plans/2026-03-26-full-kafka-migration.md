# Full Kafka Migration Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 `RabbitMQ` 事件总线、`TTL + DLX` 超时链路与 `notify` 聊天 fanout 全量迁移为 `Kafka + Debezium CDC + 显式超时调度器`，最终移除 RabbitMQ 依赖。

**Architecture:** `MySQL` 继续作为事实源；`order/payment/product/user` 业务事务内只写本地 outbox，不再由应用轮询 relay 发 MQ，而是由 `Debezium + Kafka Connect` 从 binlog 捕获 outbox 插入并投递到 Kafka topic；订单超时不再依赖 `TTL + DLX`，改为 `order-service` 内部调度器基于 `timeout_at` 生成“超时触发”事件并写入 outbox，再由 Kafka 消费处理；Java 消费端统一切到 `Spring Kafka`，Go `notify` 统一切到 `franz-go`，聊天广播通过“每实例独立 consumer group”保留 fanout 语义。

**Tech Stack:** Apache Kafka (KRaft), Kafka Connect, Debezium MySQL Connector, MySQL ROW binlog, Spring for Kafka, franz-go, Flyway, JUnit 5, Go test, Docker Compose, k6

---

## 决策

### 主题与职责划分

- `shiori.order.events`
- `shiori.order.timeout`
- `shiori.payment.wallet.events`
- `shiori.product.events`
- `shiori.user.events`
- `shiori.notify.chat.broadcast`
- `*.retry.*` / `*.dlt`

### 明确不做

- 不保留长期双总线运行态
- 不在本轮引入 Redis 作为库存/支付真相源
- 不尝试用 Kafka retention 模拟 RabbitMQ `TTL + DLX`
- 不做“应用层 dual write 到 RabbitMQ 和 Kafka”长期兼容方案

### 迁移顺序

1. 先上 Kafka / Connect / Debezium 基础设施与 MySQL binlog。
2. 再把四个 outbox 表规范化到 CDC 可消费形态。
3. 然后迁移 Java 消费端与订单超时调度。
4. 再迁移 Go `notify` 的业务事件和聊天广播。
5. 最后下线 RabbitMQ、删除 relay 与拓扑代码、重跑压测。

## 文件结构

### 新增文件

- `deploy/mysql/conf.d/001-cdc.cnf`
- `deploy/kafka/connect/register-connectors.sh`
- `deploy/kafka/connect/order-outbox-connector.json`
- `deploy/kafka/connect/payment-outbox-connector.json`
- `deploy/kafka/connect/product-outbox-connector.json`
- `deploy/kafka/connect/user-outbox-connector.json`
- `shiori-java/shiori-order-service/src/main/resources/db/migration/V15__create_order_timeout_task.sql`
- `shiori-java/shiori-order-service/src/main/resources/db/migration/V16__normalize_order_outbox_for_kafka.sql`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderKafkaProperties.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderKafkaConfiguration.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderTimeoutTaskEntity.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderTimeoutTaskRecord.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/repository/OrderTimeoutTaskMapper.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/kafka/OrderTimeoutKafkaConsumer.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/kafka/WalletBalanceChangedKafkaConsumer.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderTimeoutSchedulerService.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderTimeoutSchedulerServiceTest.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/kafka/OrderTimeoutKafkaConsumerTest.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/kafka/WalletBalanceChangedKafkaConsumerTest.java`
- `shiori-java/shiori-payment-service/src/main/resources/db/migration/V4__normalize_wallet_outbox_for_kafka.sql`
- `shiori-java/shiori-product-service/src/main/resources/db/migration/V11__normalize_product_outbox_for_kafka.sql`
- `shiori-java/shiori-user-service/src/main/resources/db/migration/V18__normalize_user_outbox_for_kafka.sql`
- `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/config/SocialKafkaProperties.java`
- `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/config/SocialKafkaConfiguration.java`
- `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/kafka/ProductEventKafkaConsumer.java`
- `shiori-java/shiori-social-service/src/test/java/moe/hhm/shiori/social/kafka/ProductEventKafkaConsumerTest.java`
- `shiori-notify/internal/kafka/consumer.go`
- `shiori-notify/internal/kafka/consumer_test.go`
- `shiori-notify/internal/chatkafka/broadcast.go`
- `shiori-notify/internal/chatkafka/broadcast_test.go`
- `docs/runbooks/kafka-cutover-runbook-2026-03-26.md`

### 修改文件

- `deploy/docker-compose.yml`
- `deploy/.env.example`
- `deploy/sql/mysql-init/001_create_service_databases.sh`
- `deploy/nacos/templates/shiori-order-service-base.yml.tmpl`
- `deploy/nacos/templates/shiori-order-service-secret.yml.tmpl`
- `deploy/nacos/templates/shiori-payment-service-base.yml.tmpl`
- `deploy/nacos/templates/shiori-payment-service-secret.yml.tmpl`
- `deploy/nacos/templates/shiori-product-service-base.yml.tmpl`
- `deploy/nacos/templates/shiori-product-service-secret.yml.tmpl`
- `deploy/nacos/templates/shiori-social-service-base.yml.tmpl`
- `deploy/nacos/templates/shiori-social-service-secret.yml.tmpl`
- `deploy/nacos/templates/shiori-user-service-base.yml.tmpl`
- `deploy/nacos/templates/shiori-user-service-secret.yml.tmpl`
- `deploy/nacos/templates/shiori-notify-service-base.yml.tmpl`
- `deploy/nacos/templates/shiori-notify-service-secret.yml.tmpl`
- `README.md`
- `shiori-java/build.gradle`
- `shiori-java/shiori-order-service/build.gradle`
- `shiori-java/shiori-payment-service/build.gradle`
- `shiori-java/shiori-social-service/build.gradle`
- `shiori-java/shiori-order-service/src/main/resources/application.yml`
- `shiori-java/shiori-payment-service/src/main/resources/application.yml`
- `shiori-java/shiori-product-service/src/main/resources/application.yml`
- `shiori-java/shiori-social-service/src/main/resources/application.yml`
- `shiori-java/shiori-user-service/src/main/resources/application.yml`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OutboxEventEntity.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OutboxEventRecord.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/repository/OrderMapper.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCreateWorkflowService.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderProperties.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandServiceTest.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OutboxRelayServiceTest.java`
- `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/repository/PaymentMapper.java`
- `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/service/PaymentService.java`
- `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/config/PaymentProperties.java`
- `shiori-java/shiori-payment-service/src/test/java/moe/hhm/shiori/payment/service/PaymentServiceTest.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/model/ProductOutboxEventEntity.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/model/ProductOutboxEventRecord.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/repository/ProductMapper.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/service/ProductService.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/config/ProductOutboxProperties.java`
- `shiori-java/shiori-product-service/src/test/java/moe/hhm/shiori/product/service/ProductServiceTest.java`
- `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/outbox/model/UserOutboxEventEntity.java`
- `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/outbox/model/UserOutboxEventRecord.java`
- `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/admin/repository/AdminUserMapper.java`
- `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/admin/service/AdminUserService.java`
- `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/authz/service/UserPermissionOverrideService.java`
- `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/config/UserOutboxProperties.java`
- `shiori-java/shiori-user-service/src/test/java/moe/hhm/shiori/user/admin/service/AdminUserServiceTest.java`
- `shiori-notify/go.mod`
- `shiori-notify/go.sum`
- `shiori-notify/internal/config/config.go`
- `shiori-notify/internal/config/config_test.go`
- `shiori-notify/internal/app/app.go`
- `shiori-notify/main.go`
- `shiori-notify/README.md`

### 最终删除文件

- `deploy/rabbitmq/init-rabbitmq-users.sh`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderMqProperties.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/OrderMqTopology.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/OrderTimeoutConsumer.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/WalletBalanceChangedConsumer.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OutboxRelayService.java`
- `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/config/PaymentMqProperties.java`
- `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/service/PaymentOutboxRelayService.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/service/ProductEventPublisher.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/service/ProductOutboxRelayService.java`
- `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/mq/SocialMqTopology.java`
- `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/mq/ProductEventConsumer.java`
- `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/outbox/service/UserOutboxRelayService.java`
- `shiori-notify/internal/mq/consumer.go`
- `shiori-notify/internal/mq/topology.go`
- `shiori-notify/internal/chatmq/broadcast.go`

## Chunk 1: Kafka / CDC 基础设施

### Task 1: 本地引入 Kafka、Kafka Connect、Debezium 与 MySQL binlog 配置

**Files:**
- Create: `deploy/mysql/conf.d/001-cdc.cnf`
- Create: `deploy/kafka/connect/register-connectors.sh`
- Create: `deploy/kafka/connect/order-outbox-connector.json`
- Create: `deploy/kafka/connect/payment-outbox-connector.json`
- Create: `deploy/kafka/connect/product-outbox-connector.json`
- Create: `deploy/kafka/connect/user-outbox-connector.json`
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/.env.example`
- Modify: `deploy/sql/mysql-init/001_create_service_databases.sh`
- Modify: `README.md`
- Test: `deploy/docker-compose.yml`

- [ ] **Step 1: 先写失败验证**

执行：

```bash
cd /Users/hhm/code/shiori
docker compose -f deploy/docker-compose.yml config | rg 'kafka|connect|debezium'
```

Expected: 现在缺少 `kafka` / `kafka-connect` / `debezium` 相关服务定义。

- [ ] **Step 2: 实现最小基础设施**

要求：

```text
- MySQL 打开 ROW binlog 与 FULL row image
- 新增 CDC 专用账号并授予 REPLICATION SLAVE / REPLICATION CLIENT / SELECT
- docker compose 新增 Kafka(KRaft)、Kafka Connect、connector init 容器
- connector 配置只捕获 outbox 表 insert 事件
```

- [ ] **Step 3: 跑 compose 校验**

执行：

```bash
cd /Users/hhm/code/shiori
docker compose -f deploy/docker-compose.yml config >/tmp/shiori-kafka-compose.yaml
docker compose -f deploy/docker-compose.yml up -d mysql kafka kafka-connect
docker compose -f deploy/docker-compose.yml exec kafka-connect curl -fsS http://localhost:8083/connectors
```

Expected:

- `docker compose config` 成功
- `mysql` / `kafka` / `kafka-connect` 能启动
- Kafka Connect REST 可访问

- [ ] **Step 4: 提交**

```bash
git add deploy/docker-compose.yml deploy/.env.example deploy/sql/mysql-init/001_create_service_databases.sh deploy/mysql/conf.d/001-cdc.cnf deploy/kafka/connect README.md
git commit -m "infra: add kafka and debezium foundation"
```

## Chunk 2: Outbox 规范化为 CDC 输入

### Task 2: 让 order / payment outbox 满足 Debezium 消费契约

**Files:**
- Create: `shiori-java/shiori-order-service/src/main/resources/db/migration/V16__normalize_order_outbox_for_kafka.sql`
- Create: `shiori-java/shiori-payment-service/src/main/resources/db/migration/V4__normalize_wallet_outbox_for_kafka.sql`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OutboxEventEntity.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OutboxEventRecord.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/repository/OrderMapper.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderProperties.java`
- Modify: `shiori-java/shiori-order-service/src/main/resources/application.yml`
- Modify: `deploy/nacos/templates/shiori-order-service-base.yml.tmpl`
- Modify: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandServiceTest.java`
- Modify: `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/repository/PaymentMapper.java`
- Modify: `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/service/PaymentService.java`
- Modify: `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/config/PaymentProperties.java`
- Modify: `shiori-java/shiori-payment-service/src/main/resources/application.yml`
- Modify: `deploy/nacos/templates/shiori-payment-service-base.yml.tmpl`
- Modify: `shiori-java/shiori-payment-service/src/test/java/moe/hhm/shiori/payment/service/PaymentServiceTest.java`

- [ ] **Step 1: 先写失败测试**

新增断言：

```java
@Test
void shouldAppendOrderOutboxWithAggregateTypeAndMessageKey() {
    // outbox 记录不再依赖 exchange/routingKey，转而写 aggregateType/messageKey
}

@Test
void shouldAppendWalletOutboxWithKafkaRoutingMetadata() {
    // 钱包事件写出 CDC 需要的 key / aggregate metadata
}
```

- [ ] **Step 2: 跑测试确认失败**

执行：

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandServiceTest' \
  :shiori-payment-service:test --tests 'moe.hhm.shiori.payment.service.PaymentServiceTest'
```

Expected: FAIL，提示 outbox 模型字段或断言不满足。

- [ ] **Step 3: 实现最小代码**

要求：

```text
- outbox 表新增 Debezium 所需字段：aggregate_type / aggregate_id / message_key / event_type
- 业务代码继续在本地事务内 insert outbox
- 先保留旧 relay 状态字段，但不再把 exchange / routingKey 作为真相字段
- 新增显式开关，为后续停用 relay 做准备
```

- [ ] **Step 4: 跑测试确认通过**

执行同上，Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add shiori-java/shiori-order-service shiori-java/shiori-payment-service deploy/nacos/templates/shiori-order-service-base.yml.tmpl deploy/nacos/templates/shiori-payment-service-base.yml.tmpl
git commit -m "refactor: normalize order and payment outbox for kafka cdc"
```

### Task 3: 让 product / user outbox 也切到同一契约

**Files:**
- Create: `shiori-java/shiori-product-service/src/main/resources/db/migration/V11__normalize_product_outbox_for_kafka.sql`
- Create: `shiori-java/shiori-user-service/src/main/resources/db/migration/V18__normalize_user_outbox_for_kafka.sql`
- Modify: `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/model/ProductOutboxEventEntity.java`
- Modify: `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/model/ProductOutboxEventRecord.java`
- Modify: `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/repository/ProductMapper.java`
- Modify: `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/service/ProductService.java`
- Modify: `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/config/ProductOutboxProperties.java`
- Modify: `shiori-java/shiori-product-service/src/main/resources/application.yml`
- Modify: `deploy/nacos/templates/shiori-product-service-base.yml.tmpl`
- Modify: `shiori-java/shiori-product-service/src/test/java/moe/hhm/shiori/product/service/ProductServiceTest.java`
- Modify: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/outbox/model/UserOutboxEventEntity.java`
- Modify: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/outbox/model/UserOutboxEventRecord.java`
- Modify: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/admin/repository/AdminUserMapper.java`
- Modify: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/admin/service/AdminUserService.java`
- Modify: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/authz/service/UserPermissionOverrideService.java`
- Modify: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/config/UserOutboxProperties.java`
- Modify: `shiori-java/shiori-user-service/src/main/resources/application.yml`
- Modify: `deploy/nacos/templates/shiori-user-service-base.yml.tmpl`
- Modify: `shiori-java/shiori-user-service/src/test/java/moe/hhm/shiori/user/admin/service/AdminUserServiceTest.java`

- [ ] **Step 1: 先写失败测试**

新增断言：

```java
@Test
void shouldAppendProductPublishedOutboxForKafkaCdc() {
    // product published outbox 写 aggregateType/messageKey
}

@Test
void shouldAppendUserAuthzOutboxForKafkaCdc() {
    // user 状态/角色/权限事件写统一 outbox metadata
}
```

- [ ] **Step 2: 跑测试确认失败**

执行：

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-product-service:test --tests 'moe.hhm.shiori.product.service.ProductServiceTest' \
  :shiori-user-service:test --tests 'moe.hhm.shiori.user.admin.service.AdminUserServiceTest'
```

Expected: FAIL

- [ ] **Step 3: 实现最小代码**

要求：

```text
- product / user 与 order / payment 使用同一 outbox 字段约定
- 不再新增 Rabbit 专属 exchange / routing key 依赖
- 为后续停用 ProductOutboxRelayService / UserOutboxRelayService 预留配置开关
```

- [ ] **Step 4: 跑测试确认通过**

执行同上，Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add shiori-java/shiori-product-service shiori-java/shiori-user-service deploy/nacos/templates/shiori-product-service-base.yml.tmpl deploy/nacos/templates/shiori-user-service-base.yml.tmpl
git commit -m "refactor: normalize product and user outbox for kafka cdc"
```

## Chunk 3: Java 消费端与订单超时替换

### Task 4: 用“显式超时调度器 + Kafka”替换 RabbitMQ TTL / DLX

**Files:**
- Create: `shiori-java/shiori-order-service/src/main/resources/db/migration/V15__create_order_timeout_task.sql`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderTimeoutTaskEntity.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderTimeoutTaskRecord.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/repository/OrderTimeoutTaskMapper.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderTimeoutSchedulerService.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderKafkaProperties.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderKafkaConfiguration.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/kafka/OrderTimeoutKafkaConsumer.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCreateWorkflowService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderProperties.java`
- Modify: `shiori-java/shiori-order-service/src/main/resources/application.yml`
- Modify: `deploy/nacos/templates/shiori-order-service-base.yml.tmpl`
- Test: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderTimeoutSchedulerServiceTest.java`
- Test: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/kafka/OrderTimeoutKafkaConsumerTest.java`

- [ ] **Step 1: 先写失败测试**

新增断言：

```java
@Test
void shouldCreateTimeoutTaskWhenOrderCreated() {
    // 创建订单后登记 timeout task，而不是立即发 Rabbit 延时消息
}

@Test
void shouldAppendTimeoutOutboxWhenTaskBecomesDue() {
    // 调度器扫到到期任务时写入 timeout topic 对应 outbox
}

@Test
void shouldHandleTimeoutEventFromKafkaIdempotently() {
    // Kafka timeout 事件重复投递不重复关单
}
```

- [ ] **Step 2: 跑测试确认失败**

执行：

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderTimeoutSchedulerServiceTest' \
  :shiori-order-service:test --tests 'moe.hhm.shiori.order.kafka.OrderTimeoutKafkaConsumerTest' \
  :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandServiceTest'
```

Expected: FAIL

- [ ] **Step 3: 实现最小代码**

要求：

```text
- 下单事务内插入 o_order_timeout_task
- 调度器按 execute_at 批量 claim due tasks，写 OrderTimeoutTriggered outbox
- 支付/取消后将 timeout task 置为 DONE 或 CANCELED
- Kafka consumer 复用现有 handleTimeout(...)，保持二次校验和幂等
```

- [ ] **Step 4: 跑测试确认通过**

执行同上，Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add shiori-java/shiori-order-service deploy/nacos/templates/shiori-order-service-base.yml.tmpl
git commit -m "feat: replace rabbit timeout flow with scheduler and kafka"
```

### Task 5: 迁移 order / social 的 Rabbit 消费者到 Kafka

**Files:**
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/kafka/WalletBalanceChangedKafkaConsumer.java`
- Create: `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/config/SocialKafkaProperties.java`
- Create: `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/config/SocialKafkaConfiguration.java`
- Create: `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/kafka/ProductEventKafkaConsumer.java`
- Modify: `shiori-java/shiori-order-service/build.gradle`
- Modify: `shiori-java/shiori-social-service/build.gradle`
- Modify: `shiori-java/shiori-order-service/src/main/resources/application.yml`
- Modify: `shiori-java/shiori-social-service/src/main/resources/application.yml`
- Modify: `deploy/nacos/templates/shiori-order-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-social-service-base.yml.tmpl`
- Test: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/kafka/WalletBalanceChangedKafkaConsumerTest.java`
- Test: `shiori-java/shiori-social-service/src/test/java/moe/hhm/shiori/social/kafka/ProductEventKafkaConsumerTest.java`

- [ ] **Step 1: 先写失败测试**

新增断言：

```java
@Test
void shouldRetryPendingRefundsWhenWalletBalanceChangedMessageArrivesFromKafka() {
    // order-service 订阅 shiori.payment.wallet.events
}

@Test
void shouldHandleProductPublishedEventFromKafka() {
    // social-service 订阅 shiori.product.events
}
```

- [ ] **Step 2: 跑测试确认失败**

执行：

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.kafka.WalletBalanceChangedKafkaConsumerTest' \
  :shiori-social-service:test --tests 'moe.hhm.shiori.social.kafka.ProductEventKafkaConsumerTest'
```

Expected: FAIL

- [ ] **Step 3: 实现最小代码**

要求：

```text
- 引入 spring-kafka
- order-service / social-service 改为 @KafkaListener
- 失败消息进入 retry topic / dlt，不做无限重试
- payload 继续沿用现有 EventEnvelope，避免改动业务处理器
```

- [ ] **Step 4: 跑测试确认通过**

执行同上，Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add shiori-java/shiori-order-service shiori-java/shiori-social-service deploy/nacos/templates/shiori-social-service-base.yml.tmpl
git commit -m "feat: migrate java consumers from rabbitmq to kafka"
```

## Chunk 4: Go notify 迁移到 Kafka

### Task 6: 用 franz-go 替换 notify 业务事件消费者与聊天 fanout

**Files:**
- Create: `shiori-notify/internal/kafka/consumer.go`
- Create: `shiori-notify/internal/kafka/consumer_test.go`
- Create: `shiori-notify/internal/chatkafka/broadcast.go`
- Create: `shiori-notify/internal/chatkafka/broadcast_test.go`
- Modify: `shiori-notify/go.mod`
- Modify: `shiori-notify/go.sum`
- Modify: `shiori-notify/internal/config/config.go`
- Modify: `shiori-notify/internal/config/config_test.go`
- Modify: `shiori-notify/internal/app/app.go`
- Modify: `shiori-notify/main.go`
- Modify: `shiori-notify/README.md`
- Modify: `deploy/nacos/templates/shiori-notify-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-notify-service-secret.yml.tmpl`

- [ ] **Step 1: 先写失败测试**

新增断言：

```go
func TestBusinessConsumerUsesStableGroup(t *testing.T) {
    // 业务事件消费者使用固定 group id
}

func TestChatBroadcastUsesPerInstanceGroupToSimulateFanout(t *testing.T) {
    // 聊天广播使用 notify-chat-${instanceID}
}
```

- [ ] **Step 2: 跑测试确认失败**

执行：

```bash
cd /Users/hhm/code/shiori/shiori-notify
go test ./internal/config ./internal/app ./internal/kafka ./internal/chatkafka
```

Expected: FAIL

- [ ] **Step 3: 实现最小代码**

要求：

```text
- 业务通知 topic 使用固定 consumer group，保持与现有共享队列语义一致
- 聊天广播 topic 使用每实例独立 group，保留当前 fanout 语义
- 保持现有 EventEnvelope 和 hub/router 逻辑不变
- 配置字段从 rabbitmq 重命名为 kafka，禁止继续依赖 AMQP
```

- [ ] **Step 4: 跑测试确认通过**

执行同上，Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add shiori-notify deploy/nacos/templates/shiori-notify-service-base.yml.tmpl deploy/nacos/templates/shiori-notify-service-secret.yml.tmpl
git commit -m "feat: migrate notify service from rabbitmq to kafka"
```

## Chunk 5: 下线 RabbitMQ、文档与验证

### Task 7: 删除 RabbitMQ 代码路径与配置模板

**Files:**
- Delete: `deploy/rabbitmq/init-rabbitmq-users.sh`
- Delete: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderMqProperties.java`
- Delete: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/OrderMqTopology.java`
- Delete: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/OrderTimeoutConsumer.java`
- Delete: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/WalletBalanceChangedConsumer.java`
- Delete: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OutboxRelayService.java`
- Delete: `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/config/PaymentMqProperties.java`
- Delete: `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/service/PaymentOutboxRelayService.java`
- Delete: `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/service/ProductEventPublisher.java`
- Delete: `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/service/ProductOutboxRelayService.java`
- Delete: `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/mq/SocialMqTopology.java`
- Delete: `shiori-java/shiori-social-service/src/main/java/moe/hhm/shiori/social/mq/ProductEventConsumer.java`
- Delete: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/outbox/service/UserOutboxRelayService.java`
- Delete: `shiori-notify/internal/mq/consumer.go`
- Delete: `shiori-notify/internal/mq/topology.go`
- Delete: `shiori-notify/internal/chatmq/broadcast.go`
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/.env.example`
- Modify: `README.md`
- Modify: `shiori-java/build.gradle`
- Modify: `deploy/nacos/templates/shiori-order-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-order-service-secret.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-payment-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-payment-service-secret.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-product-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-product-service-secret.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-social-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-social-service-secret.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-user-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-user-service-secret.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-notify-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-notify-service-secret.yml.tmpl`

- [ ] **Step 1: 先做失败检查**

执行：

```bash
cd /Users/hhm/code/shiori
rg -n "rabbitmq|amqp|TTL|DLX|OrderMqProperties|PaymentOutboxRelayService|UserOutboxRelayService" README.md deploy shiori-java shiori-notify -g '!**/build/**'
```

Expected: 会看到大量 RabbitMQ / TTL / DLX 残留。

- [ ] **Step 2: 删除旧路径并清理配置**

要求：

```text
- docker compose 不再启动 rabbitmq / rabbitmq-auth-init
- 所有 nacos 模板与 .env.example 删除 RabbitMQ 变量
- Java / Go 依赖中移除 RabbitMQ 客户端
- README 只保留 Kafka / Debezium / scheduler 描述
```

- [ ] **Step 3: 跑仓库级验证**

执行：

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew test

cd /Users/hhm/code/shiori/shiori-notify
go test ./...

cd /Users/hhm/code/shiori
docker compose -f deploy/docker-compose.yml config >/tmp/shiori-compose-final.yaml
rg -n "rabbitmq|amqp|TTL|DLX" README.md deploy shiori-java shiori-notify -g '!**/build/**'
```

Expected:

- Java 测试通过
- Go 测试通过
- `docker compose config` 成功
- 代码与文档中不再保留 RabbitMQ 运行时依赖

- [ ] **Step 4: 提交**

```bash
git add deploy README.md shiori-java shiori-notify
git commit -m "refactor: remove rabbitmq after kafka cutover"
```

### Task 8: 形成 cutover 文档并重新压测

**Files:**
- Create: `docs/runbooks/kafka-cutover-runbook-2026-03-26.md`
- Modify: `README.md`

- [ ] **Step 1: 编写 runbook**

要求覆盖：

```text
- connector 注册顺序
- topic 与 group 命名
- retry / dlt 处理规则
- order timeout scheduler 的回滚方案
- 从 RabbitMQ 切换到 Kafka 的灰度顺序
```

- [ ] **Step 2: 启动全栈并做最小联调**

执行：

```bash
cd /Users/hhm/code/shiori
docker compose -f deploy/docker-compose.yml up -d
```

最小验证：

- 注册用户
- 发布商品
- 下单 / 支付 / 发货 / 确认收货
- 检查 Kafka topic 有消息，业务侧状态正确

- [ ] **Step 3: 跑压测**

执行：

```bash
cd /Users/hhm/code/shiori
k6 run perf/k6-order-realistic.js
```

记录：

- 吞吐
- `create/pay/deliver/confirm/detail` p95
- Kafka lag
- scheduler 扫描耗时

- [ ] **Step 4: 提交**

```bash
git add docs/runbooks/kafka-cutover-runbook-2026-03-26.md README.md
git commit -m "docs: add kafka cutover runbook and validation notes"
```
