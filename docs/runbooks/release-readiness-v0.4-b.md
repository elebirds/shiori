# Shiori v0.4-b 发布准入与回滚 Runbook（商品/订单扩展）

## 1. 目标范围

1. 覆盖 `v0.4-b`：商品 v2 扩展、订单履约链路扩展、用户端与管理端页面切换到 `/api/v2/**`。
2. 本 Runbook 不覆盖用户中心/通知系统内部实现（由 `v0.4-a` 负责）。

## 2. 发布前检查

### 2.1 后端

1. 所有服务测试通过：`./gradlew test`。
2. Flyway 迁移已在预发布执行并校验：
   1. `V5__add_product_v2_fields.sql`
   2. `V6__add_order_v2_indexes.sql`
3. Nacos 配置已发布并生效：
   1. `feature.api-v2.enabled=true`
   2. `order.mq.order-delivered-routing-key=order.delivered`
   3. `order.mq.order-finished-routing-key=order.finished`
4. Gateway 路由已含：
   1. `/api/v2/product/**`
   2. `/api/v2/order/**`
   3. `/api/v2/admin/products/**`
   4. `/api/v2/admin/orders/**`

### 2.2 前端

1. `shiori-app` 构建通过：`npm run build`。
2. `shiori-admin-web` 构建通过：`npm run build`。
3. 用户端可见：
   1. 商品广场 v2 筛选与排序
   2. 卖家订单工作台 `/seller/orders`
   3. 买家确认收货按钮（`DELIVERING`）
4. 管理端可见：
   1. 商品批量下架
   2. 订单发货/完成
   3. 审计时间线侧栏

### 2.3 压测与阈值

1. `perf/k6-order.js` 走通链路：创建 -> 支付 -> 发货 -> 确认收货 -> 详情。
2. 阈值：
   1. `http_req_failed < 1%`
   2. 各阶段 `p95 < 400ms`
   3. `shiori_perf_order_biz_failed_total == 0`

## 3. 发布顺序（固定）

1. 基础设施配置（Nacos/Gateway 配置）先发。
2. 后端服务发布：gateway -> product -> order。
3. 前端发布：`shiori-app` -> `shiori-admin-web`。
4. 观察窗口：24 小时（错误率、订单状态迁移、批量下架行为）。

## 4. 验收用例（最小闭环）

1. `seller` 创建商品并上架（含四个 v2 字段）。
2. `buyer` 下单并支付。
3. `seller` 在工作台执行发货。
4. `buyer` 执行确认收货，状态到 `FINISHED`。
5. `admin` 查看订单审计时间线，状态迁移完整可追踪。

## 5. 回滚触发条件

1. 错误率连续 5 分钟 `>= 3%`。
2. 订单状态迁移异常持续复现（如 `PAID` 无法进入 `DELIVERING`）。
3. 商品批量下架出现明显误伤且短时间无法止损。

## 6. 回滚动作

1. 将 `feature.api-v2.enabled` 置为 `false`（网关与服务按配置切回 v1）。
2. 前端回滚到上一版本镜像（v1 页面/API）。
3. 数据层不回滚结构，仅保留新增字段与索引。

## 7. 观测点

1. `shiori_order_transition_total{from,to,source}`。
2. `shiori_product_query_total{filter_combo}`。
3. `o_order_status_audit_log` 连续性。
4. outbox 事件类型覆盖：`OrderDelivered`、`OrderFinished`。

