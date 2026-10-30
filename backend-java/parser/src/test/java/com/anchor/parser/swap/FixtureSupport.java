package com.anchor.parser.swap;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.ChainTransaction;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.chain.YamlChainRegistry;
import com.anchor.common.support.Addresses;
import com.anchor.common.support.RepoPaths;
import com.anchor.parser.dto.PoolTokensDTO;
import com.anchor.parser.service.PoolFactoryService;
import com.anchor.parser.service.PoolTokenService;
import com.anchor.parser.swap.hop.AerodromeHopParser;
import com.anchor.parser.swap.hop.AerodromeSlipstreamHopParser;
import com.anchor.parser.swap.hop.DexHopParser;
import com.anchor.parser.swap.hop.UniswapV2HopParser;
import com.anchor.parser.swap.hop.UniswapV3HopParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从 fixtures JSON 构建解析上下文与本地 factory/token 查询。
 */
final class FixtureSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ChainRegistry REGISTRY = new YamlChainRegistry(RepoPaths.findUp("contracts-shared/chains.yaml"));

    private FixtureSupport() {
    }

    static JsonNode root(String file) throws Exception {
        return MAPPER.readTree(FixtureSupport.class.getResourceAsStream("/fixtures/" + file));
    }

    static ChainDefinition base() {
        return REGISTRY.requireByKey("base");
    }

    static SwapParser.SwapParseContext context(JsonNode root) {
        JsonNode tx = root.get("tx");
        JsonNode receipt = root.get("receipt");
        return new SwapParser.SwapParseContext(base(), block(tx, receipt), transaction(tx), toReceipt(receipt));
    }

    static PoolFactoryService factories(JsonNode root) {
        Map<String, String> map = new HashMap<>();
        JsonNode node = root.get("poolFactories");
        if (node != null) {
            node.fields().forEachRemaining(entry -> map.put(Addresses.lower(entry.getKey()), Addresses.lower(entry.getValue().asText())));
        }
        return (chainId, pool) -> Optional.ofNullable(map.get(Addresses.lower(pool)));
    }

    static PoolTokenService tokens(JsonNode root) {
        Map<String, PoolTokensDTO> map = new HashMap<>();
        JsonNode node = root.get("poolTokens");
        if (node != null) {
            node.fields().forEachRemaining(entry -> map.put(Addresses.lower(entry.getKey()),
                    new PoolTokensDTO(Addresses.lower(entry.getValue().get("token0").asText()), Addresses.lower(entry.getValue().get("token1").asText()))));
        }
        return (chainId, pool) -> Optional.ofNullable(map.get(Addresses.lower(pool)));
    }

    static CompositeSwapParser composite(JsonNode root) {
        PoolFactoryService factoryService = factories(root);
        PoolTokenService tokenService = tokens(root);
        List<DexHopParser> parsers = List.of(
                new UniswapV3HopParser(factoryService, tokenService),
                new UniswapV2HopParser(factoryService, tokenService),
                new AerodromeHopParser(factoryService, tokenService),
                new AerodromeSlipstreamHopParser(factoryService, tokenService));
        return new CompositeSwapParser(parsers);
    }

    private static ChainBlock block(JsonNode tx, JsonNode receipt) {
        return new ChainBlock(hex(tx.get("blockNumber").asText()), Addresses.lower(receipt.get("blockHash").asText()),
                "0xparent", 0L, List.of());
    }

    private static ChainTransaction transaction(JsonNode tx) {
        return new ChainTransaction(
                Addresses.lower(tx.get("hash").asText()),
                Addresses.lower(tx.get("from").asText()),
                Addresses.lower(tx.hasNonNull("to") ? tx.get("to").asText() : null),
                hexBig(tx.get("value").asText()),
                tx.has("input") ? tx.get("input").asText() : "0x");
    }

    private static ChainReceipt toReceipt(JsonNode receipt) {
        List<ChainLog> logs = new ArrayList<>();
        for (JsonNode item : receipt.get("logs")) {
            List<String> topics = new ArrayList<>();
            for (JsonNode topic : item.get("topics")) {
                topics.add(topic.asText());
            }
            logs.add(new ChainLog(
                    Addresses.lower(item.get("address").asText()),
                    topics,
                    item.get("data").asText(),
                    hexInt(item.get("logIndex").asText())));
        }
        boolean success = "0x1".equalsIgnoreCase(receipt.get("status").asText());
        return new ChainReceipt(Addresses.lower(receipt.get("transactionHash").asText()), success,
                hexBig(receipt.get("gasUsed").asText()), logs);
    }

    private static long hex(String value) {
        return Long.parseLong(value.substring(2), 16);
    }

    private static int hexInt(String value) {
        return Integer.parseInt(value.substring(2), 16);
    }

    private static BigInteger hexBig(String value) {
        if (value == null || value.isBlank() || "0x".equalsIgnoreCase(value) || "0x0".equalsIgnoreCase(value)) {
            return BigInteger.ZERO;
        }
        return new BigInteger(value.substring(2), 16);
    }
}
