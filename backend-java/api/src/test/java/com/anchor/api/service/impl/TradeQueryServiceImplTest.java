package com.anchor.api.service.impl;

import com.anchor.api.service.QuoteQueryService;
import com.anchor.api.service.WebSocketPushService;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.chain.Signer;
import com.anchor.common.chain.StoredQuote;
import com.anchor.common.chain.TxBuilder;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.domain.SignModeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeQueryServiceImplTest {

    private QuoteQueryService quotes;
    private TradeQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        quotes = mock(QuoteQueryService.class);
        ChainRegistry registry = mock(ChainRegistry.class);
        TxBuilder txBuilder = mock(TxBuilder.class);
        Signer signer = mock(Signer.class);
        RpcGateway rpc = mock(RpcGateway.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        WebSocketPushService ws = mock(WebSocketPushService.class);
        service = new TradeQueryServiceImpl(registry, quotes, txBuilder, signer, rpc, redis, new ObjectMapper(), ws,
                new SimpleMeterRegistry());

        ChainDefinition.Contracts contracts = new ChainDefinition.Contracts(
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
        when(signer.sign(any(), eq(SignModeEnum.CLIENT))).thenReturn("");
        when(txBuilder.buildSwap(any(), anyString())).thenReturn(
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
}
