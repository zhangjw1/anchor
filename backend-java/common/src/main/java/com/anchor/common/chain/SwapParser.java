package com.anchor.common.chain;

import com.anchor.common.domain.SwapDirectionEnum;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * 从一笔交易里解析出至多一笔净交换（可含多跳路径）。
 */
public interface SwapParser {
    /**
     * 无经校验的池子交换、或净输入/输出不唯一时返回空。
     */
    Optional<ParsedSwap> parse(SwapParseContext context);

    /**
     * 解析所需的链、区块、交易和回执。
     */
    record SwapParseContext(
            ChainDefinition chain,
            ChainBlock block,
            ChainTransaction transaction,
            ChainReceipt receipt) {
    }

    /**
     * 路径中的一跳。金额是最小单位。
     */
    record ParsedHop(
            int hopIndex,
            String dex,
            String pool,
            String tokenIn,
            String tokenOut,
            BigInteger amountIn,
            BigInteger amountOut,
            int logIndex) {
    }

    /**
     * 一笔可入库的交换。金额是最小单位；logIndex 取首跳。
     */
    record ParsedSwap(
            long chainId,
            String txHash,
            int logIndex,
            long blockNumber,
            String blockHash,
            long blockTimestamp,
            String txFrom,
            String txTo,
            String dex,
            SwapDirectionEnum direction,
            String tokenIn,
            String tokenOut,
            BigInteger amountIn,
            BigInteger amountOut,
            String watchedAddress,
            String aggregator,
            List<ParsedHop> hops) {

        public ParsedSwap {
            hops = hops == null ? List.of() : List.copyOf(hops);
        }
    }
}
