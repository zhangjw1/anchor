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
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dto.SwapEventDTO;
import com.anchor.indexer.service.BlockIngestionService;
import com.anchor.indexer.service.ChainEventPublishService;
import com.anchor.indexer.dto.BlockIngestDTO;
import com.anchor.indexer.dto.ReorgHandleDTO;
import com.anchor.indexer.dto.ScanStepDTO;
import com.anchor.indexer.service.ReorgService;
import com.anchor.parser.service.TokenMetadataService;
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
    private final ChainEventPublishService publisher;
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public BlockScanner(AnchorSettings settings, ChainRegistry chainRegistry, RpcGateway rpcGateway, SwapParser swapParser,
                        BlockIngestionService ingestionService, CheckpointMapper checkpointMapper, BlockHashMapper blockHashMapper,
                        StringRedisTemplate redis, TokenMetadataService tokenMetadataService, ReorgService reorgService,
                        ChainEventPublishService publisher) {
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
        this.publisher = publisher;
    }
    /**
     * 没有检查点时从当前链头开始，不回补历史块。
     */
    @Override
    public void scanForever() {
        ChainDefinition chain = chainRegistry.requireByKey(settings.getChainKey());
        Long stored = checkpointMapper.getBlockNumber(chain.chainId());
        long next = stored == null ? -1L : stored + 1;
        log.info("开始扫描，chainId={}，检查点={}", chain.chainId(), stored);
        while (!paused.get()) {
            try {
                if (next < 0) {
                    next = rpcGateway.latestBlockNumber(chain.chainId());
                    log.info("没有检查点，从链头开始，chainId={}，区块={}", chain.chainId(), next);
                }
                long head = rpcGateway.latestBlockNumber(chain.chainId());
                if (next > head) {
                    sleep(settings.getScanner().getPollIntervalMs());
                    continue;
                }
                int count = (int) Math.min(AnchorConstants.CATCH_UP_CONCURRENCY, head - next + 1);
                for (ChainBlock block : fetchRange(chain.chainId(), next, count)) {
                    ScanStepDTO step = process(chain, block);
                    if (step.kind() == ScanStepDTO.Kind.PAUSED) {
                        return;
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
    }
    /**
     * 解析本块中命中监控地址的交换，发布成功后才推进检查点。
     */
    public ScanStepDTO process(ChainDefinition chain, ChainBlock block) {
        MDC.put("chainId", String.valueOf(chain.chainId()));
        MDC.put("blockNumber", String.valueOf(block.number()));
        try {
            if (parentMismatch(chain.chainId(), block)) {
                ReorgHandleDTO outcome = reorgService.handle(chain, block);
                if (outcome.paused()) {
                    paused.set(true);
                    log.error("父哈希不一致，扫描已暂停，chainId={}，区块={}，父哈希={}", chain.chainId(), block.number(), block.parentHash());
                    return ScanStepDTO.paused();
                }
                return ScanStepDTO.rewound(outcome.nextBlock());
            }
            List<SwapParser.ParsedSwap> swaps = parseMatched(chain, block);
            for (SwapParser.ParsedSwap swap : swaps) {
                tokenMetadataService.ensure(chain, swap.tokenIn());
                tokenMetadataService.ensure(chain, swap.tokenOut());
            }
            long finalized = rpcGateway.finalizedBlockNumber(chain.chainId());
            BlockIngestDTO ingested = ingestionService.persist(chain, block, swaps, finalized);
            // 发送成功后才推进 checkpoint，失败时外层按同一块重试
            publisher.publishSwaps(ingested.swaps().stream().map(SwapEventDTO::from).toList());
            publisher.publishChain(ingested.confirmations());
            ingestionService.advanceCheckpoint(chain.chainId(), block.number(), block.hash());
            log.info("区块已入库，chainId={}，区块={}，交换数={}", chain.chainId(), block.number(), swaps.size());
            return ScanStepDTO.ingested(block.number() + 1);
        } finally {
            MDC.remove("chainId");
            MDC.remove("blockNumber");
            MDC.remove("txHash");
        }
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
            swapParser.parse(new SwapParser.SwapParseContext(chain, block, tx, receipt)).ifPresent(swaps::add);
        }
        return swaps;
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
