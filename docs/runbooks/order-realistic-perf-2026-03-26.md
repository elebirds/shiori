# 2026-03-26 realistic 订单压测记录

## 本轮变更

- `shiori-user-service` 补充 `spring-cloud-starter-loadbalancer`
  - 原因：注册成功后同步调用 `payment-service` 预建钱包，真实容器环境中缺失 loadbalancer 依赖会导致通过服务名访问下游失败，表现为 `/api/user/auth/register` 返回 `503`，报错为“钱包服务不可达”。
- `perf/k6-order-common.js` 固定压测用户密码为短口令 `PerfPwd123!`
  - 原因：realistic 脚本 setup 阶段使用长 `perfPrefix` 拼接密码，触发 bcrypt 的 `72 bytes` 上限，导致 setup 直接报 `IllegalArgumentException: password cannot be more than 72 bytes`，压测结果无效。

## 环境与脚本

- 脚本：`./scripts/ci/run_perf_order_realistic.sh`
- 目录：`/Users/hhm/code/shiori/ci-logs/perf/order-realistic-b94d2b6-lbfix`
- 参数：
  - `K6_ORDER_DURATION=20s`
  - realistic 默认 setup：
    - `80 buyers`
    - `20 sellers`
    - `2 products/seller`

## 压测结果

| VU | http_req_failed | biz_failed | http req/s | iter/s | http p95(ms) | create p95(ms) | pay p95(ms) | deliver p95(ms) | confirm p95(ms) | detail p95(ms) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 40 | 0 | 0 | 83.88 | 14.70 | 642.21 | 1075.31 | 1019.88 | 227.46 | 450.95 | 143.64 |
| 60 | 0 | 0 | 118.01 | 21.43 | 636.03 | 795.44 | 768.35 | 254.35 | 408.50 | 260.92 |
| 80 | 0 | 0 | 116.67 | 21.18 | 907.48 | 1087.96 | 1119.62 | 360.41 | 573.85 | 451.47 |
| 100 | 0 | 0 | 97.95 | 17.45 | 1467.08 | 1767.64 | 1683.00 | 698.41 | 943.91 | 891.33 |

## 结论

- 如果“稳定”定义为 `0 HTTP 失败 + 0 业务失败`，则 `100 VU / 20s` 仍可跑通，但时延已经明显不可接受。
- 如果综合吞吐与时延，`60 VU` 是当前版本更合理的上限：
  - 吞吐达到峰值：`118 req/s`
  - 比 `80 VU` 更快，且没有出现失败
  - `80 VU` 开始出现明显排队效应，吞吐基本不再提升，但 `create/pay/confirm/detail` 时延继续恶化
- 当前主要瓶颈仍集中在：
  - `create`
  - `pay`
  - `confirm`
- `deliver` 和 `detail` 在 `40/60 VU` 下仍明显优于前三阶段，但到 `80/100 VU` 也会被整体排队拖慢。

## 观察

- `60 VU -> 80 VU`
  - 请求吞吐基本持平：`118.01 -> 116.67 req/s`
  - 但 `http_req_duration p95` 从 `636ms` 拉高到 `907ms`
  - 这是典型的排队平台期，不再是“加 VU 就线性增吞吐”
- `80 VU -> 100 VU`
  - 吞吐反而下降：`116.67 -> 97.95 req/s`
  - `create/pay` p95 进入 `1.6s~1.8s`
  - 说明系统已越过最佳工作点，继续提高并发只会放大等待时间

## 建议

- 当前 realistic 场景下，可把 `60 VU` 视为本版本的最高可用档位。
- 下一步应继续针对 `create/pay/confirm` 做链路级拆解：
  - 应用侧分阶段埋点
  - 数据库慢 SQL/锁等待采样
  - 必要时补火焰图或 async-profiler
- 在没有进一步优化前，不建议将 realistic 默认压测目标设到 `80+ VU`。
