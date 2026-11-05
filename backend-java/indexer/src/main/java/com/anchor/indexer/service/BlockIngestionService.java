package com.anchor.indexer.service;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.SwapParser;
import com.anchor.indexer.dto.BlockIngestDTO;

import java.util.List;

/**
 * 把一个区块的交换写入数据库，并计算确认进度。
 */
public interface BlockIngestionService {
    /**
     * 同一事务：写区块/交换、写 outbox、推进 checkpoint。Kafka 由 OutboxRelay 异步投递。
     */
    BlockIngestDTO persist(ChainDefinition chain, ChainBlock block, List<SwapParser.ParsedSwap> swaps, long finalizedBlock);
}
