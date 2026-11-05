package com.anchor.api.vo;

/**
 * 状态栏指标（REST 与 WS METRICS 字段对齐）。
 */
public record ChainMetricsVO(
        long chainId,
        long headBlock,
        long processedBlock,
        long lagBlocks,
        double rpcQps,
        double rpcErrorRate,
        Long kafkaLag,
        boolean kafkaStale,
        long outboxPending,
        long reorgCount,
        long reorgDepth) {
}
