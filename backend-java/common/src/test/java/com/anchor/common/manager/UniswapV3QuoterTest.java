package com.anchor.common.manager;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.Quoter;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.ChainTypeEnum;
import com.anchor.common.domain.GasModelEnum;
import com.anchor.common.domain.SignModeEnum;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UniswapV3QuoterTest {

    private static final String WETH = "0x4200000000000000000000000000000000000006";
    private static final String USDC = "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913";
    private static final String QUOTER = "0x3d4e44eb1374240ce5f1b871ab261cd16335b76a";
    private static final String ORACLE = "0x420000000000000000000000000000000000000f";

    @Test
    void picksLargestAmountOutAcrossFees() {
        ChainRegistry registry = mock(ChainRegistry.class);
        when(registry.requireById(8453L)).thenReturn(chain());
        RpcGateway rpc = mock(RpcGateway.class);
        AtomicInteger seq = new AtomicInteger();
        when(rpc.ethCall(eq(8453L), eq(QUOTER), anyString())).thenAnswer(invocation -> {
            int n = seq.incrementAndGet();
            return encodeQuote(BigInteger.valueOf(1_000L * n), BigInteger.valueOf(120_000L));
        });
        when(rpc.ethCall(eq(8453L), eq(ORACLE), anyString())).thenReturn(pad(BigInteger.TEN));
        PoolPriceOracle oracle = mock(PoolPriceOracle.class);
        when(oracle.midPriceHuman(any(), anyString(), anyInt(), anyString(), anyInt())).thenReturn(Optional.empty());

        UniswapV3Quoter quoter = new UniswapV3Quoter(registry, rpc, oracle);
        Quoter.Quote quote = quoter.quote(new Quoter.QuoteRequest(8453L, USDC, WETH, BigInteger.valueOf(1_000_000L), 50));
        assertEquals(BigInteger.valueOf(4_000L), quote.amountOut());
        assertEquals(BigInteger.valueOf(3_980L), quote.amountOutMinimum());
        assertTrue(quote.expiresAtEpochMs() > System.currentTimeMillis());
        assertEquals(BigInteger.TEN, quote.l1DataFeeWei());
    }

    @Test
    void rejectsWhenAllFeesFail() {
        ChainRegistry registry = mock(ChainRegistry.class);
        when(registry.requireById(8453L)).thenReturn(chain());
        RpcGateway rpc = mock(RpcGateway.class);
        when(rpc.ethCall(anyLong(), anyString(), anyString())).thenThrow(new RpcGateway.RpcException("boom", null));
        UniswapV3Quoter quoter = new UniswapV3Quoter(registry, rpc, mock(PoolPriceOracle.class));
        AnchorException ex = assertThrows(AnchorException.class,
                () -> quoter.quote(new Quoter.QuoteRequest(8453L, USDC, WETH, BigInteger.TEN, 50)));
        assertEquals("NO_LIQUIDITY", ex.getCode());
    }

    private static String encodeQuote(BigInteger amountOut, BigInteger gas) {
        return "0x" + padRaw(amountOut) + padRaw(BigInteger.ONE) + padRaw(BigInteger.ONE) + padRaw(gas);
    }

    private static String pad(BigInteger value) {
        return "0x" + padRaw(value);
    }

    private static String padRaw(BigInteger value) {
        String hex = value.toString(16);
        return "0".repeat(Math.max(0, 64 - hex.length())) + hex;
    }

    private static ChainDefinition chain() {
        return new ChainDefinition(
                "base", 8453L, "Base", ChainTypeEnum.EVM, false, 10, GasModelEnum.EIP1559_OP_L1_FEE,
                List.of(SignModeEnum.CLIENT),
                new ChainDefinition.NativeToken("ETH", 18, "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"),
                List.of(),
                new ChainDefinition.Contracts(WETH, USDC, null, "0xca11bde05977b3631167028862be2a173976ca11",
                        ORACLE, "0x33128a8fc17869897dce68ed026d694621f6fdfd", QUOTER,
                        "0x2626664c2603336e57b271c5c0b26f421741e481", null, null, null, null, null),
                Set.of(WETH, USDC),
                List.of());
    }
}
