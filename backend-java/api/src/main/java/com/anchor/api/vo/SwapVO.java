package com.anchor.api.vo;

import com.anchor.common.dto.SwapDTO;

/**
 * 单笔交换的返回对象。金额是最小单位的十进制字符串。
 */
public record SwapVO(String id, Long chainId, String txHash, Integer logIndex, Long blockNumber, Long timestamp,
                     String direction, String dex, TokenAmountVO tokenIn, TokenAmountVO tokenOut,
                     String confirmStatus, int confirmations, String watchedAddress) {
    /**
     * 用代币符号和精度补齐展示字段。
     */
    public static SwapVO from(SwapDTO row) {
        return new SwapVO(String.valueOf(row.getId()), row.getChainId(), row.getTxHash(), row.getLogIndex(),
                row.getBlockNumber(), row.getTimestampMs(), row.getDirection(), row.getDex(),
                TokenAmountVO.of(row.getTokenIn(), row.getTokenInSymbol(), row.getTokenInDecimals(), row.getAmountIn()),
                TokenAmountVO.of(row.getTokenOut(), row.getTokenOutSymbol(), row.getTokenOutDecimals(), row.getAmountOut()),
                row.getConfirmStatus(), row.getConfirmations(), row.getWatchedAddress());
    }
}
