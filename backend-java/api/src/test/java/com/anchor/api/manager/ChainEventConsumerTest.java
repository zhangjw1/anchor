package com.anchor.api.manager;

import com.anchor.api.service.WebSocketPushService;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ChainEventConsumerTest {

    private WebSocketPushService pushService;
    private ChainEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        pushService = mock(WebSocketPushService.class);
        objectMapper = new ObjectMapper();
        consumer = new ChainEventConsumer(objectMapper, pushService);
    }

    @Test
    void remembersSwapOnce() throws Exception {
        SwapEventDTO event = new SwapEventDTO(8453L, "0xabc", 1, 10L, "BUY", "UNI", null,
                "0xin", "0xout", "1", "2", "CONFIRMING", "0xwatch", List.of());
        String payload = objectMapper.writeValueAsString(event);

        consumer.onSwap(payload);
        consumer.onSwap(payload);

        verify(pushService, times(1)).onSwap(any());
        assertTrue(consumer.remember("other-key"));
        assertFalse(consumer.remember("other-key"));
    }

    @Test
    void chainKeyUsesTypeAndBlock() {
        ChainEventDTO confirmation = ChainEventDTO.confirmation(8453L, 100L, "0xabc", 2, "FINALIZED", 10, "0xwatch");
        assertTrue(ChainEventConsumer.chainKey(confirmation).contains(KafkaConstants.CONFIRMATION));
        ChainEventDTO reorg = ChainEventDTO.reorg(8453L, 3, 90L, 91L, 100L);
        assertTrue(ChainEventConsumer.chainKey(reorg).startsWith("8453|reorg|"));
    }
}
