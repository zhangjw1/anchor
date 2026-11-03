package com.anchor.api.vo;

import com.anchor.api.dto.AssetPortfolioDTO;

import java.util.List;

/** 资产组合响应。 */
public record AssetPortfolioVO(long chainId, String address, List<AssetHoldingVO> holdings, String netWorthUsd, long asOfMs) {

    public static AssetPortfolioVO from(AssetPortfolioDTO dto) {
        return new AssetPortfolioVO(
                dto.chainId(),
                dto.address(),
                dto.holdings().stream().map(AssetHoldingVO::from).toList(),
                dto.netWorthUsdMicro() == null ? null : dto.netWorthUsdMicro().toString(),
                dto.asOfMs());
    }
}
