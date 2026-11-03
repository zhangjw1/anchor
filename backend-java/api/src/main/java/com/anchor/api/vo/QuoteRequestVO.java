package com.anchor.api.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 报价请求体。 */
public record QuoteRequestVO(
        @NotNull Long chainId,
        @NotBlank String tokenIn,
        @NotBlank String tokenOut,
        @NotBlank String amountIn,
        @NotNull @Min(0) @Max(5000) Integer slippageBps) {
}
