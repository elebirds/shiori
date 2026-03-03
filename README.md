# 📖 Shiori (栞) - Campus Textbook Trading Platform

> “栞”（しおり），意为书签。一本教材的流转，就像上一位读者在书中夹入书签，交递给下一位求知者。

**Shiori** 是一个基于 **核心/边缘异构微服务（Core/Edge Polyglot Microservices）** 的校园二手教材交易平台：
- **Core（Java / Spring）**：承载交易关键链路（用户、商品/库存、订单、支付模拟、权限）
- **Edge（Go / WebSocket）**：承载实时通知与长连接推送（事件消费 → 会话路由 → 推送）

项目目标：以可落地、可验证的方式实践分布式系统关键问题：**防超卖、最终一致性、超时关单、幂等、可观测性与压测资产**。

## ✨ 核心架构与技术亮点

### 🚀 核心/边缘异构微服务 (Core & Edge Polyglot)

- **Java 核心域 (Spring Cloud 体系)**  
  采用 **JDK 21 + Spring Boot 4.0.x + Spring Cloud（Gateway / OpenFeign）** 承载核心交易链路（用户/商品/订单/支付模拟）。  
  重点落地：订单状态机、库存扣减、幂等控制、Outbox 事件发布、超时关单等可追问能力。

- **Go 边缘域 (Gin + WebSocket)**  
  仅负责“事件消费 → 会话路由 → 推送”，将长连接与推送压力从核心域剥离。  
  **可水平扩展**：notify 多实例部署时，每个实例维护自身连接；事件广播后仅推送本机命中的会话（实现简单且有效）。

### 🛡️ 交易一致性与并发容错（可经受追问）

- **极简防超卖机制（正确性由 DB 兜底）**  
  不将 Redis 锁作为库存正确性的前提，采用 MySQL 原子更新作为并发安全根基：  
  ```sql
  UPDATE sku
  SET stock = stock - 1
  WHERE sku_id = ? AND stock >= 1;
  ```

通过检查 `affected_rows` 判断是否扣减成功，天然支持多实例并发扣减不超卖。

* **事件驱动与最终一致性（Transactional Outbox + Relay）**
  订单服务在**同一数据库事务**中写入业务数据与 Outbox 事件（如 `OrderCreated / OrderPaid / OrderCanceled`），避免“写库成功但发消息失败”。
  通过 **Outbox Relay** 可靠投递到 MQ（可重试/可观测），实现最终一致性而不引入重型强事务框架。

* **At-Least-Once 投递语义下的幂等闭环**
  MQ 投递采用 at-least-once（允许重复），因此系统通过幂等设计消除重复副作用：

  * **HTTP 幂等**：下单/支付回调支持 `Idempotency-Key` 或业务唯一键（如 `order_no`、`payment_no`），重复请求不重复扣库存/不重复改状态
  * **消息幂等**：事件携带 `event_id`，消费端通过（DB 唯一键/去重表/Redis SETNX）保证重复消息不重复通知

### ⏳ 超时关单（TTL + DLX，二次校验避免误关单）

* 下单后投递延迟事件 `OrderTimeout`（TTL=15min），到期进入 DLQ（死信队列）。
* 订单服务消费 DLQ 消息时**二次校验订单状态**：

  * 若已支付：直接忽略（幂等）
  * 若未支付：关单 + 回滚库存 + 写入 `OrderCanceled` Outbox 事件

> 注：RabbitMQ（TTL+DLX）模式通常不“取消已发送的延迟消息”。本项目采用到期消费时二次校验状态的方式，简单可靠且可解释。

### 📦 现代化工程规范（交付友好 + 易演示）

* **Monorepo (单体仓库) 治理**
  根目录按语言与生态隔离 Java/Go/前端工程，兼顾代码聚合与跨 IDE（IDEA/GoLand）的原生体验。

* **严格的数据基建（Schema Versioning）**
  摒弃 Hibernate/JPA 式 `ddl-auto` 自动建表，使用 **Flyway** 管理数据库迁移（可追溯/可回滚）。
  金额字段统一使用 `BIGINT` 存“分”，杜绝精度丢失与对账差异。

* **统一安全网关**
  基于 API Gateway 实现全局 JWT 验签、RBAC 权限拦截与基础限流；关键写接口结合 Redis 做防刷/短期幂等保护（非库存正确性依赖）。

* **可观测性（建议落地）**
  通过 Spring Boot Actuator 暴露核心指标（QPS、p95、错误率、线程/连接池），配合 Prometheus + Grafana 可视化；
  notify 服务输出在线连接数、推送成功率、消费延迟等关键指标，便于压测与复盘。

## 🔄 核心订单流转与事件流 (State & Event Flow)

本项目通过 RabbitMQ 将同步业务转化为异步事件驱动，核心链路如下：

```text
[用户下单] 
   │
   ├─▶ [Java 订单服务] MySQL 本地事务：生成订单 (Status: UNPAID) + 写入 Outbox(OrderCreated)
   │      │
   │      ├─▶ [Java 商品/库存服务] 原子扣减库存（成功/失败由 affected_rows 判断）
   │      │
   │      └─▶ (异步投递) 发送延迟超时事件 OrderTimeout 至 MQ (TTL=15min, DLX)
   │
[用户支付成功（模拟支付/回调）]
   │
   ├─▶ [Java 订单服务] 幂等校验(Idempotency-Key / payment_no) 
   │      ├─▶ 更新订单状态 (Status: PAID)
   │      └─▶ 写入 Outbox(OrderPaid) → Relay 投递 MQ
   │
[Go 消息推送边缘服务] 
   │
   └─▶ 持续监听 MQ `OrderPaid` 事件 -> 路由本机 WebSocket 会话 -> 实时推送通知（卖家售出提醒/买家支付成功）

[15 分钟到期未支付]
   │
   └─▶ [Java 订单服务] 消费 DLQ `OrderTimeout` -> 二次校验订单状态
          ├─ 已支付：忽略（幂等）
          └─ 未支付：关单 + 回滚库存 + Outbox(OrderCanceled) → MQ
```

---

## 🛠️ 技术栈清单 (Tech Stack)

### 核心后端 (Core Services)

* **主语言 & 框架:** Java 21 / Spring Boot 4.0.x / Spring Cloud (Gateway, OpenFeign)
* **持久层:** MySQL 8.0（单实例多库：`shiori_user`/`shiori_product`/`shiori_order`） / MyBatis-Plus / Flyway
* **微服务大脑:** Nacos (服务注册与动态配置)
* **接口文档:** SpringDoc OpenAPI 3 (Swagger UI)
* **构建工具:** Gradle

### 边缘微服务 (Edge Services)

* **语言 & 框架:** Go 1.26 / Gin / Gorilla WebSocket
* **通信机制:** 基于 RabbitMQ 订阅消费 Java 侧投递的业务事件

### 中间件与基础设施 (Infrastructure)

* **缓存:** Redis（缓存/防刷/短期幂等/热点保护）
* **消息队列:** RabbitMQ（事件总线、TTL+DLX、DLQ）
* **可观测性:** Prometheus + Grafana（指标可视化）
* **压测资产:** k6（脚本化压测，纳入仓库便于复现）
* **容器化部署:** Docker & Docker Compose (一键拉起整套基建)

### 前端与多端展示 (Frontend)

* **用户端 Web (买/卖家视角):** Vue 3 + TypeScript + Vite + Tailwind CSS + Pinia + Vue Query
* **管理端 (平台运营视角):** Vue 3 + TypeScript + Vite + Tailwind CSS + Pinia + Vue Query

---

## 📁 工程目录结构

本项目采用 Monorepo 结构，克隆后请分别使用对应的 IDE 打开子目录：

```text
shiori/
├── shiori-java/                      # ☕ [核心微服务群] 请使用 IntelliJ IDEA 打开
│   ├── build.gradle                  # 全局依赖管理
│   ├── shiori-common/                # 全局异常、统一返回体 Result<T>、错误码、事件模型
│   ├── shiori-gateway-service/       # API 网关与 JWT 统一鉴权拦截
│   ├── shiori-user-service/          # 用户服务
│   ├── shiori-product-service/       # 商品/库存服务
│   └── shiori-order-service/         # 订单交易服务（Outbox + 超时关单）
├── shiori-notify/                    # 🐹 [推送边缘服务] 请使用 GoLand 打开
│   └── main.go                       # 监听 MQ 并通过 WebSocket 推送前端
├── shiori-app/                       # 🌐 [用户端 Web] 请使用 VSCode / WebStorm 打开
├── shiori-admin-web/                 # 💻 [管理端后台 Web] 请使用 WebStorm / VSCode 打开
├── deploy/                           # 🐳 [基础设施部署]
│   ├── docker-compose.yml            # MySQL, Redis, RabbitMQ, (可选 Nacos/Prom/Grafana)
│   ├── nacos/                        # Nacos 配置导入脚本与模板（templates/*.yml.tmpl）
│   ├── rabbitmq/                     # RabbitMQ 最小权限账号初始化脚本
│   └── sql/                          # MySQL 初始化脚本（创建多库）与后续运维 SQL
└── perf/                             # ⚡ [压测资产] k6 脚本与结果记录
    ├── k6-order.js
    ├── k6-ws.js
    └── README.md
```

---

## 🚀 Quick Start

### 0) Requirements

* Docker & Docker Compose
* JDK 21 + Gradle
* Go 1.26
* Node.js + pnpm（前端）

### 1) Start Infrastructure

```bash
cd deploy
cp .env.example .env
docker compose up -d
```

说明：
- 默认 `docker compose up -d` 只启动基础设施（MySQL/Redis/RabbitMQ/Nacos/MinIO + 初始化容器）。
- 如需一键启动“基础设施 + Java/Go 服务”，使用：

```bash
cd deploy
docker compose --profile app up -d --build
```

如需再加上两个前端容器（用户端 + 管理端），使用：

```bash
cd deploy
docker compose --profile app --profile web up -d --build
```

若遇到 Docker Hub 网络抖动（例如 `EOF`）导致 build 失败，可先改用经典构建器重试：

```bash
cd deploy
DOCKER_BUILDKIT=0 docker compose --profile app build
DOCKER_BUILDKIT=0 docker compose --profile app --profile web build
docker compose --profile app --profile web up -d
```

全栈容器入口：
- 用户端 Web: `http://localhost:3000`
- 管理端 Web: `http://localhost:3001`
- 网关: `http://localhost:8080`
- Notify 健康检查: `http://localhost:8090/healthz`

`docker-compose` 会通过 `deploy/sql/mysql-init/` 自动初始化业务数据库：
- `shiori_user`
- `shiori_product`
- `shiori_order`

并通过一次性容器 `nacos-config-init` 自动渲染并导入 Nacos 配置模板（`deploy/nacos/templates/*.yml.tmpl`）。
导入后的 dataId 规范：
- `shiori-user-service-base.yml`
- `shiori-user-service-secret.yml`
- `shiori-product-service-base.yml`
- `shiori-product-service-secret.yml`
- `shiori-order-service-base.yml`
- `shiori-order-service-secret.yml`
- `shiori-gateway-service-base.yml`
- `shiori-security-base.yml`
- `shiori-security-secret.yml`

Nacos 分组规则：
- `SHIORI_DEV`（`SHIORI_ENV=dev`）
- `SHIORI_TEST`（`SHIORI_ENV=test`）
- `SHIORI_PROD`（`SHIORI_ENV=prod`）
- 未显式设置 `NACOS_CONFIG_GROUP` 时，`nacos-config-init` 会按 `SHIORI_ENV` 自动推导目标 group。

可用以下命令查看导入日志：

```bash
cd deploy
docker compose logs nacos-config-init
```

如需手工重跑导入：

```bash
cd deploy
docker compose run --rm nacos-config-init
```

RabbitMQ 与 MinIO 也会通过一次性容器完成最小权限初始化：
- `rabbitmq-auth-init`：创建 `order-service` 与 `notify-service` 独立账号并写入受限权限。
- `minio-init`：创建商品桶、商品服务专用访问账号与桶级读写策略。

并启动 MinIO（商品图片对象存储）：
- S3 API: `http://localhost:9000`
- Console: `http://localhost:9001`

### 1.1) 环境配置矩阵（dev/test/prod）

| 配置项 | 敏感 | dev 来源 | test 来源 | prod 来源 | 注入位置 |
|---|---|---|---|---|---|
| `NACOS_CONFIG_GROUP` | 否 | `SHIORI_DEV` | `SHIORI_TEST` | `SHIORI_PROD` | Compose/启动环境 |
| `NACOS_CONFIG_NAMESPACE` | 否 | public 或指定 namespace | 指定 namespace | 指定 namespace | Compose/启动环境 |
| `JWT_HMAC_SECRET` | 是 | `.env` 本地密钥 | CI 运行时生成/Secret | Secret 管理系统 | `nacos-config-init` 模板渲染 |
| `GATEWAY_SIGN_SECRET` | 是 | `.env` 本地密钥 | CI 运行时生成/Secret | Secret 管理系统 | `nacos-config-init` 模板渲染 |
| `USER_DB_USERNAME` | 否（建议最小权限） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-user-service-secret.yml` |
| `USER_DB_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-user-service-secret.yml` |
| `PRODUCT_DB_USERNAME` | 否（建议最小权限） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-product-service-secret.yml` |
| `PRODUCT_DB_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-product-service-secret.yml` |
| `ORDER_DB_USERNAME` | 否（建议最小权限） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-order-service-secret.yml` |
| `ORDER_DB_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-order-service-secret.yml` |
| `ORDER_RMQ_USERNAME` | 否（建议最小权限） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-order-service-secret.yml` |
| `ORDER_RMQ_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-order-service-secret.yml` |
| `NOTIFY_RMQ_USERNAME` | 否（建议最小权限） | `.env` | CI Secret/变量 | Secret 管理系统 | notify 运行时环境 |
| `NOTIFY_RMQ_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | notify 运行时环境 |
| `MINIO_PRODUCT_ACCESS_KEY` | 否（凭证标识） | `.env` | CI Secret/变量 | Secret 管理系统 | `shiori-product-service-secret.yml` |
| `MINIO_PRODUCT_SECRET_KEY` | 是 | `.env` | CI Secret | Secret 管理系统 | `shiori-product-service-secret.yml` |
| `MYSQL_OPS_USERNAME` | 否（运维/烟测账号） | `.env` | CI Secret/变量 | Secret 管理系统 | MySQL 初始化与烟测 |
| `MYSQL_OPS_PASSWORD` | 是 | `.env` | CI Secret | Secret 管理系统 | MySQL 初始化与烟测 |
| `MYSQL_ROOT_PASSWORD` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose |
| `RABBITMQ_DEFAULT_USER` | 否（RabbitMQ 管理账号） | `.env` | CI Secret/变量 | Secret 管理系统 | docker compose |
| `RABBITMQ_DEFAULT_PASS` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose |
| `MINIO_ROOT_USER` | 否（MinIO 管理账号） | `.env` | CI Secret/变量 | Secret 管理系统 | docker compose |
| `MINIO_ROOT_PASSWORD` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose |
| `NACOS_AUTH_TOKEN` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose |
| `NACOS_AUTH_IDENTITY_KEY` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose / nacos init 请求头 |
| `NACOS_AUTH_IDENTITY_VALUE` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | docker compose / nacos init 请求头 |
| `NACOS_IMPORT_PASSWORD` | 是 | `.env` | CI 运行时生成/Secret | Secret 管理系统 | `nacos-config-init` 登录 |

最小启动方式（不含明文）：

```bash
cd deploy
cp .env.example .env
# 编辑 .env，填入真实密钥
docker compose up -d
```

### 2) Run Core Services (Java)

若你已执行 `docker compose --profile app up -d --build`，可跳过本节手工启动。

```bash
cd shiori-java
./gradlew :shiori-gateway-service:bootRun
./gradlew :shiori-user-service:bootRun
./gradlew :shiori-product-service:bootRun
./gradlew :shiori-order-service:bootRun
```

### 2.2) Nacos 数据源配置（必须）

三大业务服务的配置统一由 Nacos 下发，按 “base/secret” 拆分：
- user: `shiori-user-service-base.yml` + `shiori-user-service-secret.yml`
- product: `shiori-product-service-base.yml` + `shiori-product-service-secret.yml`
- order: `shiori-order-service-base.yml` + `shiori-order-service-secret.yml`
- shared: `shiori-security-base.yml` + `shiori-security-secret.yml`

网关额外加载：`shiori-gateway-service-base.yml`。

模板位于：
- `deploy/nacos/templates/`

### 2.1) Gateway 鉴权（第二阶段）

当前脚手架已启用“认证闭环第二阶段”：
- 网关统一 JWT 验签（HMAC）
- 认证接口白名单：`/api/user/auth/register|login|refresh|logout`
- 业务接口默认受保护：`/api/**`
- 管理路径：`/api/admin/**` 需要 `ROLE_ADMIN`
- 网关向下游透传 `X-User-Id`、`X-User-Roles`
- 网关为 `/api/**` 写入 `X-Gateway-Ts`、`X-Gateway-Sign`，业务服务执行签名校验作为第二道防线
- `GET /api/product/**` 允许匿名读取（商品浏览）

推荐通过 Nacos 配置中心下发安全配置（group 由 `SHIORI_ENV` 或 `NACOS_CONFIG_GROUP` 决定）：
- `shiori-security-base.yml`：`issuer`、`ttl`、`max-skew`
- `shiori-security-secret.yml`：`hmac-secret`、`internal-secret`

```yaml
security:
  jwt:
    issuer: "shiori"
    access-ttl-seconds: 900
    refresh-ttl-seconds: 604800
  gateway-sign:
    max-skew-seconds: 300
```

密钥通过环境变量注入到模板渲染流程（不在仓库保存明文）：

```bash
export JWT_HMAC_SECRET='<your-secret>'
export GATEWAY_SIGN_SECRET='<your-secret>'
```

### 3) Run Edge Notify (Go)

```bash
# ensure RabbitMQ is up
cd deploy
docker compose up -d rabbitmq

cd ../shiori-notify
go run .
```

常用环境变量（可选）：

```bash
export NOTIFY_HTTP_ADDR=:8090
export RABBITMQ_ADDR=amqp://<rmq-username>:<rmq-password>@localhost:5672/
export RABBITMQ_EXCHANGE=shiori.order.event
export RABBITMQ_QUEUE=notify.order.paid
export RABBITMQ_ROUTING_KEY=order.paid
```

鉴权快速验证（示例）：

```bash
# 未携带 Token，应返回 401（Result.code=20002）
curl -i http://localhost:8080/api/user/profile

# 登录签发（白名单接口，无需 Bearer）
curl -i -X POST http://localhost:8080/api/user/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password"}'

# 注册（白名单接口，无需 Bearer）
curl -i -X POST http://localhost:8080/api/user/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123","nickname":"Alice"}'

# 刷新（白名单接口，无需 Bearer）
curl -i -X POST http://localhost:8080/api/user/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<opaque-refresh-token>"}'

# 携带 Access Token 访问受保护路径（通过网关鉴权后再转发）
curl -i -H "Authorization: Bearer <access-jwt>" http://localhost:8080/api/user/profile

# 商品匿名浏览（无需 Bearer）
curl -i http://localhost:8080/api/product/products
```

### 3.1) 商品域最小闭环（当前可用）

商品服务当前已支持：
- 图片预签名直传：`POST /api/product/media/presign-upload`
- 商品读接口（匿名）：`GET /api/product/products`、`GET /api/product/products/{id}`
- 商品写接口（需登录）：`POST /api/product/products`、`PUT /api/product/products/{id}`、`publish/off-shelf`
- 库存交易接口（供后续订单服务）：`/api/product/internal/stock/deduct|release`（`bizNo + opType` 幂等）

图片上传预签名示例：

```bash
curl -X POST http://localhost:8080/api/product/media/presign-upload \
  -H "Authorization: Bearer <access-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"book-cover.jpg","contentType":"image/jpeg"}'
```

### 3.2) 订单域交易闭环（当前可用）

订单服务当前已支持：
- 下单（幂等）：`POST /api/order/orders`（必须 `Idempotency-Key`）
- 我的订单分页：`GET /api/order/orders`
- 订单详情：`GET /api/order/orders/{orderNo}`
- 模拟支付：`POST /api/order/orders/{orderNo}/pay`
- 主动取消：`POST /api/order/orders/{orderNo}/cancel`
- 超时关单：通过 `OrderTimeout` 延迟消息（TTL + DLX）自动触发，消费时二次校验状态
- Outbox 事件投递：`OrderCreated` / `OrderPaid` / `OrderCanceled`（`OrderPaid` 会分别给买家和卖家写事件）

最小调用示例：

```bash
# 1) 创建订单（多 SKU，但限制同一卖家）
curl -X POST http://localhost:8080/api/order/orders \
  -H "Authorization: Bearer <access-jwt>" \
  -H "Idempotency-Key: create-order-demo-001" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"productId": 1, "skuId": 11, "quantity": 1},
      {"productId": 1, "skuId": 12, "quantity": 2}
    ]
  }'

# 2) 支付订单（模拟）
curl -X POST http://localhost:8080/api/order/orders/<orderNo>/pay \
  -H "Authorization: Bearer <access-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"paymentNo":"pay-demo-001"}'
```

### 3.3) 一键端到端烟测（交易 + 通知 + 管理端）

当网关、user/product/order、notify 均已启动后，可运行：

```bash
bash scripts/smoke/e2e_trade_notify.sh
```

管理端闭环烟测（管理员角色、用户治理、商品下架、订单取消、审计日志）：

```bash
bash scripts/smoke/e2e_admin_console.sh
```

脚本会自动执行：
- 注册/登录 buyer + seller
- seller 创建并发布商品（2 个 SKU）
- buyer 下单并重复请求校验 `Idempotency-Key` 幂等
- buyer 支付订单
- buyer/seller 两端 WebSocket 均收到 `OrderPaid` 事件

可选环境变量（`admin` 烟测依赖 MySQL 账号）：

```bash
export GATEWAY_BASE_URL=http://localhost:8080
export NOTIFY_WS_BASE_URL=ws://localhost:8090/ws
export SMOKE_TIMEOUT_SECONDS=60
export SMOKE_PREFIX=smoke
export MYSQL_CONTAINER=shiori-mysql
export MYSQL_OPS_USERNAME=<mysql-ops-user>
export MYSQL_OPS_PASSWORD=<mysql-ops-password>
```

`ws-smoke` 探针命令（脚本内部也会调用）：

```bash
cd shiori-notify
go run ./cmd/ws-smoke -base-url ws://localhost:8090/ws -user-id 1001 -expect-type OrderPaid -expect-aggregate Oxxxx -timeout 60s
```

### 3.4) GitHub Actions CI（PR 全量自动化）

仓库已提供 CI workflow：
- `.github/workflows/ci.yml`
- Job 1：`java-test`（`shiori-java` 全量测试）
- Job 2：`e2e-trade-notify-admin`（基础设施 + 4 个 Java 服务 + notify + 交易通知烟测 + 管理端闭环烟测 + `shiori-app` Playwright E2E + `shiori-admin-web` Playwright E2E）

E2E 编排脚本：

```bash
bash scripts/ci/run_e2e_ci.sh
```

可选环境变量：

```bash
export SERVICE_READY_TIMEOUT_SECONDS=300
```

必填敏感变量（脚本会校验）：
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_OPS_PASSWORD`
- `USER_DB_PASSWORD`
- `PRODUCT_DB_PASSWORD`
- `ORDER_DB_PASSWORD`
- `RABBITMQ_DEFAULT_PASS`
- `ORDER_RMQ_PASSWORD`
- `NOTIFY_RMQ_PASSWORD`
- `MINIO_ROOT_PASSWORD`
- `MINIO_PRODUCT_SECRET_KEY`
- `NACOS_AUTH_TOKEN`
- `NACOS_IMPORT_PASSWORD`
- `JWT_HMAC_SECRET`
- `GATEWAY_SIGN_SECRET`

脚本执行时会：
- 自动 `docker compose up -d`
- 等待 `nacos-config-init` 成功退出
- 启动 user/product/order/gateway/notify
- 执行 `scripts/smoke/e2e_trade_notify.sh`
- 执行 `scripts/smoke/e2e_admin_console.sh`
- 执行 `shiori-app` 前端 Playwright 端到端用例
- 执行 `shiori-admin-web` 管理端 Playwright 端到端用例
- 失败时输出关键日志并自动清理环境

CI 日志默认落盘到仓库根目录：
- `ci-logs/*.log`

### 4) Run Frontend (Web)

```bash
cd shiori-app
cp .env.example .env
pnpm install
pnpm dev
```

默认配置：
- `VITE_API_BASE_URL=http://localhost:8080`
- `VITE_NOTIFY_WS_BASE_URL=ws://localhost:8090/ws`

前端 Playwright 冒烟（注册/登录 -> 创建商品 -> 多 SKU 下单 -> 支付 -> 通知）：

```bash
cd shiori-app
pnpm e2e:install
pnpm e2e
```

### 5) Run Admin Web

```bash
cd shiori-admin-web
cp .env.example .env
pnpm install
pnpm dev
```

默认地址：`http://localhost:5173`（仅 `ROLE_ADMIN` 可登录）。

管理端 Playwright 冒烟（登录 -> 用户禁用/启用 -> 商品强制下架 -> 订单取消）：

```bash
cd shiori-admin-web
pnpm e2e:install
pnpm e2e
```

管理端 API（统一前缀）：
- `/api/admin/users/**`
- `/api/admin/roles/**`
- `/api/admin/products/**`
- `/api/admin/orders/**`

### 5.1) 手工初始化管理员

本项目不内置默认管理员账号，请按以下步骤初始化：

1. 先调用注册接口创建普通用户（`/api/user/auth/register`）。
2. 在 MySQL 手工执行脚本授予 `ROLE_ADMIN`：

```bash
mysql -h127.0.0.1 -P3306 -u<mysql-user> -p'<mysql-password>' < deploy/sql/manual/grant_admin_role.sql
```

脚本路径：
- `deploy/sql/manual/grant_admin_role.sql`

---

## 📈 压测与可观测性 (Performance & Observability)

本项目建议以 **k6 + Prometheus + Grafana** 形成“可复现性能证据链”：

* `perf/k6-order.js`：下单/支付/查询链路压测
* `perf/k6-ws.js`：WebSocket 连接与推送压测
* Prometheus 抓取 Spring Boot Actuator 与 notify 指标，Grafana 面板展示 p95、错误率、队列堆积与在线连接数

运行示例：

```bash
cd perf
k6 run k6-order.js
k6 run k6-ws.js
```
