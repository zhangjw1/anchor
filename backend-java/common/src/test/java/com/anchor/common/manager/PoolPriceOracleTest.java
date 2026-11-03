package com.anchor.common.manager;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolPriceOracleTest {

    @Test
    void sqrtPriceOneMapsNearOne() {
        // sqrtPriceX96 = 2^96 => token1/token0 raw = 1
        BigInteger sqrt = BigInteger.ONE.shiftLeft(96);
        BigDecimal price = PoolPriceOracle.sqrtPriceToToken1PerToken0(sqrt);
        assertTrue(price.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.0000001")) < 0);
    }
}
