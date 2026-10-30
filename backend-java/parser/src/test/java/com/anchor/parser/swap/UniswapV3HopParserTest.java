package com.anchor.parser.swap;

import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.AnchorConstants;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniswapV3HopParserTest {

    @Test
    void parsesDirectSingleHop() throws Exception {
        assertFixture("direct.json");
    }

    @Test
    void parsesRouterSingleHop() throws Exception {
        assertFixture("router.json");
    }

    @Test
    void parsesEthInput() throws Exception {
        assertFixture("ethIn.json");
    }

    @Test
    void parsesEthOut() throws Exception {
        assertFixture("ethOut.json");
    }

    private void assertFixture(String file) throws Exception {
        JsonNode root = FixtureSupport.root(file);
        SwapParser.ParsedSwap swap = FixtureSupport.composite(root).parse(FixtureSupport.context(root)).orElseThrow();
        assertEquals(AnchorConstants.DEX_UNISWAP_V3, swap.dex());
        assertEquals(1, swap.hops().size());
        assertEquals(root.get("tokenIn").asText().toLowerCase(), swap.tokenIn());
        assertEquals(root.get("tokenOut").asText().toLowerCase(), swap.tokenOut());
        assertEquals(new BigInteger(root.get("amountIn").asText()), swap.amountIn());
        assertEquals(new BigInteger(root.get("amountOut").asText()), swap.amountOut());
        assertTrue(swap.amountIn().signum() > 0);
        assertTrue(swap.amountOut().signum() > 0);
        if ("ethIn.json".equals(file)) {
            assertEquals(FixtureSupport.base().nativeToken().address(), swap.tokenIn());
        }
        if ("ethOut.json".equals(file)) {
            assertEquals(FixtureSupport.base().nativeToken().address(), swap.tokenOut());
        }
    }
}
