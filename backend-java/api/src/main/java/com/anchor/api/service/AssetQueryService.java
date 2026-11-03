package com.anchor.api.service;

import com.anchor.api.dto.AssetPortfolioDTO;

import java.util.List;

/** 查询钱包余额与净值。 */
public interface AssetQueryService {

    AssetPortfolioDTO portfolio(long chainId, String address, List<String> extraTokens);
}
