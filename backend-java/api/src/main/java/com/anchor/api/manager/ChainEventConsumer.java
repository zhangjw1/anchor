package com.anchor.api.manager;

import com.anchor.api.service.WebSocketPushService;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 消费 Kafka 上的交换和链事件，再交给 WebSocket 推送。
 * at-least-once 下按短 TTL 去重，解析/推送异常交由 DefaultErrorHandler 重试并转入 DLT。
 */
@Component
@ConditionalOnProperty(name = "anchor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class ChainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChainEventConsumer.class);
    private static final Object PRESENT = new Object();

    private final ObjectMapper objectMapper;
    private final WebSocketPushService webSocketPushService;
    private final Cache<String, Object> seen;

    public ChainEventConsumer(ObjectMapper objectMapper, WebSocketPushService webSocketPushService) {
        this.objectMapper = objectMapper;
        this.webSocketPushService = webSocketPushService;
        this.seen = Caffeine.newBuilder()
                .expireAfterWrite(AnchorConstants.EVENT_DEDUPE_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(AnchorConstants.EVENT_DEDUPE_MAX_SIZE)
                .build();
    }

    /**
     * @param payload Kafka 消息体，JSON 对应 SwapEventDTO
     */
    @KafkaListener(topics = KafkaConstants.SWAP_EVENTS, groupId = AnchorConstants.KAFKA_GROUP_API)
    public void onSwap(String payload) throws Exception {
        SwapEventDTO event = objectMapper.readValue(payload, SwapEventDTO.class);
        // outbox 至少一次投递；短 TTL 内重复消息直接跳过，避免 WS 抖动
        if (!remember(swapKey(event))) {
            log.debug("Skip duplicate swap txHash={} logIndex={}", event.txHash(), event.logIndex());
            return;
        }
        webSocketPushService.onSwap(event);
    }

    /**
     * @param payload Kafka 消息体，JSON 对应 ChainEventDTO（CONFIRMATION / REORG）
     */
    @KafkaListener(topics = KafkaConstants.CHAIN_EVENTS, groupId = AnchorConstants.KAFKA_GROUP_API)
    public void onChain(String payload) throws Exception {
        ChainEventDTO event = objectMapper.readValue(payload, ChainEventDTO.class);
        if (!remember(chainKey(event))) {
            log.debug("Skip duplicate chain type={} txHash={} block={}", event.type(), event.txHash(), event.blockNumber());
            return;
        }
        webSocketPushService.onChain(event);
    }

    /**
     * @param key 去重键
     * @return true 表示首次见到，应继续处理；false 表示窗口内重复
     */
    boolean remember(String key) {
        return seen.asMap().putIfAbsent(key, PRESENT) == null;
    }

    /** swap 幂等键：chainId + txHash + logIndex */
    static String swapKey(SwapEventDTO event) {
        return event.chainId() + "|swap|" + event.txHash() + "|" + event.logIndex();
    }

    /** 确认按 type+txHash+block；reorg 按区间，避免不同事件互相误伤 */
    static String chainKey(ChainEventDTO event) {
        if (KafkaConstants.REORG.equals(event.type())) {
            return event.chainId() + "|reorg|" + event.fromBlock() + "|" + event.toBlock();
        }
        return event.chainId() + "|" + event.type() + "|" + event.txHash() + "|" + event.blockNumber();
    }
}
