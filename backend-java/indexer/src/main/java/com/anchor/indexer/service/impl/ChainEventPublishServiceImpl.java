package com.anchor.indexer.service.impl;

import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.anchor.indexer.service.ChainEventPublishService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 同步等待 Kafka 确认。超时或失败向上抛出，让扫描重试同一块。
 */
@Service
@ConditionalOnProperty(name = "anchor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ChainEventPublishServiceImpl implements ChainEventPublishService {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ChainEventPublishServiceImpl(KafkaTemplate<Object, Object> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishSwaps(List<SwapEventDTO> events) {
        send(KafkaConstants.SWAP_EVENTS, events);
    }

    @Override
    public void publishChain(List<ChainEventDTO> events) {
        send(KafkaConstants.CHAIN_EVENTS, events);
    }

    private void send(String topic, List<?> events) {
        if (events.isEmpty()) {
            return;
        }
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Object event : events) {
            futures.add(kafkaTemplate.send(topic, key(event), json(event)));
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(AnchorConstants.KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("kafka send interrupted", ex);
        } catch (TimeoutException | ExecutionException ex) {
            throw new IllegalStateException("kafka send failed", ex);
        }
    }

    private static String key(Object event) {
        long chainId = event instanceof SwapEventDTO swap ? swap.chainId() : ((ChainEventDTO) event).chainId();
        return Long.toString(chainId);
    }

    private String json(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("kafka payload failed", ex);
        }
    }
}
