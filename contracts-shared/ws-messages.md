# WebSocket 消息

API 提供 `/ws`。字段名 camelCase，金额是最小单位十进制字符串，地址小写。用户主动查询仍走 REST。

## 客户端

订阅：

```json
{"action": "subscribe", "channel": "swaps", "chainId": 8453, "address": "0xabc"}
```

```json
{"action": "subscribe", "channel": "tx", "chainId": 8453, "txHash": "0x123"}
```

```json
{"action": "subscribe", "channel": "metrics", "chainId": 8453}
```

`swaps` 必须带 `address`。`tx` 必须带 `txHash`。`metrics` 只带 `chainId`。

断线补发：

```json
{"action": "resume", "channel": "swaps", "chainId": 8453, "address": "0xabc", "sequence": 12}
```

`sequence` 是该连接最后收到的事件序号。服务端补发更大的序号。心跳应答可选：

```json
{"action": "pong"}
```

## 序号与补发

`SWAP_CREATED`、`CONFIRMATION`、`REORG` 按 `chainId` 从 1 递增，三种消息共用一条序号。内存里每条链保留最近 1000 条。`METRICS` 和 `PING` 不占用序号，也不进补发缓冲。

`resume` 的序号仍在缓冲里时，按原顺序补发。序号已经滑出缓冲，或 API 重启后缓冲为空，服务端发送：

```json
{"type": "RESYNC", "channel": "swaps", "chainId": 8453, "message": "序号已过期，请重新拉取 REST"}
```

客户端应重新请求 REST，再从新的序号继续。

参数不合法时：

```json
{"type": "ERROR", "message": "地址格式不正确"}
```

## 服务端推送

心跳每 15 秒一次，不要求客户端回复：

```json
{"type": "PING"}
```

新 Swap，来自 `swap-events`：

```json
{
  "channel": "swaps",
  "type": "SWAP_CREATED",
  "sequence": 1,
  "chainId": 8453,
  "txHash": "0x123",
  "logIndex": 12,
  "blockNumber": 21432908,
  "direction": "BUY",
  "dex": "Uniswap V3",
  "tokenIn": "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
  "tokenOut": "0x4200000000000000000000000000000000000006",
  "amountIn": "1000000",
  "amountOut": "500000000000000",
  "confirmStatus": "CONFIRMING",
  "watchedAddress": "0xabc"
}
```

确认变化，来自 `chain-events`：

```json
{
  "channel": "swaps",
  "type": "CONFIRMATION",
  "sequence": 2,
  "chainId": 8453,
  "blockNumber": 21432918,
  "txHash": "0x123",
  "logIndex": 12,
  "confirmStatus": "CONFIRMED",
  "confirmations": 10,
  "watchedAddress": "0xabc"
}
```

回滚会推给该链全部 `swaps` 和 `tx` 订阅：

```json
{
  "channel": "swaps",
  "type": "REORG",
  "sequence": 3,
  "chainId": 8453,
  "depth": 2,
  "ancestorBlock": 21432908,
  "fromBlock": 21432909,
  "toBlock": 21432910
}
```

`swaps` 只收到 `watchedAddress` 匹配的成交和确认。`tx` 只收到 `txHash` 匹配的成交和确认。

指标每 3 秒一次。`blockNumber` 是 checkpoint，`reorgCount` 是累计次数，`reorgDepth` 是最近一次深度：

```json
{
  "channel": "metrics",
  "type": "METRICS",
  "chainId": 8453,
  "blockNumber": 21432918,
  "reorgCount": 1,
  "reorgDepth": 2
}
```
