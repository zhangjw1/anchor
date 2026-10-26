package com.anchor.api.vo;

import java.math.BigInteger;

public record TokenAmountVO(String address, String symbol, Integer decimals, String amount) {

    public static TokenAmountVO of(String address, String symbol, Integer decimals, BigInteger amount) {
        return new TokenAmountVO(address, symbol, decimals, amount == null ? null : amount.toString());
    }
}
