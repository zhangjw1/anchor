package com.anchor.api.vo;

import com.anchor.api.service.TradeQueryService;

/** 准备交易响应。 */
public record TradePrepareResponse(
        String quoteId,
        long expiresAtMs,
        boolean needsApprove,
        UnsignedTxVO approveTx,
        UnsignedTxVO swapTx,
        String amountOutMinimum,
        long deadline) {

    public static TradePrepareResponse from(TradeQueryService.PrepareResult result) {
        String gas = result.gasEstimate() == null ? null : result.gasEstimate().toString();
        return new TradePrepareResponse(
                result.quoteId(),
                result.expiresAtMs(),
                result.needsApprove(),
                UnsignedTxVO.from(result.approveTx(), null),
                UnsignedTxVO.from(result.swapTx(), gas),
                result.amountOutMinimum(),
                result.deadline());
    }
}
