package com.anchor.common.chain;

import com.anchor.common.domain.SwapDirectionEnum;

import java.math.BigInteger;
import java.util.Optional;

/**
 * 从一笔交易里解析出至多一笔净交换。
 */
public interface SwapParser {
    /**
     * 不符合单池、单输入、单输出时返回空。
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
     * 一笔可入库的交换。金额是最小单位。
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
            String watchedAddress) {
    }
}
