package com.anchor.common.manager;

import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwapRouterTxBuilderTest {

    @Test
    void exactInputSingleSelectorMatchesRouter02() {
        String encoded = SwapRouterTxBuilder.encodeExactInputSingle(
                "0x4200000000000000000000000000000000000006",
                "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
                500,
                "0x1111111111111111111111111111111111111111",
                BigInteger.TEN.pow(18),
                BigInteger.TEN.pow(6));
        assertTrue(encoded.startsWith("0x"));
        // exactInputSingle((address,address,uint24,address,uint256,uint256,uint160))
        assertEquals("04e45aaf", Numeric.cleanHexPrefix(encoded).substring(0, 8));
    }
}
