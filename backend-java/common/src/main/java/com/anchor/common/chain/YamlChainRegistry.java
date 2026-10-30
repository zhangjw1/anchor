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

/**
 * 从 chains.yaml 加载链定义，并按 key、链 ID 建索引。
 */
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
    /**
     * 返回加载时的链顺序。
     */
    @Override
    public List<ChainDefinition> all() {
        return List.copyOf(byKey.values());
    }
    /**
     * key 不存在时抛未知链。
     */
    @Override
    public ChainDefinition requireByKey(String key) {
        ChainDefinition definition = byKey.get(key);
        if (definition == null) {
            throw new AnchorException("UNKNOWN_CHAIN", "未配置的链: " + key);
        }
        return definition;
    }
    /**
     * 链 ID 不存在时抛未知链。
     */
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
                        optionalAddress(contracts.aerodromeFactory()),
                        optionalAddress(contracts.aerodromeSlipstreamFactory())),
                Set.copyOf(quotes),
                List.copyOf(aggregators));
    }

    private static String optionalAddress(String raw) {
        return raw == null || raw.isBlank() ? null : Addresses.normalizeEvm(raw);
    }

    /**
     * chains.yaml 根节点。
     */
    public record ChainsFile(
            /** 链定义列表，对应 yaml 的 chains。 */
            List<ChainFile> chains) {
    }

    /**
     * chains.yaml 中一条链的原始字段。
     */
    public record ChainFile(
            /** 链标识，例如 base。 */
            String key,
            /** 链 ID。 */
            long chainId,
            /** 链显示名称。 */
            String name,
            /** 链类型字符串，例如 EVM。 */
            String type,
            /** 是否测试网。 */
            boolean testnet,
            /** 达到已确认所需的区块数。 */
            int confirmationBlocks,
            /** 手续费模型字符串，例如 EIP1559_OP_L1_FEE。 */
            String gasModel,
            /** 签名模式字符串列表，例如 CLIENT、HOT_WALLET。 */
            List<String> signModes,
            /** 原生币配置。 */
            NativeTokenFile nativeToken,
            /** RPC 节点列表。 */
            List<RpcFile> rpc,
            /** 合约地址集合。 */
            ContractsFile contracts,
            /** 报价币地址列表。 */
            List<String> quoteTokens,
            /** 聚合器列表。 */
            List<AggregatorFile> aggregators) {

        /**
         * 对应 yaml 的 nativeToken。
         */
        public record NativeTokenFile(
                /** 原生币符号。 */
                String symbol,
                /** 原生币精度。 */
                int decimals,
                /** 原生币占位地址。 */
                String address) {
        }

        /**
         * 对应 yaml 的 rpc 列表项。
         */
        public record RpcFile(
                /** 节点 URL。 */
                String url,
                /** 每秒请求上限。 */
                double requestsPerSecond) {
        }

        /**
         * 对应 yaml 的 contracts。
         */
        public record ContractsFile(
                /** WETH。 */
                String weth,
                /** USDC。 */
                String usdc,
                /** USDbC。 */
                String usdbc,
                /** Multicall3。 */
                String multicall3,
                /** OP 栈 GasPriceOracle。 */
                String gasPriceOracle,
                /** Uniswap V3 Factory。 */
                String uniswapV3Factory,
                /** Uniswap QuoterV2。 */
                String quoterV2,
                /** Uniswap SwapRouter02。 */
                String swapRouter02,
                /** Uniswap V2 Factory。 */
                String uniswapV2Factory,
                /** Uniswap V2 Router。 */
                String uniswapV2Router,
                /** Aerodrome Router。 */
                String aerodromeRouter,
                /** Aerodrome 经典池 Factory。 */
                String aerodromeFactory,
                /** Aerodrome Slipstream Factory。 */
                String aerodromeSlipstreamFactory) {
        }
    }

    /**
     * 对应 yaml 的 aggregators 列表项。
     */
    public record AggregatorFile(
            /** 聚合器名称。 */
            String name,
            /** 聚合器路由地址。 */
            String address) {
    }
}
