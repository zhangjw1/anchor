package com.anchor.indexer.service;

import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;

import java.util.List;

/**
 * 将事件写入 outbox，与业务库操作同事务提交。
 */
public interface OutboxService {

    void enqueueSwaps(List<SwapEventDTO> events);

    void enqueueChain(List<ChainEventDTO> events);
}
