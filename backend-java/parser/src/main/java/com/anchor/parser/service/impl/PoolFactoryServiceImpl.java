package com.anchor.parser.service.impl;

import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.evm.generated.UniswapV3Pool;
import com.anchor.common.support.AbiOutputs;
import com.anchor.common.support.Addresses;
import com.anchor.parser.service.PoolFactoryService;
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
 * 先查内存和 Redis，没有再调用池子的 factory()。
 */
@Service
public class PoolFactoryServiceImpl implements PoolFactoryService {

    private static final Logger log = LoggerFactory.getLogger(PoolFactoryServiceImpl.class);
    private static final String CALL_DATA = FunctionEncoder.encode(new Function(
            UniswapV3Pool.FUNC_FACTORY, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {})));

    private final RpcGateway rpcGateway;
    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, String> memory = new ConcurrentHashMap<>();

    public PoolFactoryServiceImpl(RpcGateway rpcGateway, StringRedisTemplate redis) {
        this.rpcGateway = rpcGateway;
        this.redis = redis;
    }

    @Override
    public Optional<String> findFactory(long chainId, String pool) {
        String key = chainId + ":" + pool;
        String cached = memory.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        String redisKey = RedisKeys.poolFactory(chainId, pool);
        String stored = redis.opsForValue().get(redisKey);
        if (stored != null) {
            memory.put(key, stored);
            return Optional.of(stored);
        }
        try {
            Optional<String> decoded = decodeAddress(rpcGateway.ethCall(chainId, pool, CALL_DATA));
            decoded.ifPresent(factory -> {
                memory.put(key, factory);
                redis.opsForValue().set(redisKey, factory, Duration.ofDays(AnchorConstants.POOL_CACHE_DAYS));
            });
            return decoded;
        } catch (RuntimeException ex) {
            log.warn("查询池子工厂失败，chainId={}，池子={}", chainId, pool);
            return Optional.empty();
        }
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
