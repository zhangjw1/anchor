package com.anchor.indexer.service.impl;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.entity.SwapDO;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.indexer.service.ConfirmationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 达到配置的确认块数记为已确认，进入最终高度后记为已最终确认。
 */
@Service
public class ConfirmationServiceImpl implements ConfirmationService {

    private final SwapMapper swapMapper;

    public ConfirmationServiceImpl(SwapMapper swapMapper) {
        this.swapMapper = swapMapper;
    }

    @Override
    public List<ChainEventDTO> apply(ChainDefinition chain, long head, long finalizedBlock) {
        List<ChainEventDTO> events = new ArrayList<>();
        for (SwapDO swap : swapMapper.listActive(chain.chainId(), head)) {
            String next = nextStatus(swap.getBlockNumber(), head, finalizedBlock, chain.confirmationBlocks());
            boolean changed = !next.equals(swap.getConfirmStatus());
            if (changed) {
                swapMapper.updateStatus(chain.chainId(), swap.getTxHash(), swap.getLogIndex(), next, AnchorConstants.ACTOR_INDEXER);
            }
            if (swap.getBlockNumber() < head || changed) {
                // 计算区块交易的确认进度
                int confirmations = (int) (head - swap.getBlockNumber() + 1);
                events.add(ChainEventDTO.confirmation(chain.chainId(), head, swap.getTxHash(), swap.getLogIndex(), next, confirmations, swap.getWatchedAddress()));
            }
        }
        return events;
    }
    /**
     * 高度不超过最终块为已最终确认，确认数达标为已确认，否则仍是确认中。
     */

    static String nextStatus(long blockNumber, long head, long finalizedBlock, int confirmationBlocks) {
        if (blockNumber <= finalizedBlock) {
            return ConfirmStatusEnum.FINALIZED.name();
        }
        long confirmations = head - blockNumber + 1;
        if (confirmations >= confirmationBlocks) {
            return ConfirmStatusEnum.CONFIRMED.name();
        }
        return ConfirmStatusEnum.CONFIRMING.name();
    }
}
