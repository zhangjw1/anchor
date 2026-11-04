package com.anchor.indexer.scan;

import com.anchor.common.chain.AnchorSettings;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.dao.BlockHashMapper;
import com.anchor.common.dao.CheckpointMapper;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.domain.GasModelEnum;
import com.anchor.common.domain.SignModeEnum;
import com.anchor.indexer.service.BlockIngestionService;
import com.anchor.indexer.service.ChainEventPublishService;
import com.anchor.indexer.service.ReorgService;
import com.anchor.parser.service.TokenMetadataService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockScannerResumeTest {

    private BlockScanner scanner;

    @BeforeEach
    void setUp() {
        AnchorSettings settings = new AnchorSettings();
        settings.setChainKey("base");
        ChainRegistry registry = mock(ChainRegistry.class);
        when(registry.requireByKey("base")).thenReturn(chain(8453L));
        scanner = new BlockScanner(settings, registry, mock(RpcGateway.class), mock(SwapParser.class),
                mock(BlockIngestionService.class), mock(CheckpointMapper.class), mock(BlockHashMapper.class),
                mock(StringRedisTemplate.class), mock(TokenMetadataService.class), mock(ReorgService.class),
                mock(ChainEventPublishService.class), new SimpleMeterRegistry(), mock(ScannerLease.class));
        scanner.registerPausedGauge();
    }

    @Test
    void resumeClearsPausedFlag() {
        AtomicBoolean paused = (AtomicBoolean) ReflectionTestUtils.getField(scanner, "paused");
        paused.set(true);
        assertTrue(scanner.isPaused());
        scanner.resume(8453L);
        assertFalse(scanner.isPaused());
    }

    @Test
    void resumeRejectsWrongChain() {
        assertThrows(AnchorException.class, () -> scanner.resume(1L));
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
