package com.anchor.indexer.service.impl;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.anchor.common.domain.GasModelEnum;
import com.anchor.common.domain.SignModeEnum;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.entity.SwapDO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmationServiceImplTest {

    @Test
    void nextStatusPrefersFinalizedThenConfirmed() {
        assertEquals(ConfirmStatusEnum.FINALIZED.name(), ConfirmationServiceImpl.nextStatus(90, 100, 95, 10));
        assertEquals(ConfirmStatusEnum.CONFIRMED.name(), ConfirmationServiceImpl.nextStatus(90, 100, 80, 10));
        assertEquals(ConfirmStatusEnum.CONFIRMING.name(), ConfirmationServiceImpl.nextStatus(95, 100, 80, 10));
    }

    @Test
    void applyEmitsOnlyChangedRows() {
        SwapMapper swapMapper = mock(SwapMapper.class);
        ConfirmationServiceImpl service = new ConfirmationServiceImpl(swapMapper);
        ChainDefinition chain = chain(10);

        SwapDO finalized = swap(80L, "0xfin", ConfirmStatusEnum.FINALIZED.name());
        SwapDO confirmed = swap(91L, "0xconf", ConfirmStatusEnum.CONFIRMED.name());
        when(swapMapper.promoteToFinalized(eq(8453L), eq(90L), eq(AnchorConstants.ACTOR_INDEXER)))
                .thenReturn(List.of(finalized));
        when(swapMapper.promoteToConfirmed(eq(8453L), eq(91L), eq(AnchorConstants.ACTOR_INDEXER)))
                .thenReturn(List.of(confirmed));

        List<ChainEventDTO> events = service.apply(chain, 100L, 90L);
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(e -> KafkaConstants.CONFIRMATION.equals(e.type())));
        assertEquals(ConfirmStatusEnum.FINALIZED.name(), events.get(0).confirmStatus());
        assertEquals(ConfirmStatusEnum.CONFIRMED.name(), events.get(1).confirmStatus());
        verify(swapMapper, never()).listActive(eq(8453L), eq(100L));
    }

    @Test
    void applySkipsConfirmedPromotionWhenWindowNegative() {
        SwapMapper swapMapper = mock(SwapMapper.class);
        ConfirmationServiceImpl service = new ConfirmationServiceImpl(swapMapper);
        when(swapMapper.promoteToFinalized(eq(8453L), eq(0L), eq(AnchorConstants.ACTOR_INDEXER)))
                .thenReturn(List.of());

        List<ChainEventDTO> events = service.apply(chain(10), 5L, 0L);
        assertTrue(events.isEmpty());
        verify(swapMapper, never()).promoteToConfirmed(anyLong(), anyLong(), anyString());
    }

    private static ChainDefinition chain(int confirmationBlocks) {
        return new ChainDefinition(
                "base", 8453L, "Base", ChainTypeEnum.EVM, false, confirmationBlocks,
                GasModelEnum.EIP1559_OP_L1_FEE, List.of(SignModeEnum.CLIENT),
                new ChainDefinition.NativeToken("ETH", 18, AnchorConstants.NATIVE_TOKEN_ADDRESS),
                List.of(),
                new ChainDefinition.Contracts(null, null, null, null, null, null, null, null, null, null, null, null, null),
                Set.of(), List.of());
    }

    private static SwapDO swap(long block, String hash, String status) {
        SwapDO data = new SwapDO();
        data.setChainId(8453L);
        data.setTxHash(hash);
        data.setLogIndex(1);
        data.setBlockNumber(block);
        data.setConfirmStatus(status);
        data.setWatchedAddress("0xabc");
        return data;
    }
}
