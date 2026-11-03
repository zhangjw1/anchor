package com.anchor.api.controller;

import com.anchor.api.service.AssetQueryService;
import com.anchor.api.vo.AssetPortfolioVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 钱包资产余额与净值。
 */
@Validated
@RestController
public class AssetController {

    private final AssetQueryService assetQueryService;

    public AssetController(AssetQueryService assetQueryService) {
        this.assetQueryService = assetQueryService;
    }

    @GetMapping("/api/v1/assets")
    public AssetPortfolioVO assets(@RequestParam @NotNull Long chainId,
                                   @RequestParam @NotBlank String address,
                                   @RequestParam(required = false) List<String> tokens) {
        return AssetPortfolioVO.from(assetQueryService.portfolio(chainId, address, tokens));
    }
}
