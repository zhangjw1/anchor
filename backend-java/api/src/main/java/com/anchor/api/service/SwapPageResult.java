package com.anchor.api.service;

import com.anchor.common.dto.SwapDTO;

import java.util.List;

/**
 * Swap 列表 keyset 分页结果。
 */
public record SwapPageResult(
        List<SwapDTO> items,
        long size,
        long total,
        boolean hasMore,
        Long nextBeforeBlock,
        Integer nextBeforeLogIndex) {
}
