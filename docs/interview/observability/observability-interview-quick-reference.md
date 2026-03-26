# 可观测性面试速记版

## 1. 这次改造一句话怎么讲

可以直接背：

“我把 k6 压测和运行时监控串起来了。应用通过 Spring Boot Actuator 暴露 Prometheus 指标，Prometheus 采集，Grafana 展示关键面板，这样我不只是知道系统慢了，还能判断瓶颈大概落在哪一层。”

---

## 2. 这次最重要的四个指标

### 2.1 订单创建 QPS

- 指标：`shiori_order_transition_total{from="NEW",to="UNPAID"}`
- 用途：看订单创建吞吐有没有顶住

### 2.2 库存扣减延迟

- 指标：`shiori_product_stock_deduct_latency_seconds`
- 用途：看交易关键路径是不是变慢了

### 2.3 Kafka 消费 lag

- 指标：`shiori_order_kafka_consumer_lag_seconds`
- 用途：看异步链路有没有积压

### 2.4 连接池活跃连接数

- 指标：`hikaricp_connections_active`
- 用途：看数据库连接资源是不是接近上限

---

## 3. 这次 dashboard 里主要看什么

Grafana 面板里我重点看：

1. `Order Create QPS`
2. `Stock Deduct p95 Latency`
3. `Kafka Consumer Lag`
4. `Hikari Active Connections`
5. `HTTP QPS`
6. `HTTP p95 Latency`
7. `HTTP 5xx Ratio`

这套组合够回答“哪里慢、哪里开始出问题”。

---

## 4. 面试时为什么这套东西有价值

因为它能把“我跑过压测”升级成“我能定位瓶颈”。

更准确地说，这套指标能覆盖四个问题：

1. 吞吐有没有上去
2. 同步核心链路有没有变慢
3. 异步消费有没有积压
4. 数据库资源有没有顶满

---

## 5. 为什么不是一上来做很全

不要说“我想先做全链路监控”。

更好的回答是：

“这次追求的是最高性价比。我先把最小可用闭环做出来，先能支撑压测定位和面试展示，再考虑 trace、告警和更细粒度指标。”

---

## 6. 高频追问速答

### 6.1 为什么不用日志定位瓶颈

日志更适合查单次错误，不适合持续观察吞吐、延迟、积压和资源水位。

### 6.2 为什么库存扣减用 p95

因为平均值会掩盖尾延迟，高并发场景更应该看 p95/p99。

### 6.3 为什么 Kafka lag 很重要

因为接口层看起来正常，不代表后面的异步消费没有堆积。

### 6.4 为什么连接池指标也要看

因为很多性能问题最后都会落到数据库连接资源上。

---

## 7. 本地验证边界怎么说

这句要记住：

“Grafana、Prometheus、数据源和 dashboard provisioning 已经验证可用；但当前本地业务服务受 MySQL 旧数据卷密码不一致影响，Prometheus targets 不稳定，所以还不能说业务指标曲线已经完整跑通。”

---

## 8. 落地点怎么记

- `OrderMetrics`：订单相关指标和 Kafka lag gauge
- `ProductMetrics`：库存扣减延迟 timer
- `ProductStockService`：库存扣减埋点入口
- `WalletBalanceOutboxCdcConsumer`：Kafka lag 记录
- `shiori-overview.json`：Grafana dashboard

---

## 9. 一句话总结

“这次不是单纯加了监控工具，而是补了一个压测后的定位闭环，让性能问题可以从业务吞吐、核心耗时、异步积压和数据库资源四个维度一起观察。”
