# Kafka 消费侧健壮性面试问答

## 一、术语题

### Q1：你先解释一下，什么叫 `at-least-once`？

答：

`at-least-once` 的意思是 Kafka 只保证消息至少会投递一次，不保证业务侧只执行一次。所以我不会假设“消息绝不会重复”，而是把重复消费当作正常前提，再用消费端幂等保证业务副作用只落一次。

### Q2：什么叫幂等？和“不重复消费”是一个意思吗？

答：

不是一个意思。

不重复消费强调的是“消息只来一次”，而幂等强调的是：

> 即使同一条消息重复到达，业务结果也只能落一次。

这次项目里的幂等是基于 `eventId` 做持久化消费日志去重，不是靠运气赌消息不重复。

### Q3：什么叫死信队列？Kafka 里为什么又叫 `DLT`？

答：

Kafka 里更准确的说法其实是死信 `topic`，所以常见缩写是 `DLT`，即 `Dead Letter Topic`。面试里大家会口语化说“死信队列”，但如果要讲严谨一点，可以补一句：

> Kafka 里本质上是死信 topic，不是传统 MQ 语境里的 queue。

在这个项目里，失败消息会被投递到 `<原 topic>.dlt`。

### Q4：什么叫 `ack`？

答：

`ack` 就是消费者显式告诉 Kafka：“这条消息我已经处理到可以提交 offset 了。”
我这次用的是手动 `ack`，目的是把业务成功和 offset 提交绑定起来。

### Q5：什么叫 `offset`？

答：

`offset` 是 Kafka 分区内消息的位置编号。它只回答一个问题：

> 这个 consumer group 下次从哪里继续读。

它不负责回答“这条业务事件是不是已经成功处理过”，后者是本地消费日志的职责。

### Q6：`consumer group` 在这个方案里有什么作用？

答：

它有两层作用：

1. Kafka 层面，它决定消费位点和分区分配。
2. 业务层面，它决定幂等去重的作用域。

所以我在消费日志表里用 `(consumerGroup, eventId)` 做唯一键，而不是只用 `eventId`。

### Q7：`auto-offset-reset: latest` 是不是表示每次重启都从最新消息开始读？

答：

不是，这个说法是错的。

`auto-offset-reset` 只在“没有 committed offset”或者“原 offset 无效”时才生效。只要 Kafka 里已经有这个 consumer group 的已提交 offset，消费者重启后就会从已提交 offset 继续，而不是看 `latest`。

---

## 二、方案设计题

### Q8：你这次到底补了哪些能力？

答：

我补了 5 块：

1. 手动 `ack`，业务成功后再提交 offset
2. 基于 `eventId` 的持久化幂等
3. 可恢复异常有限重试
4. 不可恢复异常或重试耗尽进入 DLT
5. 消费日志记录 `topic / partition / offset / status / last_error`

### Q9：为什么你不直接说自己实现了 `exactly-once`？

答：

因为这次实现的不是端到端 `exactly-once`，而是：

1. Kafka `at-least-once`
2. 消费端持久化幂等

真正的业务副作用发生在数据库和业务代码里，最后还是要靠幂等保证“重复消息不重复落副作用”。所以我不会把 Kafka 自身能力和业务结果混在一起夸大成端到端 `exactly-once`。

### Q10：为什么不用 Redis `SETNX` 做幂等？

答：

Redis `SETNX` 可以做，但这次我更倾向数据库持久化消费日志。因为它不仅能去重，还能顺手记录：

1. `consumer_group`
2. `topic`
3. `partition`
4. `offset`
5. `status`
6. `last_error`

这样它同时兼顾了幂等、审计和排障。

### Q11：为什么本地消费日志和 Kafka offset 要同时存在？

答：

因为它们解决的是两个完全不同的问题。

Kafka offset 解决的是：

1. 消费者重启后从哪里继续读

本地消费日志解决的是：

1. 这条业务事件有没有成功处理过
2. 如果失败了，失败在哪
3. 是否应该重复执行业务副作用

两者互补，谁也替代不了谁。

### Q12：为什么要改成手动 `ack`？

答：

因为我要把 offset 提交点放到业务成功之后。否则最危险的情况是：

1. 业务副作用还没真正成功
2. offset 却已经推进
3. 进程宕机后消息不再重放

手动 `ack` 就是为了避免“业务失败但消息丢回放机会”。

### Q13：为什么要配 DLT？

答：

DLT 的价值不只是“消息别丢”，更重要的是把坏消息从主消费链路隔离出去。否则一个脏消息可能会在同一分区里反复失败，把正常消息一直堵在后面。

### Q14：你这里的 DLT 是怎么路由的？

答：

我是用 `DeadLetterPublishingRecoverer` 按 `<原 topic>.dlt` 路由，并保持原分区号不变。这样定位问题更直接，也符合 Kafka 的排障习惯。

### Q15：你为什么还要配置 `commitRecovered(true)`？

答：

因为消息一旦已经被成功送进 DLT，就说明主链路已经完成了“隔离处理”。这时候应该提交它的 offset，避免它继续在主链路上被重复拉起，造成死循环。

这个点很容易被忽略，但技术深度很够。

---

## 三、失败处理题

### Q16：消费失败怎么办？

答：

我把异常分成两类：

1. 可恢复异常：有限重试
2. 不可恢复异常：直接进 DLT

可恢复异常一般是数据库瞬时失败、业务处理失败这类问题；不可恢复异常一般是 JSON 非法、payload 缺失、`eventId` 缺失这类脏消息。

### Q17：为什么脏消息不应该重试？

答：

因为脏消息不是“暂时失败”，而是“内容本身就错了”。你重试 3 次、30 次、300 次，它还是错的。

所以对这类错误，正确做法不是一直重试，而是尽快送 DLT，把主消费链路释放出来。

### Q18：如果业务成功了，但 `ack` 之前服务宕机了，会怎样？

答：

这条消息会再次被投递，因为 Kafka 还没收到 offset 提交。

但我有消费端幂等，所以：

1. Kafka 可能再次投递
2. 业务副作用不会再次重复执行

这正是 `at-least-once + consumer-side idempotency` 的典型模式。

### Q19：如果消息已经进了 DLT，还会不会继续卡主链路？

答：

按当前实现，不会。因为错误处理器启用了 `commitRecovered(true)`，也就是说消息成功送入 DLT 后，会提交这条恢复消息的 offset，主链路会继续向后推进。

### Q20：order-service 里为什么还要额外改退款重试逻辑？

答：

因为原来那段批量退款重试逻辑会吞掉单条失败，只打日志不抛错。这样 Kafka 会误判为“整条消息处理成功”。

我这次专门补了 `retryPendingRefundsBySellerOrThrow(...)`：

1. 普通场景可以继续用宽松版，只记日志
2. Kafka 场景必须用抛错版
3. 只要批次里仍有失败，就抛 `OrderRefundBatchRetryException`

这样消息才能继续走重试或 DLT，而不是被误提交。

---

## 四、重启与恢复题

### Q21：消费者重启后从哪里继续消费？

答：

从 Kafka 已提交 offset 继续消费。

如果这个 consumer group 之前没有 committed offset，或者 offset 已经无效，才会回落到 `auto-offset-reset`。当前配置是 `latest`，也就是那种情况下从最新消息开始读。

### Q22：如果本地消费日志里已经是 `SUCCEEDED`，但 Kafka 还是把消息又投过来了，怎么办？

答：

直接跳过，不再重复执行业务副作用。

因为我在 `startProcessing(...)` 里会先查 `(consumerGroup, eventId)`，如果已经是 `SUCCEEDED`，就直接返回，不再重复处理。

### Q23：为什么消费日志状态要有 `PROCESSING / SUCCEEDED / FAILED`，而不是只记一个“是否成功”？

答：

因为状态机越完整，排障越清楚。

1. `PROCESSING` 说明消息已经开始处理
2. `SUCCEEDED` 说明副作用已经成功落地
3. `FAILED` 说明处理失败并记录了 `last_error`

这比只有一个布尔值更适合排查重试、失败和补偿问题。

---

## 五、边界与不夸大

### Q24：你这次是不是已经把全项目所有 Kafka 消费器都统一了？

答：

没有，我这次主要补齐的是 `order-service` 和 `social-service` 两条 Java CDC 消费链。所以我会说“这两条链路已经按统一模式改造”，不会夸大成“所有消费者都已统一”。

### Q25：你这次是不是把 DLT 回放平台也做了？

答：

没有，这次做到的是消费闭环本身：

1. 幂等
2. 重试
3. DLT 隔离
4. offset 提交语义清晰化
5. 消费日志审计

还没有扩展到 DLT 可视化回放平台。

### Q26：你怎么证明这套方案不是只停留在设计上，真实跑过吗？

答：

跑过，而且我做的是带真实容器的联调，不是只看单测。

我当时实际验证了两类消息：

1. 成功消息重复投递两次
2. payload 非法的脏消息投递一次

真实结果是：

1. `social-service` 的成功事件 `it-social-ok-1774546040` 在 `s_event_consume_log` 里只落了 1 条 `SUCCEEDED`，对应 `s_post` 也只插入了 1 条
2. `order-service` 的成功事件 `it-order-ok-1774546040` 在 `o_event_consume_log` 里也只落了 1 条 `SUCCEEDED`
3. `it-social-bad-1774546040` 实际进入了 `shiori.cdc.product.outbox.raw.dlt`
4. `it-order-bad-1774546040` 实际进入了 `shiori.cdc.payment.outbox.raw.dlt`

所以这套方案不是“我觉得应该可以”，而是：

1. 重复消费已经被真实验证能被幂等拦住
2. 失败消息已经被真实验证会进 DLT

### Q27：真实联调时，有没有暴露出单测里看不出来的问题？

答：

有，而且这正好说明真实联调有价值。

我踩到两个运行时问题：

1. 本地 MySQL 历史数据卷里的账号密码和当前 `.env` / Nacos 不一致，先导致服务整体起不来
2. Spring Boot 4 下只引 `spring-kafka` 不够，运行时还要显式引入 `spring-boot-kafka`，不然 `KafkaOperations` 不会自动装配，错误处理器会直接起不来

第二个点尤其值得讲，因为它有技术深度：

1. 单测里如果手工 mock 了 `KafkaOperations`，你可能感觉一切正常
2. 但真实容器启动时，自动配置没生效，消费者会直接因为缺 Bean 启动失败

所以我后来补了：

1. `build.gradle` 里的 `spring-boot-kafka`
2. 自动配置集成测试，确保 `KafkaOperations / ConsumerFactory / CommonErrorHandler` 在真实 Boot 自动配置下都能起来

---

## 六、完整回答模板

### 30 秒版本

> 我这次补的是 Kafka 消费侧的可靠性闭环。语义上按 `at-least-once` 设计，不假设消息只来一次，而是基于 `eventId` 做持久化幂等，保证业务副作用不重复执行。
> 处理成功后才手动 `ack` 提交 offset；失败时区分可恢复和不可恢复异常，可恢复异常有限重试，不可恢复异常或重试耗尽进入 `<topic>.dlt`。另外我还把 `topic、partition、offset、status、last_error` 落到了消费日志表里，方便审计和排障。这套方案我还做过真实联调，重复消息只落一次，脏消息会实际进入 DLT。

### 90 秒版本

> 这次我重点补的是 Kafka 消费侧的可靠性，因为 CDC 把消息投到 Kafka 只是前半段，真正决定链路靠不靠谱的是消费侧。
> 我按 `at-least-once` 来设计，也就是接受消息可能重复投递，然后用消费端幂等保证业务副作用只落一次。每条事件都带 `eventId`，消费前先查本地消费日志，按 `(consumerGroup, eventId)` 做持久化去重；如果已经成功处理过，就直接跳过。
> offset 这块我改成了业务成功后手动 `ack`，这样提交点就和业务成功绑定起来了。失败处理上，我用 `DefaultErrorHandler` 做有限重试，用 `DeadLetterPublishingRecoverer` 把不可恢复异常或重试耗尽的消息送到 `<topic>.dlt`，并通过 `commitRecovered(true)` 提交恢复后的 offset，避免死信消息继续卡主链路。
> 另外我把 `topic、partition、offset、status、last_error` 一起落库，方便排障。在 `order-service` 里，我还额外修了一个关键问题：原来退款批量重试会吞掉单条失败，导致 Kafka 误判成功；现在只要批次里还有失败就抛异常，让消息继续走重试或 DLT。真实联调里，我还手工发过重复消息和脏消息，验证过成功消息只落一次、坏消息会进入 DLT。这样重复消费、失败处理和重启恢复这三类追问就都能讲完整。

### 面试时最该强调的三句话

1. 我这条 Kafka CDC 消费链按 `at-least-once` 设计，靠消费端幂等保证副作用不重复。
2. 业务成功后才手动 `ack`，可恢复异常有限重试，脏消息或重试耗尽的消息进入 DLT。
3. 消费者重启从 Kafka 已提交 offset 继续，本地消费日志负责幂等、审计和排障，不替代 Kafka offset。
