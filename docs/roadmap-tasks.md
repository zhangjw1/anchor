# Anchor 后续路线图与 AI 任务拆分

> 用法：每次只把**一个 Task** 连同「0. 通用约束」交给 Cursor。Task 完成并通过验收后再开下一个。
> 优先级依据：对求职（Web3 交易钱包后端）的价值，而非功能数量。

## 0. 通用约束（每个 Task 都附带）

- 沿用现有栈：Java 21、Maven 多模块（`common` / `indexer` / `api`）、MyBatis-Plus + XML、Flyway、Kafka（Redpanda）、Redis、Micrometer。
- 数据库变更只能新增 `db/migrations/V{n}__xxx.sql`，当前最新为 V5，新任务从 **V6** 起；禁止改已有迁移。
- 合约地址只从 `contracts-shared/chains.yaml` 读取，业务代码禁止硬编码。
- 新增指标统一在 `AnchorMetricNames` 注册；新增常量放 `AnchorConstants`；可调参数放 `anchor-defaults.yml`。
- 异步事件一律走 Transactional Outbox（`OutboxService`），消费者必须幂等。
- 每个 Task 必须带单元测试；涉及 DB 的写集成测试；不得降低现有测试通过率。
- 提交粒度：一个 Task 一个 commit，格式 `feat(scope): ...` / `fix(scope): ...`。
- 不做超出 Task 范围的重构；发现的相关问题写进 Task 末尾的「遗留」而不是顺手改。
- 代码注释只写必要说明，中英文均可，沿用现有风格。

## 1. 优先级总览

| 阶段 | 内容 | 优先级 | 预估 |
|---|---|---|---|
| A | 压测与告警 | P0 | 3–5 天 |
| B | 限价单 / 止盈止损 | P0 | 2–3 周 |
| C | RPC 韧性增强 | P1 | 3–5 天 |
| D | 持仓与盈亏（PnL） | P1 | 1–2 周 |
| E | 代币风险检测 | P2 | 1–2 周 |
| F | 第二条 EVM 链 | P2 | 1 周 |
| G | Telegram Bot | P3 | 2–3 天 |
| H | Go 版 / Solana | P3 | 1 个月+ |

建议顺序：A → B → （此时可开始投简历）→ C → D → E/F/G/H 按需。

---

## 阶段 A：压测与告警

### A1 基线指标补全
- **依赖**：无
- **目标**：补齐压测和告警所需指标，现有 `AnchorMetricNames` 已有 RPC、扫块、报价、交易、WS、Kafka、Outbox 指标，缺：
  - HTTP 接口 P50/P95/P99（Spring Boot Actuator `http.server.requests` 开启 histogram/percentiles）
  - `anchor.indexer.lag.blocks`（Gauge，head − checkpoint；现状已有 REST/WS 字段 `lagBlocks`，本项补 Prometheus Gauge）
  - `anchor.ws.push.latency`（事件落库到 WS 推送的耗时）
- **验收**：`/actuator/prometheus` 能看到上述指标；Prometheus 能抓到。

### A2 Grafana 告警规则
- **依赖**：A1
- **目标**：在 `deploy/grafana/provisioning/` 下新增 alerting 规则文件，至少包含：
  - 落后块数 > 500 持续 5 分钟
  - outbox pending > 100 持续 2 分钟
  - RPC 错误率 > 5% 持续 3 分钟
  - Kafka consume failures / DLT 发布数 > 0
  - 扫块暂停（`anchor.scanner.paused` = 1）
  - API 5xx 比例 > 1%
- **验收**：重启 Grafana 后规则自动加载；人为停 Redpanda 可触发 outbox 告警（写在 `docs/` 的验证步骤里）。
- **注意**：Dashboard 补一页「告警总览」。

### A3 k6 压测脚本
- **依赖**：A1
- **目标**：新建 `loadtest/` 目录，k6 脚本覆盖：
  - `GET /api/v1/swaps`（keyset 分页，并发 50/200/500）
  - `POST /api/v1/quotes`（带缓存与不带缓存）
  - WebSocket 连接 1000 个并保持，统计推送延迟
  - `GET /api/v1/chains/{id}/metrics` 高频调用
- **验收**：每个场景输出 QPS、P95、P99、错误率；结果写入 `docs/loadtest-report.md`（含机器配置、参数、瓶颈分析）。

### A4 瓶颈优化（按压测结果）
- **依赖**：A3
- **目标**：只针对报告中最慢的前 2–3 项优化（常见：swap 查询索引、报价缓存命中、WS 广播序列化），优化前后数据对比写入报告。
- **验收**：报告里有前后对比，至少一项指标提升 30% 以上或给出无法提升的原因。

---

## 阶段 B：限价单 / 止盈止损

### 设计决策（已定，不再讨论）
- **非托管**：后端不持有用户私钥，也不持有用户资产。
- **用户授权**：用户一次性 `approve` Permit2；每个订单由用户用 EIP-712 签名。
- **执行方式**：自写 `LimitOrderExecutor` 合约，由 Keeper（热钱包，仅付 gas）调用；合约内验签、通过 Permit2 `permitWitnessTransferFrom` 拉取代币、经 SwapRouter02 兑换、校验 `minAmountOut`、把产出直接转给用户。
- **链**：先在 Base Sepolia 部署和联调，主网只读监控不执行。
- **触发方式**：由 Swap 事件流驱动价格更新，不轮询 RPC。
- **订单类型**：v1 只做 `LIMIT_BUY` / `LIMIT_SELL` / `STOP_LOSS` / `TAKE_PROFIT`，单跳 Uniswap V3 池。

### 订单状态机
```text
OPEN ──触发──▶ TRIGGERED ──提交──▶ SUBMITTED ──确认──▶ FILLED
 │                │                    │
 │                └──模拟失败──▶ OPEN（重试次数用尽 → FAILED）
 ├──用户取消──▶ CANCELLED
 ├──过期──────▶ EXPIRED
 └──reorg 回滚时 SUBMITTED/FILLED ──▶ TRIGGERED（重新执行）
```

### B1 合约：LimitOrderExecutor
- **依赖**：无
- **目标**：新建 `contracts-sol/`（Foundry 工程），实现 `LimitOrderExecutor.sol`：
  - EIP-712 订单结构：`owner, tokenIn, tokenOut, fee, amountIn, minAmountOut, triggerPrice, orderType, nonce, deadline`
  - `execute(order, signature)`：验签 → Permit2 拉取 → SwapRouter02 `exactInputSingle` → 校验最小产出 → 转给 owner
  - nonce 使用 Permit2 的无序 nonce 机制，支持链上取消 `cancel(nonce)`
  - 仅允许白名单 Keeper 调用（`onlyKeeper`），owner 可更换 Keeper
  - 事件：`OrderExecuted` / `OrderCancelled`
- **测试**：Foundry fork 测试（Base Sepolia 或 Base 主网 fork）：正常成交、签名伪造、过期、重放、滑点不足、非 Keeper 调用。
- **验收**：`forge test` 全绿；部署脚本 `script/Deploy.s.sol`；部署地址写入 `chains.yaml`（新增 `limitOrderExecutor`、`permit2` 字段）。

### B2 数据模型与迁移
- **依赖**：无（可与 B1 并行）
- **目标**：`V6__limit_order.sql`：
  - `limit_order`：id、order_hash（唯一）、chain_id、owner、token_in、token_out、fee、amount_in、min_amount_out、trigger_price、order_type、nonce、deadline、signature、status、retry_count、tx_hash、fail_reason、审计字段
  - `limit_order_event`：状态变更流水（order_id、from_status、to_status、reason、created_at）
  - 索引：`(chain_id, status, token_in, token_out)`（供触发引擎扫描）、`(owner, created_at desc)`
- **验收**：迁移可重复执行于空库；对应 DO / Mapper / XML 完成。

### B3 订单 API 与 EIP-712 验签
- **依赖**：B2
- **目标**：
  - `POST /api/v1/orders`：接收订单 + 签名，服务端用 web3j 验签（恢复地址必须等于 owner），校验 deadline、金额、token 白名单、`amountIn` 不超过 owner 的 Permit2 allowance 与余额，写库为 `OPEN`
  - `GET /api/v1/orders?owner=&status=`、`GET /api/v1/orders/{orderHash}`
  - `DELETE /api/v1/orders/{orderHash}`：需要 owner 对取消消息再签名，状态置 `CANCELLED`
  - 同一 owner 同一 nonce 拒绝重复
- **验收**：伪造签名、过期订单、余额不足、重复 nonce 都返回明确错误码；单测覆盖。

### B4 价格源
- **依赖**：无
- **目标**：`PriceService`：
  - 由 Indexer 的 Swap 事件（`sqrtPriceX96` / 实际成交价）更新各池最新价，Redis 保存 `price:{chainId}:{pool}`（带时间戳）
  - 对外提供 `latest(tokenIn, tokenOut)`；价格超过 N 秒未更新视为过期，不触发订单
  - 价格单位统一：tokenOut / tokenIn，使用 BigDecimal，精度处理要写单测
- **验收**：价格换算对照链上 Quoter 报价误差 < 0.5%（集成测试）。

### B5 触发引擎
- **依赖**：B3、B4
- **目标**：
  - 启动时把 `OPEN` 订单按池子加载到内存索引（TreeMap：按触发价排序）
  - 新订单、取消、过期事件通过 Kafka（`order-events`）同步到索引
  - 价格更新时只检查对应池子的索引，命中则 CAS 把状态 `OPEN → TRIGGERED` 并写 outbox 事件
  - 重启后能从库重建
- **验收**：10 万订单下一次价格更新的匹配耗时 < 5ms（JMH 或单测计时）；重复触发不会产生两次 TRIGGERED。

### B6 Keeper 执行器
- **依赖**：B1、B5
- **目标**：
  - 消费 `TRIGGERED` 事件；执行前 `eth_call` 模拟 `execute`，失败则记录原因并按退避重试，超过 `max-retries` 置 `FAILED`
  - 通过 `ScannerLease` 同款机制保证同一时刻只有一个 Keeper 实例提交
  - Keeper 热钱包私钥通过环境变量注入，只允许在 `signModes` 含 `HOT_WALLET` 的链启用；日志与异常中禁止打印私钥
  - 提交后状态 `SUBMITTED`，记录 `tx_hash`；使用 nonce 管理器避免并发 nonce 冲突；gas 价格有上限
  - 指标：`anchor.order.triggered` / `.submitted` / `.filled` / `.failed`、触发到提交延迟
- **验收**：Base Sepolia 端到端：创建订单 → 价格触发 → 链上成交；模拟失败场景有重试记录。

### B7 成交确认与 reorg 处理
- **依赖**：B6
- **目标**：
  - Indexer 解析 `OrderExecuted` 事件，把订单置 `FILLED`，关联实际成交数量
  - 复用现有确认状态机（CONFIRMING → CONFIRMED → FINALIZED）
  - 发生 reorg 时，受影响的 `SUBMITTED/FILLED` 订单回退到 `TRIGGERED` 并写状态流水
  - 过期扫描任务：到期的 `OPEN` 订单置 `EXPIRED`
- **验收**：reorg 集成测试（沿用现有 reorg 测试夹具）能让订单状态正确回退并重新执行，且不会重复成交（链上 nonce 保证）。

### B8 前端：订单页
- **依赖**：B3
- **目标**：
  - 新增「限价单」Tab：创建订单表单（买/卖、触发价、数量、有效期）、钱包签名（`eth_signTypedData_v4`）、当前订单列表、取消
  - 首次使用时引导 `approve` Permit2
  - WS 订阅订单状态变化实时刷新
- **验收**：Sepolia 上完整操作一遍，截图放入 `docs/`。

### B9 文档与面试材料
- **依赖**：B1–B8
- **目标**：`docs/limit-order-design.md`，含：架构图、状态机、签名结构、安全分析（重放、签名伪造、Keeper 作恶、价格操纵、MEV）、故障场景表。
- **验收**：能独立回答 "非托管怎么保证"、"Keeper 挂了怎么办"、"reorg 怎么处理"。

---

## 阶段 C：RPC 韧性增强

### C1 多节点配置与健康检查
- **依赖**：无
- **目标**：基于现有 `FailoverRpcGateway`：
  - `chains.yaml` 支持配置多个 RPC，并带权重/优先级
  - 节点熔断：连续失败 N 次摘除 T 秒，半开探测恢复
  - 节点落后检测：对比各节点 `eth_blockNumber`，落后超过阈值的节点降权
  - 指标：每节点的可用状态、延迟、错误数
- **验收**：单测模拟节点故障、限流（429）、落后；Grafana 增加「节点健康」面板。

### C2 请求限流与优先级
- **依赖**：C1
- **目标**：扫块、报价、Keeper 提交使用不同优先级队列；Keeper 提交与报价优先于追块。
- **验收**：追块高负载时，报价 P95 不明显恶化（压测对比）。

---

## 阶段 D：持仓与盈亏（PnL）

### D1 持仓模型
- **依赖**：无
- **目标**：`V7__position.sql`：`position`（地址、token、数量、加权平均成本、已实现盈亏）、`position_lot` 或流水表；由 Swap 事件幂等驱动更新。
- **验收**：同一事件重放不重复计算；reorg 时回滚相应流水。

### D2 价格路径与估值
- **依赖**：B4
- **目标**：Token → WETH → USDC 的价格路径；估值精度 BigDecimal；无路径的 Token 标记为"无法估值"而不是 0。

### D3 PnL API 与前端
- **依赖**：D1、D2
- **目标**：`GET /api/v1/positions?address=`，返回持仓、成本、未实现/已实现盈亏、占比；前端新增「持仓」页。

---

## 阶段 E：代币风险检测

### E1 链上静态检查
- **目标**：读取 owner、是否可增发、黑名单/暂停函数、是否代理合约，缓存到 Redis。
### E2 交易模拟检测
- **目标**：`eth_call` 模拟买入再卖出，识别貔貅盘；计算买卖税率。
### E3 集成
- **目标**：报价和 prepare 接口返回风险等级与理由；前端兑换面板展示警告；高风险需二次确认。

---

## 阶段 F / G / H（待启动前再细化）

- **F 第二条 EVM 链**：新增 BSC 或 Arbitrum 的 `chains.yaml` 配置与 DEX 适配，验证抽象；重点验证不同出块时间、确认深度与 gas 模型。
- **G Telegram Bot**：监控地址有成交时推送，复用 Kafka 事件流，独立消费组。
- **H Go 版 / Solana**：待 Java 版业务形态稳定后，参照 `docs/backend-design.md` 的接口契约另起 `backend-go/`。

---

## 3. 里程碑检查点

| 里程碑 | 完成标志 | 此时可以做的事 |
|---|---|---|
| M1 | A1–A4 完成，有压测报告 | 简历写入量化数据 |
| M2 | B1–B9 完成，Sepolia 端到端跑通 | 开始投简历 |
| M3 | C、D 完成 | 面试中补强 |

## 4. 已知前置遗留

- `max-catchup-blocks`（默认 2000）已落地：落后过多时 `BlockScanner.applyMaxCatchup` 跳到 `head - N` 并 WARN 跳过区间；运维需观察跳块后首块 `parentMismatch` 是否误触发 reorg/暂停。
- `chains.yaml` 目前每条链只配了一个 RPC，C1 之前先接入一个备用节点。
