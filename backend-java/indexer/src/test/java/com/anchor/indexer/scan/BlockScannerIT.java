package com.anchor.indexer.scan;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.domain.AnchorConstants;
import com.anchor.common.domain.ConfirmStatus;
import com.anchor.common.persistence.entity.SwapEntity;
import com.anchor.common.persistence.mapper.CheckpointMapper;
import com.anchor.common.persistence.mapper.SwapMapper;
import com.anchor.indexer.bootstrap.IndexerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = IndexerApplication.class, properties = {"anchor.scanner.enabled=false", "server.port=0"})
@Testcontainers(disabledWithoutDocker = true)
class BlockScannerIT {

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

    @Test
    void missingCheckpointIsNull() {
        assertNull(checkpointMapper.findBlockNumber(1L));
    }

    @Test
    void checkpointAdvancesAndParentMismatchStops() {
        ChainDefinition chain = chainRegistry.requireByKey("base");
        ChainBlock block = new ChainBlock(100L, "0xaaa", "0xparent", 10L, List.of());
        assertTrue(blockScanner.process(chain, block));
        assertEquals(100L, checkpointMapper.findBlockNumber(chain.chainId()));

        ChainBlock replay = new ChainBlock(100L, "0xaaa", "0xparent", 10L, List.of());
        assertTrue(blockScanner.process(chain, replay));
        assertEquals(100L, checkpointMapper.findBlockNumber(chain.chainId()));

        ChainBlock fork = new ChainBlock(101L, "0xbbb", "0xother", 12L, List.of());
        assertFalse(blockScanner.process(chain, fork));
        assertEquals(100L, checkpointMapper.findBlockNumber(chain.chainId()));
    }

    @Test
    void repeatedUpsertKeepsOneRow() {
        ChainDefinition chain = chainRegistry.requireByKey("base");
        swapMapper.upsertBatch(List.of(swap(chain.chainId())));
        swapMapper.upsertBatch(List.of(swap(chain.chainId())));
        assertEquals(1L, swapMapper.countByChain(chain.chainId()));
    }

    private static SwapEntity swap(long chainId) {
        SwapEntity entity = new SwapEntity();
        entity.setChainId(chainId);
        entity.setTxHash("0x123");
        entity.setLogIndex(1);
        entity.setBlockNumber(100L);
        entity.setBlockHash("0xaaa");
        entity.setBlockTimestamp(10L);
        entity.setTxFrom("0xabc");
        entity.setTxTo("0xdef");
        entity.setDex(AnchorConstants.DEX_UNISWAP_V3);
        entity.setDirection("BUY");
        entity.setTokenIn("0x1111111111111111111111111111111111111111");
        entity.setTokenOut("0x2222222222222222222222222222222222222222");
        entity.setAmountIn(BigInteger.ONE);
        entity.setAmountOut(BigInteger.TEN);
        entity.setConfirmStatus(ConfirmStatus.CONFIRMING.name());
        entity.setWatchedAddress("0xabc");
        entity.setCreatedBy(AnchorConstants.ACTOR_INDEXER);
        entity.setUpdatedBy(AnchorConstants.ACTOR_INDEXER);
        return entity;
    }
}
