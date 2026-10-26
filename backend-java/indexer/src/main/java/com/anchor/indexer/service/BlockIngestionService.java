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
     * 保存区块哈希和交换，并返回待发布的事件。检查点由调用方在发布成功后推进。
     */

    BlockIngestDTO persist(ChainDefinition chain, ChainBlock block, List<SwapParser.ParsedSwap> swaps, long finalizedBlock);
    /**
     * 把扫描进度推进到这个高度。
     */
    void advanceCheckpoint(long chainId, long blockNumber, String blockHash);
}
