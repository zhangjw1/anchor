package com.anchor.indexer.scan;

import com.anchor.common.chain.AnchorSettings;
import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.ChainScanner;
import com.anchor.common.chain.ChainTransaction;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.metrics.AnchorMetricNames;
import com.anchor.indexer.service.BlockIngestionService;
import com.anchor.indexer.dto.BlockIngestDTO;
import com.anchor.indexer.dto.ReorgHandleDTO;
import com.anchor.indexer.dto.ScanStepDTO;
import com.anchor.indexer.service.ReorgService;
import com.anchor.parser.service.TokenMetadataService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按检查点顺序扫块。父哈希对不上时先尝试重组，无法定位就暂停。
 */
@Service
public class BlockScanner implements ChainScanner {

    private static final Logger log = LoggerFactory.getLogger(BlockScanner.class);

    private final AnchorSettings settings;
    private final ChainRegistry chainRegistry;
    private final RpcGateway rpcGateway;
    private final SwapParser swapParser;
    private final BlockIngestionService ingestionService;
    private final CheckpointMapper checkpointMapper;
    private final BlockHashMapper blockHashMapper;
    private final StringRedisTemplate redis;
    private final TokenMetadataService tokenMetadataService;
    private final ReorgService reorgService;
    private final MeterRegistry meterRegistry;
    private final ScannerLease scannerLease;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger pausedGauge = new AtomicInteger(0);

    public BlockScanner(AnchorSettings settings, ChainRegistry chainRegistry, RpcGateway rpcGateway, SwapParser swapParser,
                        BlockIngestionService ingestionService, CheckpointMapper checkpointMapper, BlockHashMapper blockHashMapper,
                        StringRedisTemplate redis, TokenMetadataService tokenMetadataService, ReorgService reorgService,
                        MeterRegistry meterRegistry, ScannerLease scannerLease) {
        this.settings = settings;
        this.chainRegistry = chainRegistry;
        this.rpcGateway = rpcGateway;
        this.swapParser = swapParser;
        this.ingestionService = ingestionService;
        this.checkpointMapper = checkpointMapper;
        this.blockHashMapper = blockHashMapper;
        this.redis = redis;
        this.tokenMetadataService = tokenMetadataService;
        this.reorgService = reorgService;
        this.meterRegistry = meterRegistry;
        this.scannerLease = scannerLease;
    }

    @PostConstruct
    void registerPausedGauge() {
        ChainDefinition chain = chainRegistry.requireByKey(settings.getChainKey());
        Gauge.builder(AnchorMetricNames.SCANNER_PAUSED, pausedGauge, AtomicInteger::get)
                .description("Whether the block scanner is paused (1=paused)")
                .tag("chainId", String.valueOf(chain.chainId()))
                .register(meterRegistry);
    }

    /**
     * 没有检查点时从当前链头开始，不回补历史块。暂停时在循环内等待 resume。
     */
    @Override
    public void scanForever() {
        ChainDefinition chain = chainRegistry.requireByKey(settings.getChainKey());
        Long stored = checkpointMapper.getBlockNumber(chain.chainId());
        long next = stored == null ? -1L : stored + 1;
        log.info("开始扫描，chainId={}，检查点={}", chain.chainId(), stored);
        try {
            while (true) {
                if (!scannerLease.tryAcquire(chain.chainId())) {
                    log.warn("未拿到扫块锁，等待中，chainId={}，owner候选={}", chain.chainId(), scannerLease.ownerId());
                    sleep(settings.getScanner().getPollIntervalMs());
                    continue;
                }
                if (paused.get()) {
                    scannerLease.renewIfDue(chain.chainId());
                    sleep(settings.getScanner().getPollIntervalMs());
                    continue;
                }
                try {
                    if (next < 0) {
                        next = rpcGateway.latestBlockNumber(chain.chainId());
                        log.info("没有检查点，从链头开始，chainId={}，区块={}", chain.chainId(), next);
                    }
                    long head = rpcGateway.latestBlockNumber(chain.chainId());
                    if (next > head) {
                        scannerLease.renewIfDue(chain.chainId());
                        sleep(settings.getScanner().getPollIntervalMs());
                        continue;
                    }
                    next = applyMaxCatchup(chain.chainId(), next, head);
                    if (next > head) {
                        continue;
                    }
                    int batch = Math.max(1, settings.getScanner().getCatchupBatchSize());
                    int count = (int) Math.min(batch, head - next + 1);
                    for (ChainBlock block : fetchRange(chain.chainId(), next, count)) {
                        if (paused.get() || !scannerLease.isHeldBySelf(chain.chainId())) {
                            break;
                        }
                        scannerLease.renewIfDue(chain.chainId());
                        ScanStepDTO step = process(chain, block);
                        if (step.kind() == ScanStepDTO.Kind.PAUSED) {
                            break;
                        }
                        next = step.nextBlock();
                        if (step.kind() == ScanStepDTO.Kind.REWOUND) {
                            break;
                        }
                    }
                } catch (RuntimeException ex) {
                    log.error("扫描失败，chainId={}，区块={}", chain.chainId(), next, ex);
                    sleep(settings.getScanner().getPollIntervalMs());
                }
            }
        } finally {
            scannerLease.release(chain.chainId());
        }
    }

    @Override
    public void resume(long chainId) {
        ChainDefinition chain = chainRegistry.requireByKey(settings.getChainKey());
        if (chain.chainId() != chainId) {
            throw new AnchorException("VALIDATION_ERROR", "chainId 与本实例配置的链不一致");
        }
        if (!paused.compareAndSet(true, false)) {
            log.info("扫描器未处于暂停，忽略 resume，chainId={}", chainId);
            return;
        }
        pausedGauge.set(0);
        log.info("扫描已恢复，chainId={}", chainId);
    }

    @Override
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * 处理单块：校验父哈希 → 解析监控地址 Swap → 同事务入库/outbox/checkpoint。
     *
     * @param chain 当前链
     * @param block 已拉取的区块（含交易列表）
     * @return 下一步动作：ingested / rewound / paused
     */
    public ScanStepDTO process(ChainDefinition chain, ChainBlock block) {
        MDC.put("chainId", String.valueOf(chain.chainId()));
        MDC.put("blockNumber", String.valueOf(block.number()));
        try {
            // 发现链上父区块与已经扫描入库的上一个区块不一致，尝试重组
            if (parentMismatch(chain.chainId(), block)) {
                ReorgHandleDTO outcome = reorgService.handle(chain, block);
                if (outcome.paused()) {
                    markPaused(chain.chainId(), block);
                    return ScanStepDTO.paused();
                }
                return ScanStepDTO.rewound(outcome.nextBlock());
            }
            // 获取区块里匹配的监控地址的交易，解析交换事件
            List<SwapParser.ParsedSwap> swaps = parseMatched(chain, block);
            // 本块涉及代币元数据一次性补齐（Multicall）
            if (!swaps.isEmpty()) {
                List<String> tokens = new ArrayList<>(swaps.size() * 2);
                for (SwapParser.ParsedSwap swap : swaps) {
                    tokens.add(swap.tokenIn());
                    tokens.add(swap.tokenOut());
                }
                tokenMetadataService.ensureAll(chain, tokens);
            }
            // 获取执行层/共识层 确认的最新区块高度(这个高度之前的区块永远不会再被重组)
            long finalized = rpcGateway.finalizedBlockNumber(chain.chainId());
            // 入库 + outbox + checkpoint 同事务；Kafka 由 OutboxRelay 异步投递
            BlockIngestDTO ingested = ingestionService.persist(chain, block, swaps, finalized);
            String chainTag = String.valueOf(chain.chainId());
            meterRegistry.counter(AnchorMetricNames.INDEXER_BLOCKS, "chainId", chainTag).increment();
            if (!swaps.isEmpty()) {
                meterRegistry.counter(AnchorMetricNames.INDEXER_SWAPS, "chainId", chainTag).increment(swaps.size());
            }
            log.info("区块已入库，chainId={}，区块={}，交换数={}，确认事件={}",
                    chain.chainId(), block.number(), swaps.size(), ingested.confirmations().size());
            return ScanStepDTO.ingested(block.number() + 1);
        } finally {
            MDC.remove("chainId");
            MDC.remove("blockNumber");
            MDC.remove("txHash");
        }
    }

    private void markPaused(long chainId, ChainBlock block) {
        paused.set(true);
        pausedGauge.set(1);
        log.error("父哈希不一致且无法定位祖先，扫描已暂停，请调用 POST /admin/scanner/{}/resume，chainId={}，区块={}，父哈希={}",
                chainId, chainId, block.number(), block.parentHash());
    }

    private boolean parentMismatch(long chainId, ChainBlock block) {
        String stored = blockHashMapper.getHash(chainId, block.number() - 1);
        return stored != null && !stored.equalsIgnoreCase(block.parentHash());
    }

    private List<SwapParser.ParsedSwap> parseMatched(ChainDefinition chain, ChainBlock block) {
        Set<String> watched = redis.opsForSet().members(RedisKeys.watchSet(chain.chainId()));
        if (watched == null || watched.isEmpty()) {
            return List.of();
        }
        List<ChainTransaction> matched = block.transactions().stream()
                .filter(tx -> tx.from() != null && watched.contains(tx.from()))
                .toList();
        if (matched.isEmpty()) {
            return List.of();
        }
        Map<String, ChainReceipt> receipts = new HashMap<>();
        for (ChainReceipt receipt : rpcGateway.getReceipts(chain.chainId(), matched.stream().map(ChainTransaction::hash).toList())) {
            receipts.put(receipt.transactionHash(), receipt);
        }
        List<SwapParser.ParsedSwap> swaps = new ArrayList<>();
        for (ChainTransaction tx : matched) {
            ChainReceipt receipt = receipts.get(tx.hash());
            if (receipt == null || !receipt.success()) {
                continue;
            }
            MDC.put("txHash", tx.hash());
            try {
                swapParser.parse(new SwapParser.SwapParseContext(chain, block, tx, receipt)).ifPresent(swaps::add);
            } catch (RuntimeException ex) {
                meterRegistry.counter(AnchorMetricNames.INDEXER_PARSE_FAILURES, "chainId", String.valueOf(chain.chainId())).increment();
                log.warn("解析 Swap 失败，txHash={}", tx.hash(), ex);
            }
        }
        return swaps;
    }

    /**
     * 落后超过 max-catchup-blocks 时，从 head - N 起扫并 WARN 跳过区间。
     * 注：监控按 tx.from 匹配，仍须逐块 getBlock 做父哈希校验；eth_getLogs 不能替代。
     */
    long applyMaxCatchup(long chainId, long next, long head) {
        int maxCatchup = settings.getScanner().getMaxCatchupBlocks();
        if (maxCatchup <= 0 || next > head) {
            return next;
        }
        long lag = head - next + 1;
        if (lag <= maxCatchup) {
            return next;
        }
        long jumpTo = Math.max(0L, head - maxCatchup);
        if (jumpTo <= next) {
            return next;
        }
        log.warn("Catch-up lag too large, skipping blocks [{}, {}) chainId={} head={} maxCatchup={}",
                next, jumpTo, chainId, head, maxCatchup);
        return jumpTo;
    }

    private List<ChainBlock> fetchRange(long chainId, long start, int count) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<ChainBlock>> futures = new ArrayList<>();
            for (int offset = 0; offset < count; offset++) {
                long number = start + offset;
                futures.add(CompletableFuture.supplyAsync(() -> rpcGateway.getBlock(chainId, number), executor));
            }
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
