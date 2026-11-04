package com.anchor.indexer.manager;

import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.domain.ConfirmStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReorgManagerTest {

    @Test
    void applyMarksSwapsDeletesHashesAndRewindsCheckpoint() {
        BlockHashMapper blockHashMapper = mock(BlockHashMapper.class);
        CheckpointMapper checkpointMapper = mock(CheckpointMapper.class);
        SwapMapper swapMapper = mock(SwapMapper.class);
        when(swapMapper.markReorgedAfter(8453L, 100L, ConfirmStatusEnum.REORGED.name(), AnchorConstants.ACTOR_INDEXER))
                .thenReturn(2);
        when(blockHashMapper.deleteAfter(8453L, 100L)).thenReturn(3);
        when(checkpointMapper.rewind(8453L, 100L, "0xabc", AnchorConstants.ACTOR_INDEXER)).thenReturn(1);

        ReorgManager manager = new ReorgManager(blockHashMapper, checkpointMapper, swapMapper);
        assertEquals(6, manager.apply(8453L, 100L, "0xabc"));

        verify(swapMapper).markReorgedAfter(8453L, 100L, ConfirmStatusEnum.REORGED.name(), AnchorConstants.ACTOR_INDEXER);
        verify(blockHashMapper).deleteAfter(8453L, 100L);
        verify(checkpointMapper).rewind(8453L, 100L, "0xabc", AnchorConstants.ACTOR_INDEXER);
    }
}
