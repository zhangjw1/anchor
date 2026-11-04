package com.anchor.api.service.impl;

import com.anchor.api.service.QuoteQueryService;
import com.anchor.api.service.TokenInfoService;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.Quoter;
import com.anchor.common.chain.StoredQuote;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.metrics.AnchorMetricNames;
import com.anchor.common.support.Addresses;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 报价限流、短缓存，并按 quoteId 持久化供交易构建。
 */
@Service
public class QuoteQueryServiceImpl implements QuoteQueryService {

    private static final Logger log = LoggerFactory.getLogger(QuoteQueryServiceImpl.class);

    private final ChainRegistry chainRegistry;
    private final Quoter quoter;
    private final TokenInfoService tokenInfoService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public QuoteQueryServiceImpl(ChainRegistry chainRegistry, Quoter quoter, TokenInfoService tokenInfoService,
                                  StringRedisTemplate redis, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.chainRegistry = chainRegistry;
        this.quoter = quoter;
        this.tokenInfoService = tokenInfoService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public QuoteView quote(long chainId, String tokenIn, String tokenOut, BigInteger amountIn, int slippageBps, String clientIp) {
        enforceRateLimit(clientIp);
        Timer.Sample sample = Timer.start(meterRegistry);
        ChainDefinition chain = chainRegistry.requireById(chainId);
        String rawIn = Addresses.normalizeEvm(tokenIn);
        String rawOut = Addresses.normalizeEvm(tokenOut);
        boolean nativeIn = isNative(chain, rawIn);
        boolean nativeOut = isNative(chain, rawOut);
        String in = nativeIn ? Addresses.lower(chain.contracts().weth()) : rawIn;
        String out = nativeOut ? Addresses.lower(chain.contracts().weth()) : rawOut;
        String cacheKey = RedisKeys.quote(chainId, rawIn, rawOut, amountIn.toString(), slippageBps);
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                QuoteView view = objectMapper.readValue(cached, QuoteView.class);
                persistById(chainId, view);
                sample.stop(meterRegistry.timer(AnchorMetricNames.QUOTE_LATENCY, "chainId", String.valueOf(chainId), "result", "cache"));
                return view;
            } catch (JsonProcessingException ex) {
                log.warn("报价缓存反序列化失败，忽略");
            }
        }
        try {
            Quoter.Quote quote = quoter.quote(new Quoter.QuoteRequest(chainId, rawIn, rawOut, amountIn, slippageBps));
            TokenInfoService.TokenInfo inInfo = tokenInfoService.resolve(chainId, nativeIn ? chain.nativeToken().address() : in);
            TokenInfoService.TokenInfo outInfo = tokenInfoService.resolve(chainId, nativeOut ? chain.nativeToken().address() : out);
            QuoteView view = new QuoteView(quote, inInfo.address(), inInfo.symbol(), inInfo.decimals(),
                    outInfo.address(), outInfo.symbol(), outInfo.decimals(), amountIn, slippageBps, nativeIn, nativeOut);
            try {
                redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(view),
                        Duration.ofSeconds(AnchorConstants.QUOTE_CACHE_SECONDS));
            } catch (JsonProcessingException ex) {
                log.warn("报价缓存序列化失败，跳过写入");
            }
            persistById(chainId, view);
            sample.stop(meterRegistry.timer(AnchorMetricNames.QUOTE_LATENCY, "chainId", String.valueOf(chainId), "result", "ok"));
            return view;
        } catch (RuntimeException ex) {
            sample.stop(meterRegistry.timer(AnchorMetricNames.QUOTE_LATENCY, "chainId", String.valueOf(chainId), "result", "error"));
            meterRegistry.counter(AnchorMetricNames.QUOTE_FAILURES, "chainId", String.valueOf(chainId)).increment();
            throw ex;
        }
    }

    @Override
    public StoredQuote findStored(String quoteId) {
        if (quoteId == null || quoteId.isBlank()) {
            return null;
        }
        String raw = redis.opsForValue().get(RedisKeys.quoteById(quoteId));
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, StoredQuote.class);
        } catch (JsonProcessingException ex) {
            log.warn("按 quoteId 反序列化失败，quoteId={}", quoteId);
            return null;
        }
    }

    private void persistById(long chainId, QuoteView view) {
        StoredQuote stored = new StoredQuote(
                view.quote().quoteId(),
                chainId,
                poolToken(view.tokenIn(), view.nativeIn(), chainId),
                poolToken(view.tokenOut(), view.nativeOut(), chainId),
                view.nativeIn(),
                view.nativeOut(),
                view.amountIn(),
                view.quote().amountOut(),
                view.quote().amountOutMinimum(),
                view.quote().fee(),
                view.slippageBps(),
                view.quote().expiresAtEpochMs());
        long ttlMs = Math.max(1_000L, view.quote().expiresAtEpochMs() - System.currentTimeMillis());
        try {
            redis.opsForValue().set(RedisKeys.quoteById(view.quote().quoteId()),
                    objectMapper.writeValueAsString(stored), Duration.ofMillis(ttlMs));
        } catch (JsonProcessingException ex) {
            log.warn("按 quoteId 写入失败，quoteId={}", view.quote().quoteId());
        }
    }

    private String poolToken(String displayAddress, boolean nativeToken, long chainId) {
        if (!nativeToken) {
            return Addresses.lower(displayAddress);
        }
        ChainDefinition chain = chainRegistry.requireById(chainId);
        return Addresses.lower(chain.contracts().weth());
    }

    private static boolean isNative(ChainDefinition chain, String address) {
        return address.equals(chain.nativeToken().address()) || address.equals(AnchorConstants.NATIVE_TOKEN_ADDRESS);
    }

    private void enforceRateLimit(String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        String key = RedisKeys.quoteRateLimit(ip);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, AnchorConstants.QUOTE_RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count != null && count > AnchorConstants.QUOTE_RATE_LIMIT_PER_WINDOW) {
            throw new AnchorException("RATE_LIMITED", "报价请求过于频繁，请稍后再试");
        }
    }
}
