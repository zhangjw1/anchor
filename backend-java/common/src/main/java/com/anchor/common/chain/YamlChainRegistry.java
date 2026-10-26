package com.anchor.common.chain;

import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.domain.GasModelEnum;
import com.anchor.common.domain.SignModeEnum;
import com.anchor.common.support.Addresses;
import com.anchor.common.support.RepoPaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class YamlChainRegistry implements ChainRegistry {

    private final Map<String, ChainDefinition> byKey;
    private final Map<Long, ChainDefinition> byId;

    public YamlChainRegistry(AnchorSettings settings) {
        this(RepoPaths.resolveConfigured(settings.getChainsFile()));
    }

    public YamlChainRegistry(Path path) {
        ChainsFile file = read(path);
        Map<String, ChainDefinition> keyed = new LinkedHashMap<>();
        Map<Long, ChainDefinition> ids = new LinkedHashMap<>();
        for (ChainFile item : file.chains()) {
            ChainDefinition definition = toDefinition(item);
            keyed.put(definition.key(), definition);
            ids.put(definition.chainId(), definition);
        }
        this.byKey = Map.copyOf(keyed);
        this.byId = Map.copyOf(ids);
    }

    @Override
    public List<ChainDefinition> all() {
        return List.copyOf(byKey.values());
    }

    @Override
    public ChainDefinition requireByKey(String key) {
        ChainDefinition definition = byKey.get(key);
        if (definition == null) {
            throw new AnchorException("UNKNOWN_CHAIN", "未配置的链: " + key);
        }
        return definition;
    }

    @Override
    public ChainDefinition requireById(long chainId) {
        ChainDefinition definition = byId.get(chainId);
        if (definition == null) {
            throw new AnchorException("UNKNOWN_CHAIN", "未配置的链: " + chainId);
        }
        return definition;
    }

    private static ChainsFile read(Path path) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.readValue(path.toFile(), ChainsFile.class);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read chains file " + path.toAbsolutePath(), ex);
        }
    }

    private static ChainDefinition toDefinition(ChainFile item) {
        List<SignModeEnum> modes = item.signModes().stream().map(value -> SignModeEnum.valueOf(value.trim())).toList();
        Set<String> quotes = new LinkedHashSet<>();
        for (String quote : item.quoteTokens()) {
            quotes.add(Addresses.normalizeEvm(quote));
        }
        List<ChainDefinition.Aggregator> aggregators = new ArrayList<>();
        if (item.aggregators() != null) {
            for (AggregatorFile aggregator : item.aggregators()) {
                aggregators.add(new ChainDefinition.Aggregator(aggregator.name(), Addresses.normalizeEvm(aggregator.address())));
            }
        }
        ChainFile.ContractsFile contracts = item.contracts();
        return new ChainDefinition(
                item.key(),
                item.chainId(),
                item.name(),
                ChainTypeEnum.valueOf(item.type().trim().toUpperCase(Locale.ROOT)),
                item.testnet(),
                item.confirmationBlocks(),
                GasModelEnum.valueOf(item.gasModel().trim()),
                modes,
                new ChainDefinition.NativeToken(item.nativeToken().symbol(), item.nativeToken().decimals(), Addresses.normalizeEvm(item.nativeToken().address())),
                item.rpc().stream().map(node -> new ChainDefinition.RpcNode(node.url(), node.requestsPerSecond())).toList(),
                new ChainDefinition.Contracts(
                        optionalAddress(contracts.weth()),
                        optionalAddress(contracts.usdc()),
                        optionalAddress(contracts.usdbc()),
                        optionalAddress(contracts.multicall3()),
                        optionalAddress(contracts.gasPriceOracle()),
                        optionalAddress(contracts.uniswapV3Factory()),
                        optionalAddress(contracts.quoterV2()),
                        optionalAddress(contracts.swapRouter02()),
                        optionalAddress(contracts.uniswapV2Factory()),
                        optionalAddress(contracts.uniswapV2Router()),
                        optionalAddress(contracts.aerodromeRouter()),
                        optionalAddress(contracts.aerodromeFactory())),
                Set.copyOf(quotes),
                List.copyOf(aggregators));
    }

    private static String optionalAddress(String raw) {
        return raw == null || raw.isBlank() ? null : Addresses.normalizeEvm(raw);
    }

    public record ChainsFile(List<ChainFile> chains) {
    }

    public record ChainFile(
            String key,
            long chainId,
            String name,
            String type,
            boolean testnet,
            int confirmationBlocks,
            String gasModel,
            List<String> signModes,
            NativeTokenFile nativeToken,
            List<RpcFile> rpc,
            ContractsFile contracts,
            List<String> quoteTokens,
            List<AggregatorFile> aggregators) {

        public record NativeTokenFile(String symbol, int decimals, String address) {
        }

        public record RpcFile(String url, double requestsPerSecond) {
        }

        public record ContractsFile(
                String weth,
                String usdc,
                String usdbc,
                String multicall3,
                String gasPriceOracle,
                String uniswapV3Factory,
                String quoterV2,
                String swapRouter02,
                String uniswapV2Factory,
                String uniswapV2Router,
                String aerodromeRouter,
                String aerodromeFactory) {
        }
    }

    public record AggregatorFile(String name, String address) {
    }
}
