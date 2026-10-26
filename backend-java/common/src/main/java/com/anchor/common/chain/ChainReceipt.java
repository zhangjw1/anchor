package com.anchor.common.chain;

import java.math.BigInteger;
import java.util.List;

public record ChainReceipt(
        String transactionHash,
        boolean success,
        BigInteger gasUsed,
        List<ChainLog> logs) {
}
