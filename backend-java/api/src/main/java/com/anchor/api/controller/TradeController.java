package com.anchor.api.controller;

import com.anchor.api.service.TradeQueryService;
import com.anchor.api.vo.TradePrepareRequest;
import com.anchor.api.vo.TradePrepareResponse;
import com.anchor.api.vo.TradeReportRequest;
import com.anchor.api.vo.TradeStatusVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模式 A 交易：准备 / 上报 / 查询。
 */
@Validated
@RestController
public class TradeController {

    private final TradeQueryService tradeQueryService;

    public TradeController(TradeQueryService tradeQueryService) {
        this.tradeQueryService = tradeQueryService;
    }

    @PostMapping("/api/v1/trades/prepare")
    public TradePrepareResponse prepare(@Valid @RequestBody TradePrepareRequest request) {
        boolean infinite = Boolean.TRUE.equals(request.infiniteApprove());
        return TradePrepareResponse.from(tradeQueryService.prepare(request.quoteId(), request.userAddress(), infinite));
    }

    @PostMapping("/api/v1/trades/report")
    public TradeStatusVO report(@Valid @RequestBody TradeReportRequest request) {
        return TradeStatusVO.from(tradeQueryService.report(
                request.chainId(), request.txHash(), request.kind(), request.quoteId(), request.userAddress()));
    }

    @GetMapping("/api/v1/trades/{txHash}")
    public TradeStatusVO status(@PathVariable @NotBlank String txHash, @RequestParam @NotNull Long chainId) {
        return TradeStatusVO.from(tradeQueryService.status(chainId, txHash));
    }
}
