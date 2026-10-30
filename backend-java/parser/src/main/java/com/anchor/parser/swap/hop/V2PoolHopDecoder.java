package com.anchor.parser.swap.hop;

import com.anchor.common.chain.ChainLog;
import com.anchor.common.support.AbiOutputs;
import com.anchor.common.support.Addresses;
import com.anchor.parser.dto.PoolTokensDTO;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * 解码 Uniswap V2 / Aerodrome 经典池 Swap 事件的 token 流向。
 */
final class V2PoolHopDecoder {

    /** data 依次是 amount0In、amount1In、amount0Out、amount1Out。 */
    private static final int AMOUNT_WORDS = 4;

    private V2PoolHopDecoder() {
    }

    static Optional<DetectedHop> decode(String dex, ChainLog logItem, PoolTokensDTO tokens) {
        @SuppressWarnings("rawtypes")
        List<Type> decoded = FunctionReturnDecoder.decode(logItem.data(), AbiOutputs.of(
                new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {},
                new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
        if (decoded.size() < AMOUNT_WORDS) {
            return Optional.empty();
        }
        BigInteger amount0In = ((Uint256) decoded.get(0)).getValue();
        BigInteger amount1In = ((Uint256) decoded.get(1)).getValue();
        BigInteger amount0Out = ((Uint256) decoded.get(2)).getValue();
        BigInteger amount1Out = ((Uint256) decoded.get(3)).getValue();
        String pool = Addresses.lower(logItem.address());
        if (amount0In.signum() > 0 && amount1Out.signum() > 0 && amount1In.signum() == 0 && amount0Out.signum() == 0) {
            return Optional.of(new DetectedHop(dex, pool, tokens.token0(), tokens.token1(), amount0In, amount1Out, logItem.logIndex()));
        }
        if (amount1In.signum() > 0 && amount0Out.signum() > 0 && amount0In.signum() == 0 && amount1Out.signum() == 0) {
            return Optional.of(new DetectedHop(dex, pool, tokens.token1(), tokens.token0(), amount1In, amount0Out, logItem.logIndex()));
        }
        return Optional.empty();
    }
}
