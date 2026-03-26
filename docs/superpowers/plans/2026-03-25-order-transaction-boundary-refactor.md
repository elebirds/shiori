# Order Transaction Boundary Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不引入 Redis、不中断现有同步 API 语义的前提下，将 `order-service` 的长事务改造成“短事务 + 持久化命令日志 + 恢复优先、补偿兜底”，消除 `order-service` 连接池先被打满的问题。

**Architecture:** `order-service` 继续作为订单编排者，但不再用单个本地事务包裹远程调用。新增本地命令日志表 `o_order_command` 记录 `create/pay_balance` 的请求快照、远程执行进度和恢复状态；正常路径仍然同步返回，异常路径通过短事务记录进度，并由本地恢复任务优先重试“本地 finalize”，必要时再执行补偿。

**Tech Stack:** Spring Boot, MyBatis, Flyway, Scheduled worker, RabbitMQ outbox, JUnit 5, Mockito, Gradle

---

## 决策

### 方案对比

1. **只扩容 Hikari / 只调连接池**
   - 优点：改动最小，验证快。
   - 缺点：只能推迟阈值，不解决“事务里同步远程调用”这个根因。

2. **推荐：短事务 + 命令日志 + 恢复/补偿**
   - 优点：直接降低 `order-service` 连接持有时长，且能把跨服务一致性从“内存补偿”升级为“持久化恢复”。
   - 缺点：需要新增表、状态机和调度任务，改造范围中等。

3. **MQ-first 全异步 Saga / Redis 预扣**
   - 优点：理论上可进一步释放同步链路压力。
   - 缺点：对现有接口、一致性和运维复杂度影响过大，不适合作为这轮首选。

### 最终选择

采用方案 2。

### 明确不做

- 不引入 Redis 作为库存/余额的真相源
- 不做核心订单/支付事实的延迟写库
- 不在本轮引入分布式事务框架或二阶段提交

## 目标行为

### `createOrder(...)`

最终改造成以下时序：

1. 事务外完成商品读取、参数校验、`PreparedOrderLine` 组装。
2. 短事务写入 `o_order_command(PREPARED)`，占住 `(operator_user_id, command_type, idempotency_key)`。
3. 事务外逐条调用 `productServiceClient.deductStock(...)`。
4. 每次扣库存成功后，用短事务把已成功扣减的 `skuId/quantity/bizNo` 追加写入命令进度。
5. 全部扣减完成后，短事务把命令置为 `REMOTE_SUCCEEDED`。
6. 再用短事务完成：
   - `insertCreateIdempotency`
   - `persistOrder`
   - `appendOrderCreatedOutbox`
   - `appendOrderTimeoutOutbox`
   - 命令置为 `COMPLETED`
7. 如果第 6 步失败：
   - 优先短事务保留 `REMOTE_SUCCEEDED` 进度
   - 恢复任务先尝试再次 finalize 本地订单
   - 只有确认无法 finalize 时，才按进度中记录的已扣减行执行 `releaseStock`

### `payOrderByBalance(...)`

最终改造成以下时序：

1. 短事务校验订单状态、幂等状态，写入 `o_order_command(PREPARED)`。
2. 事务外调用 `paymentServiceClient.reserveOrderPayment(...)`。
3. 短事务把 `paymentNo` 和远程结果写入命令进度；状态置为 `REMOTE_SUCCEEDED`。
4. 再用短事务完成：
   - `markOrderPaidByBalance`
   - `appendOrderPaidOutbox`
   - `insertStatusAudit`
   - `saveOperateIdempotency`
   - 命令置为 `COMPLETED`
5. 如果第 4 步失败：
   - 恢复任务优先重试本地 finalize
   - 只有在本地状态确认无法安全推进时，才调用 `releaseOrderPayment(...)`

### 重复请求语义

- 同一 `idempotencyKey` 命中 `COMPLETED`：
  - 返回当前已有的幂等成功结果
- 同一 `idempotencyKey` 命中 `PREPARED` / `REMOTE_SUCCEEDED` / `COMPENSATING`：
  - 返回新的领域错误码 `ORDER_REQUEST_PROCESSING`
  - 不再二次发起远程扣减/预留
- 同一 `idempotencyKey` 命中 `FAILED` / `COMPENSATED`：
  - 返回该命令记录的终态失败结果
  - 不对同一 key 重新执行副作用

## 文件结构

### 新增文件

- `shiori-java/shiori-order-service/src/main/resources/db/migration/V14__add_order_command_journal.sql`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/domain/OrderCommandType.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/domain/OrderCommandState.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderCommandEntity.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderCommandRecord.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/repository/OrderCommandMapper.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCreateWorkflowService.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderPayWorkflowService.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandRecoveryService.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCreateWorkflowServiceTest.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderPayWorkflowServiceTest.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandRecoveryServiceTest.java`

### 修改文件

- `shiori-java/shiori-common/src/main/java/moe/hhm/shiori/common/error/OrderErrorCode.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderProperties.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java`
- `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandServiceTest.java`
- `deploy/nacos/templates/shiori-order-service-base.yml.tmpl`

### 表结构建议

`o_order_command`

```sql
CREATE TABLE o_order_command (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  command_no VARCHAR(64) NOT NULL,
  command_type VARCHAR(32) NOT NULL,
  operator_user_id BIGINT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  request_payload JSON NOT NULL,
  progress_payload JSON NULL,
  result_code INT NULL,
  result_message VARCHAR(255) NULL,
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(500) NULL,
  next_retry_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_order_command_no (command_no),
  UNIQUE KEY uk_order_command_idem (operator_user_id, command_type, idempotency_key),
  KEY idx_order_command_status_retry (status, next_retry_at, id)
);
```

`progress_payload` 约定：

- `CREATE_ORDER`
  - `preparedLines`
  - `deductedLines`
- `PAY_BALANCE_ORDER`
  - `paymentNo`
  - `reserveStatus`

## Chunk 1: 基础模型与持久化命令日志

### Task 1: 新增命令日志模型、枚举和迁移

**Files:**
- Create: `shiori-java/shiori-order-service/src/main/resources/db/migration/V14__add_order_command_journal.sql`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/domain/OrderCommandType.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/domain/OrderCommandState.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderCommandEntity.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderCommandRecord.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/repository/OrderCommandMapper.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderProperties.java`
- Modify: `deploy/nacos/templates/shiori-order-service-base.yml.tmpl`

- [ ] **Step 1: 为命令状态机写最小失败测试**

在 `OrderCommandRecoveryServiceTest` 里先写出状态机预期：

```java
@Test
void shouldTreatRemoteSucceededCreateCommandAsRecoverable() {
    // given command.status == REMOTE_SUCCEEDED
    // when recovery scans due commands
    // then it tries local finalize before compensation
}
```

- [ ] **Step 2: 跑失败测试确认当前缺少模型与服务**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandRecoveryServiceTest'
```

Expected: FAIL，提示类或方法不存在。

- [ ] **Step 3: 实现命令日志基础设施**

新增：

```java
public enum OrderCommandType {
    CREATE_ORDER,
    PAY_BALANCE_ORDER
}

public enum OrderCommandState {
    PREPARED,
    REMOTE_SUCCEEDED,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED
}
```

新增 `OrderCommandMapper` 的最小接口：

```java
int insertOrderCommand(OrderCommandEntity entity);
OrderCommandRecord findByOperatorAndTypeAndIdempotencyKey(Long operatorUserId, String commandType, String idempotencyKey);
int markPreparedProgress(...);
int markRemoteSucceeded(...);
int markCompleted(...);
int markFailed(...);
List<OrderCommandRecord> listRecoveryCandidates(int limit);
int markCompensating(...);
int markCompensated(...);
```

并在 `OrderProperties` / Nacos 模板中增加：

```yaml
order:
  command:
    recovery-fixed-delay-ms: 3000
    recovery-batch-size: 100
    stale-prepared-seconds: 30
    max-backoff-seconds: 300
```

- [ ] **Step 4: 跑测试确认基础设施可编译**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:compileJava :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandRecoveryServiceTest'
```

Expected: 失败点从“类不存在”推进到“业务行为未实现”。

- [ ] **Step 5: Commit**

```bash
git add \
  shiori-java/shiori-order-service/src/main/resources/db/migration/V14__add_order_command_journal.sql \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/domain/OrderCommandType.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/domain/OrderCommandState.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderCommandEntity.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/model/OrderCommandRecord.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/repository/OrderCommandMapper.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/config/OrderProperties.java \
  deploy/nacos/templates/shiori-order-service-base.yml.tmpl \
  shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandRecoveryServiceTest.java
git commit -m "feat: add durable order command journal"
```

## Chunk 2: 创建订单改造

### Task 2: 将 `createOrder(...)` 改造成短事务 + 进度持久化

**Files:**
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCreateWorkflowService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java`
- Modify: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandServiceTest.java`
- Create: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCreateWorkflowServiceTest.java`

- [ ] **Step 1: 先写失败测试覆盖创建链路的三种关键分支**

测试至少覆盖：

```java
@Test
void shouldReturnProcessingWhenCreateCommandAlreadyInFlight() {}

@Test
void shouldPersistDeductedLinesProgressBeforeFinalLocalPersist() {}

@Test
void shouldRecordRemoteSucceededCreateCommandWhenFinalLocalPersistFails() {}
```

- [ ] **Step 2: 跑测试确认当前行为不满足**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCreateWorkflowServiceTest'
```

Expected: FAIL

- [ ] **Step 3: 实现 `OrderCreateWorkflowService`**

要求：

- `prepareOrderLines(...)` 保持事务外执行
- 首个本地短事务只负责插入命令日志 `PREPARED`
- 逐条 `deductStock` 成功后，立即短事务更新 `deductedLines`
- 全量扣减完成后，置 `REMOTE_SUCCEEDED`
- 最终本地落单单独用短事务完成
- `OrderCommandService.createOrder(...)` 只做入口委派，不再自己持有整段 `@Transactional`

服务骨架应接近：

```java
public CreateOrderResponse createOrder(...) {
    PreparedCreateContext ctx = prepareContext(...);
    OrderCommandRecord command = prepareCreateCommand(ctx);
    return switch (command.status()) {
        case COMPLETED -> buildCompletedResponse(command);
        case PREPARED, REMOTE_SUCCEEDED, COMPENSATING -> throw processing();
        default -> executeForwardAndFinalize(ctx, command);
    };
}
```

- [ ] **Step 4: 跑测试确认创建链路通过**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCreateWorkflowServiceTest'
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandServiceTest'
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCreateWorkflowService.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java \
  shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCreateWorkflowServiceTest.java \
  shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandServiceTest.java
git commit -m "refactor: split create order into short transactions"
```

## Chunk 3: 余额支付改造

### Task 3: 将 `payOrderByBalance(...)` 改造成短事务 + 命令恢复

**Files:**
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderPayWorkflowService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java`
- Modify: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandServiceTest.java`
- Create: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderPayWorkflowServiceTest.java`
- Modify: `shiori-java/shiori-common/src/main/java/moe/hhm/shiori/common/error/OrderErrorCode.java`

- [ ] **Step 1: 先写支付链路失败测试**

至少覆盖：

```java
@Test
void shouldReturnProcessingWhenBalancePayCommandAlreadyInFlight() {}

@Test
void shouldPersistPaymentNoBeforeFinalOrderPaidTransaction() {}

@Test
void shouldLeaveRecoverableCommandWhenReserveSucceededButMarkPaidFails() {}
```

- [ ] **Step 2: 跑失败测试**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderPayWorkflowServiceTest'
```

Expected: FAIL

- [ ] **Step 3: 实现 `OrderPayWorkflowService` 与新错误码**

新增错误码：

```java
ORDER_REQUEST_PROCESSING(50039, "请求处理中，请稍后重试")
```

支付流程要求：

- 第一个本地短事务只做校验与 `PREPARED`
- 远程 `reserveOrderPayment(...)` 在事务外
- 收到 `paymentNo` 后，先独立短事务落命令进度
- 最终 `markOrderPaidByBalance(...)` 单独短事务执行
- 命中同 `idempotencyKey` 的处理中命令时，直接抛 `ORDER_REQUEST_PROCESSING`

- [ ] **Step 4: 跑支付链路测试**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderPayWorkflowServiceTest'
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandServiceTest'
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderPayWorkflowService.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java \
  shiori-java/shiori-common/src/main/java/moe/hhm/shiori/common/error/OrderErrorCode.java \
  shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderPayWorkflowServiceTest.java \
  shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandServiceTest.java
git commit -m "refactor: split balance pay into short transactions"
```

## Chunk 4: 恢复优先、补偿兜底

### Task 4: 新增 `OrderCommandRecoveryService`

**Files:**
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandRecoveryService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java`
- Create: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandRecoveryServiceTest.java`

- [ ] **Step 1: 先写恢复策略失败测试**

至少覆盖：

```java
@Test
void shouldRetryLocalFinalizeBeforeCompensatingCreateCommand() {}

@Test
void shouldCompensateCreateCommandUsingRecordedDeductedLines() {}

@Test
void shouldRetryLocalFinalizeBeforeReleasingReservedPayment() {}

@Test
void shouldBackoffWhenCompensationFails() {}
```

- [ ] **Step 2: 跑失败测试**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandRecoveryServiceTest'
```

Expected: FAIL

- [ ] **Step 3: 实现恢复逻辑**

恢复规则：

- `CREATE_ORDER`
  - `REMOTE_SUCCEEDED`：
    - 若订单与幂等记录已存在，置 `COMPLETED`
    - 否则先尝试本地 finalize
    - 若遇到不可恢复冲突，再按 `deductedLines` 做 `releaseStock`
- `PAY_BALANCE_ORDER`
  - `REMOTE_SUCCEEDED`：
    - 若订单已是 `PAID` 且 `paymentNo` 一致，置 `COMPLETED`
    - 否则先尝试本地 finalize
    - 若确定无法推进，再做 `releaseOrderPayment`
- 失败统一套用和 `OutboxRelayService` 一样的指数退避

- [ ] **Step 4: 跑恢复任务测试**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandRecoveryServiceTest'
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandRecoveryService.java \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java \
  shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandRecoveryServiceTest.java
git commit -m "feat: add order command recovery worker"
```

## Chunk 5: 指标、文档、回归验证

### Task 5: 补齐指标和回归验证

**Files:**
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java`
- Modify: `docs/runbooks/order-latency-investigation-2026-03-25.md`

- [ ] **Step 1: 给命令日志流程补指标**

至少新增：

- `order_command_total{type,state}`
- `order_command_recovery_total{type,result}`
- `order_command_compensation_total{type,result}`
- `order_command_processing_total{type}`

- [ ] **Step 2: 更新现有调查文档**

在 [docs/runbooks/order-latency-investigation-2026-03-25.md](/Users/hhm/code/shiori/docs/runbooks/order-latency-investigation-2026-03-25.md) 追加“最终落地方案”摘要，并注明：

- 不引入 Redis
- 通过短事务和命令日志解决 `order-service` 连接池问题
- 恢复优先，补偿兜底

- [ ] **Step 3: 跑 order-service 全量测试**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test
```

Expected: PASS

- [ ] **Step 4: 跑一轮轻量压测复验**

Run:

```bash
cd /Users/hhm/code/shiori
RUN_WS=0 RUN_ORDER=1 PERF_PREFIX=verify36 PERF_LOG_DIR=/Users/hhm/code/shiori/ci-logs/perf/verify/order_v36_refactor \
K6_ORDER_VUS=36 K6_ORDER_DURATION=20s ./scripts/ci/run_perf_oneclick.sh
```

Expected:

- `http_req_failed = 0`
- `order-service` 的 `hikaricp_connections_pending` 峰值明显低于当前基线

- [ ] **Step 5: Commit**

```bash
git add \
  shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java \
  docs/runbooks/order-latency-investigation-2026-03-25.md
git commit -m "docs: record final order consistency refactor plan"
```

## 验收标准

- `OrderCommandService.createOrder(...)` 与 `payOrderByBalance(...)` 不再由单个长事务包裹远程调用
- `order-service` 存在可重放的持久化命令日志，不再只靠日志打印补偿失败
- 同 `idempotencyKey` 的处理中请求不会再次触发远程副作用
- 正常路径 API 返回不变
- 异常路径具备“恢复优先、补偿兜底”的可追踪状态
- `36/40 VU` 下 `order-service` 的 `hikaricp_connections_pending` 相比当前基线显著下降

## 风险与边界

- 本方案不会消除 `payment-service` 钱包热点和 `product-service` 库存热点，只会先把 `order-service` 的连接池瓶颈拿掉。
- `createOrder` 的逐条扣库存进度必须在每次成功后立即持久化，否则进程崩溃时仍可能丢失已扣减行信息。
- `releaseStock(...)` 目前不是“无前置扣减证明的安全 no-op”；恢复逻辑只能按已记录的 `deductedLines` 精确回补，不能对未知行盲目释放。
- 如果本轮改造后瓶颈继续下沉到 `payment-service` 或 `product-service`，下一步再分别处理钱包热点与库存热点，而不是跳到 Redis。

Plan complete and saved to `docs/superpowers/plans/2026-03-25-order-transaction-boundary-refactor.md`. Ready to execute?
