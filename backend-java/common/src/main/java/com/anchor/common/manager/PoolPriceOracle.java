package com.anchor.common.manager;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.evm.generated.UniswapV3Factory;
import com.anchor.common.evm.generated.UniswapV3Pool;
import com.anchor.common.support.Addresses;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint128;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint24;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 用 Uniswap V3 池子中间价推导代币 USD 价格（微美元，6 位小数）。
 * 流动性低于阈值时返回 empty。
 */
public class PoolPriceOracle {

    private static final MathContext MATH = new MathContext(64, RoundingMode.HALF_UP);
    private static final BigDecimal Q96 = BigDecimal.valueOf(2).pow(96, MATH);
    private static final String ZERO_ADDRESS = "0x0000000000000000000000000000000000000000";

    private final RpcGateway rpcGateway;

    public PoolPriceOracle(RpcGateway rpcGateway) {
        this.rpcGateway = rpcGateway;
    }

    /**
     * @param token            代币地址（原生币应先换成 WETH）
     * @param tokenDecimals    代币精度
     * @return 1 个整币对应的微美元；无法计价时 empty
     */
    public Optional<BigInteger> priceUsdMicro(ChainDefinition chain, String token, int tokenDecimals) {
        String normalized = Addresses.lower(token);
        String usdc = Addresses.lower(chain.contracts().usdc());
        String weth = Addresses.lower(chain.contracts().weth());
        if (usdc != null && normalized.equals(usdc)) {
            return Optional.of(BigInteger.TEN.pow(AnchorConstants.USD_MICRO_DECIMALS));
        }
        if (usdc != null) {
            Optional<BigInteger> direct = priceViaPair(chain, normalized, tokenDecimals, usdc, 6, true);
            if (direct.isPresent()) {
                return direct;
            }
        }
        if (weth == null || usdc == null) {
            return Optional.empty();
        }
        if (normalized.equals(weth)) {
            return priceViaPair(chain, weth, 18, usdc, 6, true);
        }
        Optional<BigDecimal> tokenPerWeth = midPriceHuman(chain, normalized, tokenDecimals, weth, 18);
        Optional<BigDecimal> wethUsd = midPriceHuman(chain, weth, 18, usdc, 6);
        if (tokenPerWeth.isEmpty() || wethUsd.isEmpty()) {
            return Optional.empty();
        }
        // token 以 WETH 计价时 mid 是 token1/token0；统一成「1 token = ? USD」
        BigDecimal usd = tokenPerWeth.get().multiply(wethUsd.get(), MATH);
        return Optional.of(toMicro(usd));
    }

    /** 读取用于价格影响的池子中间价（tokenOut 人类单位 / tokenIn 人类单位）。 */
    public Optional<BigDecimal> midPriceHuman(ChainDefinition chain, String tokenIn, int inDecimals,
                                              String tokenOut, int outDecimals) {
        Optional<PoolState> state = bestPool(chain, Addresses.lower(tokenIn), Addresses.lower(tokenOut));
        if (state.isEmpty()) {
            return Optional.empty();
        }
        PoolState pool = state.get();
        BigDecimal raw = sqrtPriceToToken1PerToken0(pool.sqrtPriceX96());
        if (raw.signum() <= 0) {
            return Optional.empty();
        }
        boolean inIsToken0 = pool.token0().equalsIgnoreCase(tokenIn);
        BigDecimal adjusted;
        if (inIsToken0) {
            adjusted = raw.multiply(BigDecimal.TEN.pow(inDecimals, MATH), MATH)
                    .divide(BigDecimal.TEN.pow(outDecimals, MATH), MATH);
        } else {
            adjusted = BigDecimal.ONE.divide(raw, MATH)
                    .multiply(BigDecimal.TEN.pow(inDecimals, MATH), MATH)
                    .divide(BigDecimal.TEN.pow(outDecimals, MATH), MATH);
        }
        return Optional.of(adjusted);
    }

    private Optional<BigInteger> priceViaPair(ChainDefinition chain, String token, int tokenDecimals,
                                              String quote, int quoteDecimals, boolean quoteIsUsd) {
        Optional<BigDecimal> human = midPriceHuman(chain, token, tokenDecimals, quote, quoteDecimals);
        if (human.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal usd = human.get();
        if (!quoteIsUsd) {
            return Optional.empty();
        }
        return Optional.of(toMicro(usd));
    }

    private Optional<PoolState> bestPool(ChainDefinition chain, String tokenA, String tokenB) {
        String factory = chain.contracts().uniswapV3Factory();
        if (factory == null) {
            return Optional.empty();
        }
        PoolState best = null;
        for (int fee : AnchorConstants.UNISWAP_V3_FEE_TIERS) {
            Optional<PoolState> candidate = readPool(chain, factory, tokenA, tokenB, fee);
            if (candidate.isEmpty()) {
                continue;
            }
            if (best == null || candidate.get().liquidity().compareTo(best.liquidity()) > 0) {
                best = candidate.get();
            }
        }
        if (best == null || best.liquidity().compareTo(BigInteger.valueOf(AnchorConstants.MIN_POOL_LIQUIDITY)) < 0) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private Optional<PoolState> readPool(ChainDefinition chain, String factory, String tokenA, String tokenB, int fee) {
        try {
            Function getPool = new Function(UniswapV3Factory.FUNC_GETPOOL,
                    Arrays.asList(new Address(tokenA), new Address(tokenB), new Uint24(fee)),
                    Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
            String poolRaw = rpcGateway.ethCall(chain.chainId(), factory, FunctionEncoder.encode(getPool));
            @SuppressWarnings("rawtypes")
            List<Type> poolDecoded = FunctionReturnDecoder.decode(poolRaw, getPool.getOutputParameters());
            if (poolDecoded.isEmpty()) {
                return Optional.empty();
            }
            String pool = Addresses.lower(((Address) poolDecoded.get(0)).getValue());
            if (pool == null || ZERO_ADDRESS.equals(pool)) {
                return Optional.empty();
            }
            Function token0Fn = new Function(UniswapV3Pool.FUNC_TOKEN0, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
            Function token1Fn = new Function(UniswapV3Pool.FUNC_TOKEN1, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
            Function liqFn = new Function(UniswapV3Pool.FUNC_LIQUIDITY, List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Uint128>() {}));
            Function slot0Fn = new Function(UniswapV3Pool.FUNC_SLOT0, List.of(), Arrays.<TypeReference<?>>asList(
                    new TypeReference<Uint160>() {},
                    new TypeReference<org.web3j.abi.datatypes.generated.Int24>() {},
                    new TypeReference<org.web3j.abi.datatypes.generated.Uint16>() {},
                    new TypeReference<org.web3j.abi.datatypes.generated.Uint16>() {},
                    new TypeReference<org.web3j.abi.datatypes.generated.Uint16>() {},
                    new TypeReference<org.web3j.abi.datatypes.generated.Uint8>() {},
                    new TypeReference<org.web3j.abi.datatypes.Bool>() {}));
            String t0 = decodeAddress(rpcGateway.ethCall(chain.chainId(), pool, FunctionEncoder.encode(token0Fn)));
            String t1 = decodeAddress(rpcGateway.ethCall(chain.chainId(), pool, FunctionEncoder.encode(token1Fn)));
            BigInteger liquidity = decodeUint(rpcGateway.ethCall(chain.chainId(), pool, FunctionEncoder.encode(liqFn)));
            String slotRaw = rpcGateway.ethCall(chain.chainId(), pool, FunctionEncoder.encode(slot0Fn));
            @SuppressWarnings("rawtypes")
            List<Type> slot = FunctionReturnDecoder.decode(slotRaw, slot0Fn.getOutputParameters());
            if (slot.isEmpty() || t0 == null || t1 == null) {
                return Optional.empty();
            }
            BigInteger sqrtPriceX96 = ((Uint160) slot.get(0)).getValue();
            return Optional.of(new PoolState(pool, t0, t1, sqrtPriceX96, liquidity, fee));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    static BigDecimal sqrtPriceToToken1PerToken0(BigInteger sqrtPriceX96) {
        BigDecimal sqrt = new BigDecimal(sqrtPriceX96, MATH);
        return sqrt.divide(Q96, MATH).pow(2, MATH);
    }

    private static BigInteger toMicro(BigDecimal usdHuman) {
        return usdHuman.multiply(BigDecimal.TEN.pow(AnchorConstants.USD_MICRO_DECIMALS), MATH)
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger();
    }

    private static String decodeAddress(String raw) {
        if (raw == null || raw.length() < 3) {
            return null;
        }
        @SuppressWarnings("rawtypes")
        Function fn = new Function("x", List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        List<Type> decoded = FunctionReturnDecoder.decode(raw, fn.getOutputParameters());
        if (decoded.isEmpty()) {
            return null;
        }
        return Addresses.lower(((Address) decoded.get(0)).getValue());
    }

    private static BigInteger decodeUint(String raw) {
        if (raw == null || raw.length() < 3) {
            return BigInteger.ZERO;
        }
        return Numeric.toBigInt(raw);
    }

    record PoolState(String pool, String token0, String token1, BigInteger sqrtPriceX96, BigInteger liquidity, int fee) {
    }
}
