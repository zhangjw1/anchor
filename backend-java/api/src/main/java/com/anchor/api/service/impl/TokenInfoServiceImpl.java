package com.anchor.api.service.impl;

import com.anchor.api.service.TokenInfoService;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.TokenMapper;
import com.anchor.common.entity.TokenDO;
import com.anchor.common.evm.generated.Erc20;
import com.anchor.common.support.AbiOutputs;
import com.anchor.common.support.Addresses;
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
import java.util.Arrays;
import java.util.List;

/**
 * API 侧代币元数据：原生币走链配置，ERC20 先 Redis/DB，再 eth_call。
 */
@Service
public class TokenInfoServiceImpl implements TokenInfoService {

    private static final Logger log = LoggerFactory.getLogger(TokenInfoServiceImpl.class);
    private static final String SYMBOL_DATA = FunctionEncoder.encode(new Function(
            Erc20.FUNC_SYMBOL, List.of(), Arrays.asList(new TypeReference<Utf8String>() {})));
    private static final String DECIMALS_DATA = FunctionEncoder.encode(new Function(
            Erc20.FUNC_DECIMALS, List.of(), Arrays.asList(new TypeReference<Uint8>() {})));

    private final ChainRegistry chainRegistry;
    private final TokenMapper tokenMapper;
    private final RpcGateway rpcGateway;
    private final StringRedisTemplate redis;

    public TokenInfoServiceImpl(ChainRegistry chainRegistry, TokenMapper tokenMapper, RpcGateway rpcGateway,
                                StringRedisTemplate redis) {
        this.chainRegistry = chainRegistry;
        this.tokenMapper = tokenMapper;
        this.rpcGateway = rpcGateway;
        this.redis = redis;
    }

    @Override
    public TokenInfo resolve(long chainId, String address) {
        ChainDefinition chain = chainRegistry.requireById(chainId);
        String normalized = Addresses.lower(address);
        if (normalized.equals(chain.nativeToken().address()) || normalized.equals(AnchorConstants.NATIVE_TOKEN_ADDRESS)) {
            return new TokenInfo(chain.nativeToken().address(), chain.nativeToken().symbol(), chain.nativeToken().decimals());
        }
        String cacheKey = RedisKeys.token(chainId, normalized);
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            String[] parts = cached.split("\\|", -1);
            Integer decimals = parts.length > 1 && !parts[1].isBlank() ? Integer.valueOf(parts[1]) : null;
            return new TokenInfo(normalized, blankToNull(parts[0]), decimals);
        }
        TokenDO row = tokenMapper.getByChainAndAddress(chainId, normalized);
        if (row != null) {
            cache(cacheKey, row.getSymbol(), row.getDecimals());
            return new TokenInfo(normalized, row.getSymbol(), row.getDecimals());
        }
        String symbol = readSymbol(chainId, normalized);
        Integer decimals = readDecimals(chainId, normalized);
        cache(cacheKey, symbol, decimals);
        return new TokenInfo(normalized, symbol, decimals);
    }

    private void cache(String key, String symbol, Integer decimals) {
        if (symbol == null && decimals == null) {
            return;
        }
        redis.opsForValue().set(key, (symbol == null ? "" : symbol) + "|" + (decimals == null ? "" : decimals),
                Duration.ofHours(AnchorConstants.TOKEN_CACHE_HOURS));
    }

    private String readSymbol(long chainId, String address) {
        try {
            String raw = rpcGateway.ethCall(chainId, address, SYMBOL_DATA);
            if (raw == null || "0x".equals(raw)) {
                return null;
            }
            var decoded = FunctionReturnDecoder.decode(raw, AbiOutputs.single(new TypeReference<Utf8String>() {}));
            if (decoded.isEmpty()) {
                return bytes32Symbol(raw);
            }
            String symbol = ((Utf8String) decoded.get(0)).getValue();
            return symbol == null || symbol.isBlank() ? bytes32Symbol(raw) : symbol;
        } catch (RuntimeException ex) {
            log.warn("读取代币符号失败，chainId={}，地址={}", chainId, address);
            return null;
        }
    }

    private Integer readDecimals(long chainId, String address) {
        try {
            String raw = rpcGateway.ethCall(chainId, address, DECIMALS_DATA);
            var decoded = FunctionReturnDecoder.decode(raw, AbiOutputs.single(new TypeReference<Uint8>() {}));
            if (decoded.isEmpty()) {
                return null;
            }
            return ((Uint8) decoded.get(0)).getValue().intValue();
        } catch (RuntimeException ex) {
            log.warn("读取代币精度失败，chainId={}，地址={}", chainId, address);
            return null;
        }
    }

    private static String bytes32Symbol(String raw) {
        String hex = Numeric.cleanHexPrefix(raw);
        if (hex.length() < 64) {
            return null;
        }
        byte[] bytes = Numeric.hexStringToByteArray(hex.substring(0, 64));
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        String symbol = new String(bytes, 0, end);
        return symbol.isBlank() ? null : symbol;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
