package com.anchor.api.vo;

import com.anchor.api.dto.AssetHoldingDTO;

/** 单币种持仓响应。 */
public record AssetHoldingVO(TokenAmountVO token, String balance, String priceUsd, String valueUsd) {

    public static AssetHoldingVO from(AssetHoldingDTO dto) {
        TokenAmountVO token = new TokenAmountVO(dto.address(), dto.symbol(), dto.decimals(),
                dto.balance() == null ? "0" : dto.balance().toString());
        return new AssetHoldingVO(
                token,
                token.amount(),
                dto.priceUsdMicro() == null ? null : dto.priceUsdMicro().toString(),
                dto.valueUsdMicro() == null ? null : dto.valueUsdMicro().toString());
    }
}
