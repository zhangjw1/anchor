package com.anchor.parser.swap.hop;

import com.anchor.common.chain.ChainLog;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.support.AbiOutputs;
import com.anchor.common.support.Addresses;
import com.anchor.parser.dto.PoolTokensDTO;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Int256;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * 解码 Uniswap V3 / Slipstream 风格 Swap 事件的 token 流向。
 */
final class ClPoolHopDecoder {

    /** data 前两个字是 amount0、amount1，其余字段不需要。 */
    private static final int AMOUNT_WORDS = 2;

    private ClPoolHopDecoder() {
    }

    /**
     * pool 侧 amount0/amount1：正表示池子收到（用户付出），负表示池子付出（用户收到）。
     */
    static Optional<DetectedHop> decode(String dex, ChainLog logItem, PoolTokensDTO tokens) {
        if (logItem.data() == null) {
            return Optional.empty();
        }
        String hex = Numeric.cleanHexPrefix(logItem.data());
        int amountsLength = AMOUNT_WORDS * AnchorConstants.ABI_WORD_HEX_CHARS;
        if (hex.length() < amountsLength) {
            return Optional.empty();
        }
        @SuppressWarnings("rawtypes")
        List<Type> decoded = FunctionReturnDecoder.decode(
                Numeric.prependHexPrefix(hex.substring(0, amountsLength)),
                AbiOutputs.of(new TypeReference<Int256>() {}, new TypeReference<Int256>() {}));
        if (decoded.size() < AMOUNT_WORDS) {
            return Optional.empty();
        }
        BigInteger amount0 = ((Int256) decoded.get(0)).getValue();
        BigInteger amount1 = ((Int256) decoded.get(1)).getValue();
        String pool = Addresses.lower(logItem.address());
        if (amount0.signum() > 0 && amount1.signum() < 0) {
            return Optional.of(new DetectedHop(dex, pool, tokens.token0(), tokens.token1(), amount0, amount1.abs(), logItem.logIndex()));
        }
        if (amount1.signum() > 0 && amount0.signum() < 0) {
            return Optional.of(new DetectedHop(dex, pool, tokens.token1(), tokens.token0(), amount1, amount0.abs(), logItem.logIndex()));
        }
        return Optional.empty();
    }
}
