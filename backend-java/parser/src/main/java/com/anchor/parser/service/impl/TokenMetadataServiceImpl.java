package com.anchor.parser.service.impl;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.TokenMapper;
import com.anchor.common.entity.TokenDO;
import com.anchor.common.evm.generated.Erc20;
import com.anchor.common.manager.MulticallClient;
import com.anchor.common.support.AbiOutputs;
import com.anchor.common.support.Addresses;
import com.anchor.parser.service.TokenMetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.utils.Numeric;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 原生币直接用链配置。ERC20 先查缓存，缺失时 Multicall3 批量读 symbol/decimals。
 */
@Service
public class TokenMetadataServiceImpl implements TokenMetadataService {

    private static final Logger log = LoggerFactory.getLogger(TokenMetadataServiceImpl.class);
    private static final String SYMBOL_DATA = FunctionEncoder.encode(new Function(
            Erc20.FUNC_SYMBOL, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {})));
    private static final String DECIMALS_DATA = FunctionEncoder.encode(new Function(
            Erc20.FUNC_DECIMALS, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {})));

    private final RpcGateway rpcGateway;
    private final MulticallClient multicallClient;
    private final TokenMapper tokenMapper;
    private final StringRedisTemplate redis;

    public TokenMetadataServiceImpl(RpcGateway rpcGateway, MulticallClient multicallClient, TokenMapper tokenMapper,
                                    StringRedisTemplate redis) {
        this.rpcGateway = rpcGateway;
        this.multicallClient = multicallClient;
        this.tokenMapper = tokenMapper;
        this.redis = redis;
    }

    @Override
    public void ensure(ChainDefinition chain, String address) {
        ensureAll(chain, List.of(address));
    }

    @Override
    public void ensureAll(ChainDefinition chain, Collection<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>();
        for (String address : addresses) {
            if (address == null || address.isBlank()) {
                continue;
            }
            String normalized = Addresses.lower(address);
            if (normalized.equals(chain.nativeToken().address())
                    || normalized.equals(AnchorConstants.NATIVE_TOKEN_ADDRESS)) {
                saveToken(chain.chainId(), chain.nativeToken().address(), chain.nativeToken().symbol(),
                        chain.nativeToken().decimals());
                continue;
            }
            String cacheKey = RedisKeys.token(chain.chainId(), normalized);
            if (redis.opsForValue().get(cacheKey) != null || tokenMapper.getByChainAndAddress(chain.chainId(), normalized) != null) {
                continue;
            }
            missing.add(normalized);
        }
        if (missing.isEmpty()) {
            return;
        }
        try {
            fetchAndSaveBatch(chain, List.copyOf(missing));
        } catch (RuntimeException ex) {
            log.warn("Multicall token metadata failed, fallback one-by-one, chainId={}, size={}",
                    chain.chainId(), missing.size());
            for (String address : missing) {
                String symbol = readSymbol(chain.chainId(), address);
                Integer decimals = readDecimals(chain.chainId(), address);
                persist(chain.chainId(), address, symbol, decimals);
            }
        }
    }

    private void fetchAndSaveBatch(ChainDefinition chain, List<String> addresses) {
        List<MulticallClient.Call> calls = new ArrayList<>(addresses.size() * 2);
        for (String address : addresses) {
            calls.add(new MulticallClient.Call(address, true, SYMBOL_DATA));
            calls.add(new MulticallClient.Call(address, true, DECIMALS_DATA));
        }
        List<byte[]> results = multicallClient.aggregate3(chain, calls);
        for (int i = 0; i < addresses.size(); i++) {
            String address = addresses.get(i);
            String symbol = decodeSymbol(results.get(i * 2));
            Integer decimals = decodeDecimals(results.get(i * 2 + 1));
            persist(chain.chainId(), address, symbol, decimals);
        }
    }

    private void persist(long chainId, String address, String symbol, Integer decimals) {
        saveToken(chainId, address, symbol, decimals);
        if (symbol != null && decimals != null) {
            redis.opsForValue().set(RedisKeys.token(chainId, address), symbol + "|" + decimals,
                    Duration.ofHours(AnchorConstants.TOKEN_CACHE_HOURS));
        }
    }

    private void saveToken(long chainId, String address, String symbol, Integer decimals) {
        TokenDO data = new TokenDO();
        data.setChainId(chainId);
        data.setAddress(address);
        data.setSymbol(symbol);
        data.setDecimals(decimals);
        data.setCreatedBy(AnchorConstants.ACTOR_PARSER);
        data.setUpdatedBy(AnchorConstants.ACTOR_PARSER);
        data.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        data.setUpdatedAt(data.getCreatedAt());
        tokenMapper.save(data);
    }

    private String readSymbol(long chainId, String address) {
        try {
            return decodeSymbol(Numeric.hexStringToByteArray(rpcGateway.ethCall(chainId, address, SYMBOL_DATA)));
        } catch (RuntimeException ex) {
            log.warn("读取代币符号失败，chainId={}，地址={}", chainId, address);
            return null;
        }
    }

    private Integer readDecimals(long chainId, String address) {
        try {
            return decodeDecimals(Numeric.hexStringToByteArray(rpcGateway.ethCall(chainId, address, DECIMALS_DATA)));
        } catch (RuntimeException ex) {
            log.warn("读取代币精度失败，chainId={}，地址={}", chainId, address);
            return null;
        }
    }

    private static String decodeSymbol(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        String hex = Numeric.toHexString(raw);
        try {
            var decoded = FunctionReturnDecoder.decode(hex, AbiOutputs.single(new TypeReference<Utf8String>() {}));
            if (!decoded.isEmpty()) {
                String symbol = ((Utf8String) decoded.get(0)).getValue();
                if (symbol != null && !symbol.isBlank()) {
                    return symbol;
                }
            }
        } catch (RuntimeException ignored) {
            // fall through to bytes32
        }
        return bytes32Symbol(hex);
    }

    private static Integer decodeDecimals(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        try {
            var decoded = FunctionReturnDecoder.decode(Numeric.toHexString(raw),
                    AbiOutputs.single(new TypeReference<Uint8>() {}));
            if (decoded.isEmpty()) {
                return null;
            }
            return ((Uint8) decoded.get(0)).getValue().intValue();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String bytes32Symbol(String raw) {
        String hex = Numeric.cleanHexPrefix(raw);
        if (hex.length() < 64) {
            return null;
        }
        byte[] bytes = Numeric.hexStringToByteArray(hex.substring(0, 64));
        int end = bytes.length;
        while (end > 0 && bytes[end - 1] == 0) {
            end--;
        }
        return end == 0 ? null : new String(bytes, 0, end, java.nio.charset.StandardCharsets.UTF_8);
    }
}
