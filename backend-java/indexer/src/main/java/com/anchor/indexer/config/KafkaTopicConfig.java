package com.anchor.indexer.config;

import com.anchor.common.constant.KafkaConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 声明交换事件和链事件两个主题。
 */
@Configuration
@ConditionalOnProperty(name = "anchor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {
    /**
     * 新交换事件主题。
     */
    @Bean
    public NewTopic swapEventsTopic() {
        return TopicBuilder.name(KafkaConstants.SWAP_EVENTS).partitions(1).replicas(1).build();
    }
    /**
     * 确认进度和重组事件主题。
     */
    @Bean
    public NewTopic chainEventsTopic() {
        return TopicBuilder.name(KafkaConstants.CHAIN_EVENTS).partitions(1).replicas(1).build();
    }
}
