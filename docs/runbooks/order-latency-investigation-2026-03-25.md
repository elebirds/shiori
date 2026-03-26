# 订单链路时延调查（2026-03-25）

## 1. 背景

在修复 `k6` 多 `VU` 下的库存/支付死锁并临时关闭网关限流后，订单链路已经不再报错，但在更高并发下出现明显时延爬升：

- 裸压测下，`36 VU` 仍可通过阈值。
- 裸压测下，`40 VU` 开始出现多个阶段 `p95 > 400ms`。
- 裸压测下，`48 VU` 继续保持 `0` 失败率，但时延继续上升。

因此，当前问题不是“错误恢复”，而是“高并发下的时延瓶颈”。

## 2. 调查目标

本次调查要回答三个问题：

1. 时延上升是否仍由数据库死锁或锁等待回潮导致。
2. 压力主要集中在 `gateway`、`order-service`、`product-service`、`payment-service` 还是 MySQL。
3. 下一轮优化应优先做连接池扩容、事务缩短，还是数据库层继续调优。

## 3. 裸压测基线

以下数据来自解除限流后的裸压测日志：

| 档位 | 日志目录 | `http p95` | 吞吐（`http_reqs/s`） | 结果 |
| --- | --- | ---: | ---: | --- |
| `20 VU` | `ci-logs/perf/ramp/order_v20_nolimit` | `122.66ms` | `219.66/s` | 通过 |
| `36 VU` | `ci-logs/perf/ramp/order_v36_nolimit` | `286.96ms` | `189.46/s` | 通过 |
| `40 VU` | `ci-logs/perf/ramp/order_v40_nolimit` | `416.40ms` | `170.25/s` | 失败，`create/pay/confirm` 超阈值 |
| `48 VU` | `ci-logs/perf/ramp/order_v48_nolimit` | `418.48ms` | `186.90/s` | 失败，`pay/confirm/detail` 超阈值 |

当前裸压测结论：

- 最高稳定并发档：`36 VU`
- 最高稳定吞吐档：`20 VU`
- 时延临界区间：`36 -> 40 VU`

## 4. 采样方案与扰动说明

### 4.1 第一轮：重观测（`36vu`）

首轮对 `gateway/order/product/payment` 四个服务同时抓取：

- `JFR(profile)`
- `Prometheus` 指标快照
- `docker stats`
- `SHOW FULL PROCESSLIST`
- `performance_schema`

产物目录：

- `ci-logs/perf/investigations/2026-03-25-order-latency/36vu`

这一轮的问题是观测扰动过大：

- `36 VU` 本应是裸压测可通过档位。
- 但在重观测下，`http p95` 被放大到 `956.79ms`。
- `create/pay/deliver/confirm/detail` 五个阶段全部超阈值。

因此，`36vu` 目录只能用于“定位方向”，不能直接拿来定义稳定边界。

### 4.2 第二轮：轻量观测（`36vu-lite` / `40vu`）

为降低扰动，后续改为：

- 只抓 `order-service` 与 `payment-service` 的 `JFR`
- 只采集 `order/payment` 两个服务的 `Prometheus` 指标
- `docker stats` 与 `SHOW FULL PROCESSLIST` 采样频率降为 `2s`

产物目录：

- `ci-logs/perf/investigations/2026-03-25-order-latency/36vu-lite`
- `ci-logs/perf/investigations/2026-03-25-order-latency/40vu`

这两轮结果仍有观测扰动，但已经明显低于首轮重观测，适合作为“同一观测条件下”的对照数据。

## 5. 关键证据

### 5.1 `36 VU` 重观测：订单服务连接池先出现排队

`36vu/metrics-samples.prom` 的峰值显示：

| 服务 | `max_process_cpu` | `max_hikari_active` | `max_hikari_pending` | 说明 |
| --- | ---: | ---: | ---: | --- |
| `gateway` | `0.218` | `0` | `0` | 未见 DB 池压力 |
| `order` | `0.256` | `10` | `27` | 连接池撞满并排队 |
| `product` | `0.186` | `5` | `0` | 未排队 |
| `payment` | `0.180` | `6` | `0` | 未排队 |

订单服务排队出现时间：

- `max_active=10 @ 2026-03-25T18:25:54+0800`
- `max_pending=27 @ 2026-03-25T18:25:55+0800`
- `pending_samples=14`

这说明即使在被重观测放大的场景中，首先被压住的也不是 `product-service` 或 `payment-service`，而是 `order-service` 自身的连接池。

### 5.2 `36 VU` 轻量观测：连接池仍已贴边

`36vu-lite/k6-order-oneclick.log`：

- `http p95 = 361.48ms`
- `confirm p95 = 401.89ms`
- `pay p95 = 410.88ms`
- `create/detail/deliver` 仍低于 `400ms`

`36vu-lite/metrics-samples.prom`：

| 服务 | `max_cpu` | `max_active` | `max_pending` | `pending_samples` |
| --- | ---: | ---: | ---: | ---: |
| `order` | `0.138` | `10` | `26` | `9` |
| `payment` | `0.123` | `6` | `0` | `0` |

订单服务排队峰值：

- `max_active=10 @ 2026-03-25T18:36:20+0800`
- `max_pending=26 @ 2026-03-25T18:36:20+0800`

结论：

- 即使只做轻量观测，`36 VU` 时订单服务连接池也已经贴近极限。
- 这与裸压测下 `36 VU` 仍能通过、但已经接近阈值的现象一致。

### 5.3 `40 VU` 轻量观测：订单服务排队进一步放大

`40vu/k6-order-oneclick.log`：

- `http p95 = 546.71ms`
- `create p95 = 565.65ms`
- `pay p95 = 620.43ms`
- `confirm p95 = 554.69ms`
- `detail p95 = 473.62ms`
- `deliver p95 = 395.63ms`

`40vu/metrics-samples.prom`：

| 服务 | `max_process_cpu` | `max_hikari_active` | `max_hikari_pending` | `pending_samples` |
| --- | ---: | ---: | ---: | ---: |
| `order` | `0.208` | `10` | `31` | `9` |
| `payment` | `0.147` | `6` | `0` | `0` |

订单服务排队峰值：

- `max_active=10 @ 2026-03-25T18:33:29+0800`
- `max_pending=31 @ 2026-03-25T18:33:33+0800`

与 `36vu-lite` 对比：

- `order-service` 的 `active` 一直顶在 `10`
- `pending` 从 `26` 升到 `31`
- `payment-service` 仍未出现池内排队

这说明当并发从 `36` 提升到 `40` 时，订单服务连接池等待被进一步放大，已经跨过当前时延阈值。

### 5.4 MySQL 证据：没有死锁回潮，但有事务热点

#### 5.4.1 未见死锁和锁等待队列

`36vu-lite/mysql-innodb-status.txt` 与 `40vu/mysql-innodb-status.txt` 都没有出现：

- `LATEST DETECTED DEADLOCK`
- `LOCK WAIT`
- `queries in queue`

`40vu` 的 InnoDB 快照仅看到少量 `waiting for handler commit`，属于提交阶段，而不是互相等待的锁死。

因此，本轮时延问题不应归因于“之前那类死锁又回来了”。

#### 5.4.2 热点 SQL 集中在支付钱包行锁和库存行锁

`36vu-lite/mysql-statements-summary.tsv` 的总耗时前几项：

1. `SELECT ... FROM p_wallet_account WHERE user_id = ? LIMIT ? FOR UPDATE`
   - `COUNT = 2299`
   - `total = 66.07s`
   - `avg = 28.74ms`
2. `COMMIT`
   - `COUNT = 4332`
   - `total = 18.06s`
   - `avg = 4.17ms`
3. `SELECT ... FROM p_sku WHERE id = ? ... FOR UPDATE`
   - `COUNT = 759`
   - `total = 6.72s`
   - `avg = 8.85ms`

`40vu/mysql-statements-summary.tsv` 的总耗时前几项：

1. `SELECT ... FROM p_wallet_account WHERE user_id = ? LIMIT ? FOR UPDATE`
   - `COUNT = 1699`
   - `total = 64.59s`
   - `avg = 38.02ms`
2. `COMMIT`
   - 多组累计 `total > 30s`
3. `SELECT ... FROM p_sku WHERE id = ? ... FOR UPDATE`
   - `COUNT = 559`
   - `total = 7.57s`
   - `avg = 13.55ms`

这说明热点集中在：

- `payment-service` 的钱包账户行锁
- `product-service` 的库存行锁
- 提交阶段的事务完成时间

但它们表现为“热点竞争与事务耗时累积”，不是“死锁”。

### 5.5 JFR 证据：等待型特征明显强于 CPU 型特征

`order-service` JFR 摘要：

| 档位 | `ThreadPark` | `SocketRead` | `ExecutionSample` |
| --- | ---: | ---: | ---: |
| `36vu-lite` | `11911` | `2818` | `723` |
| `40vu` | `9664` | `2612` | `610` |

`payment-service` JFR 摘要：

| 档位 | `ThreadPark` | `SocketRead` | `ExecutionSample` |
| --- | ---: | ---: | ---: |
| `36vu-lite` | `1724` | `1743` | `280` |
| `40vu` | `1311` | `1482` | `324` |

含义：

- `order-service` 的等待类事件明显多于 CPU 采样事件。
- 现象更像“线程在等连接、等远程调用、等事务完成”，而不是“纯 CPU 算不过来”。

## 6. 代码侧对应关系

### 6.1 订单服务事务里夹着同步远程调用

`OrderCommandService.createOrder(...)` 和 `OrderCommandService.payOrderByBalance(...)` 都是 `@Transactional` 方法。

它们在事务内执行了同步 HTTP 调用：

- 创建订单时调用 `productServiceClient.deductStock(...)`
- 余额支付时调用 `paymentServiceClient.reserveOrderPayment(...)`

而且在远程调用之前，订单服务已经执行了本地数据库访问：

- 创建订单先查幂等键
- 支付先查订单状态

这意味着订单服务一旦在事务内拿到数据库连接，该连接就很可能跨越远程调用存活到事务结束。并发一高，`order-service` 的 Hikari 连接更容易被长期占住。

### 6.2 下游服务自身也使用行锁事务

`product-service` 扣库存路径：

- 先 `SELECT ... FROM p_sku ... FOR UPDATE`
- 再做 `UPDATE p_sku SET stock = stock - ?`

`payment-service` 托管支付路径：

- `reserveOrderPayment(...)` 会先锁买家钱包行
- `settleOrderPayment(...)` 会继续锁买家/卖家钱包行

因此，订单服务调用下游时，不只是发一个“快请求”，而是进入下游的事务锁路径。只要下游热点行上有竞争，订单服务本地连接就会被更长时间占用。

### 6.3 订单服务未见显式 Hikari 扩容配置

检查 `deploy/nacos/templates/shiori-order-service-base.yml.tmpl` 与 `shiori-order-service` 默认配置后，未看到显式的 Hikari `maximum-pool-size` 覆盖。

结合运行时指标：

- `max_hikari_active = 10`

可推断当前订单服务大概率仍使用 Spring Boot/Hikari 默认池大小 `10`。

## 7. 综合结论

当前更可信的结论如下：

1. 这轮时延问题不是死锁回潮。
2. 当前第一瓶颈在 `order-service` 数据库连接池，而不是 `payment-service` 或 `product-service` 的连接池。
3. `order-service` 在本地事务内执行同步远程调用，放大了数据库连接持有时间。
4. 下游热点主要落在：
   - `p_wallet_account ... FOR UPDATE`
   - `p_sku ... FOR UPDATE`
   - `COMMIT`
5. 系统在 `36 -> 40 VU` 区间跨过了当前时延阈值，表现为订单服务连接池排队显著加重。

## 8. 优先优化建议

按优先级建议下一步这样做：

1. 先在 `order-service` 采集更细的事务时长与连接持有时长。
   - 目标是把 `DB acquire`、`transaction wall time`、`remote call time` 拆开。
2. 优先缩短订单服务事务范围。
   - 尤其是 `createOrder(...)` 与 `payOrderByBalance(...)`。
   - 核心原则是避免“本地事务已持有连接时，再去同步等待下游事务接口”。
3. 将 `order-service` 的 Hikari 池大小作为短期缓解项做对照试验。
   - 例如先试 `20` 或 `30`，观察 `36/40/48 VU` 的 `p95` 与 `pending` 变化。
   - 但这只能缓解，不能替代事务缩短。
4. 如果还要继续取证，再做一次更聚焦的分析：
   - 仅对 `order-service` 跑 `async-profiler wall-clock`
   - 或将当前 `JFR` 导入图形化工具生成调用热点/等待火焰图

## 9. 产物索引

### 9.1 裸压测

- `ci-logs/perf/ramp/order_v20_nolimit/k6-order-oneclick.log`
- `ci-logs/perf/ramp/order_v36_nolimit/k6-order-oneclick.log`
- `ci-logs/perf/ramp/order_v40_nolimit/k6-order-oneclick.log`
- `ci-logs/perf/ramp/order_v48_nolimit/k6-order-oneclick.log`

### 9.2 调查采样

- `ci-logs/perf/investigations/2026-03-25-order-latency/36vu`
- `ci-logs/perf/investigations/2026-03-25-order-latency/36vu-lite`
- `ci-logs/perf/investigations/2026-03-25-order-latency/40vu`

## 10. 最终判断

如果只保留一句结论：

> 订单链路在 `40 VU` 失稳的直接表现，是 `order-service` 默认规模的数据库连接池被事务持有时间拉长后出现排队；根因方向是“订单服务事务范围过大且事务内同步调用下游锁事务接口”，而不是“死锁复发”。
