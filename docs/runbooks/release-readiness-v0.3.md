# Shiori v0.3 发布准入与排障 Runbook

## 1. 发布准入清单

发布前必须满足：

1. 阻塞任务全部通过：
   - `java-test`
   - `local-regression-blocking`
2. 非阻塞趋势任务已留痕（允许失败但必须评估）：
   - `perf-stress-non-blocking`
3. 冒烟脚本通过：
   - `scripts/smoke/e2e_trade_notify.sh`
   - `scripts/smoke/e2e_admin_console.sh`
4. 指标阈值未越线：
   - 订单接口错误率 < 1%
   - WebSocket 推送 p95 延迟 <= `K6_WS_LATENCY_P95_MS`
   - Outbox 未出现持续堆积（重试失败趋势可回收）
5. 变更说明完整：
   - 版本变更点
   - 回滚目标 tag
   - 回滚触发条件

## 2. 生产排障顺序

按顺序执行，避免并行误判：

1. 网关层：
   - `/actuator/health`
   - 401/403/429 是否突增
   - `shiori_gateway_governance_total` 是否出现大量 `decision=blocked`
2. 下游服务健康：
   - user/product/order/notify 健康检查
   - Nacos 配置是否与目标 group 一致
3. 订单交易链路：
   - `o_outbox_event` 重试/失败状态
   - `o_order_status_audit_log` 状态迁移是否连续
4. 通知链路：
   - notify 消费 lag
   - replay 拉取命中是否异常增加
5. 数据一致性：
   - 订单状态与库存回补是否匹配
   - 幂等冲突是否集中在特定接口

## 3. 回滚触发条件

满足任一条件应触发回滚评估：

1. 连续 5 分钟核心交易接口错误率 >= 3%。
2. 网关限流误伤导致关键写接口（登录/下单/支付）可用性显著下降。
3. 订单状态机出现不可恢复非法迁移（人工确认后仍复现）。
4. notify 补偿/实时路径同时异常，支付成功通知不可达。

## 4. 回滚步骤

1. 回滚发布版本到上一稳定 tag（例如 `v0.2.0`）。
2. 临时关闭高风险治理开关（如误伤明显）：
   - `security.rate-limit.enabled=false`
   - `security.gateway-sign.replay-protection-enabled=false`
3. 重新执行两套 smoke 验证基础可用性。
4. 记录事故时间线与指标截图，补充复盘文档。

## 5. 演练脚本

统一使用：

```bash
# 发布演练（阻塞 + 非阻塞）
bash scripts/release/release_drill.sh drill

# 回滚演练（目标版本）
ROLLBACK_TO_TAG=v0.2.0 bash scripts/release/release_drill.sh rollback
```

