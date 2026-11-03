package com.anchor.common.manager;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.Quoter;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.evm.generated.GasPriceOracle;
import com.anchor.common.support.Addresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint24;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对 Uniswap V3 QuoterV2 四个费率档并发 eth_call，选 amountOut 最大者。
 */
public class UniswapV3Quoter implements Quoter {

    private static final Logger log = LoggerFactory.getLogger(UniswapV3Quoter.class);
    private static final MathContext MATH = new MathContext(64, RoundingMode.HALF_UP);
    private static final String ZERO = "0x0000000000000000000000000000000000000000";

    private final ChainRegistry chainRegistry;
    private final RpcGateway rpcGateway;
    private final PoolPriceOracle priceOracle;

    public UniswapV3Quoter(ChainRegistry chainRegistry, RpcGateway rpcGateway, PoolPriceOracle priceOracle) {
        this.chainRegistry = chainRegistry;
        this.rpcGateway = rpcGateway;
        this.priceOracle = priceOracle;
    }

    @Override
    public Quote quote(QuoteRequest request) {
        if (request.amountIn() == null || request.amountIn().signum() <= 0) {
            throw new AnchorException("VALIDATION_ERROR", "amountIn 必须大于 0");
        }
        if (request.slippageBps() < 0 || request.slippageBps() > AnchorConstants.MAX_SLIPPAGE_BPS) {
            throw new AnchorException("VALIDATION_ERROR", "滑点超出允许范围");
        }
        ChainDefinition chain = chainRegistry.requireById(request.chainId());
        String quoter = chain.contracts().quoterV2();
        if (quoter == null || quoter.isBlank()) {
            throw new AnchorException("UNSUPPORTED", "当前链未配置 QuoterV2");
        }
        String tokenIn = toPoolToken(chain, request.tokenIn());
        String tokenOut = toPoolToken(chain, request.tokenOut());
        if (tokenIn.equals(tokenOut)) {
            throw new AnchorException("VALIDATION_ERROR", "tokenIn 与 tokenOut 不能相同");
        }
        List<FeeQuote> quotes = quoteAllFees(chain, Addresses.lower(quoter), tokenIn, tokenOut, request.amountIn());
        FeeQuote best = quotes.stream().max(Comparator.comparing(FeeQuote::amountOut)).orElse(null);
        if (best == null) {
            throw new AnchorException("NO_LIQUIDITY", "没有可用的流动性报价");
        }
        BigInteger amountOutMin = best.amountOut()
                .multiply(BigInteger.valueOf(10_000L - request.slippageBps()))
                .divide(BigInteger.valueOf(10_000L));
        int impact = priceImpactBps(chain, tokenIn, tokenOut, request.amountIn(), best.amountOut());
        BigInteger l1Fee = estimateL1Fee(chain, tokenIn, tokenOut, request.amountIn(), best.fee(), amountOutMin);
        return new Quote(
                UUID.randomUUID().toString().replace("-", ""),
                best.amountOut(),
                amountOutMin,
                best.fee(),
                impact,
                best.gasEstimate(),
                l1Fee,
                System.currentTimeMillis() + AnchorConstants.QUOTE_TTL_MS);
    }

    private List<FeeQuote> quoteAllFees(ChainDefinition chain, String quoter, String tokenIn, String tokenOut, BigInteger amountIn) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Optional<FeeQuote>>> futures = new ArrayList<>();
            for (int fee : AnchorConstants.UNISWAP_V3_FEE_TIERS) {
                int feeTier = fee;
                futures.add(CompletableFuture.supplyAsync(() -> quoteFee(chain, quoter, tokenIn, tokenOut, amountIn, feeTier), executor));
            }
            List<FeeQuote> result = new ArrayList<>();
            for (CompletableFuture<Optional<FeeQuote>> future : futures) {
                future.join().ifPresent(result::add);
            }
            return result;
        }
    }

    private Optional<FeeQuote> quoteFee(ChainDefinition chain, String quoter, String tokenIn, String tokenOut,
                                        BigInteger amountIn, int fee) {
        try {
            StaticStruct params = new StaticStruct(
                    new Address(tokenIn),
                    new Address(tokenOut),
                    new Uint256(amountIn),
                    new Uint24(fee),
                    new Uint160(BigInteger.ZERO));
            Function function = new Function("quoteExactInputSingle",
                    List.of(params),
                    Arrays.<TypeReference<?>>asList(
                            new TypeReference<Uint256>() {},
                            new TypeReference<Uint160>() {},
                            new TypeReference<Uint32>() {},
                            new TypeReference<Uint256>() {}));
            String raw = rpcGateway.ethCall(chain.chainId(), quoter, FunctionEncoder.encode(function));
            @SuppressWarnings("rawtypes")
            List<Type> decoded = FunctionReturnDecoder.decode(raw, function.getOutputParameters());
            if (decoded.size() < 4) {
                return Optional.empty();
            }
            BigInteger amountOut = ((Uint256) decoded.get(0)).getValue();
            BigInteger gasEstimate = ((Uint256) decoded.get(3)).getValue();
            if (amountOut.signum() <= 0) {
                return Optional.empty();
            }
            return Optional.of(new FeeQuote(fee, amountOut, gasEstimate));
        } catch (RuntimeException ex) {
            log.debug("费率档报价失败，chainId={}，fee={}，原因={}", chain.chainId(), fee, ex.toString());
            return Optional.empty();
        }
    }

    private int priceImpactBps(ChainDefinition chain, String tokenIn, String tokenOut, BigInteger amountIn, BigInteger amountOut) {
        // 缺少精度时无法算人类价格，按 0 处理；精度由调用方保证时更准，这里用 18/18 近似并依赖 oracle 的池价
        Optional<BigDecimal> mid = priceOracle.midPriceHuman(chain, tokenIn, 18, tokenOut, 18);
        if (mid.isEmpty() || mid.get().signum() <= 0 || amountIn.signum() <= 0) {
            return 0;
        }
        BigDecimal exec = new BigDecimal(amountOut).divide(new BigDecimal(amountIn), MATH);
        BigDecimal midPrice = mid.get();
        BigDecimal impact = exec.subtract(midPrice, MATH).abs().divide(midPrice, MATH);
        return impact.multiply(BigDecimal.valueOf(10_000L), MATH).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private BigInteger estimateL1Fee(ChainDefinition chain, String tokenIn, String tokenOut, BigInteger amountIn,
                                     int fee, BigInteger amountOutMin) {
        String oracle = chain.contracts().gasPriceOracle();
        if (oracle == null || oracle.isBlank()) {
            return BigInteger.ZERO;
        }
        // 用近似的 exactInputSingle calldata 估算 L1 data fee
        byte[] fakeCalldata = Numeric.hexStringToByteArray(buildApproxSwapCalldata(tokenIn, tokenOut, fee, amountIn, amountOutMin));
        try {
            Function function = new Function(GasPriceOracle.FUNC_GETL1FEE,
                    List.of(new DynamicBytes(fakeCalldata)),
                    Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
            String raw = rpcGateway.ethCall(chain.chainId(), Addresses.lower(oracle), FunctionEncoder.encode(function));
            @SuppressWarnings("rawtypes")
            List<Type> decoded = FunctionReturnDecoder.decode(raw, function.getOutputParameters());
            if (decoded.isEmpty()) {
                return BigInteger.ZERO;
            }
            return ((Uint256) decoded.get(0)).getValue();
        } catch (RuntimeException ex) {
            log.warn("估算 L1 data fee 失败，chainId={}", chain.chainId());
            return BigInteger.ZERO;
        }
    }

    /** SwapRouter02 exactInputSingle 选择器 + 参数占位，仅用于 L1 fee 估算长度与内容。 */
    private static String buildApproxSwapCalldata(String tokenIn, String tokenOut, int fee, BigInteger amountIn, BigInteger amountOutMin) {
        // selector exactInputSingle(ExactInputSingleParams) = 0x04e45aaf
        StringBuilder data = new StringBuilder("0x04e45aaf");
        data.append(padAddress(tokenIn));
        data.append(padAddress(tokenOut));
        data.append(padUint(BigInteger.valueOf(fee)));
        data.append(padAddress(ZERO));
        data.append(padUint(amountIn));
        data.append(padUint(amountOutMin));
        data.append(padUint(BigInteger.ZERO));
        return data.toString();
    }

    private static String padAddress(String address) {
        String hex = Numeric.cleanHexPrefix(Addresses.lower(address));
        return "000000000000000000000000" + hex;
    }

    private static String padUint(BigInteger value) {
        String hex = Numeric.toHexStringNoPrefix(value);
        return "0".repeat(Math.max(0, 64 - hex.length())) + hex;
    }

    private static String toPoolToken(ChainDefinition chain, String token) {
        String normalized = Addresses.normalizeEvm(token);
        if (normalized.equals(chain.nativeToken().address()) || normalized.equals(AnchorConstants.NATIVE_TOKEN_ADDRESS)) {
            String weth = chain.contracts().weth();
            if (weth == null) {
                throw new AnchorException("UNSUPPORTED", "当前链未配置 WETH，无法报价原生币");
            }
            return Addresses.lower(weth);
        }
        return normalized;
    }

    private record FeeQuote(int fee, BigInteger amountOut, BigInteger gasEstimate) {
    }
}
