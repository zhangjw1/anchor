package com.anchor.indexer.service.impl;

import com.anchor.common.chain.AnchorSettings;
import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.indexer.dto.ReorgHandleDTO;
import com.anchor.indexer.manager.ReorgManager;
import com.anchor.indexer.service.ChainEventPublishService;
import com.anchor.indexer.service.ReorgService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 沿父哈希向前找到仍一致的祖先，再回退数据和检查点。
 */
@Service
public class ReorgServiceImpl implements ReorgService {

    private static final Logger log = LoggerFactory.getLogger(ReorgServiceImpl.class);

    private final RpcGateway rpcGateway;
    private final BlockHashMapper blockHashMapper;
    private final CheckpointMapper checkpointMapper;
    private final ReorgManager reorgManager;
    private final ChainEventPublishService publisher;
    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final AnchorSettings settings;

    public ReorgServiceImpl(RpcGateway rpcGateway, BlockHashMapper blockHashMapper, CheckpointMapper checkpointMapper,
                            ReorgManager reorgManager, ChainEventPublishService publisher, StringRedisTemplate redis,
                            MeterRegistry meterRegistry, AnchorSettings settings) {
        this.rpcGateway = rpcGateway;
        this.blockHashMapper = blockHashMapper;
        this.checkpointMapper = checkpointMapper;
        this.reorgManager = reorgManager;
        this.publisher = publisher;
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.settings = settings;
    }

    @Override
    public ReorgHandleDTO handle(ChainDefinition chain, ChainBlock incoming) {
        Optional<ReorgPlan> found = locate(chain.chainId(), incoming);
        if (found.isEmpty()) {
            return ReorgHandleDTO.halt();
        }
        ReorgPlan plan = found.get();
        int changed = reorgManager.apply(plan.chainId(), plan.ancestorBlock(), plan.ancestorHash());
        if (changed > 0) {
            recordMetrics(plan);
        }
        publishUntilAck(plan);
        log.info("重组已处理，chainId={}，祖先块={}，深度={}", plan.chainId(), plan.ancestorBlock(), plan.depth());
        return ReorgHandleDTO.rewound(plan.ancestorBlock() + 1);
    }

    private Optional<ReorgPlan> locate(long chainId, ChainBlock incoming) {
        Long checkpoint = checkpointMapper.getBlockNumber(chainId);
        if (checkpoint == null) {
            return Optional.empty();
        }
        long minKeep = Math.max(0L, checkpoint - AnchorConstants.RECENT_HASH_LIMIT + 1);
        long candidate = incoming.number() - 1;
        String parentHash = incoming.parentHash();
        while (candidate >= minKeep) {
            String stored = blockHashMapper.getHash(chainId, candidate);
            if (stored == null) {
                return Optional.empty();
            }
            if (stored.equalsIgnoreCase(parentHash)) {
                int depth = (int) (checkpoint - candidate);
                if (depth <= 0) {
                    return Optional.empty();
                }
                return Optional.of(new ReorgPlan(chainId, candidate, stored, depth, candidate + 1, checkpoint));
            }
            try {
                parentHash = rpcGateway.getBlock(chainId, candidate).parentHash();
            } catch (RuntimeException ex) {
                log.error("重组回溯失败，chainId={}，区块={}", chainId, candidate, ex);
                return Optional.empty();
            }
            candidate--;
        }
        return Optional.empty();
    }

    private void recordMetrics(ReorgPlan plan) {
        String chainId = String.valueOf(plan.chainId());
        meterRegistry.counter("chain.reorgs", "chainId", chainId).increment();
        meterRegistry.summary("chain.reorg.depth", "chainId", chainId).record(plan.depth());
        redis.opsForValue().increment(RedisKeys.reorgCount(plan.chainId()));
        redis.opsForValue().set(RedisKeys.reorgDepth(plan.chainId()), String.valueOf(plan.depth()));
    }

    private void publishUntilAck(ReorgPlan plan) {
        ChainEventDTO event = ChainEventDTO.reorg(plan.chainId(), plan.depth(), plan.ancestorBlock(), plan.fromBlock(), plan.toBlock());
        while (true) {
            try {
                publisher.publishChain(List.of(event));
                return;
            } catch (RuntimeException ex) {
                log.error("重组事件发布失败，chainId={}，深度={}", plan.chainId(), plan.depth(), ex);
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(settings.getScanner().getPollIntervalMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("reorg publish interrupted", ex);
        }
    }

    record ReorgPlan(long chainId, long ancestorBlock, String ancestorHash, int depth, long fromBlock, long toBlock) {
    }
}
