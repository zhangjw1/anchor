package com.anchor.indexer.service.impl;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.entity.SwapDO;
import com.anchor.indexer.service.ConfirmationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 达到配置的确认块数记为已确认，进入最终高度后记为已最终确认。
 * 按区块区间批量 UPDATE，仅对状态真正变化的行发 CONFIRMATION 事件。
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
        // 先 FINALIZED：CONFIRMING 可直接跳到 FINALIZED，避免先变 CONFIRMED 再二次更新
        for (SwapDO swap : swapMapper.promoteToFinalized(chain.chainId(), finalizedBlock, AnchorConstants.ACTOR_INDEXER)) {
            events.add(toEvent(chain.chainId(), head, swap, ConfirmStatusEnum.FINALIZED.name()));
        }
        long confirmedThrough = head - chain.confirmationBlocks() + 1;
        if (confirmedThrough >= 0) {
            for (SwapDO swap : swapMapper.promoteToConfirmed(chain.chainId(), confirmedThrough, AnchorConstants.ACTOR_INDEXER)) {
                events.add(toEvent(chain.chainId(), head, swap, ConfirmStatusEnum.CONFIRMED.name()));
            }
        }
        return events;
    }

    private static ChainEventDTO toEvent(long chainId, long head, SwapDO swap, String status) {
        int confirmations = (int) Math.max(0, head - swap.getBlockNumber() + 1);
        return ChainEventDTO.confirmation(chainId, head, swap.getTxHash(), swap.getLogIndex(), status, confirmations,
                swap.getWatchedAddress());
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
