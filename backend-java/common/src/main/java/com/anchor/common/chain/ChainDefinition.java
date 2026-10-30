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
        /** chains.yaml 的 key，例如 base、base-sepolia。 */
        String key,
        /** 链 ID，例如 Base 主网 8453。 */
        long chainId,
        /** 链显示名称。 */
        String name,
        /** 链类型，对应 yaml 的 type，当前为 EVM。 */
        ChainTypeEnum type,
        /** 是否测试网，对应 yaml 的 testnet。 */
        boolean testnet,
        /** 达到已确认所需的区块数，对应 yaml 的 confirmationBlocks。 */
        int confirmationBlocks,
        /** 手续费模型，对应 yaml 的 gasModel。 */
        GasModelEnum gasModel,
        /** 允许的签名模式列表，对应 yaml 的 signModes。 */
        List<SignModeEnum> signModes,
        /** 原生币，对应 yaml 的 nativeToken。 */
        NativeToken nativeToken,
        /** RPC 节点列表，对应 yaml 的 rpc。 */
        List<RpcNode> rpcNodes,
        /** 合约地址集合，对应 yaml 的 contracts。 */
        Contracts contracts,
        /** 报价币地址集合，对应 yaml 的 quoteTokens，用于判断买卖方向。 */
        Set<String> quoteTokens,
        /** 聚合器列表，对应 yaml 的 aggregators。 */
        List<Aggregator> aggregators) {

    /**
     * 原生币。EVM 上用 0xeeee 占位，不走 ERC20 合约。
     */
    public record NativeToken(
            /** 原生币符号，例如 ETH。 */
            String symbol,
            /** 原生币精度。 */
            int decimals,
            /** 原生币占位地址，对应 yaml 的 nativeToken.address。 */
            String address) {
    }

    /**
     * 一个 RPC 节点及其每秒请求上限。
     */
    public record RpcNode(
            /** 节点 HTTP JSON-RPC 地址，对应 yaml 的 rpc.url。 */
            String url,
            /** 该节点每秒请求上限，对应 yaml 的 rpc.requestsPerSecond。 */
            double requestsPerSecond) {
    }

    /**
     * 该链上使用的合约地址。
     */
    public record Contracts(
            /** WETH 合约地址。 */
            String weth,
            /** USDC 合约地址。 */
            String usdc,
            /** USDbC 合约地址，测试网可缺省。 */
            String usdbc,
            /** Multicall3 合约地址。 */
            String multicall3,
            /** OP 栈 L1 数据费预言机，对应 yaml 的 gasPriceOracle。 */
            String gasPriceOracle,
            /** Uniswap V3 Factory 地址。 */
            String uniswapV3Factory,
            /** Uniswap QuoterV2 地址。 */
            String quoterV2,
            /** Uniswap SwapRouter02 地址。 */
            String swapRouter02,
            /** Uniswap V2 Factory 地址。 */
            String uniswapV2Factory,
            /** Uniswap V2 Router 地址。 */
            String uniswapV2Router,
            /** Aerodrome Router 地址，测试网可缺省。 */
            String aerodromeRouter,
            /** Aerodrome 经典池 Factory 地址，测试网可缺省。 */
            String aerodromeFactory,
            /** Aerodrome Slipstream（CL）Factory 地址，测试网可缺省。 */
            String aerodromeSlipstreamFactory) {
    }

    /**
     * 聚合器路由地址。
     */
    public record Aggregator(
            /** 聚合器名称，对应 yaml 的 aggregators.name。 */
            String name,
            /** 聚合器路由合约地址，对应 yaml 的 aggregators.address。 */
            String address) {
    }
}
