package com.anchor.common.chain;

import java.math.BigInteger;

/** 报价、交易、签名在 P4/P5 实现，这里只固定跨语言都能对齐的方法边界。 */
public interface Quoter {
    Quote quote(QuoteRequest request);

    record QuoteRequest(long chainId, String tokenIn, String tokenOut, BigInteger amountIn, int slippageBps) {
    }

    record Quote(BigInteger amountOut, BigInteger amountOutMinimum, String feeTier) {
    }
}
