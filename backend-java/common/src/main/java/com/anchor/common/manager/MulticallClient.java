package com.anchor.common.manager;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.domain.AnchorException;
import com.anchor.common.support.Addresses;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Multicall3 批量 eth_call 与原生币余额查询。
 */
public class MulticallClient {

    private static final String ZERO = "0x0000000000000000000000000000000000000000";

    private final RpcGateway rpcGateway;

    public MulticallClient(RpcGateway rpcGateway) {
        this.rpcGateway = rpcGateway;
    }

    /** 查询地址的原生币余额。 */
    public BigInteger ethBalance(ChainDefinition chain, String owner) {
        String multicall = requireMulticall(chain);
        Function function = new Function("getEthBalance", List.of(new Address(Addresses.lower(owner))),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        String raw = rpcGateway.ethCall(chain.chainId(), multicall, FunctionEncoder.encode(function));
        return decodeUint256(raw);
    }

    /**
     * 批量执行合约只读调用；allowFailure=true，单笔失败返回 empty 字节。
     *
     * @return 与 calls 同序的返回 data（失败时为 null）
     */
    public List<byte[]> aggregate3(ChainDefinition chain, List<Call> calls) {
        if (calls.isEmpty()) {
            return List.of();
        }
        String multicall = requireMulticall(chain);
        List<Call3> encoded = new ArrayList<>(calls.size());
        for (Call call : calls) {
            encoded.add(new Call3(call.target(), call.allowFailure(), Numeric.hexStringToByteArray(call.data())));
        }
        Function function = new Function("aggregate3",
                List.of(new DynamicArray<>(Call3.class, encoded)),
                List.of(new TypeReference<DynamicArray<Result>>() {}));
        String raw = rpcGateway.ethCall(chain.chainId(), multicall, FunctionEncoder.encode(function));
        @SuppressWarnings("rawtypes")
        List<Type> decoded = FunctionReturnDecoder.decode(raw, function.getOutputParameters());
        if (decoded.isEmpty()) {
            throw new AnchorException("RPC_ERROR", "Multicall3 返回为空");
        }
        @SuppressWarnings("unchecked")
        DynamicArray<Result> results = (DynamicArray<Result>) decoded.get(0);
        List<byte[]> out = new ArrayList<>(results.getValue().size());
        for (Result result : results.getValue()) {
            if (!result.success()) {
                out.add(null);
                continue;
            }
            out.add(result.returnData());
        }
        return out;
    }

    private static String requireMulticall(ChainDefinition chain) {
        String address = chain.contracts().multicall3();
        if (address == null || address.isBlank() || ZERO.equalsIgnoreCase(address)) {
            throw new AnchorException("UNSUPPORTED", "当前链未配置 Multicall3");
        }
        return Addresses.lower(address);
    }

    private static BigInteger decodeUint256(String raw) {
        if (raw == null || "0x".equals(raw) || raw.length() < 3) {
            return BigInteger.ZERO;
        }
        @SuppressWarnings("rawtypes")
        Function fn = new Function("x", List.of(), Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        List<Type> decoded = FunctionReturnDecoder.decode(raw, fn.getOutputParameters());
        if (decoded.isEmpty()) {
            return BigInteger.ZERO;
        }
        return ((Uint256) decoded.get(0)).getValue();
    }

    public record Call(String target, boolean allowFailure, String data) {
    }

    public static final class Call3 extends DynamicStruct {
        public Call3(String target, boolean allowFailure, byte[] callData) {
            super(new Address(target), new Bool(allowFailure), new DynamicBytes(callData));
        }

        @SuppressWarnings("rawtypes")
        public Call3(List<Type> values) {
            super(values.toArray(Type[]::new));
        }
    }

    public static final class Result extends DynamicStruct {
        public Result(Bool success, DynamicBytes returnData) {
            super(success, returnData);
        }

        @SuppressWarnings("rawtypes")
        public Result(List<Type> values) {
            super(values.toArray(Type[]::new));
        }

        public boolean success() {
            return ((Bool) getValue().get(0)).getValue();
        }

        public byte[] returnData() {
            return ((DynamicBytes) getValue().get(1)).getValue();
        }
    }
}
