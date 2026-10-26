package com.anchor.parser.swap;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.domain.SwapDirectionEnum;

public final class SwapDirections {

    private SwapDirections() {
    }

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
