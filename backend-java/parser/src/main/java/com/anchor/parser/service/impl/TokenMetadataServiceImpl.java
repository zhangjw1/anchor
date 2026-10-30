package com.anchor.parser.service.impl;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.dao.TokenMapper;
import com.anchor.common.entity.TokenDO;
import com.anchor.common.evm.generated.Erc20;
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
import java.util.Arrays;
import java.util.List;

/**
 * 原生币直接用链配置。ERC20 先查缓存，缺失时再读 symbol 和 decimals。
 */
@Service
public class TokenMetadataServiceImpl implements TokenMetadataService {

    private static final Logger log = LoggerFactory.getLogger(TokenMetadataServiceImpl.class);
    private static final String SYMBOL_DATA = FunctionEncoder.encode(new Function(
            Erc20.FUNC_SYMBOL, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {})));
    private static final String DECIMALS_DATA = FunctionEncoder.encode(new Function(
            Erc20.FUNC_DECIMALS, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {})));

    private final RpcGateway rpcGateway;
    private final TokenMapper tokenMapper;
    private final StringRedisTemplate redis;

    public TokenMetadataServiceImpl(RpcGateway rpcGateway, TokenMapper tokenMapper, StringRedisTemplate redis) {
        this.rpcGateway = rpcGateway;
        this.tokenMapper = tokenMapper;
        this.redis = redis;
    }

    @Override
    public void ensure(ChainDefinition chain, String address) {
        String normalized = Addresses.lower(address);
        if (normalized.equals(chain.nativeToken().address())) {
            saveNative(chain.chainId(), normalized, chain.nativeToken().symbol(), chain.nativeToken().decimals());
            return;
        }
        String cacheKey = RedisKeys.token(chain.chainId(), normalized);
        if (redis.opsForValue().get(cacheKey) != null || tokenMapper.getByChainAndAddress(chain.chainId(), normalized) != null) {
            return;
        }
        String symbol = readSymbol(chain.chainId(), normalized);
        Integer decimals = readDecimals(chain.chainId(), normalized);
        saveNative(chain.chainId(), normalized, symbol, decimals);
        if (symbol != null && decimals != null) {
            redis.opsForValue().set(cacheKey, symbol + "|" + decimals, Duration.ofHours(AnchorConstants.TOKEN_CACHE_HOURS));
        }
    }

    private void saveNative(long chainId, String address, String symbol, Integer decimals) {
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
        int end = bytes.length;
        while (end > 0 && bytes[end - 1] == 0) {
            end--;
        }
        return end == 0 ? null : new String(bytes, 0, end, java.nio.charset.StandardCharsets.UTF_8);
    }
}
