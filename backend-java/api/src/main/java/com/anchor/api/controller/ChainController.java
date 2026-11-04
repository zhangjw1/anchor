package com.anchor.api.controller;

import com.anchor.api.service.ChainQueryService;
import com.anchor.api.service.StatusMetricsService;
import com.anchor.api.vo.ChainMetricsVO;
import com.anchor.api.vo.ChainVO;
import com.anchor.common.chain.ChainRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 返回当前注册的链列表与状态栏指标。
 */
@RestController
public class ChainController {

    private final ChainQueryService chainQueryService;
    private final ChainRegistry chainRegistry;
    private final StatusMetricsService statusMetricsService;

    public ChainController(ChainQueryService chainQueryService, ChainRegistry chainRegistry,
                           StatusMetricsService statusMetricsService) {
        this.chainQueryService = chainQueryService;
        this.chainRegistry = chainRegistry;
        this.statusMetricsService = statusMetricsService;
    }

    /**
     * 列出链标识、链 ID 和确认块数。
     */
    @GetMapping("/api/v1/chains")
    public List<ChainVO> chains() {
        return chainQueryService.list();
    }

    /**
     * 状态栏指标；WS 不可用时前端可轮询此接口。
     */
    @GetMapping("/api/v1/chains/{chainId}/metrics")
    public ChainMetricsVO metrics(@PathVariable long chainId) {
        chainRegistry.requireById(chainId);
        return statusMetricsService.snapshot(chainId);
    }
}
