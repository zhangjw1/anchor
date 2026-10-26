package com.anchor.api.vo;

import java.math.BigInteger;

/**
 * 交换一侧的代币数量。
 */
public record TokenAmountVO(String address, String symbol, Integer decimals, String amount) {
    /**
     * 金额为空时保持 null，避免把缺失写成 0。
     */
    public static TokenAmountVO of(String address, String symbol, Integer decimals, BigInteger amount) {
        return new TokenAmountVO(address, symbol, decimals, amount == null ? null : amount.toString());
    }
}
