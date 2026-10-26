# Kafka 主题

P2 生产 `swap-events` 和 `chain-events`。解析仍在 indexer 进程内完成，落库并发送成功后才推进 checkpoint。`raw-blocks` 只保留格式，本期不生产、不消费。消息体是 JSON，key 和 value 都使用 UTF-8 字符串。Java 与 Go 共用。

## raw-blocks

| 项 | 值 |
| --- | --- |
| 名称 | `raw-blocks` |
| key | `chainId` 的十进制字符串。同一条链进同一个分区，保证块顺序 |
| 生产 | 未实现 |
| 消费 | 未实现 |

无命中交易的块不发消息。下面的格式先留着，等解析器拆成独立消费者再用。

```json
{
  "chainId": 8453,
  "blockNumber": 21432908,
  "blockHash": "0xabc",
  "parentHash": "0xdef",
  "timestamp": 1760000000,
  "transactions": [
    {
      "hash": "0x123",
      "from": "0xabc",
      "to": "0xdef",
      "value": "1000000000000000",
      "input": "0x",
      "status": "0x1",
      "logs": [
        {
          "address": "0xpool",
          "topics": ["0xtopic"],
          "data": "0x",
          "logIndex": 12
        }
      ]
    }
  ]
}
```

`value` 与日志里的金额是最小单位十进制字符串。地址小写。

## swap-events

indexer 在 Swap 落库后生产。新入库和回滚后再次入库都发。

| 项 | 值 |
| --- | --- |
| 名称 | `swap-events` |
| key | `chainId` |

```json
{
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

## chain-events

indexer 的确认模块生产。`FINALIZED` 之后不再发送。

| 项 | 值 |
| --- | --- |
| 名称 | `chain-events` |
| key | `chainId` |

确认数变化或状态变化：

```json
{
  "type": "CONFIRMATION",
  "chainId": 8453,
  "blockNumber": 21432918,
  "txHash": "0x123",
  "logIndex": 12,
  "confirmStatus": "CONFIRMED",
  "confirmations": 10,
  "watchedAddress": "0xabc"
}
```

`blockNumber` 是触发这次计算的已扫描块。`confirmations` = 该块号 − Swap 所在块号 + 1。`watchedAddress` 用于按地址过滤。

回滚：

```json
{
  "type": "REORG",
  "chainId": 8453,
  "depth": 2,
  "ancestorBlock": 21432908,
  "fromBlock": 21432909,
  "toBlock": 21432910
}
```

`depth` = 回退前的 checkpoint − `ancestorBlock`。受影响的是 `fromBlock` 到 `toBlock`，含两端。这些块上的 Swap 变为 `REORGED`，checkpoint 退到 `ancestorBlock`。
