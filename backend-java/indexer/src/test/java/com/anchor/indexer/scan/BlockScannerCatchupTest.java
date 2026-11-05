package com.anchor.indexer.scan;

import com.anchor.common.chain.AnchorSettings;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.domain.GasModelEnum;
import com.anchor.common.domain.SignModeEnum;
import com.anchor.indexer.service.BlockIngestionService;
import com.anchor.indexer.service.ReorgService;
import com.anchor.parser.service.TokenMetadataService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockScannerCatchupTest {

    private AnchorSettings settings;
    private BlockScanner scanner;

    @BeforeEach
    void setUp() {
        settings = new AnchorSettings();
        settings.setChainKey("base");
        ChainRegistry registry = mock(ChainRegistry.class);
        when(registry.requireByKey("base")).thenReturn(chain(8453L));
        scanner = new BlockScanner(settings, registry, mock(RpcGateway.class), mock(SwapParser.class),
                mock(BlockIngestionService.class), mock(CheckpointMapper.class), mock(BlockHashMapper.class),
                mock(StringRedisTemplate.class), mock(TokenMetadataService.class), mock(ReorgService.class),
                new SimpleMeterRegistry(), mock(ScannerLease.class));
    }

    @Test
    void jumpsWhenLagExceedsMaxCatchup() {
        settings.getScanner().setMaxCatchupBlocks(2000);
        assertEquals(98_000L, scanner.applyMaxCatchup(8453L, 70_000L, 100_000L));
    }

    @Test
    void keepsNextWhenWithinThreshold() {
        settings.getScanner().setMaxCatchupBlocks(2000);
        assertEquals(99_000L, scanner.applyMaxCatchup(8453L, 99_000L, 100_000L));
    }

    @Test
    void disabledWhenMaxCatchupZero() {
        settings.getScanner().setMaxCatchupBlocks(0);
        assertEquals(1L, scanner.applyMaxCatchup(8453L, 1L, 100_000L));
    }

    @Test
    void defaultMatchesConstant() {
        assertEquals(AnchorConstants.MAX_CATCHUP_BLOCKS, settings.getScanner().getMaxCatchupBlocks());
        assertEquals(AnchorConstants.CATCH_UP_CONCURRENCY, settings.getScanner().getCatchupBatchSize());
    }

    private static ChainDefinition chain(long chainId) {
        return new ChainDefinition(
                "base", chainId, "Base", ChainTypeEnum.EVM, false, 10,
                GasModelEnum.EIP1559_OP_L1_FEE, List.of(SignModeEnum.CLIENT),
                new ChainDefinition.NativeToken("ETH", 18, AnchorConstants.NATIVE_TOKEN_ADDRESS),
                List.of(),
                new ChainDefinition.Contracts(null, null, null, null, null, null, null, null, null, null, null, null, null),
                Set.of(), List.of());
    }
}
