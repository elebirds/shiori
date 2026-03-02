# 交易链路联调排障记录（2026-03-03）

## 1. 目标与验收
本次目标是把以下链路跑通并可重复验证：

1. 基础设施一键启动（MySQL/Redis/RabbitMQ/Nacos/MinIO）。
2. `nacos-config-init` 自动导入 `deploy/nacos/*.yml` 成功。
3. Java 四服务 + Go notify 可本机启动并互通。
4. `scripts/smoke/e2e_trade_notify.sh` 成功：
   - 注册/登录
   - 商品创建发布
   - 下单幂等
   - 支付
   - buyer/seller 双端收到 `OrderPaid` WebSocket 通知

验收标准是脚本退出码 `0`，并输出：
`[smoke] E2E 烟测成功`

---

## 2. 排障时间线（按问题链路）

## 阶段 A：`docker compose up -d` 失败（镜像与端口）

### 现象
1. 拉取镜像失败（tag 不存在）。
2. `shiori-redis` 启动失败：`Bind for 0.0.0.0:6379 failed: port is already allocated`。

### 验证命令
```bash
cd /Users/hhm/code/shiori/deploy
docker compose up -d
docker compose ps
```

### 根因
1. `docker-compose.yml` 中部分镜像 tag 不可用。
2. 主机本地已有 Redis 占用 6379。

### 修复
1. 修正镜像 tag：
   - `nacos/nacos-server:v3.1.1`
   - `minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1`
   - `minio/mc:RELEASE.2025-08-13T08-35-41Z-cpuv1`
2. Redis 端口改为可配置并默认避冲突：
   - `${REDIS_HOST_PORT:-6380}:6379`

---

## 阶段 B：Nacos 配置自动导入失败（`nacos-config-init` 退出 1）

### 现象
`shiori-nacos-config-init` 持续失败，日志出现 500/410/400。

### 验证命令
```bash
docker ps -a --filter name=shiori-nacos-config-init
docker logs shiori-nacos-config-init
docker logs shiori-nacos --tail 200
```

### 根因
1. 脚本调用了旧 API：`/nacos/v1/auth/users/login`、`/nacos/v1/auth/users/admin`。
2. Nacos 3.1.1 已要求使用 v3 API，并提示旧接口兼容即将废弃。
3. 登录 token 解析阶段把日志写到 stdout，污染了 `ACCESS_TOKEN` 变量，导致配置发布 400。

### 修复
1. 脚本切换到 Nacos 3.x API：
   - 初始化管理员：`POST /nacos/v3/auth/user/admin`
   - 登录：`POST /nacos/v3/auth/user/login`
   - 发布配置：`POST /nacos/v3/admin/cs/config`
2. 增加登录重试等待。
3. 脚本日志改写 stderr，避免 token 污染。
4. 重建 init 容器验证：`Exited (0)`。

### 验证命令
```bash
docker compose -f /Users/hhm/code/shiori/deploy/docker-compose.yml up -d --force-recreate nacos-config-init
docker ps -a --filter name=shiori-nacos-config-init --format '{{.Status}}'
docker logs shiori-nacos-config-init
```

---

## 阶段 C：Java 服务连接 Nacos 失败（启动即退出）

### 现象
`user/product/order/gateway` 启动时 Nacos 客户端报连接失败并退出。

### 根因
Nacos 3.x 客户端注册与订阅依赖 gRPC 端口，compose 仅暴露了 `8848`，未暴露 `9848/9849`。

### 修复
在 `nacos` 服务端口映射补齐：
1. `9848:9848`
2. `9849:9849`

### 验证
服务日志出现：
1. `grpc client connection server ... 9848`
2. `register finished`

---

## 阶段 D：烟测失败（支付后 WS 超时）

### 现象
烟测卡在支付后，最终报错：
`buyer WS 未在超时内收到 OrderPaid`

### 关键定位链路

## D1. 先查订单状态与 Outbox
```sql
SELECT order_no,status,payment_no FROM shiori_order.o_order ORDER BY id DESC LIMIT 5;
SELECT id,type,status,retry_count,last_error,sent_at FROM shiori_order.o_outbox_event ORDER BY id DESC LIMIT 20;
```

### 发现
订单已 `PAID`，但 outbox 事件长期 `PENDING`，说明“写出去了但未 relay”。

### 根因 1（order）
`OutboxRelayService` 没有实际生效（定时投递未运行），导致事件未发送到 MQ。

### 修复 1
1. 移除在 `@Component/@Service` 上不稳定的 `@ConditionalOnBean` 使用。
2. 保留 `@ConditionalOnProperty` 作为功能开关。
3. 重启 `order-service` 后，历史 outbox 自动转为 `SENT`。

## D2. Outbox 已 SENT 但 WS 仍收不到

### 现象
notify 日志出现：
`json: cannot unmarshal number into Go struct field ... userId of type string`

### 根因 2（notify）
`OrderPaid` payload 中 `userId` 是数字，notify 路由只接受字符串。

### 修复 2
`shiori-notify` 路由改为兼容两种类型：
1. `userId` 为 string
2. `userId` 为 number（转为字符串后路由）

并补充测试覆盖 numeric userId 场景。

---

## 阶段 E：修复后的测试与回归

### 局部回归
1. `go test ./...`（`shiori-notify`）通过。
2. `./gradlew :shiori-order-service:test --no-daemon` 通过。

### 端到端回归
```bash
bash /Users/hhm/code/shiori/scripts/smoke/e2e_trade_notify.sh
```
结果：
1. 下单幂等通过（同一 `Idempotency-Key` 返回同一 `orderNo`）。
2. buyer/seller 双端 WS 收到 `OrderPaid`。
3. 脚本输出 `E2E 烟测成功`。

---

## 3. 关键“反例”与结论

## 反例
中间曾尝试用 `ObjectProvider` 包装注入来绕过启动失败。

## 为什么不推荐
1. 业务组件语义被稀释（真实依赖关系不清晰）。
2. 运行时问题被“静默跳过”，故障更隐蔽。
3. 不是根因修复，只是症状规避。

## 最终策略
1. 运行时问题按真实依赖修复（Nacos 端口、组件条件装配）。
2. 测试环境通过测试配置隔离（关闭 outbox/mq）避免引入无关基础设施。

---

## 4. 本次改动文件（与排障直接相关）

1. `/Users/hhm/code/shiori/deploy/docker-compose.yml`
2. `/Users/hhm/code/shiori/deploy/nacos/import-nacos-configs.sh`
3. `/Users/hhm/code/shiori/shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/service/OutboxRelayService.java`
4. `/Users/hhm/code/shiori/shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/OrderMqTopology.java`
5. `/Users/hhm/code/shiori/shiori-java/shiori-order-service/src/main/java/moe/hhm/shiori/order/mq/OrderTimeoutConsumer.java`
6. `/Users/hhm/code/shiori/shiori-java/shiori-order-service/src/test/resources/application.yml`
7. `/Users/hhm/code/shiori/shiori-notify/internal/router/router.go`
8. `/Users/hhm/code/shiori/shiori-notify/internal/router/router_test.go`

---

## 5. 下次可直接复用的排障剧本（10 分钟版）

1. `docker compose up -d` 后先看：
   - `docker compose ps`
   - `docker ps -a --filter name=shiori-nacos-config-init`
2. 若 init 失败：
   - `docker logs shiori-nacos-config-init`
   - `docker logs shiori-nacos --tail 200`
3. 若 Java 服务起不来：
   - 先确认 `8848/9848/9849` 是否映射
   - 再看服务日志里的 `register finished`
4. 若烟测支付后 WS 超时：
   - 查 `o_order` 是否 `PAID`
   - 查 `o_outbox_event` 是否 `SENT`
   - 若 `PENDING`：看 order relay
   - 若 `SENT`：看 notify 消费反序列化错误

