package com.anchor.api.controller;

import com.anchor.api.service.SwapQueryService;
import com.anchor.api.vo.SwapPageVO;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class SwapController {

    private final SwapQueryService swapQueryService;

    public SwapController(SwapQueryService swapQueryService) {
        this.swapQueryService = swapQueryService;
    }

    @GetMapping("/api/v1/swaps")
    public SwapPageVO swaps(@RequestParam @NotNull Long chainId, @RequestParam(required = false) String address,
                            @RequestParam(required = false) String dex, @RequestParam(required = false) String direction,
                            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return SwapPageVO.from(swapQueryService.list(chainId, address, dex, direction, page, size));
    }
}
