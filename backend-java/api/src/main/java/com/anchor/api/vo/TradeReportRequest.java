package com.anchor.api.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 上报已广播交易。 */
public record TradeReportRequest(
        @NotNull Long chainId,
        @NotBlank String txHash,
        @NotBlank String kind,
        String quoteId,
        @NotBlank String userAddress) {
}
