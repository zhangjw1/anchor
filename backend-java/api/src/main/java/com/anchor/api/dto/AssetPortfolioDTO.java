package com.anchor.api.dto;

import java.math.BigInteger;
import java.util.List;

/** 钱包资产组合快照。 */
public record AssetPortfolioDTO(
        long chainId,
        String address,
        List<AssetHoldingDTO> holdings,
        BigInteger netWorthUsdMicro,
        long asOfMs) {
}
