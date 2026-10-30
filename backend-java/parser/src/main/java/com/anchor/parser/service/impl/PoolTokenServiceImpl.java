package com.anchor.parser.service.impl;

import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.evm.generated.UniswapV3Pool;
import com.anchor.common.support.AbiOutputs;
import com.anchor.common.support.Addresses;
import com.anchor.parser.dto.PoolTokensDTO;
import com.anchor.parser.service.PoolTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 先查内存和 Redis，没有再调用池子的 token0()/token1()。
 */
@Service
public class PoolTokenServiceImpl implements PoolTokenService {

    private static final Logger log = LoggerFactory.getLogger(PoolTokenServiceImpl.class);
    private static final String TOKEN0_DATA = FunctionEncoder.encode(new Function(
            UniswapV3Pool.FUNC_TOKEN0, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {})));
    private static final String TOKEN1_DATA = FunctionEncoder.encode(new Function(
            UniswapV3Pool.FUNC_TOKEN1, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {})));
    private static final String STORED_SEPARATOR = ",";

    private final RpcGateway rpcGateway;
    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, PoolTokensDTO> memory = new ConcurrentHashMap<>();

    public PoolTokenServiceImpl(RpcGateway rpcGateway, StringRedisTemplate redis) {
        this.rpcGateway = rpcGateway;
        this.redis = redis;
    }

    @Override
    public Optional<PoolTokensDTO> findTokens(long chainId, String pool) {
        String key = chainId + ":" + pool;
        PoolTokensDTO cached = memory.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        String redisKey = RedisKeys.poolTokens(chainId, pool);
        String stored = redis.opsForValue().get(redisKey);
        if (stored != null) {
            Optional<PoolTokensDTO> parsed = parseStored(stored);
            parsed.ifPresent(tokens -> memory.put(key, tokens));
            return parsed;
        }
        try {
            Optional<String> token0 = decodeAddress(rpcGateway.ethCall(chainId, pool, TOKEN0_DATA));
            Optional<String> token1 = decodeAddress(rpcGateway.ethCall(chainId, pool, TOKEN1_DATA));
            if (token0.isEmpty() || token1.isEmpty()) {
                return Optional.empty();
            }
            PoolTokensDTO tokens = new PoolTokensDTO(token0.get(), token1.get());
            memory.put(key, tokens);
            redis.opsForValue().set(redisKey, tokens.token0() + STORED_SEPARATOR + tokens.token1(), Duration.ofDays(AnchorConstants.POOL_CACHE_DAYS));
            return Optional.of(tokens);
        } catch (RuntimeException ex) {
            log.warn("查询池子代币失败，chainId={}，池子={}", chainId, pool);
            return Optional.empty();
        }
    }

    private static Optional<PoolTokensDTO> parseStored(String stored) {
        int split = stored.indexOf(STORED_SEPARATOR);
        if (split <= 0 || split >= stored.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new PoolTokensDTO(stored.substring(0, split), stored.substring(split + 1)));
    }

    private static Optional<String> decodeAddress(String raw) {
        if (raw == null || raw.length() < AnchorConstants.ABI_WORD_HEX_LENGTH) {
            return Optional.empty();
        }
        @SuppressWarnings("rawtypes")
        List<Type> decoded = FunctionReturnDecoder.decode(raw, AbiOutputs.single(new TypeReference<Address>() {}));
        if (decoded.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Addresses.lower(((Address) decoded.get(0)).getValue()));
    }
}
