# 可观测性改造面试知识点

## 1. 这次改造解决了什么问题

这次改造的目标，不是单纯“把 Grafana 跑起来”，而是把压测结果和运行时指标打通。

改造前我只有两类证据：

1. k6 压测报告
2. 应用日志

这两类信息只能说明“系统有没有变慢、有没有报错”，但很难回答更关键的问题：

1. 到底是哪个服务先到瓶颈
2. 是接口 QPS 顶住了，还是数据库连接池顶住了
3. 是库存扣减变慢了，还是异步消费积压了

所以这次我补了一套最小可用的可观测性闭环：

1. Spring Boot Actuator 暴露 Prometheus 指标
2. Prometheus 定时抓取各服务 metrics
3. Grafana 预置 dashboard 展示关键瓶颈指标

这样面试里就可以把“我做过压测”升级成“我能结合压测和监控定位瓶颈”。

---

## 2. 这次重点监控了哪些指标

这次没有追求“大而全”，而是只挑了几个最适合面试讲、也最能反映瓶颈的指标。

### 2.1 订单创建 QPS

使用指标：

- `shiori_order_transition_total{from="NEW",to="UNPAID"}`

这个指标本质上表示订单从“新建”进入“待支付”的状态迁移次数。

因为订单创建成功后一定会进入 `UNPAID`，所以它可以作为订单创建吞吐的近似观测指标。

Grafana 面板里用的是：

- `Order Create QPS`

对应 PromQL 是对这个 counter 做 `rate(...)`。

### 2.2 库存扣减延迟

使用指标：

- `shiori_product_stock_deduct_latency_seconds`

这个指标是在 `ProductStockService.deduct(...)` 里埋的 `Timer`，统计一次库存扣减从进入方法到返回的耗时。

这里我把结果按标签区分成：

- `success`
- `idempotent_success`
- `stock_not_enough`
- `error`

Grafana 面板里重点看：

- `Stock Deduct p95 Latency`

因为压测或高并发场景下，p95 比平均值更容易暴露尾延迟问题。

### 2.3 Kafka 消费 lag

使用指标：

- `shiori_order_kafka_consumer_lag_seconds`

这个指标是在 `WalletBalanceOutboxCdcConsumer` 里按 `record.timestamp()` 和当前时间差计算出来的。

它表达的是：

- 消息写入 Kafka 到当前消费者真正处理之间，已经滞后了多少秒

Grafana 面板里对应：

- `Kafka Consumer Lag`

这个指标很适合解释“异步链路是不是已经开始积压”。

### 2.4 连接池活跃连接数

使用指标：

- `hikaricp_connections_active`

这个指标不是手写埋点，而是 Hikari + Micrometer 默认会暴露出来的连接池活跃连接数。

Grafana 面板里对应：

- `Hikari Active Connections`

它很适合和接口延迟一起看：

1. 如果延迟升高，活跃连接数也接近池上限，通常说明数据库或连接池压力在上升。
2. 如果延迟升高，但连接池并不高，那瓶颈更可能在业务逻辑、下游调用或消息积压。

### 2.5 通用 HTTP 指标

这次 dashboard 里还顺手放了三组 Actuator 默认指标：

1. `HTTP QPS`
2. `HTTP p95 Latency`
3. `HTTP 5xx Ratio`

这些指标不是业务特化埋点，但很适合先快速判断：

1. 哪个服务流量最高
2. 哪个服务先开始变慢
3. 哪个服务开始出现错误率上升

---

## 3. 这次具体做了哪些改动

### 3.1 暴露 Prometheus endpoint

给几个核心 Spring Boot 服务统一补了 Actuator Prometheus 暴露配置，包括：

- `shiori-gateway-service`
- `shiori-user-service`
- `shiori-product-service`
- `shiori-order-service`
- `shiori-payment-service`

核心思路是：

1. 打开 `health/info/prometheus` 等 endpoint
2. 允许 Prometheus 抓取 `/actuator/prometheus`

这样每个服务都能被 Prometheus 统一采集。

### 3.2 补业务指标埋点

主要新增了两类埋点：

1. `product-service` 的库存扣减耗时指标
2. `order-service` 的 Kafka 消费 lag 指标

同时订单创建 QPS 复用了已有的订单状态迁移计数器，没有重复造轮子。

### 3.3 增加 Grafana 预置 dashboard

我把默认 dashboard 改成了一个更贴近压测排障场景的总览面板，核心面板包括：

1. `Order Create QPS`
2. `Stock Deduct p95 Latency`
3. `Kafka Consumer Lag`
4. `Hikari Active Connections`
5. `HTTP QPS`
6. `HTTP p95 Latency`
7. `HTTP 5xx Ratio`
8. `Order Command Processing`

这样面试时可以直接截图说明：

- 我不是只会看日志，而是有一套最小可用的运行时监控面板。

---

## 4. 这次代码落点

你可以直接把下面这些文件当成面试时的落地证据：

- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OrderMetrics.java`
- `shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/WalletBalanceOutboxCdcConsumer.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/service/ProductMetrics.java`
- `shiori-java/shiori-product-service/src/main/java/moe/hhm/shiori/product/service/ProductStockService.java`
- `deploy/observability/grafana/dashboards/shiori-overview.json`
- `shiori-java/shiori-gateway-service/src/main/resources/application.yml`
- `shiori-java/shiori-user-service/src/main/resources/application.yml`
- `shiori-java/shiori-product-service/src/main/resources/application.yml`
- `shiori-java/shiori-order-service/src/main/resources/application.yml`
- `shiori-java/shiori-payment-service/src/main/resources/application.yml`

---

## 5. 面试时可以怎么讲这次方案

建议按下面这个顺序讲。

### 第一步：先讲问题

“我原来只有 k6 压测报告和日志，但这不足以定位瓶颈。面试官如果追问‘你怎么知道慢在什么地方’，只靠日志其实说服力不够。”

### 第二步：讲最小闭环

“所以我补了一套最小可用的可观测性闭环：应用通过 Actuator 暴露 Prometheus 指标，Prometheus 负责采集，Grafana 负责展示关键面板。”

### 第三步：讲指标选择

“我没有一开始就铺很大的监控体系，而是优先挑了四个最有性价比的指标：订单创建 QPS、库存扣减延迟、Kafka 消费 lag、连接池活跃连接数。”

### 第四步：讲定位能力

“这样在压测时我就能判断，到底是入口流量上来了、库存扣减慢了、异步链路积压了，还是数据库连接池先顶满了。”

这段回答会比“我跑了 k6，然后看日志”更完整。

---

## 6. 这些指标为什么有面试价值

### 6.1 为什么订单创建 QPS 值得看

因为它是最直接的业务吞吐指标之一。

如果压测流量上升，但订单创建 QPS 没同步升高，说明系统可能已经在某处限速或阻塞。

### 6.2 为什么库存扣减延迟值得看

因为库存扣减通常在交易链路的关键路径上。

它一旦变慢，会直接拖高下单耗时，而且很容易和数据库更新压力相关。

### 6.3 为什么 Kafka lag 值得看

因为异步系统最怕“表面接口还好，但后面的消费已经堆住了”。

如果 lag 越来越高，说明系统虽然还在接请求，但后续处理吞吐已经跟不上了。

### 6.4 为什么连接池活跃连接数值得看

因为很多 Java 服务的性能问题，最后都会体现在数据库连接资源紧张上。

这个指标能帮助你把“慢”进一步分解成：

1. 是数据库/连接池顶住了
2. 还是业务层自己变慢了

---

## 7. 这次验证到了什么程度

这块面试里要实话实说，不要夸大。

当前已经验证到：

1. Grafana 服务本体能正常启动
2. Prometheus 服务本体能正常启动
3. Grafana 数据源 provisioning 正常加载
4. `Shiori Overview` dashboard 已被自动加载

但当前本地环境还有一个边界：

1. 多个业务服务容器因为 MySQL 旧数据卷密码不一致而反复重启
2. 所以 Prometheus 对这些服务的抓取 target 会出现 `down/EOF`
3. 这会导致 dashboard 当前不能稳定显示完整业务数据

也就是说：

可以说“可观测性基础设施和 dashboard provisioning 已跑通”，

但不能说“本地已经完整看到了所有业务指标曲线”。

这个边界在面试里如实说明反而更专业。

---

## 8. 高频面试追问

### 8.1 为什么不是一上来接 OpenTelemetry

因为这次目标是最高性价比地补齐压测监控闭环。

Prometheus + Grafana + Actuator 的接入成本更低，出效果更快，已经足够支持“先找瓶颈落点”这个目标。

### 8.2 为什么选这几个指标，而不是铺很多业务埋点

因为面试项目最重要的是“能回答性能定位问题”，不是监控面板越多越好。

这四类指标已经能覆盖：

1. 入口吞吐
2. 核心同步链路耗时
3. 异步积压
4. 数据库资源使用

### 8.3 Kafka lag 为什么不用 broker 侧指标

broker 侧 lag 更完整，但这次为了快速落地，我先在消费端按消息时间戳和当前处理时间做了应用侧 lag 观测。

它虽然不是最严格的 consumer group lag 指标，但足够用于面试演示和本地排障。

### 8.4 为什么库存扣减看 p95，不看平均值

因为平均值会掩盖尾延迟。

高并发场景下，真正影响用户体验和系统稳定性的往往是慢请求尾部，而不是均值。

### 8.5 如果后面继续完善，你会加什么

可以继续补三类内容：

1. Trace 链路追踪
2. 告警规则
3. 更细的 Kafka consumer group lag 和数据库慢查询观测

但这属于下一阶段，不是这次最小闭环的必要条件。

---

## 9. 一句话总结

这次改造本质上是：

“把压测、运行指标和 dashboard 串成一个最小可用闭环，让我不只是知道系统变慢了，还能初步判断慢在入口吞吐、库存扣减、异步消费还是数据库连接池。”
