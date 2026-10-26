package com.anchor.common.chain;

import java.util.List;

public record ChainBlock(
        long number,
        String hash,
        String parentHash,
        long timestamp,
        List<ChainTransaction> transactions) {
}
