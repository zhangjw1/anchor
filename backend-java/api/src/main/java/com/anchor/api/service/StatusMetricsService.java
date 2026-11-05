package com.anchor.api.service;

import com.anchor.api.vo.ChainMetricsVO;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dao.OutboxMapper;
import com.anchor.common.metrics.AnchorMetricNames;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 状态栏指标：链头/Kafka lag 后台刷新缓存，snapshot 只读缓存，避免请求线程被 RPC/Kafka 拖死。
 */
@Service
public class StatusMetricsService {

    private static final Logger log = LoggerFactory.getLogger(StatusMetricsService.class);
    private static final long KAFKA_ADMIN_TIMEOUT_MS = 1_500L;

    private final CheckpointMapper checkpointMapper;
    private final OutboxMapper outboxMapper;
    private final RpcGateway rpcGateway;
    private final ChainRegistry chainRegistry;
    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<KafkaAdmin> kafkaAdmin;
    private final Map<Long, RateSample> rpcSamples = new ConcurrentHashMap<>();
    private final Map<Long, RateSnapshot> rpcRateCache = new ConcurrentHashMap<>();
    private final Map<Long, AtomicLong> headCache = new ConcurrentHashMap<>();
    private final AtomicLong lastKafkaLag = new AtomicLong(-1L);
    private final AtomicLong lastKafkaSuccessAtMs = new AtomicLong(0L);
    private final AtomicLong outboxPendingCache = new AtomicLong(0L);
    private final AtomicReference<AdminClient> adminClientRef = new AtomicReference<>();
    private ScheduledExecutorService scheduler;

    public StatusMetricsService(CheckpointMapper checkpointMapper, OutboxMapper outboxMapper, RpcGateway rpcGateway,
                                ChainRegistry chainRegistry, StringRedisTemplate redis, MeterRegistry meterRegistry,
                                ObjectProvider<KafkaAdmin> kafkaAdmin) {
        this.checkpointMapper = checkpointMapper;
        this.outboxMapper = outboxMapper;
        this.rpcGateway = rpcGateway;
        this.chainRegistry = chainRegistry;
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.kafkaAdmin = kafkaAdmin;
    }

    @PostConstruct
    void startRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::refreshCaches, 0L, AnchorConstants.METRICS_REFRESH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        AdminClient client = adminClientRef.getAndSet(null);
        if (client != null) {
            try {
                client.close(java.time.Duration.ofSeconds(2));
            } catch (Exception ignored) {
                // ignore close errors on shutdown
            }
        }
    }

    /**
     * 向已有 METRICS 节点写入状态栏字段。
     */
    public void fill(ObjectNode node, long chainId) {
        ChainMetricsVO view = snapshot(chainId);
        node.put("headBlock", view.headBlock());
        node.put("processedBlock", view.processedBlock());
        node.put("lagBlocks", view.lagBlocks());
        node.put("blockNumber", view.processedBlock());
        node.put("rpcQps", view.rpcQps());
        node.put("rpcErrorRate", view.rpcErrorRate());
        if (view.kafkaLag() != null) {
            node.put("kafkaLag", view.kafkaLag());
        } else {
            node.putNull("kafkaLag");
        }
        node.put("kafkaStale", view.kafkaStale());
        node.put("outboxPending", view.outboxPending());
        node.put("reorgCount", view.reorgCount());
        node.put("reorgDepth", view.reorgDepth());
    }

    /** REST / 测试用快照：只读缓存，不阻塞。 */
    public ChainMetricsVO snapshot(long chainId) {
        long processed = processedBlock(chainId);
        long head = headCache.computeIfAbsent(chainId, id -> new AtomicLong(processed)).get();
        if (head <= 0) {
            head = processed;
        }
        long lag = Math.max(0L, head - processed);
        RateSnapshot rpc = rpcRateCache.getOrDefault(chainId, RateSnapshot.ZERO);
        long kafkaLagValue = lastKafkaLag.get();
        boolean stale = isKafkaStale();
        return new ChainMetricsVO(
                chainId,
                head,
                processed,
                lag,
                rpc.qps(),
                rpc.errorRate(),
                kafkaLagValue >= 0 ? kafkaLagValue : null,
                stale,
                outboxPendingCache.get(),
                redisLong(RedisKeys.reorgCount(chainId)),
                redisLong(RedisKeys.reorgDepth(chainId)));
    }

    private void refreshCaches() {
        try {
            for (ChainDefinition chain : chainRegistry.all()) {
                refreshHead(chain.chainId());
                refreshRpcRate(chain.chainId());
            }
            refreshKafkaLag();
            refreshOutboxPending();
        } catch (Exception ex) {
            log.debug("metrics refresh failed: {}", ex.getMessage());
        }
    }

    private void refreshHead(long chainId) {
        try {
            long head = rpcGateway.latestBlockNumber(chainId);
            headCache.computeIfAbsent(chainId, id -> new AtomicLong()).set(head);
        } catch (RuntimeException ex) {
            log.debug("refresh head failed chainId={}", chainId);
        }
    }

    private void refreshRpcRate(long chainId) {
        rpcRateCache.put(chainId, computeRpcRate(chainId));
    }

    private void refreshOutboxPending() {
        try {
            outboxPendingCache.set(outboxMapper.countPending());
        } catch (RuntimeException ex) {
            log.debug("refresh outbox pending failed: {}", ex.getMessage());
        }
    }

    private void refreshKafkaLag() {
        KafkaAdmin admin = kafkaAdmin.getIfAvailable();
        if (admin == null) {
            lastKafkaLag.set(-1L);
            return;
        }
        AdminClient client = adminClient();
        if (client == null) {
            return;
        }
        try {
            ListConsumerGroupOffsetsResult offsetsResult = client.listConsumerGroupOffsets(AnchorConstants.KAFKA_GROUP_API);
            Map<TopicPartition, OffsetAndMetadata> committed = offsetsResult.partitionsToOffsetAndMetadata()
                    .get(KAFKA_ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (committed == null || committed.isEmpty()) {
                lastKafkaLag.set(0L);
                lastKafkaSuccessAtMs.set(System.currentTimeMillis());
                return;
            }
            Map<TopicPartition, OffsetSpec> endSpecs = new HashMap<>();
            for (TopicPartition partition : committed.keySet()) {
                if (KafkaConstants.SWAP_EVENTS.equals(partition.topic())
                        || KafkaConstants.CHAIN_EVENTS.equals(partition.topic())) {
                    endSpecs.put(partition, OffsetSpec.latest());
                }
            }
            if (endSpecs.isEmpty()) {
                lastKafkaLag.set(0L);
                lastKafkaSuccessAtMs.set(System.currentTimeMillis());
                return;
            }
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> ends =
                    client.listOffsets(endSpecs).all().get(KAFKA_ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            long lag = 0L;
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committed.entrySet()) {
                if (!endSpecs.containsKey(entry.getKey())) {
                    continue;
                }
                long consumer = entry.getValue() == null ? 0L : entry.getValue().offset();
                var end = ends.get(entry.getKey());
                long latest = end == null ? consumer : end.offset();
                lag += Math.max(0L, latest - consumer);
            }
            lastKafkaLag.set(lag);
            lastKafkaSuccessAtMs.set(System.currentTimeMillis());
        } catch (Exception ex) {
            log.debug("refresh Kafka lag failed: {}", ex.getMessage());
            closeAdminClient();
        }
    }

    private AdminClient adminClient() {
        AdminClient existing = adminClientRef.get();
        if (existing != null) {
            return existing;
        }
        KafkaAdmin admin = kafkaAdmin.getIfAvailable();
        if (admin == null) {
            return null;
        }
        synchronized (adminClientRef) {
            existing = adminClientRef.get();
            if (existing != null) {
                return existing;
            }
            Properties props = new Properties();
            props.putAll(admin.getConfigurationProperties());
            props.putIfAbsent("request.timeout.ms", String.valueOf(KAFKA_ADMIN_TIMEOUT_MS));
            props.putIfAbsent("default.api.timeout.ms", String.valueOf(KAFKA_ADMIN_TIMEOUT_MS));
            AdminClient created = AdminClient.create(props);
            adminClientRef.set(created);
            return created;
        }
    }

    private void closeAdminClient() {
        AdminClient client = adminClientRef.getAndSet(null);
        if (client != null) {
            try {
                client.close(java.time.Duration.ofMillis(500));
            } catch (Exception ignored) {
                // recreate on next refresh
            }
        }
    }

    private boolean isKafkaStale() {
        long successAt = lastKafkaSuccessAtMs.get();
        if (successAt <= 0L) {
            return true;
        }
        return System.currentTimeMillis() - successAt > AnchorConstants.KAFKA_LAG_STALE_MS;
    }

    private long processedBlock(long chainId) {
        Long stored = checkpointMapper.getBlockNumber(chainId);
        return stored == null ? 0L : stored;
    }

    private RateSnapshot computeRpcRate(long chainId) {
        String tag = String.valueOf(chainId);
        double requests = sumCounter(AnchorMetricNames.RPC_REQUESTS, "chainId", tag);
        double errors = sumCounter(AnchorMetricNames.RPC_ERRORS, "chainId", tag);
        long now = System.currentTimeMillis();
        RateSample previous = rpcSamples.put(chainId, new RateSample(requests, errors, now));
        if (previous == null || now <= previous.atMs()) {
            return RateSnapshot.ZERO;
        }
        double elapsedSec = (now - previous.atMs()) / 1000.0;
        if (elapsedSec <= 0) {
            return RateSnapshot.ZERO;
        }
        double deltaReq = Math.max(0.0, requests - previous.requests());
        double deltaErr = Math.max(0.0, errors - previous.errors());
        double qps = deltaReq / elapsedSec;
        double errorRate = deltaReq <= 0 ? 0.0 : deltaErr / deltaReq;
        return new RateSnapshot(round3(qps), round3(errorRate));
    }

    private double sumCounter(String name, String tagKey, String tagValue) {
        double total = 0.0;
        for (Counter counter : meterRegistry.find(name).tag(tagKey, tagValue).counters()) {
            total += counter.count();
        }
        return total;
    }

    private long redisLong(String key) {
        String raw = redis.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record RateSample(double requests, double errors, long atMs) {
    }

    private record RateSnapshot(double qps, double errorRate) {
        static final RateSnapshot ZERO = new RateSnapshot(0.0, 0.0);
    }
}
