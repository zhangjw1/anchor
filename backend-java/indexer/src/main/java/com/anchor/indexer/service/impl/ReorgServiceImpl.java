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

    /**
     * 处理一次链重组：定位分叉祖先、回滚本地数据、广播 REORG 事件，并返回重扫起点。
     * 由 BlockScanner 检测到父哈希不一致时调用。
     */
    @Override
    public ReorgHandleDTO handle(ChainDefinition chain, ChainBlock incoming) {
        // 找到新链与旧链的分叉前的最后一个区块（祖先块），如果找不到就要求扫描器暂停
        Optional<ReorgPlan> found = locate(chain.chainId(), incoming);
        if (found.isEmpty()) {
            return ReorgHandleDTO.halt();
        }
        ReorgPlan plan = found.get();
        // 回滚祖先块之后已入库的区块哈希和交换数据
        int changed = reorgManager.apply(plan.chainId(), plan.ancestorBlock(), plan.ancestorHash());
        if (changed > 0) {
            recordMetrics(plan);
        }
        // 通知下游（API/WebSocket）该区间数据已作废，发送失败会一直重试
        publishUntilAck(plan);
        log.info("重组已处理，chainId={}，祖先块={}，深度={}", plan.chainId(), plan.ancestorBlock(), plan.depth());
        // 告诉扫描器把进度拨回祖先块的下一块，按新链重扫
        return ReorgHandleDTO.rewound(plan.ancestorBlock() + 1);
    }

    /**
     * 沿新链往回找，定位新旧链最后一个相同的块（共同祖先）。
     *
     * @param chainId  链 ID
     * @param incoming 触发重组检测的新块（扫描器刚取到的 checkpoint+1，父哈希已与本地不一致）
     * @return 找到则返回重组计划；找不到（无检查点、本地缺哈希、越过保留窗口、RPC 失败）则 empty，上层会暂停扫描
     */
    private Optional<ReorgPlan> locate(long chainId, ChainBlock incoming) {
        // 本地已确认入库的最高区块高度，例：104
        Long checkpoint = checkpointMapper.getBlockNumber(chainId);
        if (checkpoint == null) {
            return Optional.empty();
        }
        // 允许回溯的最低高度：本地只保留最近 RECENT_HASH_LIMIT（64）个块哈希，再往前本地没有 stored 可比
        // 例：checkpoint=104 → minKeep=104-64+1=41；candidate 低于此值则放弃并暂停
        long minKeep = Math.max(0L, checkpoint - AnchorConstants.RECENT_HASH_LIMIT + 1);
        // 当前正在比对的高度，从「新块的父高度」开始往回走，例：incoming=105 → candidate 从 104 起
        long candidate = incoming.number() - 1;
        // 新主链在「candidate 这一高度」上的块哈希；初始取 incoming 的父哈希，之后每轮用 RPC 更新为更矮一层
        // 例：Z105.parentHash=Y104，下一轮变成 X103，再下一轮变成 C102
        String parentHash = incoming.parentHash();
        while (candidate >= minKeep) {
            // 本地 block_hash 表在 candidate 高度存的哈希（旧链视角），例：104→E104、103→D103、102→C102
            String stored = blockHashMapper.getHash(chainId, candidate);
            if (stored == null) {
                // 本地缺该高度哈希，无法继续比对
                return Optional.empty();
            }
            if (stored.equalsIgnoreCase(parentHash)) {
                // stored == parentHash：本地与新链在该高度一致 → candidate 即为共同祖先
                // 深度 = 需要作废的块数，例：checkpoint=104、candidate=102 → depth=2，作废区间 [103,104]
                int depth = (int) (checkpoint - candidate);
                if (depth <= 0) {
                    return Optional.empty();
                }
                // fromBlock=祖先+1，toBlock=checkpoint，即要撤销并重扫的闭区间
                return Optional.of(new ReorgPlan(chainId, candidate, stored, depth, candidate + 1, checkpoint));
            }
            try {
                // 不相等：该高度已分叉；向 RPC 取新链在 candidate 高度的块，用其父哈希继续比对更矮一层
                parentHash = rpcGateway.getBlock(chainId, candidate).parentHash();
            } catch (RuntimeException ex) {
                log.error("重组回溯失败，chainId={}，区块={}", chainId, candidate, ex);
                return Optional.empty();
            }
            candidate--;
        }
        // 越过 minKeep 仍未找到共同祖先，深度超过本地可验证范围
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

    /**
     * locate 的结果：新旧链共同祖先，以及需要作废的区块区间。
     *
     * @param chainId       链 ID
     * @param ancestorBlock 共同祖先高度（该高度及以前本地与新链一致）
     * @param ancestorHash  共同祖先哈希
     * @param depth         重组深度，等于 checkpoint - ancestorBlock，即要作废的块数
     * @param fromBlock     作废区间起点（含），一般为 ancestorBlock + 1
     * @param toBlock       作废区间终点（含），一般为原 checkpoint
     */
    record ReorgPlan(long chainId, long ancestorBlock, String ancestorHash, int depth, long fromBlock, long toBlock) {
    }
}
