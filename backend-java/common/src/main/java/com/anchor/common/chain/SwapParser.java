package com.anchor.common.chain;

import com.anchor.common.domain.SwapDirectionEnum;

import java.math.BigInteger;
import java.util.Optional;

public interface SwapParser {

    Optional<ParsedSwap> parse(SwapParseContext context);

    record SwapParseContext(
            ChainDefinition chain,
            ChainBlock block,
            ChainTransaction transaction,
            ChainReceipt receipt) {
    }

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
