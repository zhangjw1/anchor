package com.anchor.api.vo;

import jakarta.validation.constraints.NotBlank;

/** 准备交易请求。 */
public record TradePrepareRequest(
        @NotBlank String quoteId,
        @NotBlank String userAddress,
        Boolean infiniteApprove) {
}
