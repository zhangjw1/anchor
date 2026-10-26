package com.anchor.api.controller;

import com.anchor.api.service.ChainQueryService;
import com.anchor.api.vo.ChainVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 返回当前注册的链列表。
 */
@RestController
public class ChainController {

    private final ChainQueryService chainQueryService;

    public ChainController(ChainQueryService chainQueryService) {
        this.chainQueryService = chainQueryService;
    }
    /**
     * 列出链标识、链 ID 和确认块数。
     */
    @GetMapping("/api/v1/chains")
    public List<ChainVO> chains() {
        return chainQueryService.list();
    }
}
