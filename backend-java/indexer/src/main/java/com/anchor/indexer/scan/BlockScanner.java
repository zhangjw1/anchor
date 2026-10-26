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
import com.anchor.common.domain.AnchorConstants;
import com.anchor.common.domain.RedisKeys;
import com.anchor.common.persistence.mapper.BlockHashMapper;
import com.anchor.common.persistence.mapper.CheckpointMapper;
import com.anchor.parser.swap.TokenMetadataService;
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
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public BlockScanner(AnchorSettings settings, ChainRegistry chainRegistry, RpcGateway rpcGateway, SwapParser swapParser,
                        BlockIngestionService ingestionService, CheckpointMapper checkpointMapper,
                        BlockHashMapper blockHashMapper, StringRedisTemplate redis,
                        TokenMetadataService tokenMetadataService) {
        this.settings = settings;
        this.chainRegistry = chainRegistry;
        this.rpcGateway = rpcGateway;
        this.swapParser = swapParser;
        this.ingestionService = ingestionService;
        this.checkpointMapper = checkpointMapper;
        this.blockHashMapper = blockHashMapper;
        this.redis = redis;
        this.tokenMetadataService = tokenMetadataService;
    }

    @Override
    public void scanForever() {
        ChainDefinition chain = chainRegistry.requireByKey(settings.getChainKey());
        Long stored = checkpointMapper.findBlockNumber(chain.chainId());
        long next = stored == null ? -1L : stored + 1;
        log.info("scanner loop chainId={} checkpoint={}", chain.chainId(), stored);
        while (!paused.get()) {
            try {
                if (next < 0) {
                    next = rpcGateway.latestBlockNumber(chain.chainId());
                    log.info("no checkpoint, start from head chainId={} blockNumber={}", chain.chainId(), next);
                }
                long head = rpcGateway.latestBlockNumber(chain.chainId());
                if (next > head) {
                    sleep(settings.getScanner().getPollIntervalMs());
                    continue;
                }
                int count = (int) Math.min(AnchorConstants.CATCH_UP_CONCURRENCY, head - next + 1);
                for (ChainBlock block : fetchRange(chain.chainId(), next, count)) {
                    if (!process(chain, block)) {
                        return;
                    }
                    next = block.number() + 1;
                }
            } catch (RuntimeException ex) {
                log.error("scan failed chainId={} blockNumber={}", chain.chainId(), next, ex);
                sleep(settings.getScanner().getPollIntervalMs());
            }
        }
    }

    public boolean process(ChainDefinition chain, ChainBlock block) {
        MDC.put("chainId", String.valueOf(chain.chainId()));
        MDC.put("blockNumber", String.valueOf(block.number()));
        try {
            if (parentMismatch(chain.chainId(), block)) {
                paused.set(true);
                log.error("parent hash mismatch, scanner paused chainId={} blockNumber={} parentHash={}", chain.chainId(), block.number(), block.parentHash());
                return false;
            }
            List<SwapParser.ParsedSwap> swaps = parseMatched(chain, block);
            for (SwapParser.ParsedSwap swap : swaps) {
                tokenMetadataService.ensure(chain, swap.tokenIn());
                tokenMetadataService.ensure(chain, swap.tokenOut());
            }
            ingestionService.persist(chain, block, swaps);
            log.info("block ingested chainId={} blockNumber={} swaps={}", chain.chainId(), block.number(), swaps.size());
            return true;
        } finally {
            MDC.remove("chainId");
            MDC.remove("blockNumber");
            MDC.remove("txHash");
        }
    }

    private boolean parentMismatch(long chainId, ChainBlock block) {
        String stored = blockHashMapper.findHash(chainId, block.number() - 1);
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
