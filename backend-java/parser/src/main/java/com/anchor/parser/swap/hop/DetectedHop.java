package com.anchor.parser.swap.hop;

import java.math.BigInteger;

/**
 * 单个池子的一次 Swap，尚未编号；编排层按 logIndex 排序后再赋 hopIndex。
 */
public record DetectedHop(
        String dex,
        String pool,
        String tokenIn,
        String tokenOut,
        BigInteger amountIn,
        BigInteger amountOut,
        int logIndex) {
}
