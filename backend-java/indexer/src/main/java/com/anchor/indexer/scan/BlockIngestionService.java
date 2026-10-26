package com.anchor.indexer.scan;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.domain.AnchorConstants;
import com.anchor.common.domain.ConfirmStatus;
import com.anchor.common.persistence.entity.SwapEntity;
import com.anchor.common.persistence.mapper.BlockHashMapper;
import com.anchor.common.persistence.mapper.CheckpointMapper;
import com.anchor.common.persistence.mapper.SwapMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class BlockIngestionService {

    private final BlockHashMapper blockHashMapper;
    private final CheckpointMapper checkpointMapper;
    private final SwapMapper swapMapper;

    public BlockIngestionService(BlockHashMapper blockHashMapper, CheckpointMapper checkpointMapper, SwapMapper swapMapper) {
        this.blockHashMapper = blockHashMapper;
        this.checkpointMapper = checkpointMapper;
        this.swapMapper = swapMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void persist(com.anchor.common.chain.ChainDefinition chain, ChainBlock block, List<SwapParser.ParsedSwap> swaps) {
        blockHashMapper.upsert(chain.chainId(), block.number(), block.hash(), block.parentHash(), AnchorConstants.ACTOR_INDEXER);
        long minKeep = block.number() - AnchorConstants.RECENT_HASH_LIMIT + 1;
        if (minKeep > 0) {
            blockHashMapper.deleteOlder(chain.chainId(), minKeep);
        }
        writeSwaps(swaps);
        checkpointMapper.advance(chain.chainId(), block.number(), block.hash(), AnchorConstants.ACTOR_INDEXER);
    }

    private void writeSwaps(List<SwapParser.ParsedSwap> swaps) {
        if (swaps.isEmpty()) {
            return;
        }
        List<SwapEntity> batch = new ArrayList<>();
        for (SwapParser.ParsedSwap swap : swaps) {
            batch.add(toEntity(swap));
            if (batch.size() == AnchorConstants.BATCH_SIZE) {
                swapMapper.upsertBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            swapMapper.upsertBatch(batch);
        }
    }

    private static SwapEntity toEntity(SwapParser.ParsedSwap swap) {
        SwapEntity entity = new SwapEntity();
        entity.setChainId(swap.chainId());
        entity.setTxHash(swap.txHash());
        entity.setLogIndex(swap.logIndex());
        entity.setBlockNumber(swap.blockNumber());
        entity.setBlockHash(swap.blockHash());
        entity.setBlockTimestamp(swap.blockTimestamp());
        entity.setTxFrom(swap.txFrom());
        entity.setTxTo(swap.txTo());
        entity.setDex(swap.dex());
        entity.setDirection(swap.direction().name());
        entity.setTokenIn(swap.tokenIn());
        entity.setTokenOut(swap.tokenOut());
        entity.setAmountIn(swap.amountIn());
        entity.setAmountOut(swap.amountOut());
        entity.setConfirmStatus(ConfirmStatus.CONFIRMING.name());
        entity.setWatchedAddress(swap.watchedAddress());
        entity.setCreatedBy(AnchorConstants.ACTOR_INDEXER);
        entity.setUpdatedBy(AnchorConstants.ACTOR_INDEXER);
        return entity;
    }
}
