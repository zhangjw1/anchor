package com.anchor.indexer.manager;

import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.domain.ConfirmStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在一个事务里标记重组交换、删除分叉后的哈希并回退检查点。
 */
@Service
public class ReorgManager {

    private final BlockHashMapper blockHashMapper;
    private final CheckpointMapper checkpointMapper;
    private final SwapMapper swapMapper;

    public ReorgManager(BlockHashMapper blockHashMapper, CheckpointMapper checkpointMapper, SwapMapper swapMapper) {
        this.blockHashMapper = blockHashMapper;
        this.checkpointMapper = checkpointMapper;
        this.swapMapper = swapMapper;
    }
    /**
     * 祖先块本身保留。返回值只用于判断这次是否真的改了数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public int apply(long chainId, long ancestorBlock, String ancestorHash) {
        int swaps = swapMapper.markReorgedAfter(chainId, ancestorBlock, ConfirmStatusEnum.REORGED.name(), AnchorConstants.ACTOR_INDEXER);
        int hashes = blockHashMapper.deleteAfter(chainId, ancestorBlock);
        int checkpoint = checkpointMapper.rewind(chainId, ancestorBlock, ancestorHash, AnchorConstants.ACTOR_INDEXER);
        return swaps + hashes + checkpoint;
    }
}
