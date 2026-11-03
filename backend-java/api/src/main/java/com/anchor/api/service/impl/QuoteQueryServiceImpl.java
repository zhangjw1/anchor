package com.anchor.api.service.impl;

import com.anchor.api.service.QuoteQueryService;
import com.anchor.api.service.TokenInfoService;
import com.anchor.common.chain.Quoter;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.support.Addresses;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 报价限流、短缓存，并补齐代币展示字段。
 */
@Service
public class QuoteQueryServiceImpl implements QuoteQueryService {

    private static final Logger log = LoggerFactory.getLogger(QuoteQueryServiceImpl.class);

    private final Quoter quoter;
    private final TokenInfoService tokenInfoService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public QuoteQueryServiceImpl(Quoter quoter, TokenInfoService tokenInfoService, StringRedisTemplate redis,
                                 ObjectMapper objectMapper) {
        this.quoter = quoter;
        this.tokenInfoService = tokenInfoService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public QuoteView quote(long chainId, String tokenIn, String tokenOut, BigInteger amountIn, int slippageBps, String clientIp) {
        enforceRateLimit(clientIp);
        String in = Addresses.normalizeEvm(tokenIn);
        String out = Addresses.normalizeEvm(tokenOut);
        String cacheKey = RedisKeys.quote(chainId, in, out, amountIn.toString(), slippageBps);
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, QuoteView.class);
            } catch (JsonProcessingException ex) {
                log.warn("报价缓存反序列化失败，忽略");
            }
        }
        Quoter.Quote quote = quoter.quote(new Quoter.QuoteRequest(chainId, in, out, amountIn, slippageBps));
        TokenInfoService.TokenInfo inInfo = tokenInfoService.resolve(chainId, in);
        TokenInfoService.TokenInfo outInfo = tokenInfoService.resolve(chainId, out);
        QuoteView view = new QuoteView(quote, inInfo.address(), inInfo.symbol(), inInfo.decimals(),
                outInfo.address(), outInfo.symbol(), outInfo.decimals(), amountIn);
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(view),
                    Duration.ofSeconds(AnchorConstants.QUOTE_CACHE_SECONDS));
        } catch (JsonProcessingException ex) {
            log.warn("报价缓存序列化失败，跳过写入");
        }
        return view;
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
