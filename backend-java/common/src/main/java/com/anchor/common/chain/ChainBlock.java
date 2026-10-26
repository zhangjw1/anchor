package com.anchor.common.chain;

import java.util.List;

/**
 * 一个区块及其交易。timestamp 是秒。
 */
public record ChainBlock(
        long number,
        String hash,
        String parentHash,
        long timestamp,
        List<ChainTransaction> transactions) {
}
