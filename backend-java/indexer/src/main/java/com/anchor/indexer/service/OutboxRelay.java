package com.anchor.indexer.service;

import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.OutboxMapper;
import com.anchor.common.entity.OutboxEventDO;
import com.anchor.common.metrics.AnchorMetricNames;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 领取 PENDING outbox 并投递 Kafka。同批任一行失败即停止，保证同 key 顺序；Kafka 未启用时不处理、不 markSent。
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxMapper outboxMapper;
    private final ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final AtomicLong pendingGauge = new AtomicLong();

    public OutboxRelay(OutboxMapper outboxMapper, ObjectProvider<KafkaTemplate<Object, Object>> kafkaTemplate,
                       MeterRegistry meterRegistry) {
        this.outboxMapper = outboxMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauge() {
        Gauge.builder(AnchorMetricNames.OUTBOX_PENDING, pendingGauge, AtomicLong::get)
                .description("Pending outbox events")
                .register(meterRegistry);
    }

    /**
     * 定时领取 PENDING 并投递。事务内持有行锁，多实例靠 SKIP LOCKED 分片。
     * 无 KafkaTemplate 时直接返回，绝不 markSent，避免静默丢事件。
     */
    @Scheduled(fixedDelayString = "${anchor.outbox.relay-delay-ms:500}")
    @Transactional(rollbackFor = Exception.class)
    public void relay() {
        pendingGauge.set(outboxMapper.countPending());
        KafkaTemplate<Object, Object> template = kafkaTemplate.getIfAvailable();
        if (template == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<OutboxEventDO> batch = outboxMapper.claimPending(AnchorConstants.OUTBOX_BATCH_SIZE, now);
        if (batch.isEmpty()) {
            return;
        }
        // 同批按 id 顺序发送；任一条失败即停，避免同 key 后发先到
        for (OutboxEventDO row : batch) {
            if (!deliver(row, now, template)) {
                break;
            }
        }
        pendingGauge.set(outboxMapper.countPending());
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        OffsetDateTime before = OffsetDateTime.now().minusDays(AnchorConstants.OUTBOX_RETENTION_DAYS);
        int deleted = outboxMapper.deleteSentBefore(before);
        if (deleted > 0) {
            log.info("Outbox cleanup deleted {} sent rows before {}", deleted, before);
        }
    }

    /**
     * @return true 表示可继续下一条；false 表示本轮应停止（保序）
     */
    boolean deliver(OutboxEventDO row, OffsetDateTime now, KafkaTemplate<Object, Object> template) {
        try {
            template.send(row.getTopic(), row.getMsgKey(), row.getPayload())
                    .get(AnchorConstants.OUTBOX_SEND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            outboxMapper.markSent(row.getId(), AnchorConstants.ACTOR_INDEXER, OffsetDateTime.now());
            return true;
        } catch (Exception ex) {
            int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
            OffsetDateTime next = now.plusSeconds(backoffSeconds(attempts));
            outboxMapper.markFailed(row.getId(), attempts, next, AnchorConstants.ACTOR_INDEXER);
            meterRegistry.counter(AnchorMetricNames.OUTBOX_PUBLISH_FAILURES, "topic", row.getTopic()).increment();
            log.warn("Outbox publish failed id={} topic={} attempts={}: {}", row.getId(), row.getTopic(), attempts, ex.getMessage());
            return false;
        }
    }

    static long backoffSeconds(int attempts) {
        long seconds = 1L << Math.min(6, Math.max(0, attempts - 1));
        return Math.min(AnchorConstants.OUTBOX_MAX_BACKOFF_SECONDS, seconds);
    }
}
