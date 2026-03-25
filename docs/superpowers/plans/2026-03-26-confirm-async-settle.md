# Confirm Async Settle Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将注册链路改为预建钱包，并把 `confirm` 的同步支付结算拆到订单命令恢复链路中，缩短确认收货同步时延与热点锁持有时间。

**Architecture:** `user-service` 在注册事务内完成用户建档后，立即调用 `payment-service` 内部幂等接口初始化钱包；`order-service` 的 `confirm` 保持本地短事务，只落订单完成、副作用审计与异步结算命令，支付结算改由 `o_order_command` 恢复任务异步执行；`payment-service` 提供轻量、幂等的钱包初始化入口，避免首笔结算时再走“查无则插”分支。

**Tech Stack:** Spring Boot, RestClient, MyBatis, Scheduled recovery worker, JUnit 5, Mockito, Gradle

---

## Chunk 1: 注册预建钱包

### Task 1: 为注册成功后预建钱包补测试与实现

**Files:**
- Create: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/config/UserPaymentClientProperties.java`
- Create: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/config/UserRestClientConfiguration.java`
- Create: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/payment/client/PaymentWalletClient.java`
- Modify: `shiori-java/shiori-user-service/src/main/java/moe/hhm/shiori/user/auth/service/AuthService.java`
- Modify: `shiori-java/shiori-user-service/src/main/resources/application.yml`
- Modify: `deploy/nacos/templates/shiori-user-service-base.yml.tmpl`
- Modify: `deploy/nacos/templates/shiori-user-service-secret.yml.tmpl`
- Test: `shiori-java/shiori-user-service/src/test/java/moe/hhm/shiori/user/auth/service/AuthServiceTest.java`

- [ ] **Step 1: 先写失败测试**

新增断言：

```java
@Test
void shouldCreateWalletAfterRegisterSuccess() {
    // register 成功后调用 payment wallet internal api
}
```

- [ ] **Step 2: 跑测试确认失败**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-user-service:test --tests 'moe.hhm.shiori.user.auth.service.AuthServiceTest'
```

Expected: FAIL，提示缺少 wallet client 依赖或未发生调用。

- [ ] **Step 3: 实现最小代码**

要求：

```java
public void ensureWalletInitialized(Long userId) {
    // POST /api/payment/internal/wallets/{userId}/init
    // 已存在返回成功
}
```

`AuthService.register(...)` 在 `insertUserRole(...)` 之后调用该接口；若失败，整个注册事务回滚。

- [ ] **Step 4: 跑测试确认通过**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-user-service:test --tests 'moe.hhm.shiori.user.auth.service.AuthServiceTest'
```

Expected: PASS

## Chunk 2: Payment 内部钱包初始化接口

### Task 2: 为 payment-service 新增内部幂等建钱包接口

**Files:**
- Create: `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/dto/internal/InitWalletAccountResponse.java`
- Modify: `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/controller/InternalPaymentOrderController.java`
- Modify: `shiori-java/shiori-payment-service/src/main/java/moe/hhm/shiori/payment/service/PaymentService.java`
- Modify: `shiori-java/shiori-payment-service/src/test/java/moe/hhm/shiori/payment/service/PaymentServiceTest.java`
- Modify: `shiori-java/shiori-payment-service/src/test/java/moe/hhm/shiori/payment/controller/InternalPaymentOrderControllerTest.java`

- [ ] **Step 1: 先写失败测试**

新增断言：

```java
@Test
void shouldInitWalletAccountIdempotently() {
    // 不存在时插入，已存在时返回 idempotent=true
}
```

- [ ] **Step 2: 跑测试确认失败**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-payment-service:test --tests 'moe.hhm.shiori.payment.service.PaymentServiceTest' --tests 'moe.hhm.shiori.payment.controller.InternalPaymentOrderControllerTest'
```

Expected: FAIL

- [ ] **Step 3: 实现最小代码**

要求：

```java
@PostMapping("/internal/wallets/{userId}/init")
```

服务层语义：

- 已有钱包：直接返回 `idempotent=true`
- 无钱包：插入 `p_wallet_account(user_id, 0, 0)`，返回 `idempotent=false`
- 并发重复插入：吞掉唯一键冲突后返回 `idempotent=true`

- [ ] **Step 4: 跑测试确认通过**

Run 同上，Expected: PASS

## Chunk 3: confirm 异步化结算

### Task 3: 为 confirm 引入异步结算命令并移除同步 settle

**Files:**
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/domain/OrderCommandType.java`
- Create: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderConfirmSettlementWorkflowService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandService.java`
- Modify: `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderCommandRecoveryService.java`
- Modify: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandServiceTest.java`
- Create: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderConfirmSettlementWorkflowServiceTest.java`
- Modify: `shiori-java/shiori-order-service/src/test/java/moe/hhm/shiori/order/service/OrderCommandRecoveryServiceTest.java`

- [ ] **Step 1: 先写失败测试**

新增断言：

```java
@Test
void shouldNotSynchronouslySettleWhenBuyerConfirmReceipt() {
    // confirm 同步路径不再调用 paymentServiceClient.settleOrderPayment(...)
}

@Test
void shouldRecoverConfirmSettlementCommandAsync() {
    // recovery 调度时再调用 settle
}
```

- [ ] **Step 2: 跑测试确认失败**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandServiceTest' --tests 'moe.hhm.shiori.order.service.OrderCommandRecoveryServiceTest' --tests 'moe.hhm.shiori.order.service.OrderConfirmSettlementWorkflowServiceTest'
```

Expected: FAIL

- [ ] **Step 3: 实现最小代码**

要求：

- `confirmReceiptAsBuyer(...)`、`finishOrderAsSeller(...)`、`finishOrderAsAdmin(...)` 本地事务完成后只登记结算命令，不再同步调用 `settle`
- 仅 `BALANCE_ESCROW` 且未退款订单才登记结算命令
- 命令恢复逻辑：
  - 已结算成功则 `markCompleted`
  - 未结算则调用 `paymentServiceClient.settleOrderPayment(...)`
  - 失败按现有 backoff 机制重试

- [ ] **Step 4: 跑测试确认通过**

Run 同上，Expected: PASS

## Chunk 4: 集成验证与提交

### Task 4: 运行目标验证并提交

**Files:**
- Modify: only files changed above

- [ ] **Step 1: 跑目标测试集**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew \
  :shiori-user-service:test --tests 'moe.hhm.shiori.user.auth.service.AuthServiceTest' \
  :shiori-payment-service:test --tests 'moe.hhm.shiori.payment.service.PaymentServiceTest' \
  :shiori-payment-service:test --tests 'moe.hhm.shiori.payment.controller.InternalPaymentOrderControllerTest' \
  :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandServiceTest' \
  :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderCommandRecoveryServiceTest' \
  :shiori-order-service:test --tests 'moe.hhm.shiori.order.service.OrderConfirmSettlementWorkflowServiceTest'
```

- [ ] **Step 2: 构建涉及服务**

Run:

```bash
cd /Users/hhm/code/shiori/shiori-java
./gradlew :shiori-user-service:bootJar :shiori-payment-service:bootJar :shiori-order-service:bootJar -x test
```

- [ ] **Step 3: 提交**

```bash
git add <changed files>
git commit -m "perf: async confirm settlement and precreate wallets"
```
