package com.anchor.api.vo;

import com.anchor.api.service.TradeQueryService;

/** 交易状态。 */
public record TradeStatusVO(
        long chainId,
        String txHash,
        String kind,
        String status,
        String quoteId,
        String userAddress,
        String revertReason,
        long updatedAtMs) {

    public static TradeStatusVO from(TradeQueryService.TradeStatusView view) {
        return new TradeStatusVO(view.chainId(), view.txHash(), view.kind(), view.status(), view.quoteId(),
                view.userAddress(), view.revertReason(), view.updatedAtMs());
    }
}
