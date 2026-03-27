# 分布式链路追踪面试问答

## 一、方案动机

### Q1：为什么这次要补分布式链路追踪？

答：

因为原来跨服务问题主要靠两类证据：

1. 各服务自己的日志
2. Prometheus / Grafana 指标

它们能回答“有没有慢、有没有错”，但不容易回答“一个具体请求到底经过了哪些服务、哪一跳最慢”。所以这次要补的是按请求维度排障的能力。

### Q2：为什么只看日志不够？

答：

日志适合看具体异常，但跨服务排障时要靠时间窗口去拼接多个服务的日志，成本很高，也很依赖经验。

分布式追踪的价值是：

1. 用一个 TraceId 把整条链路串起来。
2. 直接看到每一跳 span。
3. 直接比较每一跳耗时。

### Q3：为什么这次选 Micrometer Tracing + OTLP + Jaeger？

答：

因为这是当前项目里性价比比较合适的方案：

1. 项目本身就是 Spring Boot，Micrometer Tracing 接入成本低。
2. OTLP 是更通用的标准协议，后面切后端更容易。
3. Jaeger all-in-one 本地就能跑 UI，演示和排障都直观。

---

## 二、方案设计

### Q4：你这次具体改了什么？

答：

我做了四类事情：

1. 给 6 个 Java 服务补 `spring-boot-starter-opentelemetry`
2. 在 `application.yml` 和 Nacos 模板里统一 tracing、OTLP、日志关联配置
3. 显式关闭 OTLP metrics / logging export，继续保留 Prometheus 指标体系
4. 把跨服务 HTTP 调用统一成框架管理的 `RestClient` / `WebClient` Builder
5. 在 Docker 里补了 Jaeger，本地可以直接看 Trace

### Q5：为什么强调“框架管理的 Builder”？

答：

因为这次真正的坑就在这里。原来代码里手写了 `RestClient.builder()` 和 `WebClient.builder()`，这种写法会绕过 Spring Boot 自动注入的 tracing 拦截器。

结果就是：

1. 服务能调通。
2. tracing 开关也开了。
3. 但请求头里没有 `traceparent`。

所以我后面改成：

1. `RestClientBuilderConfigurer`
2. `WebClientCustomizer`

本质上是在保留负载均衡能力的同时，把 tracing 自动透传能力接回来。

### Q6：为什么统一用 W3C `traceparent`？

答：

因为这次要的是标准化而不是临时可用：

1. W3C 更通用。
2. Java 和后续可能接入的 Go 都更容易统一。
3. 避免现在混用 `b3` 和 `w3c`，后面再做一次迁移。

---

## 三、实现细节

### Q7：日志里为什么还要打印 `traceId/spanId`？

答：

因为 Trace 和日志是互补关系，不是替代关系。

我把日志 pattern 统一成：

1. 服务名
2. `traceId`
3. `spanId`

这样我可以：

1. 先在 Jaeger 里找到一条慢链路，再回日志看具体报错
2. 或者先在日志里拿到一个 `traceId`，再去 Jaeger 看完整调用树

### Q8：你怎么控制 tracing 的开关和成本？

答：

我做了两层控制：

1. 源码 `application.yml` 默认 `TRACING_ENABLED=false`
2. Nacos 模板默认 `TRACING_ENABLED=true`

这样纯本地单服务调试不会一直刷 exporter 错误，而联调和演示环境默认能直接看到 Trace。

同时采样率也单独做成了变量：

1. `TRACING_SAMPLING_PROBABILITY`

当前默认是 `1.0`，是为了演示和验证更直观，后面如果压测或生产有成本顾虑，可以单独调低。

### Q9：Jaeger 怎么接进去的？

答：

Docker Compose 里新增了 Jaeger all-in-one，并打开了 OTLP：

1. UI 端口 `16686`
2. OTLP gRPC `4317`
3. OTLP HTTP `4318`

Java 服务统一把 OTLP endpoint 指到 Jaeger。

Docker 联调组默认用：

1. `http://jaeger:4318/v1/traces`

本机 IDE 调试组默认用：

1. `http://127.0.0.1:4318/v1/traces`

---

## 四、验证与排障

### Q10：你怎么证明 tracing 真的生效了？

答：

我做了两层验证。

第一层是自动化测试：

1. `OrderRestClientTracingConfigurationTest`
2. `UserRestClientTracingConfigurationTest`
3. `GatewayHttpClientTracingConfigurationTest`

这些测试会在存在活动 Observation 时发请求，并断言请求头里真的有 `traceparent`。

第二层是运行时验证：

1. 起 Jaeger
2. 发一次真实请求
3. 在 Jaeger UI 里看 span 树
4. 再拿 `traceId` 去日志里反查

### Q11：这次调试过程中最关键的发现是什么？

答：

最关键的发现是：

> tracing 不生效不一定是 exporter 配错了，也可能是客户端 builder 的来源错了。

我一开始已经把 starter 和 tracing 配置都加了，但测试还是没有 `traceparent`。最后发现是自定义 Builder 绕过了自动配置，这个点修掉之后透传才真正打通。

### Q12：如果 Jaeger 里搜不到 Trace，你会先查什么？

答：

我会按这个顺序查：

1. `TRACING_ENABLED` 是否打开
2. `OTEL_EXPORTER_OTLP_ENDPOINT` 是否可达
3. Jaeger OTLP HTTP 端口是否真的监听
4. 这条调用链是否走了统一的框架 Builder
5. 日志里是否已经有 `traceId`

如果日志里已经有 `traceId`，但 Jaeger 没看到，通常更像 exporter 或 Jaeger 可达性问题；如果连请求头里都没有 `traceparent`，就优先查客户端 builder 或调用路径。

---

## 五、边界与取舍

### Q13：这次 tracing 覆盖到了什么范围？

答：

当前我只明确覆盖了 Java 微服务主 HTTP 链路：

1. gateway
2. user
3. product
4. order
5. payment
6. social

### Q14：为什么这次没有把 Go 的 `notify-service` 一起做掉？

答：

因为这次目标是先把主链路最有价值的部分打通，也就是 Java 服务之间的同步 HTTP 调用。

如果把 Go 服务和跨语言问题一起做，改造和验证范围会明显扩大，成本就不再是“一天内能稳定落地”的量级了。

所以这里是有意做了分阶段：

1. 先把 Java 主链路打通
2. 再补 Go 和更完整的跨语言链路

### Q15：Kafka 异步链路这次算不算也做完了？

答：

这次不应该夸大说已经做完。

更准确的说法是：

1. 当前明确验证的是同步 HTTP 主链路 tracing
2. Kafka 异步链路还没有作为这次交付范围去做完整传播验证

面试里我会主动把这个边界说清楚。

---

## 六、完整回答模板

### 30 秒版本

> 我这次给项目补了一套基于 Micrometer Tracing、OpenTelemetry OTLP 和 Jaeger 的分布式链路追踪。核心价值是把原来跨服务靠猜的问题，变成可以按 TraceId 直接看调用树和每一跳耗时。实现上不只是加 starter，我还修了项目里自定义的 `RestClient` 和 `WebClient` Builder，避免它们绕过 Spring Boot 自动 tracing 拦截器；同时把日志统一带上 `traceId/spanId`，这样 Jaeger 和日志可以双向关联。

### 90 秒版本

> 我这次做的是一套中等成本、但工程价值很高的分布式链路追踪改造。之前项目已经有日志和指标，但一旦微服务链路出问题，只能分服务看日志，再靠时间窗口拼调用链，定位跨服务问题效率很低。
> 所以我给 6 个 Java 服务统一接了 Micrometer Tracing 和 OpenTelemetry OTLP，用 Jaeger 做本地可视化后端，并且统一了 W3C `traceparent`。这里真正的坑不是加依赖，而是项目里原来手写了 `RestClient.builder()` 和 `WebClient.builder()`，这会绕过 Spring Boot 自动挂上的 tracing 拦截器，导致请求头没有 `traceparent`。我最后把它们改成基于 `RestClientBuilderConfigurer` 和 `WebClientCustomizer` 派生的 `@LoadBalanced` Builder，才把透传真正打通。
> 同时我把日志 pattern 统一成带 `traceId/spanId`，这样可以在 Jaeger 里看到一条请求经过哪些服务、每个服务耗时多少，再拿同一个 TraceId 回日志查异常。当前范围我会实话实说，只覆盖 Java HTTP 主链路，不夸大到 Go 服务和 Kafka 异步链路都已经做完。

### 面试时最该强调的三句话

1. 我解决的不是“有没有监控”，而是“跨服务请求能不能按 TraceId 直接定位”。
2. 这次真正的技术点是修掉了自定义 HTTP Builder 绕过 tracing 自动配置的问题。
3. 我现在可以在 Jaeger 里看到一个请求经过哪些服务、每一跳耗时多少，再回到日志里按同一个 TraceId 查具体异常。
