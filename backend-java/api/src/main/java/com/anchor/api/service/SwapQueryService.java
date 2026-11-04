package com.anchor.api.service;

/**
 * 交换记录分页查询（keyset）。
 */
public interface SwapQueryService {

    SwapPageResult list(long chainId, String address, String dex, String direction, Integer size,
                        boolean includeReorged, boolean includeSeed, Long beforeBlock, Integer beforeLogIndex);
}
