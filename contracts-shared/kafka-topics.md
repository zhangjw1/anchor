# Kafka 主题

P1 不连接 Kafka。扫描器解析成功后直接写入 PostgreSQL，再推进 checkpoint。下面的格式留给 P2，Java 与 Go 共用。消息体是 JSON，key 和 value 都使用 UTF-8 字符串。

## raw-blocks

| 项 | 值 |
| --- | --- |
| 名称 | `raw-blocks` |
| key | `chainId` 的十进制字符串。同一条链进同一个分区，保证块顺序 |
| 生产 | indexer，P2 |
| 消费 | parser，P2 |

投递成功之后才允许推进 checkpoint。无命中交易的块不发消息。

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

P2 由解析器在落库后生产。P1 不生产。

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

P2 由确认模块生产。P1 不生产。

| 项 | 值 |
| --- | --- |
| 名称 | `chain-events` |
| key | `chainId` |

```json
{
  "type": "CONFIRMATION",
  "chainId": 8453,
  "blockNumber": 21432918,
  "txHash": "0x123",
  "logIndex": 12,
  "confirmStatus": "CONFIRMED",
  "confirmations": 10
}
```

`type` 还预留 `REORG`。回滚时带 `depth` 和受影响的起始块号，具体字段在 P2 实现前再补进本文件，不在 P1 生产。
