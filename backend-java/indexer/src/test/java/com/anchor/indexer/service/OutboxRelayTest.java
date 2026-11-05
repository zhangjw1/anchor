package com.anchor.indexer.service;

import com.anchor.common.dao.OutboxMapper;
import com.anchor.common.entity.OutboxEventDO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private OutboxMapper outboxMapper;
    private KafkaTemplate<Object, Object> kafkaTemplate;
    private ObjectProvider<KafkaTemplate<Object, Object>> kafkaProvider;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        outboxMapper = mock(OutboxMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        kafkaProvider = mock(ObjectProvider.class);
        when(outboxMapper.countPending()).thenReturn(0L);
        relay = new OutboxRelay(outboxMapper, kafkaProvider, new SimpleMeterRegistry());
        relay.registerGauge();
    }

    @Test
    void kafkaDisabledDoesNotClaimOrMarkSent() {
        when(kafkaProvider.getIfAvailable()).thenReturn(null);
        when(outboxMapper.countPending()).thenReturn(3L);

        relay.relay();

        verify(outboxMapper, never()).claimPending(anyInt(), any());
        verify(outboxMapper, never()).markSent(anyLong(), anyString(), any());
    }

    @Test
    void firstFailureStopsBatch() {
        when(kafkaProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        OutboxEventDO first = row(1L, "swap-events");
        OutboxEventDO second = row(2L, "swap-events");
        when(outboxMapper.claimPending(anyInt(), any())).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(eq("swap-events"), eq("8453"), any()))
                .thenReturn(failedFuture())
                .thenReturn(okFuture());

        relay.relay();

        verify(outboxMapper, times(1)).markFailed(eq(1L), eq(1), any(), anyString());
        verify(outboxMapper, never()).markSent(eq(2L), anyString(), any());
        verify(kafkaTemplate, times(1)).send(eq("swap-events"), eq("8453"), any());
    }

    @Test
    void deliverSuccessContinues() {
        when(kafkaProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(okFuture());
        OutboxEventDO row = row(9L, "chain-events");
        OffsetDateTime now = OffsetDateTime.now();

        assertTrue(relay.deliver(row, now, kafkaTemplate));
        verify(outboxMapper).markSent(eq(9L), anyString(), any());
    }

    @Test
    void deliverFailureReturnsFalseAndBacksOff() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture());
        OutboxEventDO row = row(3L, "swap-events");
        row.setAttempts(0);
        OffsetDateTime now = OffsetDateTime.now();

        assertFalse(relay.deliver(row, now, kafkaTemplate));
        verify(outboxMapper).markFailed(eq(3L), eq(1), any(), anyString());
        assertEquals(1L, OutboxRelay.backoffSeconds(1));
        assertEquals(2L, OutboxRelay.backoffSeconds(2));
        assertEquals(60L, OutboxRelay.backoffSeconds(20));
    }

    private static OutboxEventDO row(long id, String topic) {
        OutboxEventDO row = new OutboxEventDO();
        row.setId(id);
        row.setTopic(topic);
        row.setMsgKey("8453");
        row.setPayload("{}");
        row.setAttempts(0);
        return row;
    }

    private static CompletableFuture<SendResult<Object, Object>> okFuture() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<SendResult<Object, Object>> failedFuture() {
        return CompletableFuture.failedFuture(new RuntimeException("kafka down"));
    }
}
