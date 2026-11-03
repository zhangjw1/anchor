package com.anchor.api.service;

import com.anchor.common.chain.Quoter;

import java.math.BigInteger;

/** 对外报价，含限流与展示字段补齐。 */
public interface QuoteQueryService {

    QuoteView quote(long chainId, String tokenIn, String tokenOut, BigInteger amountIn, int slippageBps, String clientIp);

    record QuoteView(
            Quoter.Quote quote,
            String tokenIn,
            String tokenInSymbol,
            Integer tokenInDecimals,
            String tokenOut,
            String tokenOutSymbol,
            Integer tokenOutDecimals,
            BigInteger amountIn) {
    }
}
