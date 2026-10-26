package com.anchor.common.chain;

import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.domain.GasModelEnum;
import com.anchor.common.domain.SignModeEnum;

import java.util.List;
import java.util.Set;

/**
 * 一条链的静态定义，来自 chains.yaml。
 */
public record ChainDefinition(
        String key,
        long chainId,
        String name,
        ChainTypeEnum type,
        boolean testnet,
        int confirmationBlocks,
        GasModelEnum gasModel,
        List<SignModeEnum> signModes,
        NativeToken nativeToken,
        List<RpcNode> rpcNodes,
        Contracts contracts,
        Set<String> quoteTokens,
        List<Aggregator> aggregators) {
    /**
     * 原生币。EVM 上用 0xeeee 占位，不走 ERC20 合约。
     */
    public record NativeToken(String symbol, int decimals, String address) {
    }
    /**
     * 一个 RPC 节点及其每秒请求上限。
     */
    public record RpcNode(String url, double requestsPerSecond) {
    }
    /**
     * 该链上使用的合约地址。
     */
    public record Contracts(
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
    /**
     * 聚合器路由地址。
     */
    public record Aggregator(String name, String address) {
    }
}
