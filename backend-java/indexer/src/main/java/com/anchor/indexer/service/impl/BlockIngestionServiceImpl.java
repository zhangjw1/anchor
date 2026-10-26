package com.anchor.indexer.service.impl;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.dataobject.SwapDO;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.indexer.service.BlockIngestionService;
import com.anchor.indexer.service.ConfirmationService;
import com.anchor.indexer.dto.BlockIngestDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 同一事务内写区块哈希、交换和确认状态。
 */
@Service
public class BlockIngestionServiceImpl implements BlockIngestionService {

    private final BlockHashMapper blockHashMapper;
    private final CheckpointMapper checkpointMapper;
    private final SwapMapper swapMapper;
    private final ConfirmationService confirmationService;

    public BlockIngestionServiceImpl(BlockHashMapper blockHashMapper, CheckpointMapper checkpointMapper,
                                     SwapMapper swapMapper, ConfirmationService confirmationService) {
        this.blockHashMapper = blockHashMapper;
        this.checkpointMapper = checkpointMapper;
        this.swapMapper = swapMapper;
        this.confirmationService = confirmationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlockIngestDTO persist(ChainDefinition chain, ChainBlock block, List<SwapParser.ParsedSwap> swaps, long finalizedBlock) {
        blockHashMapper.save(chain.chainId(), block.number(), block.hash(), block.parentHash(), AnchorConstants.ACTOR_INDEXER);
        long minKeep = block.number() - AnchorConstants.RECENT_HASH_LIMIT + 1;
        if (minKeep > 0) {
            blockHashMapper.deleteOlder(chain.chainId(), minKeep);
        }
        writeSwaps(swaps);
        List<ChainEventDTO> confirmations = confirmationService.apply(chain, block.number(), finalizedBlock);
        return new BlockIngestDTO(swaps, confirmations);
    }

    @Override
    public void advanceCheckpoint(long chainId, long blockNumber, String blockHash) {
        checkpointMapper.advance(chainId, blockNumber, blockHash, AnchorConstants.ACTOR_INDEXER);
    }

    private void writeSwaps(List<SwapParser.ParsedSwap> swaps) {
        if (swaps.isEmpty()) {
            return;
        }
        List<SwapDO> batch = new ArrayList<>();
        for (SwapParser.ParsedSwap swap : swaps) {
            batch.add(toDataObject(swap));
            if (batch.size() == AnchorConstants.BATCH_SIZE) {
                swapMapper.saveBatch(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            swapMapper.saveBatch(batch);
        }
    }

    private static SwapDO toDataObject(SwapParser.ParsedSwap swap) {
        SwapDO data = new SwapDO();
        data.setChainId(swap.chainId());
        data.setTxHash(swap.txHash());
        data.setLogIndex(swap.logIndex());
        data.setBlockNumber(swap.blockNumber());
        data.setBlockHash(swap.blockHash());
        data.setBlockTimestamp(swap.blockTimestamp());
        data.setTxFrom(swap.txFrom());
        data.setTxTo(swap.txTo());
        data.setDex(swap.dex());
        data.setDirection(swap.direction().name());
        data.setTokenIn(swap.tokenIn());
        data.setTokenOut(swap.tokenOut());
        data.setAmountIn(swap.amountIn());
        data.setAmountOut(swap.amountOut());
        data.setConfirmStatus(ConfirmStatusEnum.CONFIRMING.name());
        data.setWatchedAddress(swap.watchedAddress());
        data.setCreatedBy(AnchorConstants.ACTOR_INDEXER);
        data.setUpdatedBy(AnchorConstants.ACTOR_INDEXER);
        return data;
    }
}
