package com.anchor.api.dto;

import java.math.BigInteger;

/** 单币种持仓。 */
public record AssetHoldingDTO(
        String address,
        String symbol,
        Integer decimals,
        BigInteger balance,
        BigInteger priceUsdMicro,
        BigInteger valueUsdMicro) {
}
