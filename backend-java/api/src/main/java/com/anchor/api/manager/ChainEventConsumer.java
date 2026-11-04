package com.anchor.api.manager;

import com.anchor.api.service.WebSocketPushService;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 消费 Kafka 上的交换和链事件，再交给 WebSocket 推送。
 * 解析/推送异常交由 DefaultErrorHandler 重试并转入 DLT，不再静默吞掉。
 */
@Component
@ConditionalOnProperty(name = "anchor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ChainEventConsumer {

    private final ObjectMapper objectMapper;
    private final WebSocketPushService webSocketPushService;

    public ChainEventConsumer(ObjectMapper objectMapper, WebSocketPushService webSocketPushService) {
        this.objectMapper = objectMapper;
        this.webSocketPushService = webSocketPushService;
    }

    @KafkaListener(topics = KafkaConstants.SWAP_EVENTS, groupId = AnchorConstants.KAFKA_GROUP_API)
    public void onSwap(String payload) throws Exception {
        webSocketPushService.onSwap(objectMapper.readValue(payload, SwapEventDTO.class));
    }

    @KafkaListener(topics = KafkaConstants.CHAIN_EVENTS, groupId = AnchorConstants.KAFKA_GROUP_API)
    public void onChain(String payload) throws Exception {
        webSocketPushService.onChain(objectMapper.readValue(payload, ChainEventDTO.class));
    }
}
