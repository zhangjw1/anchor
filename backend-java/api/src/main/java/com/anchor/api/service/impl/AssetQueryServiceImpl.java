package com.anchor.api.service.impl;

import com.anchor.api.dto.AssetHoldingDTO;
import com.anchor.api.dto.AssetPortfolioDTO;
import com.anchor.api.service.AssetQueryService;
import com.anchor.api.service.TokenInfoService;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.constant.RedisKeys;
import com.anchor.common.evm.generated.Erc20;
import com.anchor.common.manager.MulticallClient;
import com.anchor.common.manager.PoolPriceOracle;
import com.anchor.common.support.Addresses;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Multicall3 批量查余额，池价推 USD，Redis 短缓存。
 */
@Service
public class AssetQueryServiceImpl implements AssetQueryService {

    private static final Logger log = LoggerFactory.getLogger(AssetQueryServiceImpl.class);

    private final ChainRegistry chainRegistry;
    private final MulticallClient multicallClient;
    private final PoolPriceOracle priceOracle;
    private final TokenInfoService tokenInfoService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public AssetQueryServiceImpl(ChainRegistry chainRegistry, MulticallClient multicallClient, PoolPriceOracle priceOracle,
                                 TokenInfoService tokenInfoService, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.chainRegistry = chainRegistry;
        this.multicallClient = multicallClient;
        this.priceOracle = priceOracle;
        this.tokenInfoService = tokenInfoService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssetPortfolioDTO portfolio(long chainId, String address, List<String> extraTokens) {
        ChainDefinition chain = chainRegistry.requireById(chainId);
        String owner = Addresses.normalizeEvm(address);
        List<String> tokens = resolveTokenList(chain, extraTokens);
        String fingerprint = String.join(",", tokens);
        String cacheKey = RedisKeys.assetPortfolio(chainId, owner, Integer.toHexString(fingerprint.hashCode()));
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, AssetPortfolioDTO.class);
            } catch (JsonProcessingException ex) {
                log.warn("资产缓存反序列化失败，忽略");
            }
        }
        // 计算资产组合
        AssetPortfolioDTO portfolio = load(chain, owner, tokens);
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(portfolio), Duration.ofSeconds(AnchorConstants.ASSET_CACHE_SECONDS));
        } catch (JsonProcessingException ex) {
            log.warn("资产缓存序列化失败，跳过写入");
        }
        return portfolio;
    }

    private AssetPortfolioDTO load(ChainDefinition chain, String owner, List<String> tokens) {
        List<AssetHoldingDTO> holdings = new ArrayList<>();
        // 计算原生币ETH余额
        BigInteger ethBalance = multicallClient.ethBalance(chain, owner);
        TokenInfoService.TokenInfo nativeInfo = tokenInfoService.resolve(chain.chainId(), chain.nativeToken().address());
        holdings.add(holding(chain, nativeInfo, ethBalance, true));

        // 计算ERC20代币余额
        List<String> erc20 = tokens.stream()
                .filter(t -> !t.equals(chain.nativeToken().address()) && !t.equals(AnchorConstants.NATIVE_TOKEN_ADDRESS))
                .toList();
        if (!erc20.isEmpty()) {
            List<MulticallClient.Call> calls = new ArrayList<>(erc20.size());
            for (String token : erc20) {
                Function balanceOf = new Function(Erc20.FUNC_BALANCEOF, List.of(new Address(owner)), List.of(new org.web3j.abi.TypeReference<Uint256>() {}));
                calls.add(new MulticallClient.Call(token, true, FunctionEncoder.encode(balanceOf)));
            }
            List<byte[]> results = multicallClient.aggregate3(chain, calls);
            for (int i = 0; i < erc20.size(); i++) {
                BigInteger balance = decodeBalance(results.size() > i ? results.get(i) : null);
                TokenInfoService.TokenInfo info = tokenInfoService.resolve(chain.chainId(), erc20.get(i));
                holdings.add(holding(chain, info, balance, false));
            }
        }
        // 计算资产总值
        BigInteger net = null;
        for (AssetHoldingDTO holding : holdings) {
            if (holding.valueUsdMicro() == null) {
                continue;
            }
            net = net == null ? holding.valueUsdMicro() : net.add(holding.valueUsdMicro());
        }
        return new AssetPortfolioDTO(chain.chainId(), owner, holdings, net, System.currentTimeMillis());
    }

    private AssetHoldingDTO holding(ChainDefinition chain, TokenInfoService.TokenInfo info, BigInteger balance, boolean nativeToken) {
        Integer decimals = info.decimals();
        BigInteger price = null;
        BigInteger value = null;
        if (decimals != null) {
            String priceToken = nativeToken ? Addresses.lower(chain.contracts().weth()) : info.address();
            if (priceToken != null) {
                Optional<BigInteger> priced = priceOracle.priceUsdMicro(chain, priceToken, decimals);
                if (priced.isPresent()) {
                    price = priced.get();
                    // value = balance / 10^decimals * priceMicro
                    value = balance.multiply(price).divide(BigInteger.TEN.pow(decimals));
                }
            }
        }
        return new AssetHoldingDTO(info.address(), info.symbol(), decimals, balance, price, value);
    }

    private static List<String> resolveTokenList(ChainDefinition chain, List<String> extraTokens) {
        Set<String> tokens = new LinkedHashSet<>();
        tokens.add(chain.nativeToken().address());
        if (chain.quoteTokens() != null) {
            for (String quote : chain.quoteTokens()) {
                tokens.add(Addresses.lower(quote));
            }
        }
        if (chain.contracts().weth() != null) {
            tokens.add(Addresses.lower(chain.contracts().weth()));
        }
        if (chain.contracts().usdc() != null) {
            tokens.add(Addresses.lower(chain.contracts().usdc()));
        }
        if (extraTokens != null) {
            for (String token : extraTokens) {
                if (token != null && !token.isBlank()) {
                    tokens.add(Addresses.normalizeEvm(token));
                }
            }
        }
        return List.copyOf(tokens);
    }

    private static BigInteger decodeBalance(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return BigInteger.ZERO;
        }
        return Numeric.toBigInt(raw);
    }
}
