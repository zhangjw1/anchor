package com.anchor.indexer.service.impl;

import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.dao.OutboxMapper;
import com.anchor.common.domain.OutboxStatusEnum;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.anchor.common.entity.OutboxEventDO;
import com.anchor.indexer.service.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 序列化事件并批量插入 outbox_event；Kafka 未启用时不入队。
 */
@Service
public class OutboxServiceImpl implements OutboxService {

    private final OutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;
    private final boolean kafkaEnabled;

    public OutboxServiceImpl(OutboxMapper outboxMapper, ObjectMapper objectMapper,
                             @Value("${anchor.kafka.enabled:true}") boolean kafkaEnabled) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Override
    public void enqueueSwaps(List<SwapEventDTO> events) {
        enqueue(KafkaConstants.SWAP_EVENTS, events);
    }

    @Override
    public void enqueueChain(List<ChainEventDTO> events) {
        enqueue(KafkaConstants.CHAIN_EVENTS, events);
    }

    /**
     * @param topic Kafka 主题常量
     * @param events SwapEventDTO 或 ChainEventDTO 列表；空或 Kafka 关闭时直接返回
     */
    private void enqueue(String topic, List<?> events) {
        // Kafka 未启用时不写 outbox，避免堆积无法投递的 PENDING
        if (!kafkaEnabled || events == null || events.isEmpty()) {
            return;
        }
        List<OutboxEventDO> rows = new ArrayList<>(events.size());
        for (Object event : events) {
            OutboxEventDO row = new OutboxEventDO();
            row.setTopic(topic);
            row.setMsgKey(key(event));
            row.setPayload(json(event));
            row.setStatus(OutboxStatusEnum.PENDING.name());
            row.setAttempts(0);
            row.setCreatedBy(AnchorConstants.ACTOR_INDEXER);
            row.setUpdatedBy(AnchorConstants.ACTOR_INDEXER);
            rows.add(row);
        }
        for (int from = 0; from < rows.size(); from += AnchorConstants.BATCH_SIZE) {
            outboxMapper.insertBatch(rows.subList(from, Math.min(from + AnchorConstants.BATCH_SIZE, rows.size())));
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
            throw new IllegalStateException("outbox payload failed", ex);
        }
    }
}
