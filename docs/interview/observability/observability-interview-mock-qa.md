# 可观测性面试问答模拟版

## 1. 你为什么要做这次可观测性改造

可以这样回答：

“因为我原来只有 k6 压测报告和应用日志，这能说明系统有没有慢、有没有报错，但不能很好回答‘瓶颈到底在哪里’。所以我补了一套最小可用的监控闭环，让压测结果能和运行时指标对应起来。”

---

## 2. 你这次具体接了什么

标准回答：

“我用的是 Spring Boot Actuator + Prometheus + Grafana。应用暴露 `/actuator/prometheus`，Prometheus 定时抓取，Grafana 用预置 dashboard 展示关键指标。”

---

## 3. 你为什么不用单纯看日志

标准回答：

“日志更适合查具体异常和单次问题，但它不适合持续看吞吐、延迟分布、Kafka 积压和连接池水位。定位性能瓶颈还是要靠时序指标。”

---

## 4. 你这次最核心的指标有哪些

可以这样回答：

“我重点做了四类指标：订单创建 QPS、库存扣减延迟、Kafka 消费 lag、连接池活跃连接数。再加上 HTTP QPS、HTTP p95 和 5xx 比例做总览，已经够支撑一轮压测定位。”

---

## 5. 订单创建 QPS 你是怎么做的

标准回答：

“我没有额外新造一个订单创建 counter，而是复用了订单状态迁移指标。订单创建成功后会从 `NEW` 进入 `UNPAID`，所以我用 `shiori_order_transition_total{from=\"NEW\",to=\"UNPAID\"}` 做 rate，作为订单创建 QPS 的近似观测。”

如果被追问“为什么说是近似观测”，可以补一句：

“因为它依赖业务状态迁移语义，不是独立埋点，但在当前系统里这个迁移和成功创建订单是强相关的，所以足够用于压测观察。”

---

## 6. 库存扣减延迟是怎么做的

标准回答：

“我在 `product-service` 的 `ProductStockService.deduct(...)` 上埋了 Micrometer `Timer`，指标名是 `shiori_product_stock_deduct_latency_seconds`。这样可以直接看库存扣减这个关键路径的耗时分布，Grafana 里重点看 p95。”

---

## 7. 为什么库存扣减要看 p95，不看平均值

标准回答：

“因为平均值会掩盖尾延迟。高并发场景下真正说明问题的是慢请求尾部，所以我优先看 p95，这样更容易看出数据库争用、锁等待或者下游抖动。”

---

## 8. Kafka 消费 lag 你是怎么做的

标准回答：

“我在 `order-service` 的 Kafka 消费器里，拿消息的 `record.timestamp()` 和当前处理时间做差，记录成 `shiori_order_kafka_consumer_lag_seconds`。这个指标可以直观看出异步消费是不是已经开始积压。”

如果被追问“这是不是最严格的 consumer group lag”，可以说：

“不是 broker 侧最标准的 lag 指标，但它接入成本低，足够做本地排障和面试演示。后面如果继续完善，可以再接 broker/exporter 侧更标准的 lag 观测。”

---

## 9. 为什么连接池活跃连接数也很重要

标准回答：

“因为很多 Java 服务的性能瓶颈最后都会落到数据库连接资源上。看 `hikaricp_connections_active`，可以帮助我区分到底是数据库连接池快打满了，还是业务层自己先变慢了。”

---

## 10. 你在 Grafana 上放了哪些面板

可以这样回答：

“我做的是一个总览 dashboard，核心面板有 `Order Create QPS`、`Stock Deduct p95 Latency`、`Kafka Consumer Lag`、`Hikari Active Connections`，另外还补了 `HTTP QPS`、`HTTP p95 Latency` 和 `HTTP 5xx Ratio` 用来做整体判断。”

---

## 11. 如果压测时延迟上升，你怎么用这套面板判断问题

建议这样回答：

“我会先看 HTTP p95 和 5xx 有没有异常，再看订单创建 QPS 有没有上升但吞吐没跟住；如果库存扣减 p95 明显变差，说明同步交易链路在变慢；如果 Kafka lag 持续升高，说明异步消费跟不上；如果连接池活跃连接数接近上限，那我会优先怀疑数据库或连接池资源成为瓶颈。”

---

## 12. 为什么这次不一上来做全链路 trace

标准回答：

“这次追求的是最高性价比。Prometheus + Grafana + Actuator 接入快、改造小，已经能支撑压测定位。Trace 很有价值，但它属于下一阶段增强，不是这次最小闭环的必要条件。”

---

## 13. 这次你验证到什么程度

这题一定要实话实说。

标准回答：

“我已经验证了 Grafana 本体、Prometheus 本体、数据源 provisioning 和 dashboard provisioning 都是通的，dashboard 也能自动加载。但当前本地环境里，多个业务服务因为 MySQL 旧数据卷密码不一致反复重启，所以 Prometheus targets 还不稳定。也就是说，基础设施链路是通的，但完整业务数据曲线还受本地环境问题影响。”

---

## 14. 面试官如果问，这算不算真正做完了可观测性，你怎么答

可以这样回答：

“如果按生产级标准看，这当然还不算完整可观测性，因为还缺告警、trace、日志聚合和更细粒度指标。但如果按项目阶段目标看，这次已经把压测后的最小监控闭环补上了，足够支持性能定位和演示。”

---

## 15. 如果后面继续完善，你下一步会做什么

可以这样回答：

“我会优先补三块：第一，给关键链路加 trace；第二，加告警规则，比如连接池、错误率和 Kafka lag 阈值告警；第三，把 Kafka lag 和数据库慢查询做得更标准、更细。”

---

## 16. 一段完整的面试回答模板

你可以直接背这个版本：

“我这次补的是一套最小可用的可观测性闭环。之前我只有 k6 压测报告和应用日志，但这不足以说明瓶颈具体在哪一层。所以我给几个核心 Spring Boot 服务统一打开了 Actuator Prometheus endpoint，让 Prometheus 采集，再用 Grafana 做了一个总览 dashboard。

指标上我没有一开始做很全，而是优先挑了最有性价比的四类：订单创建 QPS、库存扣减延迟、Kafka 消费 lag、连接池活跃连接数。订单创建 QPS 复用了订单状态迁移指标，库存扣减延迟是在 `ProductStockService` 上埋的 Micrometer Timer，Kafka lag 是在 `order-service` 消费器里按消息时间戳和处理时间差记录的，连接池则直接使用 Hikari 默认指标。

这样压测时我就能判断，到底是入口吞吐上不去、同步交易链路变慢、异步消费积压，还是数据库连接池先顶满了。当前本地我已经验证了 Grafana、Prometheus、数据源和 dashboard provisioning 都可用，不过业务服务还受 MySQL 旧数据卷密码不一致影响，Prometheus targets 还不稳定，所以我不会夸大说业务曲线已经完整跑通。”

---

## 17. 面试时最重要的三句话

如果时间不够，只记这三句：

1. “我做的不只是压测，而是压测加监控的定位闭环。”
2. “我重点看订单创建 QPS、库存扣减延迟、Kafka lag 和连接池活跃连接数。”
3. “Grafana 和 Prometheus 基础设施已经跑通，但当前本地业务数据受旧 MySQL 数据卷环境问题影响，还不能夸大为完整业务监控已全部验证。”
