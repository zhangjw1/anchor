package com.anchor.parser.swap;

import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.support.Addresses;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeSwapParserTest {

    @Test
    void parsesUniswapV2() throws Exception {
        assertSingleHop("v2-single.json", AnchorConstants.DEX_UNISWAP_V2);
    }

    @Test
    void parsesAerodromeClassic() throws Exception {
        assertSingleHop("aerodrome-single.json", AnchorConstants.DEX_AERODROME);
    }

    @Test
    void parsesAerodromeSlipstream() throws Exception {
        assertSingleHop("slipstream-single.json", AnchorConstants.DEX_AERODROME_SLIPSTREAM);
    }

    @Test
    void parsesMultiHopPath() throws Exception {
        SwapParser.ParsedSwap swap = parse("multi-hop.json");
        assertTrue(swap.hops().size() >= 2);
        assertEquals(AnchorConstants.DEX_MULTI, swap.dex());
        assertPathConnected(swap);
    }

    @Test
    void tagsUniversalRouterAggregator() throws Exception {
        JsonNode root = FixtureSupport.root("aggregator-universal.json");
        SwapParser.ParsedSwap swap = FixtureSupport.composite(root).parse(FixtureSupport.context(root)).orElseThrow();
        assertEquals("Uniswap UniversalRouter", swap.aggregator());
        assertEquals(Addresses.lower(root.get("tx").get("to").asText()), swap.txTo());
        assertFalse(swap.hops().isEmpty());
        assertPathConnected(swap);
    }

    @Test
    void rejectsForgedPool() throws Exception {
        JsonNode root = FixtureSupport.root("direct.json");
        ObjectNode factories = (ObjectNode) root.get("poolFactories");
        factories.fields().forEachRemaining(entry -> factories.put(entry.getKey(), "0x0000000000000000000000000000000000000001"));
        Optional<SwapParser.ParsedSwap> swap = FixtureSupport.composite(root).parse(FixtureSupport.context(root));
        assertTrue(swap.isEmpty());
    }

    private static SwapParser.ParsedSwap parse(String file) throws Exception {
        JsonNode root = FixtureSupport.root(file);
        return FixtureSupport.composite(root).parse(FixtureSupport.context(root)).orElseThrow();
    }

    private static void assertSingleHop(String file, String dex) throws Exception {
        JsonNode root = FixtureSupport.root(file);
        SwapParser.ParsedSwap swap = FixtureSupport.composite(root).parse(FixtureSupport.context(root)).orElseThrow();
        assertEquals(dex, swap.dex());
        assertEquals(1, swap.hops().size());
        assertEquals(dex, swap.hops().get(0).dex());
        assertTrue(swap.amountIn().signum() > 0);
        assertTrue(swap.amountOut().signum() > 0);
        if (root.has("tokenIn")) {
            assertEquals(root.get("tokenIn").asText(), swap.tokenIn());
            assertEquals(root.get("tokenOut").asText(), swap.tokenOut());
            assertEquals(new BigInteger(root.get("amountIn").asText()), swap.amountIn());
            assertEquals(new BigInteger(root.get("amountOut").asText()), swap.amountOut());
        }
    }

    /** 首跳付出币、末跳收到币与用户净流向一致，且 hopIndex 连续、logIndex 递增。 */
    private static void assertPathConnected(SwapParser.ParsedSwap swap) {
        List<SwapParser.ParsedHop> hops = swap.hops();
        assertEquals(poolToken(swap.tokenIn()), hops.get(0).tokenIn());
        assertEquals(poolToken(swap.tokenOut()), hops.get(hops.size() - 1).tokenOut());
        for (int i = 0; i < hops.size(); i++) {
            assertEquals(i, hops.get(i).hopIndex());
            if (i > 0) {
                assertTrue(hops.get(i).logIndex() > hops.get(i - 1).logIndex());
            }
        }
    }

    private static String poolToken(String token) {
        if (token.equals(FixtureSupport.base().nativeToken().address())) {
            return Addresses.lower(FixtureSupport.base().contracts().weth());
        }
        return token;
    }
}
