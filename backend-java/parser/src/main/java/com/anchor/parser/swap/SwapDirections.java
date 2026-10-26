package com.anchor.parser.swap;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.domain.SwapDirectionEnum;

/**
 * 用报价币集合判断买入、卖出或兑换。
 */
public final class SwapDirections {

    private SwapDirections() {
    }
    /**
     * 付出报价币得到非报价币为买入，反向为卖出，两边同为或都不是则为兑换。
     */
    public static SwapDirectionEnum of(ChainDefinition chain, String tokenIn, String tokenOut) {
        boolean inQuote = chain.quoteTokens().contains(tokenIn);
        boolean outQuote = chain.quoteTokens().contains(tokenOut);
        if (inQuote && !outQuote) {
            return SwapDirectionEnum.BUY;
        }
        if (!inQuote && outQuote) {
            return SwapDirectionEnum.SELL;
        }
        return SwapDirectionEnum.SWAP;
    }
}
