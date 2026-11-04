package com.anchor.common.manager;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.StoredQuote;
import com.anchor.common.chain.TxBuilder;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.support.Addresses;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint24;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 手写编码 SwapRouter02 approve / exactInputSingle + multicall(deadline)。
 */
public class SwapRouterTxBuilder implements TxBuilder {

    private final ChainRegistry chainRegistry;

    public SwapRouterTxBuilder(ChainRegistry chainRegistry) {
        this.chainRegistry = chainRegistry;
    }

    @Override
    public UnsignedTransaction buildApprove(long chainId, String token, String spender, BigInteger amount) {
        Function function = new Function(
                "approve",
                Arrays.asList(new Address(Addresses.lower(spender)), new Uint256(amount)),
                Collections.emptyList());
        return new UnsignedTransaction(chainId, Addresses.lower(token), FunctionEncoder.encode(function), "0");
    }

    @Override
    public UnsignedTransaction buildSwap(StoredQuote quote, String recipient, long deadlineEpochSec) {
        ChainDefinition chain = chainRegistry.requireById(quote.chainId());
        String router = chain.contracts().swapRouter02();
        if (router == null || router.isBlank()) {
            throw new AnchorException("UNSUPPORTED", "当前链未配置 SwapRouter02");
        }
        byte[] inner = Numeric.hexStringToByteArray(encodeExactInputSingle(
                quote.tokenIn(), quote.tokenOut(), quote.fee(), Addresses.lower(recipient),
                quote.amountIn(), quote.amountOutMinimum()));
        Function multicall = new Function(
                "multicall",
                Arrays.asList(new Uint256(BigInteger.valueOf(deadlineEpochSec)),
                        new DynamicArray<>(DynamicBytes.class, List.of(new DynamicBytes(inner)))),
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<DynamicBytes>>() {}));
        String value = quote.nativeIn() ? quote.amountIn().toString() : "0";
        return new UnsignedTransaction(quote.chainId(), Addresses.lower(router), FunctionEncoder.encode(multicall), value);
    }

    /** exactInputSingle((address,address,uint24,address,uint256,uint256,uint160)) */
    static String encodeExactInputSingle(String tokenIn, String tokenOut, int fee, String recipient,
                                         BigInteger amountIn, BigInteger amountOutMinimum) {
        ExactInputSingleParams params = new ExactInputSingleParams(
                new Address(Addresses.lower(tokenIn)),
                new Address(Addresses.lower(tokenOut)),
                new Uint24(fee),
                new Address(Addresses.lower(recipient)),
                new Uint256(amountIn),
                new Uint256(amountOutMinimum),
                new Uint160(BigInteger.ZERO));
        Function function = new Function(
                "exactInputSingle",
                List.of(params),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return FunctionEncoder.encode(function);
    }

    public static final class ExactInputSingleParams extends StaticStruct {
        public ExactInputSingleParams(Address tokenIn, Address tokenOut, Uint24 fee, Address recipient,
                                      Uint256 amountIn, Uint256 amountOutMinimum, Uint160 sqrtPriceLimitX96) {
            super(tokenIn, tokenOut, fee, recipient, amountIn, amountOutMinimum, sqrtPriceLimitX96);
        }
    }
}
