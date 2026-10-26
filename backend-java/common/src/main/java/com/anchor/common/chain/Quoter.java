package com.anchor.common.chain;

import java.math.BigInteger;

/** 报价、交易、签名在 P4/P5 实现，这里只固定跨语言都能对齐的方法边界。 */
public interface Quoter {
    /**
     * 按输入数量估算输出。当前只定义边界，报价尚未实现。
     */
    Quote quote(QuoteRequest request);

    record QuoteRequest(long chainId, String tokenIn, String tokenOut, BigInteger amountIn, int slippageBps) {
    }

    record Quote(BigInteger amountOut, BigInteger amountOutMinimum, String feeTier) {
    }
}
