# Cursor 提示词：Anchor 后端（Java 21 版）

> 使用方式：先把「主提示词」整段发给 Cursor（Agent 模式），它只会完成 P0 + P1。验收通过后，再按顺序发送「后续阶段提示词」。

---

## 主提示词

```text
你是一名资深 Java 后端 + Web3 工程师。请根据 docs/backend-design.md 实现 Anchor 项目的后端（Java 21 版本）。
开始前先完整阅读 docs/backend-design.md 和 front_end/ 目录，理解页面区域与后端模块的对应关系。设计文档是唯一需求来源，如有冲突以设计文档为准；遇到文档没覆盖的决策，先列出来问我，不要自行扩大范围。

# 一、背景
- 项目：Base 链 DEX Swap 监控 + 兑换面板。前端已用 mock 数据完成（front_end/，Next.js，localhost:3000），本次不改前端。
- 第一版用 Java 21 实现，之后会用 Go 再实现一个功能完全相同的版本。因此必须让「对外契约」与语言无关（见第四节）。

# 二、技术栈（固定，不要替换）
- Java 21，Spring Boot 3.3.x，Maven 多模块（父 POM 统一管理依赖版本，使用 Maven Wrapper ./mvnw）
- 开启虚拟线程：spring.threads.virtual.enabled=true；RPC 并发调用使用虚拟线程 + 结构化并发或 CompletableFuture
- 链交互：web3j 4.x（ABI 使用 web3j-maven-plugin 在构建时从 backend-java/contracts/abi/*.json 生成包装类到 target/generated-sources，不手写 ABI 编码、不提交生成代码）
- 数据库：PostgreSQL 16；表结构由 Flyway 管理（spring.flyway.locations 指向根目录 db/migrations）
- 持久层：MyBatis-Plus（mybatis-plus-spring-boot3-starter），不用 JPA，规则：
  - 单表简单 CRUD（地址簿、Token 元数据、checkpoint）可用 BaseMapper / IService
  - 批量写入、Upsert（INSERT ... ON CONFLICT ... DO UPDATE/NOTHING）、多条件分页筛选、统计等复杂 SQL 一律写在 Mapper XML 中，禁止用字符串拼接 SQL
  - 分页使用 MyBatis-Plus 分页插件（DbType.POSTGRE_SQL）
  - 关闭 MyBatis-Plus 的自动建表/逻辑删除等隐式行为，表结构只以 Flyway 脚本为准
  - 自定义 TypeHandler：NUMERIC(78,0) ↔ BigInteger；JSONB ↔ Java 对象（多跳路径等）
  - 批量插入使用 XML foreach 或 ExecutorType.BATCH，单批不超过 500 条
- 缓存：Redis 7（Spring Data Redis / Lettuce）
- 消息：Kafka（Spring Kafka），本地用 Redpanda 或 apache/kafka KRaft 镜像
- 推送：Spring WebSocket（原生 WebSocket，不用 STOMP）
- 指标：Micrometer + Prometheus
- 测试：JUnit 5 + Testcontainers（PG/Redis/Kafka）
- 部署：deploy/docker-compose.yml 一键启动 PG、Redis、Kafka、Prometheus、Grafana

# 三、工程结构
在项目根目录创建 backend-java/，结构如下：

backend-java/
├─ pom.xml        # 父 POM（packaging=pom，dependencyManagement 引入 spring-boot-dependencies）
├─ mvnw / .mvn/
├─ common/        # 领域模型、链注册表、RPC 网关、存储封装、接口定义（ChainScanner、SwapParser、Quoter、TxBuilder、TxTracker、Signer）
├─ indexer/       # 可执行：区块扫描器 + 确认与 Reorg
├─ parser/        # 可执行：Kafka 消费 + Swap 解析 + 落库
├─ api/           # 可执行：REST + WebSocket + 地址簿/资产/报价/交易/指标
└─ contracts/abi/ # ABI JSON

根目录（与语言无关，Go 版本会复用）：
contracts-shared/
├─ openapi.yaml           # REST 契约
├─ ws-messages.md         # WebSocket 消息格式（JSON Schema 或示例）
├─ kafka-topics.md        # topic 名称、key、消息 JSON 格式
└─ chains.yaml            # 链注册表配置
db/migrations/            # Flyway SQL（V1__init.sql …），Java 与 Go 共用同一套 SQL

# 四、语言无关契约（重要）
1. REST 先写 contracts-shared/openapi.yaml，再实现 Controller；字段命名 camelCase，金额一律用字符串表示最小单位整数（如 "1000000"），另给 decimals。
2. Kafka 消息为 JSON（不要用 Java 序列化），格式写进 kafka-topics.md。
3. 数据库 schema 只通过 db/migrations 下的纯 SQL 维护（Flyway 命名 V{n}__描述.sql，已执行的脚本不得修改，只能新增），不依赖任何 ORM 自动建表。
4. 链注册表从 contracts-shared/chains.yaml 读取，代码中不允许硬编码合约地址。
5. 业务代码通过 common 中的接口调用链能力，业务层不直接暴露 web3j 类型（为后续 Solana 预留，见设计文档 7.3）。

# 五、通用约束
- 金额全程使用 BigInteger，数据库用 NUMERIC(78,0)，禁止 double/float 参与金额计算；展示用 BigDecimal 按 decimals 换算。
- 地址统一存小写字符串；地址、交易哈希字段不假设 0x 格式长度（为 Solana 预留）。
- 确认状态枚举：CONFIRMING / CONFIRMED / FINALIZED / REORGED。
- 幂等键 (chain_id, tx_hash, log_index)，使用 INSERT ... ON CONFLICT。
- 先投递 Kafka 成功再推进 checkpoint（at-least-once），消费端幂等。
- RPC 网关：多节点、令牌桶限速、超时、指数退避重试、故障切换，统计 QPS/错误率/延迟。
- 配置全部走 application.yml + 环境变量，私钥只能来自环境变量，不得写入代码、日志、数据库。
- 日志为 JSON 结构化，包含 chainId、blockNumber、txHash。
- 每个模块写单元测试；解析器必须用真实 Base 主网交易回执做测试夹具（把 receipt JSON 存到 test/resources）。

# 六、链注册表初始内容（chains.yaml）
- base（chainId 8453），RPC：https://mainnet.base.org（预留多个节点位置）
- base-sepolia（chainId 84532），RPC：https://sepolia.base.org
- 每条链包含：类型(EVM)、是否测试网、RPC 列表与限速、WETH、USDC、Uniswap V3 Factory / QuoterV2 / SwapRouter02、Uniswap V2 Factory/Router、Aerodrome Router/Factory、Multicall3、GasPriceOracle、计价币列表、聚合器 Router 列表、确认块数（base 10 / base-sepolia 5）、Gas 模型（eip1559+op-l1-fee）、允许的签名模式（base 仅 CLIENT，base-sepolia CLIENT+HOT_WALLET）。
- 参考地址（必须到 Uniswap / Base / Aerodrome 官方文档逐一核实后再写入，核实不了的标 TODO 并告诉我）：
  - WETH 0x4200000000000000000000000000000000000006
  - USDC(Base) 0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913
  - Multicall3 0xcA11bde05977b3631167028862bE2a173976CA11
  - GasPriceOracle 0x420000000000000000000000000000000000000F
  - Uniswap V3 Factory(Base) 0x33128a8fC17869897dcE68Ed026d694621f6FDfD
  - QuoterV2(Base) 0x3d4e44Eb1374240CE5F1B871ab261CD16335B76a
  - SwapRouter02(Base) 0x2626664c2603336E57B271c5C0b26F421741e481

# 七、本次任务范围：只做 P0 + P1
P0 工程骨架：
- Maven 多模块、docker-compose、Flyway 初始化、链注册表加载、JSON 日志、健康检查。
P1 最小闭环：
- RPC 网关（设计文档模块 1）
- 区块扫描器：checkpoint 断点续扫、parentHash 记录（Reorg 处理放到 P2，但 P1 要存最近 N 块 hash）、按 tx.from ∈ 监控地址过滤（地址集合从 Redis 读取）、批量拉回执、投递 Kafka raw-blocks
- Swap 解析器：只实现 Uniswap V3；净流向按 ERC20 Transfer 计算（设计文档 4.1），ETH 用 tx.value 与 WETH Withdrawal 补齐；方向规则 BUY/SELL/SWAP；Token 元数据(symbol/decimals)懒加载 + Redis 缓存
- 地址簿：增删改查、标签、Swap 次数/最近活跃统计，变更同步到 Redis 集合
- 查询服务：Swap 列表分页，按地址/DEX/方向筛选
- 先更新 openapi.yaml、kafka-topics.md，再写实现

# 八、验收标准（完成后逐条自测并汇报结果）
1. docker compose up -d 后，三个服务可启动，/actuator/health 为 UP。
2. 通过 API 添加一个在 Base 主网活跃交易的地址后，indexer 从最新块开始扫描，parser 能把该地址的 Uniswap V3 Swap 写入数据库。
3. GET Swap 列表接口能返回正确的 tokenIn/tokenOut、金额、方向、区块号、txHash。
4. 重启 indexer 后从 checkpoint 继续，不重复、不遗漏（重复消费不产生重复记录）。
5. 解析器单元测试覆盖：单跳 V3、经 Router 的 V3、ETH 作为输入、ETH 作为输出 四种情况。
6. ./mvnw clean verify 全部通过。

# 九、工作方式
- 先输出实施计划（文件清单 + 步骤），等我确认后再写代码。
- 每完成一个子模块就运行测试，失败自行修复后再继续。
- 完成后输出：做了什么、如何运行、验收结果、已知问题、P2 建议。
- 不要实现 P2 及之后的功能，不要修改 front_end/ 和 docs/backend-design.md。
```

---

## 后续阶段提示词

### P2：确认、Reorg、WebSocket
```text
继续按 docs/backend-design.md 实现 P2，沿用已有工程与约束：
1. 确认与 Reorg（设计文档 4.2 与 6.2）：两级确认，达到 chains.yaml 配置块数置 CONFIRMED，块号 ≤ finalized 块号置 FINALIZED；parentHash 不匹配时回溯到共同祖先，受影响 Swap 置 REORGED，checkpoint 回退重扫；Reorg 次数与深度计入指标。
2. Kafka topics：swap-events、chain-events，先更新 kafka-topics.md。
3. WebSocket 推送网关：订阅（按地址 / txHash / 指标）、心跳、带事件序号断线补发；先写 contracts-shared/ws-messages.md。
4. 测试：用模拟 RPC 构造一次 2 块深度的 Reorg，验证回滚与重扫正确。
先出计划等我确认。
```

### P3：更多 DEX
```text
实现 P3：在 SwapParser 接口下新增 Uniswap V2、Aerodrome（经典池 + Slipstream CL 池分别实现）、多跳路径（同一交易多个 Swap 事件串成路径，存 swap_hop）、聚合器识别（tx.to 命中 chains.yaml 中的聚合器 Router 打标签）。
池合约必须通过对应 Factory 校验，防伪造池。每种情况都要有真实 Base 主网回执做测试夹具。先出计划。
```

### P4：资产 + 报价
```text
实现 P4：
1. 资产服务：Multicall3 批量查询 ETH + ERC20 余额；价格从 WETH/USDC 流动性足够的池子推导，流动性不足返回 null；净值汇总；Redis 短缓存。
2. 报价服务：QuoterV2 对 100/500/3000/10000 四个费率档并发 eth_call，选最优；计算价格影响、按滑点计算 amountOutMinimum；Gas 估算包含 OP Stack L1 data fee（GasPriceOracle）；返回报价有效期；按 IP 限流。
先更新 openapi.yaml，再出计划。
```

### P5a：交易服务 + 模式 A
```text
实现 P5a（设计文档 6.4、6.6）：
1. 在 common 定义 Signer 接口，本阶段实现 ClientSigner。
2. 交易服务：allowance 检查并构建 approve（默认精确数量）、构建 SwapRouter02 exactInputSingle calldata（必须带 amountOutMinimum 与 deadline）、eth_call 模拟 + estimateGas（失败返回 revert 原因）、接收前端上报 txHash、追踪 PENDING/CONFIRMED/FAILED/REPLACED 并通过 WebSocket 推送。
3. Router、Token 地址只从 chains.yaml 读取，不接受前端传入。
4. 只在 base-sepolia 允许真实发送。先出计划。
```

### P5b：模式 B
```text
实现 P5b：
1. HotWalletSigner：私钥来自环境变量，仅 chains.yaml 中允许 HOT_WALLET 的链可用，否则拒绝请求。
2. Nonce 管理器：Redis 分布式锁 + 本地递增 + 启动/失败时与链上 pending nonce 校准；支持并发提交。
3. 加速（同 nonce 提高 gas 重发）与取消（同 nonce 给自己转 0）。
4. 请求中增加签名模式参数，更新 openapi.yaml。
5. 测试：并发提交 5 笔交易，nonce 连续且无冲突。先出计划。
```

### P6：指标
```text
实现 P6：按设计文档第 10 节暴露 Prometheus 指标，并通过 WebSocket 每 3 秒推送状态栏所需指标（最新块、延迟块数、Kafka lag、RPC QPS/错误率、Reorg 次数）。在 deploy/ 下提供 Grafana 面板 JSON 与自动导入配置。先出计划。
```

### 前端对接（任一阶段完成后可做）
```text
根据 contracts-shared/openapi.yaml 与 ws-messages.md，把 front_end/ 中对应区域的 mock 数据替换为真实接口，保留 mock 开关（环境变量）便于离线演示。钱包使用 wagmi + viem + RainbowKit 替换 mock 连接。只改数据层，不改页面样式。先出计划。
```

---

## 之后做 Go 版本时
```text
参考 backend-java/ 的实现与 docs/backend-design.md，在 backend-go/ 用 Go 1.23 + go-ethereum(abigen) 实现功能完全相同的后端。
必须复用 contracts-shared/（openapi.yaml、ws-messages.md、kafka-topics.md、chains.yaml）和 db/migrations/，不得修改这些契约；
前端无需任何改动即可切换到 Go 后端。可以与 Java 版共用同一套 docker-compose 基础设施。
```
