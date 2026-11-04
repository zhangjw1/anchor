package com.anchor.api.service.impl;

import com.anchor.api.service.QuoteQueryService;
import com.anchor.api.service.TradeQueryService;
import com.anchor.api.service.WebSocketPushService;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.chain.StoredQuote;
import com.anchor.common.chain.TxBuilder;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.SignModeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeQueryServiceImplTest {

    private QuoteQueryService quotes;
    private TxBuilder txBuilder;
    private RpcGateway rpc;
    private TradeQueryServiceImpl service;
    private ChainDefinition.Contracts contracts;

    @BeforeEach
    void setUp() {
        quotes = mock(QuoteQueryService.class);
        ChainRegistry registry = mock(ChainRegistry.class);
        txBuilder = mock(TxBuilder.class);
        rpc = mock(RpcGateway.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        when(redis.opsForSet()).thenReturn(mock(SetOperations.class));
        WebSocketPushService ws = mock(WebSocketPushService.class);
        service = new TradeQueryServiceImpl(registry, quotes, txBuilder, rpc, redis, new ObjectMapper(), ws,
                new SimpleMeterRegistry());

        contracts = new ChainDefinition.Contracts(
                "0x4200000000000000000000000000000000000006", null, null,
                "0xcA11bde05977b3631167028862bE2a173976CA11", null, null,
                null, "0x94cC0AaC535CCDB3C01d6787D6413C739ae12bc4", null, null, null, null, null);
        ChainDefinition chain = new ChainDefinition(
                "base-sepolia", AnchorConstants.TRADE_ALLOWED_CHAIN_ID, "Base Sepolia",
                com.anchor.common.domain.ChainTypeEnum.EVM, true, 5,
                com.anchor.common.domain.GasModelEnum.EIP1559_OP_L1_FEE,
                List.of(SignModeEnum.CLIENT),
                new ChainDefinition.NativeToken("ETH", 18, AnchorConstants.NATIVE_TOKEN_ADDRESS),
                List.of(), contracts, java.util.Set.of(), List.of());
        when(registry.requireById(AnchorConstants.TRADE_ALLOWED_CHAIN_ID)).thenReturn(chain);
        when(txBuilder.buildSwap(any(), anyString(), anyLong())).thenReturn(
                new TxBuilder.UnsignedTransaction(AnchorConstants.TRADE_ALLOWED_CHAIN_ID, contracts.swapRouter02(), "0xabc", "0"));
        when(rpc.ethCall(anyLong(), anyString(), anyString(), anyString(), any())).thenReturn("0x");
        when(rpc.estimateGas(anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(BigInteger.valueOf(21000));
    }

    @Test
    void rejectsMainnet() {
        StoredQuote quote = new StoredQuote("q1", 8453L, "0x4200000000000000000000000000000000000006",
                "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913", false, false,
                BigInteger.TEN, BigInteger.ONE, BigInteger.ONE, 500, 50, System.currentTimeMillis() + 60_000);
        when(quotes.findStored("q1")).thenReturn(quote);
        AnchorException ex = assertThrows(AnchorException.class,
                () -> service.prepare("q1", "0x1111111111111111111111111111111111111111", false));
        assertEquals("UNSUPPORTED", ex.getCode());
    }

    @Test
    void rejectsExpiredQuote() {
        StoredQuote quote = new StoredQuote("q2", AnchorConstants.TRADE_ALLOWED_CHAIN_ID,
                "0x4200000000000000000000000000000000000006",
                "0x4200000000000000000000000000000000000006", false, false,
                BigInteger.TEN, BigInteger.ONE, BigInteger.ONE, 500, 50, System.currentTimeMillis() - 1);
        when(quotes.findStored("q2")).thenReturn(quote);
        AnchorException ex = assertThrows(AnchorException.class,
                () -> service.prepare("q2", "0x1111111111111111111111111111111111111111", false));
        assertEquals("QUOTE_EXPIRED", ex.getCode());
    }

    @Test
    void skipsSimulationWhenApproveNeeded() {
        StoredQuote quote = erc20Quote("q3");
        when(quotes.findStored("q3")).thenReturn(quote);
        when(rpc.ethCall(eq(AnchorConstants.TRADE_ALLOWED_CHAIN_ID), eq(quote.tokenIn()), anyString()))
                .thenReturn(encodeUint256(BigInteger.ZERO));
        when(txBuilder.buildApprove(anyLong(), anyString(), anyString(), any()))
                .thenReturn(new TxBuilder.UnsignedTransaction(AnchorConstants.TRADE_ALLOWED_CHAIN_ID, quote.tokenIn(), "0xapprove", "0"));

        TradeQueryService.PrepareResult result = service.prepare("q3", "0x1111111111111111111111111111111111111111", false);
        assertTrue(result.needsApprove());
        assertNull(result.gasEstimate());
        verify(rpc, never()).ethCall(anyLong(), anyString(), anyString(), anyString(), any());
        verify(rpc, never()).estimateGas(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void simulatesWhenAllowanceEnough() {
        StoredQuote quote = erc20Quote("q4");
        when(quotes.findStored("q4")).thenReturn(quote);
        when(rpc.ethCall(eq(AnchorConstants.TRADE_ALLOWED_CHAIN_ID), eq(quote.tokenIn()), anyString()))
                .thenReturn(encodeUint256(BigInteger.valueOf(100)));

        TradeQueryService.PrepareResult result = service.prepare("q4", "0x1111111111111111111111111111111111111111", false);
        assertFalse(result.needsApprove());
        assertEquals(BigInteger.valueOf(21000), result.gasEstimate());
        verify(rpc).ethCall(anyLong(), anyString(), anyString(), anyString(), any());
        verify(rpc).estimateGas(anyLong(), anyString(), anyString(), anyString(), any());
    }

    private static StoredQuote erc20Quote(String id) {
        return new StoredQuote(id, AnchorConstants.TRADE_ALLOWED_CHAIN_ID,
                "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
                "0x4200000000000000000000000000000000000006", false, false,
                BigInteger.TEN, BigInteger.ONE, BigInteger.ONE, 500, 50, System.currentTimeMillis() + 60_000);
    }

    /** eth_call 返回的单个 uint256 ABI 编码。 */
    private static String encodeUint256(BigInteger value) {
        String hex = value.toString(16);
        return "0x" + "0".repeat(Math.max(0, 64 - hex.length())) + hex;
    }
}
