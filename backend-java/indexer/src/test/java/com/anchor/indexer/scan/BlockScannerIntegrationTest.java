package com.anchor.indexer.scan;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.KafkaConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.dao.SwapMapper;
import com.anchor.common.entity.SwapDO;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.anchor.common.dto.ChainEventDTO;
import com.anchor.common.dto.SwapEventDTO;
import com.anchor.indexer.bootstrap.IndexerApplication;
import com.anchor.indexer.dto.ScanStepDTO;
import com.anchor.indexer.service.ChainEventPublishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = IndexerApplication.class, properties = {
        "anchor.scanner.enabled=false",
        "anchor.kafka.enabled=false",
        "anchor.scanner.poll-interval-ms=1",
        "server.port=0",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@Import(BlockScannerIntegrationTest.Overrides.class)
@Testcontainers(disabledWithoutDocker = true)
class BlockScannerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private BlockScanner blockScanner;
    @Autowired
    private ChainRegistry chainRegistry;
    @Autowired
    private CheckpointMapper checkpointMapper;
    @Autowired
    private SwapMapper swapMapper;
    @Autowired
    private ScriptedRpcGateway rpc;
    @Autowired
    private RecordingPublisher publisher;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        long chainId = chainRegistry.requireByKey("base").chainId();
        jdbc.update("DELETE FROM swap WHERE chain_id = ?", chainId);
        jdbc.update("DELETE FROM block_hash WHERE chain_id = ?", chainId);
        jdbc.update("DELETE FROM block_checkpoint WHERE chain_id = ?", chainId);
        redis.delete(RedisKeys.reorgCount(chainId));
        redis.delete(RedisKeys.reorgDepth(chainId));
        rpc.reset();
        publisher.clear();
    }

    @Test
    void missingCheckpointIsNull() {
        assertNull(checkpointMapper.getBlockNumber(1L));
    }

    @Test
    void checkpointAdvancesAndParentMismatchStops() {
        ChainDefinition chain = chainRegistry.requireByKey("base");
        ChainBlock block = new ChainBlock(100L, "0xaaa", "0xparent", 10L, List.of());
        rpc.put(block);
        assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, block).kind());
        assertEquals(100L, checkpointMapper.getBlockNumber(chain.chainId()));

        ChainBlock replay = new ChainBlock(100L, "0xaaa", "0xparent", 10L, List.of());
        assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, replay).kind());
        assertEquals(100L, checkpointMapper.getBlockNumber(chain.chainId()));

        ChainBlock fork = new ChainBlock(101L, "0xbbb", "0xother", 12L, List.of());
        assertEquals(ScanStepDTO.Kind.PAUSED, blockScanner.process(chain, fork).kind());
        assertEquals(100L, checkpointMapper.getBlockNumber(chain.chainId()));
        assertEquals(0, publisher.chains().size());
    }

    @Test
    void repeatedUpsertKeepsOneRow() {
        ChainDefinition chain = chainRegistry.requireByKey("base");
        swapMapper.saveBatch(List.of(swap(chain.chainId(), 100L, "0x123", 1, "0xaaa")));
        swapMapper.saveBatch(List.of(swap(chain.chainId(), 100L, "0x123", 1, "0xaaa")));
        assertEquals(1L, swapMapper.countByChain(chain.chainId()));
    }

    @Test
    void reorgDepthTwoRewindsAndRescans() {
        ChainDefinition chain = chainRegistry.requireByKey("base");
        assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, block(10, "0x10", "0x09")).kind());
        assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, block(11, "0x11", "0x10")).kind());
        assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, block(12, "0x12", "0x11")).kind());
        swapMapper.saveBatch(List.of(swap(chain.chainId(), 11L, "0xold11", 1, "0x11")));
        swapMapper.saveBatch(List.of(swap(chain.chainId(), 12L, "0xold12", 1, "0x12")));

        rpc.put(block(11, "0x11b", "0x10"));
        publisher.failNext(1);
        ScanStepDTO reorg = blockScanner.process(chain, block(12, "0x12b", "0x11b"));
        assertEquals(ScanStepDTO.Kind.REWOUND, reorg.kind());
        assertEquals(11L, reorg.nextBlock());
        assertEquals(10L, checkpointMapper.getBlockNumber(chain.chainId()));
        assertEquals(ConfirmStatusEnum.REORGED.name(), status("0xold11"));
        assertEquals(ConfirmStatusEnum.REORGED.name(), status("0xold12"));
        assertNull(hash(chain.chainId(), 11L));
        assertNull(hash(chain.chainId(), 12L));
        assertEquals("2", redis.opsForValue().get(RedisKeys.reorgDepth(chain.chainId())));
        assertEquals("1", redis.opsForValue().get(RedisKeys.reorgCount(chain.chainId())));
        ChainEventDTO event = publisher.chains().get(0);
        assertEquals(KafkaConstants.REORG, event.type());
        assertEquals(2, event.depth());
        assertEquals(10L, event.ancestorBlock());
        assertEquals(11L, event.fromBlock());
        assertEquals(12L, event.toBlock());

        assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, block(11, "0x11b", "0x10")).kind());
        assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, block(12, "0x12b", "0x11b")).kind());
        assertEquals(12L, checkpointMapper.getBlockNumber(chain.chainId()));
        assertEquals("0x11b", hash(chain.chainId(), 11L));
        swapMapper.saveBatch(List.of(swap(chain.chainId(), 11L, "0xold11", 1, "0x11b")));
        swapMapper.saveBatch(List.of(swap(chain.chainId(), 11L, "0xnew11", 2, "0x11b")));
        assertEquals(ConfirmStatusEnum.CONFIRMING.name(), status("0xold11"));
        assertEquals(11L, blockNumber("0xold11"));
        assertEquals(ConfirmStatusEnum.CONFIRMING.name(), status("0xnew11"));
        assertEquals(ConfirmStatusEnum.REORGED.name(), status("0xold12"));
    }

    @Test
    void confirmationReachesConfirmedThenFinalized() {
        ChainDefinition chain = chainRegistry.requireByKey("base");
        String parent = "0x19";
        for (long number = 20; number <= 29; number++) {
            String hash = "0x" + number;
            assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, block(number, hash, parent)).kind());
            if (number == 20) {
                swapMapper.saveBatch(List.of(swap(chain.chainId(), 20L, "0xconfirm", 1, hash)));
            }
            parent = hash;
        }
        assertEquals(ConfirmStatusEnum.CONFIRMED.name(), status("0xconfirm"));
        assertTrue(publisher.chains().stream().anyMatch(event -> KafkaConstants.CONFIRMATION.equals(event.type())
                && "0xconfirm".equals(event.txHash())
                && ConfirmStatusEnum.CONFIRMED.name().equals(event.confirmStatus())
                && Integer.valueOf(chain.confirmationBlocks()).equals(event.confirmations())));

        rpc.setFinalized(20L);
        assertEquals(ScanStepDTO.Kind.INGESTED, blockScanner.process(chain, block(30, "0x30", parent)).kind());
        assertEquals(ConfirmStatusEnum.FINALIZED.name(), status("0xconfirm"));
    }

    private static ChainBlock block(long number, String hash, String parent) {
        return new ChainBlock(number, hash, parent, number, List.of());
    }

    private String status(String txHash) {
        return jdbc.queryForObject("SELECT confirm_status FROM swap WHERE tx_hash = ?", String.class, txHash);
    }

    private long blockNumber(String txHash) {
        return jdbc.queryForObject("SELECT block_number FROM swap WHERE tx_hash = ?", Long.class, txHash);
    }

    private String hash(long chainId, long blockNumber) {
        List<String> hashes = jdbc.queryForList("SELECT block_hash FROM block_hash WHERE chain_id = ? AND block_number = ?", String.class, chainId, blockNumber);
        return hashes.isEmpty() ? null : hashes.get(0);
    }

    private static SwapDO swap(long chainId, long blockNumber, String txHash, int logIndex, String blockHash) {
        SwapDO entity = new SwapDO();
        entity.setChainId(chainId);
        entity.setTxHash(txHash);
        entity.setLogIndex(logIndex);
        entity.setBlockNumber(blockNumber);
        entity.setBlockHash(blockHash);
        entity.setBlockTimestamp(10L);
        entity.setTxFrom("0xabc");
        entity.setTxTo("0xdef");
        entity.setDex(AnchorConstants.DEX_UNISWAP_V3);
        entity.setDirection("BUY");
        entity.setTokenIn("0x1111111111111111111111111111111111111111");
        entity.setTokenOut("0x2222222222222222222222222222222222222222");
        entity.setAmountIn(BigInteger.ONE);
        entity.setAmountOut(BigInteger.TEN);
        entity.setConfirmStatus(ConfirmStatusEnum.CONFIRMING.name());
        entity.setWatchedAddress("0xabc");
        entity.setCreatedBy(AnchorConstants.ACTOR_INDEXER);
        entity.setUpdatedBy(AnchorConstants.ACTOR_INDEXER);
        return entity;
    }

    @TestConfiguration
    static class Overrides {
        @Bean
        @Primary
        ScriptedRpcGateway scriptedRpcGateway() {
            return new ScriptedRpcGateway();
        }

        @Bean
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }
    }

    static final class ScriptedRpcGateway implements RpcGateway {
        private final Map<Long, ChainBlock> blocks = new ConcurrentHashMap<>();
        private long finalized;

        void put(ChainBlock block) {
            blocks.put(block.number(), block);
        }

        void setFinalized(long finalized) {
            this.finalized = finalized;
        }

        void reset() {
            blocks.clear();
            finalized = 0L;
        }

        @Override
        public long latestBlockNumber(long chainId) {
            return blocks.keySet().stream().mapToLong(Long::longValue).max().orElse(0L);
        }

        @Override
        public long finalizedBlockNumber(long chainId) {
            return finalized;
        }

        @Override
        public ChainBlock getBlock(long chainId, long blockNumber) {
            ChainBlock block = blocks.get(blockNumber);
            if (block == null) {
                throw new RpcException("block not found " + blockNumber, null);
            }
            return block;
        }

        @Override
        public List<ChainReceipt> getReceipts(long chainId, List<String> txHashes) {
            return List.of();
        }

        @Override
        public String ethCall(long chainId, String to, String data) {
            throw new UnsupportedOperationException();
        }
    }

    static final class RecordingPublisher implements ChainEventPublishService {
        private final List<SwapEventDTO> swaps = new CopyOnWriteArrayList<>();
        private final List<ChainEventDTO> chains = new CopyOnWriteArrayList<>();
        private final AtomicInteger failures = new AtomicInteger();

        void failNext(int times) {
            failures.set(times);
        }

        void clear() {
            swaps.clear();
            chains.clear();
            failures.set(0);
        }

        List<ChainEventDTO> chains() {
            return new ArrayList<>(chains);
        }

        @Override
        public void publishSwaps(List<SwapEventDTO> events) {
            guard();
            swaps.addAll(events);
        }

        @Override
        public void publishChain(List<ChainEventDTO> events) {
            if (events.isEmpty()) {
                return;
            }
            guard();
            chains.addAll(events);
        }

        private void guard() {
            if (failures.getAndUpdate(value -> value > 0 ? value - 1 : 0) > 0) {
                throw new IllegalStateException("kafka down");
            }
        }
    }
}
