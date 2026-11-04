package com.anchor.api.service;

import com.anchor.common.chain.Quoter;
import com.anchor.common.chain.StoredQuote;

import java.math.BigInteger;

/** 对外报价，含限流与展示字段补齐。 */
public interface QuoteQueryService {

    QuoteView quote(long chainId, String tokenIn, String tokenOut, BigInteger amountIn, int slippageBps, String clientIp);

    /** 按报价单号读取；不存在或损坏时返回 null。 */
    StoredQuote findStored(String quoteId);

    record QuoteView(
            Quoter.Quote quote,
            String tokenIn,
            String tokenInSymbol,
            Integer tokenInDecimals,
            String tokenOut,
            String tokenOutSymbol,
            Integer tokenOutDecimals,
            BigInteger amountIn,
            int slippageBps,
            boolean nativeIn,
            boolean nativeOut) {
    }
}
