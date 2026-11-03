# chains.yaml 字段说明

配置文件：[`contracts-shared/chains.yaml`](../contracts-shared/chains.yaml)  
加载代码：`YamlChainRegistry` → `ChainDefinition`  
约定：**合约地址只从本文件读取，业务代码禁止硬编码。**

路径由 `anchor.chains-file` 指定（默认指向仓库内该文件）；当前启用哪条链由 `anchor.chain-key` 决定（如 `base`）。

---

## 顶层

| 字段 | 含义 |
|------|------|
| `chains` | 链列表。每一项描述一条可运行的链（主网 / 测试网各一条配置）。 |

---

## 链基本信息

| 字段 | 示例 | 含义 |
|------|------|------|
| `key` | `base` | 逻辑键，配置与日志里用这个名字引用链，对应 `ANCHOR_CHAIN_KEY`。 |
| `chainId` | `8453` | EVM 链 ID，API 入参 `chainId`、入库、RPC 路由都靠它。 |
| `name` | `Base` | 展示名称。 |
| `type` | `EVM` | 链类型。当前只实现 EVM；预留扩展（如未来 Solana）。 |
| `testnet` | `false` | 是否测试网。影响运维预期；热钱包等危险能力通常只开在测试网。 |
| `confirmationBlocks` | `10` | **确认档**所需的区块数：头块高度 − 交易块号 + 1 ≥ 该值 → Swap 标为 `CONFIRMED`。另有 `FINALIZED` 看链的 finalized 头，不由此字段单独决定。 |
| `gasModel` | `EIP1559_OP_L1_FEE` | 手续费模型。`EIP1559` 普通 EIP-1559；`EIP1559_OP_L1_FEE` 表示还要算 OP Stack 的 **L1 data fee**（Base 属于这类）。 |
| `signModes` | `[CLIENT]` | 允许的签名方式。`CLIENT`：用户钱包签；`HOT_WALLET`：服务端热钱包签（仅声明允许的链可用，一般只给测试网）。 |

---

## nativeToken（原生币）

EVM 原生币没有 ERC-20 合约，用占位地址表示，便于和代币地址同一套逻辑处理。

| 字段 | 含义 |
|------|------|
| `symbol` | 符号，如 `ETH`。 |
| `decimals` | 精度，通常 `18`。 |
| `address` | 占位地址，本项目用 `0xeeee…eeee`。查余额走 `eth_getBalance` / Multicall3 `getEthBalance`，不走 `balanceOf`。 |

---

## rpc（节点列表）

| 字段 | 含义 |
|------|------|
| `url` | HTTP JSON-RPC 地址。 |
| `requestsPerSecond` | 该节点客户端侧限速（令牌桶）。多节点会按 Failover 顺序尝试；**不要把同一公共 URL 写两遍**，限速会叠加算错。 |

可配置多个节点：主用公共 Base RPC，备用可加 Alchemy / QuickNode 等。

---

## contracts（协议与基础设施地址）

全部为该链上的合约地址；缺省字段表示本链暂未配置（如 base-sepolia 未写全 USDC / Aerodrome）。

| 字段 | 谁 | 本系统用途（简述） |
|------|----|-------------------|
| `weth` | WETH9 | 包装 ETH；计价时常把 ETH 价格映射到 WETH 池；资产默认列表。 |
| `usdc` | 原生 USDC | 稳定币计价、资产默认列表、买卖方向判断的报价币之一。 |
| `usdbc` | 桥接 USDC（旧） | 报价币 / 资产列表扩展；部分池仍用。 |
| `multicall3` | Multicall3 | **资产接口**批量 `balanceOf` + `getEthBalance`。 |
| `gasPriceOracle` | OP GasPriceOracle | 报价时估算 **L1 data fee**（`getL1Fee`）。 |
| `uniswapV3Factory` | Uniswap V3 Factory | 校验 V3 池是否官方工厂创建的；池价 / 解析。 |
| `quoterV2` | Uniswap QuoterV2 | **报价接口**：多费率档 `eth_call` 模拟兑换。 |
| `swapRouter02` | SwapRouter02 | P5 兑换组交易（exactInputSingle 等）。 |
| `uniswapV2Factory` | Uniswap V2 Factory | V2 池归属校验与解析。 |
| `uniswapV2Router` | Uniswap V2 Router | V2 路由相关（解析/后续交易）。 |
| `aerodromeRouter` | Aerodrome Router | Base 主流 DEX 路由。 |
| `aerodromeFactory` | Aerodrome 经典池 Factory | 经典池 Swap 解析与工厂校验。 |
| `aerodromeSlipstreamFactory` | Slipstream（CL）Factory | 集中流动性池解析与工厂校验。 |

ABI 不在本文件：见 `backend-java/contracts/abi/*.json`。本文件只解决「**调哪个地址**」。

---

## quoteTokens（报价币集合）

用于 **Swap 方向判断**（以及资产默认代币名单的一部分）：

- 付出报价币、收到非报价币 → 偏 **BUY**
- 付出非报价币、收到报价币 → 偏 **SELL**
- 两边都是或都不是 → **SWAP**

通常包含：原生占位、WETH、USDC、USDbC 等。

`/api/v1/assets` 在未传 `tokens` 时，也会把这里的地址（再加 `weth`/`usdc` 等）纳入默认查询列表。

---

## aggregators（聚合器 Router）

| 字段 | 含义 |
|------|------|
| `name` | 展示名，如 `Uniswap UniversalRouter`、`Odos V2 Router`。 |
| `address` | 聚合器路由合约。 |

解析 Swap 时：若交易的 `tx.to` 命中该列表，则给记录打上 **aggregator** 标签（多跳常经聚合器进池）。

---

## 和代码的对应关系（速查）

```text
chains.yaml
    │
    ▼
YamlChainRegistry 读入
    │
    ▼
ChainDefinition（内存模型）
    │
    ├─ rpc          → FailoverRpcGateway（限速 / 切换节点）
    ├─ multicall3   → MulticallClient → /api/v1/assets
    ├─ quoterV2 + gasPriceOracle → 报价服务
    ├─ *Factory / Router → Parser 校验与路径识别
    ├─ quoteTokens  → 买卖方向；资产默认列表
    └─ aggregators  → 聚合器打标
```

---

## 维护注意

1. 改地址前核对官方部署文档或区块浏览器 Verified 合约。  
2. 测试网字段可少于主网；缺的能力对应功能会不可用或跳过。  
3. ABI 变更走 `contracts/abi`，地址变更只改本 yaml。  
4. 文件顶部的 TODO 表示「已知要补但尚未核实写入」的项，不要把未核实地址先写进去。
