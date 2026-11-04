package com.anchor.common.chain;

import java.math.BigInteger;

/**
 * 按 quoteId 落 Redis 的完整报价，供 TxBuilder 组装兑换交易。
 */
public record StoredQuote(
        String quoteId,
        long chainId,
        String tokenIn,
        String tokenOut,
        boolean nativeIn,
        boolean nativeOut,
        BigInteger amountIn,
        BigInteger amountOut,
        BigInteger amountOutMinimum,
        int fee,
        int slippageBps,
        long expiresAtEpochMs) {
}
