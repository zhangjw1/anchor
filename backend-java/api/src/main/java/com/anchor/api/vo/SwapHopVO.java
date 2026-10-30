package com.anchor.api.vo;

import com.anchor.common.dto.SwapHopDTO;

/**
 * 交换路径中一跳的返回对象。
 */
public record SwapHopVO(int hopIndex, String dex, String pool, TokenAmountVO tokenIn, TokenAmountVO tokenOut, int logIndex) {
    public static SwapHopVO from(SwapHopDTO row) {
        return new SwapHopVO(row.getHopIndex(), row.getDex(), row.getPool(),
                TokenAmountVO.of(row.getTokenIn(), row.getTokenInSymbol(), row.getTokenInDecimals(), row.getAmountIn()),
                TokenAmountVO.of(row.getTokenOut(), row.getTokenOutSymbol(), row.getTokenOutDecimals(), row.getAmountOut()),
                row.getLogIndex());
    }
}
