package com.anchor.common.chain;

import java.math.BigInteger;
import java.util.List;

/**
 * 交易回执。success 为 false 时不解析交换。
 */
public record ChainReceipt(
        String transactionHash,
        boolean success,
        BigInteger gasUsed,
        List<ChainLog> logs) {
}
