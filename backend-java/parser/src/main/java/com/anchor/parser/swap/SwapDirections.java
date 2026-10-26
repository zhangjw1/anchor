package com.anchor.parser.swap;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.domain.SwapDirection;

public final class SwapDirections {

    private SwapDirections() {
    }

    public static SwapDirection of(ChainDefinition chain, String tokenIn, String tokenOut) {
        boolean inQuote = chain.quoteTokens().contains(tokenIn);
        boolean outQuote = chain.quoteTokens().contains(tokenOut);
        if (inQuote && !outQuote) {
            return SwapDirection.BUY;
        }
        if (!inQuote && outQuote) {
            return SwapDirection.SELL;
        }
        return SwapDirection.SWAP;
    }
}
