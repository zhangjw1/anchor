package com.anchor.parser.swap;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.ChainTransaction;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.chain.YamlChainRegistry;
import com.anchor.common.domain.AnchorConstants;
import com.anchor.common.support.Addresses;
import com.anchor.common.support.RepoPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniswapV3SwapParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ChainRegistry registry = new YamlChainRegistry(RepoPaths.findUp("contracts-shared/chains.yaml"));

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
    void parsesEthOutput() throws Exception {
        assertFixture("ethOut.json");
    }

    private void assertFixture(String file) throws Exception {
        JsonNode root = mapper.readTree(UniswapV3SwapParserTest.class.getResourceAsStream("/fixtures/" + file));
        JsonNode tx = root.get("tx");
        JsonNode receipt = root.get("receipt");
        ChainDefinition chain = registry.requireByKey("base");
        UniswapV3SwapParser parser = new UniswapV3SwapParser((chainId, pool) ->
                Optional.ofNullable(root.get("poolFactories").get(pool)).map(JsonNode::asText));
        SwapParser.ParsedSwap swap = parser.parse(new SwapParser.SwapParseContext(
                chain, block(tx, receipt), transaction(tx), toReceipt(receipt))).orElseThrow();
        assertEquals(Addresses.lower(root.get("tokenIn").asText()), swap.tokenIn());
        assertEquals(Addresses.lower(root.get("tokenOut").asText()), swap.tokenOut());
        assertEquals(new BigInteger(root.get("amountIn").asText()), swap.amountIn());
        assertEquals(new BigInteger(root.get("amountOut").asText()), swap.amountOut());
        assertEquals(AnchorConstants.DEX_UNISWAP_V3, swap.dex());
        assertEquals(Addresses.lower(tx.get("from").asText()), swap.watchedAddress());
        assertTrue(swap.amountIn().signum() > 0);
        assertTrue(swap.amountOut().signum() > 0);
        if (file.equals("ethIn.json")) {
            assertEquals(chain.nativeToken().address(), swap.tokenIn());
        }
        if (file.equals("ethOut.json")) {
            assertEquals(chain.nativeToken().address(), swap.tokenOut());
        }
    }

    private static ChainBlock block(JsonNode tx, JsonNode receipt) {
        return new ChainBlock(hex(tx.get("blockNumber").asText()), Addresses.lower(receipt.get("blockHash").asText()),
                "0xparent", 0L, List.of());
    }

    private static ChainTransaction transaction(JsonNode tx) {
        return new ChainTransaction(Addresses.lower(tx.get("hash").asText()), Addresses.lower(tx.get("from").asText()),
                text(tx.get("to")), hexQuantity(tx.get("value").asText()), tx.get("input").asText());
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : Addresses.lower(node.asText());
    }

    private static BigInteger hexQuantity(String value) {
        return new BigInteger(value.substring(2), 16);
    }

    private static ChainReceipt toReceipt(JsonNode receipt) {
        List<ChainLog> logs = new ArrayList<>();
        for (JsonNode log : receipt.get("logs")) {
            List<String> topics = new ArrayList<>();
            log.get("topics").forEach(topic -> topics.add(Addresses.lower(topic.asText())));
            logs.add(new ChainLog(Addresses.lower(log.get("address").asText()), topics, log.get("data").asText(), hex(log.get("logIndex").asText())));
        }
        return new ChainReceipt(Addresses.lower(receipt.get("transactionHash").asText()), true, BigInteger.ZERO, logs);
    }

    private static int hex(String value) {
        return Integer.parseInt(value.substring(2), 16);
    }
}
