package com.anchor.api.config;

import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.metrics.AnchorMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 消费失败重试后发往 DLT，并打失败指标。
 */
@Configuration
@ConditionalOnProperty(name = "anchor.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> {
            String dlt = dltTopic(record.topic());
            meterRegistry.counter(AnchorMetricNames.KAFKA_DLT_PUBLISHED, "topic", record.topic()).increment();
            log.error("Kafka record sent to DLT, topic={}, partition={}, offset={}, dlt={}",
                    record.topic(), record.partition(), record.offset(), dlt, ex);
            return new TopicPartition(dlt, record.partition());
        });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
        handler.setRetryListeners((ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) ->
                meterRegistry.counter(AnchorMetricNames.KAFKA_CONSUME_FAILURES, "topic", record.topic()).increment());
        return handler;
    }

    private static String dltTopic(String topic) {
        if (KafkaConstants.SWAP_EVENTS.equals(topic)) {
            return KafkaConstants.SWAP_EVENTS_DLT;
        }
        if (KafkaConstants.CHAIN_EVENTS.equals(topic)) {
            return KafkaConstants.CHAIN_EVENTS_DLT;
        }
        return topic + ".DLT";
    }
}
