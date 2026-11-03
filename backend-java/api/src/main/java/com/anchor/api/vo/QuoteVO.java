package com.anchor.api.vo;

import com.anchor.api.service.QuoteQueryService;

/** 报价响应。 */
public record QuoteVO(
        String quoteId,
        long chainId,
        TokenAmountVO tokenIn,
        TokenAmountVO tokenOut,
        String amountIn,
        String amountOut,
        String amountOutMinimum,
        int fee,
        int priceImpactBps,
        String gasEstimate,
        String l1DataFeeWei,
        long expiresAtMs) {

    public static QuoteVO from(long chainId, QuoteQueryService.QuoteView view) {
        return new QuoteVO(
                view.quote().quoteId(),
                chainId,
                new TokenAmountVO(view.tokenIn(), view.tokenInSymbol(), view.tokenInDecimals(), view.amountIn().toString()),
                new TokenAmountVO(view.tokenOut(), view.tokenOutSymbol(), view.tokenOutDecimals(), view.quote().amountOut().toString()),
                view.amountIn().toString(),
                view.quote().amountOut().toString(),
                view.quote().amountOutMinimum().toString(),
                view.quote().fee(),
                view.quote().priceImpactBps(),
                view.quote().gasEstimate().toString(),
                view.quote().l1DataFeeWei().toString(),
                view.quote().expiresAtEpochMs());
    }
}
