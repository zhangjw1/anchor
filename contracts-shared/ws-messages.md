# WebSocket 消息

P2 再实现推送。P1 没有 WebSocket 端点。连接建立后，客户端发送订阅，服务端推送下面三类消息。字段名 camelCase，金额是最小单位十进制字符串。

计划中的路径：`/ws`。

## 客户端订阅

```json
{
  "action": "subscribe",
  "channel": "swaps",
  "chainId": 8453,
  "address": "0xabc"
}
```

`channel` 预留 `swaps`、`tx`、`metrics`。`tx` 用 `txHash` 订阅单笔交易，`metrics` 不带地址。

## 服务端消息

新 Swap：

```json
{
  "channel": "swaps",
  "type": "SWAP_CREATED",
  "sequence": 1,
  "chainId": 8453,
  "txHash": "0x123",
  "logIndex": 12,
  "confirmStatus": "CONFIRMING"
}
```

确认变化与回滚使用 `type` = `CONFIRMATION` 或 `REORG`。断线重连时客户端带上最后的 `sequence`，补发留到 P2。
