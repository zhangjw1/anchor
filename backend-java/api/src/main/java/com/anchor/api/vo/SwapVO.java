package com.anchor.api.vo;

import com.anchor.common.dto.SwapDTO;

public record SwapVO(String id, Long chainId, String txHash, Integer logIndex, Long blockNumber, Long timestamp,
                     String direction, String dex, TokenAmountVO tokenIn, TokenAmountVO tokenOut,
                     String confirmStatus, String watchedAddress) {

    public static SwapVO from(SwapDTO row) {
        return new SwapVO(String.valueOf(row.getId()), row.getChainId(), row.getTxHash(), row.getLogIndex(),
                row.getBlockNumber(), row.getTimestampMs(), row.getDirection(), row.getDex(),
                TokenAmountVO.of(row.getTokenIn(), row.getTokenInSymbol(), row.getTokenInDecimals(), row.getAmountIn()),
                TokenAmountVO.of(row.getTokenOut(), row.getTokenOutSymbol(), row.getTokenOutDecimals(), row.getAmountOut()),
                row.getConfirmStatus(), row.getWatchedAddress());
    }
}
