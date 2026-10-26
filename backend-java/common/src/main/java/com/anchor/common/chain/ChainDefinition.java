package com.anchor.common.chain;

import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.domain.GasModelEnum;
import com.anchor.common.domain.SignModeEnum;

import java.util.List;
import java.util.Set;

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

    public record NativeToken(String symbol, int decimals, String address) {
    }

    public record RpcNode(String url, double requestsPerSecond) {
    }

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

    public record Aggregator(String name, String address) {
    }
}
