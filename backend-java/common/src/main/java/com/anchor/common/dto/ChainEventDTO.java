package com.anchor.common.dto;

import com.anchor.common.constant.KafkaConstants;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 确认进度或重组事件。两种类型不用的字段留空。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChainEventDTO(String type, long chainId, Long blockNumber, String txHash, Integer logIndex,
                         String confirmStatus, Integer confirmations, String watchedAddress, Integer depth,
                         Long ancestorBlock, Long fromBlock, Long toBlock) {
    /**
     * 一笔交换的新确认状态。
     */
    public static ChainEventDTO confirmation(long chainId, long head, String txHash, int logIndex, String confirmStatus,
                                          int confirmations, String watchedAddress) {
        return new ChainEventDTO(KafkaConstants.CONFIRMATION, chainId, head, txHash, logIndex, confirmStatus, confirmations,
                watchedAddress, null, null, null, null);
    }
    /**
     * 一次重组影响的高度区间。
     */
    public static ChainEventDTO reorg(long chainId, int depth, long ancestorBlock, long fromBlock, long toBlock) {
        return new ChainEventDTO(KafkaConstants.REORG, chainId, null, null, null, null, null, null, depth, ancestorBlock,
                fromBlock, toBlock);
    }
}
