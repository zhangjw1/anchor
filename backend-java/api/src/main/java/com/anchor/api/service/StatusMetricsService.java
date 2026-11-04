package com.anchor.api.service;

import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.metrics.AnchorMetricNames;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 组装状态栏所需指标：链头、延迟、RPC 速率、Kafka lag、Reorg。
 */
@Service
public class StatusMetricsService {

    private static final Logger log = LoggerFactory.getLogger(StatusMetricsService.class);
    private static final long KAFKA_ADMIN_TIMEOUT_MS = 1_500L;

    private final CheckpointMapper checkpointMapper;
    private final RpcGateway rpcGateway;
    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final ObjectProvider<KafkaAdmin> kafkaAdmin;
    private final Map<Long, RateSample> rpcSamples = new ConcurrentHashMap<>();
    private final AtomicLong lastKafkaLag = new AtomicLong(-1L);
    private volatile long lastKafkaLagAtMs;

    public StatusMetricsService(CheckpointMapper checkpointMapper, RpcGateway rpcGateway, StringRedisTemplate redis,
                                MeterRegistry meterRegistry, ObjectProvider<KafkaAdmin> kafkaAdmin) {
        this.checkpointMapper = checkpointMapper;
        this.rpcGateway = rpcGateway;
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.kafkaAdmin = kafkaAdmin;
    }

    /**
     * 向已有 METRICS 节点写入状态栏字段。
     */
    public void fill(ObjectNode node, long chainId) {
        long processed = processedBlock(chainId);
        long head = headBlock(chainId, processed);
        long lag = Math.max(0L, head - processed);
        RateSnapshot rpc = rpcRate(chainId);
        long kafkaLag = kafkaLag();

        node.put("headBlock", head);
        node.put("processedBlock", processed);
        node.put("lagBlocks", lag);
        node.put("blockNumber", processed);
        node.put("rpcQps", rpc.qps());
        node.put("rpcErrorRate", rpc.errorRate());
        if (kafkaLag >= 0) {
            node.put("kafkaLag", kafkaLag);
        } else {
            node.putNull("kafkaLag");
        }
        node.put("reorgCount", redisLong(RedisKeys.reorgCount(chainId)));
        node.put("reorgDepth", redisLong(RedisKeys.reorgDepth(chainId)));
    }

    private long processedBlock(long chainId) {
        Long stored = checkpointMapper.getBlockNumber(chainId);
        return stored == null ? 0L : stored;
    }

    private long headBlock(long chainId, long fallback) {
        try {
            return rpcGateway.latestBlockNumber(chainId);
        } catch (RuntimeException ex) {
            log.debug("读取链头失败，chainId={}", chainId);
            return fallback;
        }
    }

    private RateSnapshot rpcRate(long chainId) {
        String tag = String.valueOf(chainId);
        double requests = sumCounter(AnchorMetricNames.RPC_REQUESTS, "chainId", tag);
        double errors = sumCounter(AnchorMetricNames.RPC_ERRORS, "chainId", tag);
        long now = System.currentTimeMillis();
        RateSample previous = rpcSamples.get(chainId);
        rpcSamples.put(chainId, new RateSample(requests, errors, now));
        if (previous == null || now <= previous.atMs()) {
            return new RateSnapshot(0.0, 0.0);
        }
        double elapsedSec = (now - previous.atMs()) / 1000.0;
        if (elapsedSec <= 0) {
            return new RateSnapshot(0.0, 0.0);
        }
        double deltaReq = Math.max(0.0, requests - previous.requests());
        double deltaErr = Math.max(0.0, errors - previous.errors());
        double qps = deltaReq / elapsedSec;
        double errorRate = deltaReq <= 0 ? 0.0 : deltaErr / deltaReq;
        return new RateSnapshot(round3(qps), round3(errorRate));
    }

    private long kafkaLag() {
        long now = System.currentTimeMillis();
        if (now - lastKafkaLagAtMs < AnchorConstants.WS_METRICS_INTERVAL_MS) {
            return lastKafkaLag.get();
        }
        KafkaAdmin admin = kafkaAdmin.getIfAvailable();
        if (admin == null) {
            lastKafkaLag.set(-1L);
            lastKafkaLagAtMs = now;
            return -1L;
        }
        try (AdminClient client = AdminClient.create(admin.getConfigurationProperties())) {
            ListConsumerGroupOffsetsResult offsetsResult = client.listConsumerGroupOffsets(AnchorConstants.KAFKA_GROUP_API);
            Map<TopicPartition, OffsetAndMetadata> committed = offsetsResult.partitionsToOffsetAndMetadata()
                    .get(KAFKA_ADMIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (committed == null || committed.isEmpty()) {
                lastKafkaLag.set(0L);
                lastKafkaLagAtMs = now;
                return 0L;
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
                lastKafkaLagAtMs = now;
                return 0L;
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
            lastKafkaLagAtMs = now;
            return lag;
        } catch (Exception ex) {
            log.debug("读取 Kafka lag 失败: {}", ex.getMessage());
            lastKafkaLagAtMs = now;
            return lastKafkaLag.get();
        }
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
    }
}
