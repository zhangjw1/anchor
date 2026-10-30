package com.anchor.indexer.service.impl;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dao.SwapHopMapper;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.entity.SwapDO;
import com.anchor.common.entity.SwapHopDO;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.indexer.service.BlockIngestionService;
import com.anchor.indexer.service.ConfirmationService;
import com.anchor.indexer.dto.BlockIngestDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同一事务内写区块哈希、交换、路径跳和确认状态。
 */
@Service
public class BlockIngestionServiceImpl implements BlockIngestionService {

    private final BlockHashMapper blockHashMapper;
    private final CheckpointMapper checkpointMapper;
    private final SwapMapper swapMapper;
    private final SwapHopMapper swapHopMapper;
    private final ConfirmationService confirmationService;

    public BlockIngestionServiceImpl(BlockHashMapper blockHashMapper, CheckpointMapper checkpointMapper,
                                     SwapMapper swapMapper, SwapHopMapper swapHopMapper,
                                     ConfirmationService confirmationService) {
        this.blockHashMapper = blockHashMapper;
        this.checkpointMapper = checkpointMapper;
        this.swapMapper = swapMapper;
        this.swapHopMapper = swapHopMapper;
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
        writeSwaps(chain.chainId(), swaps);
        List<ChainEventDTO> confirmations = confirmationService.apply(chain, block.number(), finalizedBlock);
        return new BlockIngestDTO(swaps, confirmations);
    }

    @Override
    public void advanceCheckpoint(long chainId, long blockNumber, String blockHash) {
        checkpointMapper.advance(chainId, blockNumber, blockHash, AnchorConstants.ACTOR_INDEXER);
    }

    private void writeSwaps(long chainId, List<SwapParser.ParsedSwap> swaps) {
        if (swaps.isEmpty()) {
            return;
        }
        List<SwapDO> rows = swaps.stream().map(BlockIngestionServiceImpl::toDataObject).toList();
        for (List<SwapDO> batch : partition(rows)) {
            swapMapper.saveBatch(batch);
        }
        writeHops(chainId, swaps, rows);
    }

    /**
     * 先批量取回 swap 主键，再整体替换这些 swap 的路径跳，保证重复入库幂等。
     */
    private void writeHops(long chainId, List<SwapParser.ParsedSwap> swaps, List<SwapDO> rows) {
        Map<String, Long> idByKey = new HashMap<>();
        for (List<SwapDO> batch : partition(rows)) {
            for (SwapDO saved : swapMapper.listIdsByKeys(chainId, batch)) {
                idByKey.put(swapKey(saved.getTxHash(), saved.getLogIndex()), saved.getId());
            }
        }
        List<Long> swapIds = new ArrayList<>();
        List<SwapHopDO> hops = new ArrayList<>();
        for (SwapParser.ParsedSwap swap : swaps) {
            Long swapId = idByKey.get(swapKey(swap.txHash(), swap.logIndex()));
            if (swapId == null) {
                continue;
            }
            swapIds.add(swapId);
            for (SwapParser.ParsedHop hop : swap.hops()) {
                hops.add(toHop(swapId, hop));
            }
        }
        for (List<Long> batch : partition(swapIds)) {
            swapHopMapper.deleteBySwapIds(batch);
        }
        for (List<SwapHopDO> batch : partition(hops)) {
            swapHopMapper.saveBatch(batch);
        }
    }

    private static String swapKey(String txHash, int logIndex) {
        return txHash + ":" + logIndex;
    }

    private static <T> List<List<T>> partition(List<T> items) {
        List<List<T>> batches = new ArrayList<>();
        for (int from = 0; from < items.size(); from += AnchorConstants.BATCH_SIZE) {
            batches.add(items.subList(from, Math.min(from + AnchorConstants.BATCH_SIZE, items.size())));
        }
        return batches;
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
        data.setAggregator(swap.aggregator());
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

    private static SwapHopDO toHop(long swapId, SwapParser.ParsedHop hop) {
        SwapHopDO data = new SwapHopDO();
        data.setSwapId(swapId);
        data.setHopIndex(hop.hopIndex());
        data.setDex(hop.dex());
        data.setPool(hop.pool());
        data.setTokenIn(hop.tokenIn());
        data.setTokenOut(hop.tokenOut());
        data.setAmountIn(hop.amountIn());
        data.setAmountOut(hop.amountOut());
        data.setLogIndex(hop.logIndex());
        data.setCreatedBy(AnchorConstants.ACTOR_INDEXER);
        data.setUpdatedBy(AnchorConstants.ACTOR_INDEXER);
        return data;
    }
}
