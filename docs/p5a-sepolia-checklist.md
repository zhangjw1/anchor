# P5a Base Sepolia 联调清单

概念与端到端流程（询价、prepare、allowance、SwapRouter、eth_call 模拟等）见 [swap-trade-flow.md](./swap-trade-flow.md)。

## 前置

1. Docker：Postgres + Redis 已起（`deploy/docker-compose.yml`）。
2. API：`backend-java` 下 `mvnw -pl api spring-boot:run -Dspring-boot.run.arguments=--anchor.kafka.enabled=false`。
3. 前端：`.env.local` 设 `VITE_USE_MOCK=false`，`npm run dev`。
4. 钱包切到 **Base Sepolia (84532)**，准备测试 ETH。

## 步骤

1. 页面顶部网络选 **Sepolia**，Connect 钱包。
2. Swap 输入数量（建议 WETH→其他有流动性的币；Sepolia 池子可能稀少）。
3. 确认出现 live 报价与 `quoteId` 路由。
4. 点兑换：应弹出 MetaMask（如需先 approve 再 swap）。
5. 步骤条：模拟 → 签名 → 广播 hash → Pending → 已确认。
6. 主网链上点兑换：应提示仅 Sepolia / 或 prepare 返回 `UNSUPPORTED`。

## 接口自测

```bash
# 先 quotes 拿到 quoteId（chainId=84532）
# POST /api/v1/trades/prepare { quoteId, userAddress }
# 钱包发送后 POST /api/v1/trades/report
# GET /api/v1/trades/{txHash}?chainId=84532
```
