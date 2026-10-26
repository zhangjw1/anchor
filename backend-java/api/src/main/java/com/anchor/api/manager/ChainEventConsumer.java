package com.anchor.api.manager;

import com.anchor.api.service.WebSocketPushService;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 消费 Kafka 上的交换和链事件，再交给 WebSocket 推送。
 */
@Component
@ConditionalOnProperty(name = "anchor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ChainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChainEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final WebSocketPushService webSocketPushService;

    public ChainEventConsumer(ObjectMapper objectMapper, WebSocketPushService webSocketPushService) {
        this.objectMapper = objectMapper;
        this.webSocketPushService = webSocketPushService;
    }
    /**
     * 解析交换事件。解析失败时丢弃本条，避免阻塞后续消息。
     */
    @KafkaListener(topics = KafkaConstants.SWAP_EVENTS, groupId = AnchorConstants.KAFKA_GROUP_API)
    public void onSwap(String payload) {
        try {
            webSocketPushService.onSwap(objectMapper.readValue(payload, SwapEventDTO.class));
        } catch (Exception ex) {
            log.error("交换事件已丢弃", ex);
        }
    }
    /**
     * 解析确认或重组事件。解析失败时丢弃本条。
     */
    @KafkaListener(topics = KafkaConstants.CHAIN_EVENTS, groupId = AnchorConstants.KAFKA_GROUP_API)
    public void onChain(String payload) {
        try {
            webSocketPushService.onChain(objectMapper.readValue(payload, ChainEventDTO.class));
        } catch (Exception ex) {
            log.error("链事件已丢弃", ex);
        }
    }
}
