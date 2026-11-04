package com.anchor.api.vo;

import com.anchor.api.service.SwapPageResult;

import java.util.List;

/**
 * 交换分页结果（keyset）。
 */
public record SwapPageVO(
        List<SwapVO> items,
        long page,
        long size,
        long total,
        boolean hasMore,
        Long nextBeforeBlock,
        Integer nextBeforeLogIndex) {

    public static SwapPageVO from(SwapPageResult result) {
        List<SwapVO> items = result.items().stream().map(SwapVO::from).toList();
        // page 固定为 1：深度翻页请用 nextBefore* 游标，不再支持 offset page
        return new SwapPageVO(items, 1L, result.size(), result.total(), result.hasMore(),
                result.nextBeforeBlock(), result.nextBeforeLogIndex());
    }
}
