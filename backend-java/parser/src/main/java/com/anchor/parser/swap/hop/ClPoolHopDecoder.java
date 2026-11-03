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
        // logItem.data()是还没解码的0x开头的十六进制日志数据，需要先去0x，然后解码前两个参数amount0和amount1。
        String hex = Numeric.cleanHexPrefix(logItem.data());
        // amount0/amount1 各占一个 ABI word（32 字节 = 64 个 hex 字符），共需 128 个字符
        int amountsLength = AMOUNT_WORDS * AnchorConstants.ABI_WORD_HEX_CHARS;
        // hex 长度不足两个字，说明 data 异常，无法安全截取，直接跳过
        if (hex.length() < amountsLength) {
            return Optional.empty();
        }
        // 截取前两个字（amount0 + amount1）对应的裸 hex（无 0x 前缀）
        String amountsHex = hex.substring(0, amountsLength);
        // 加回 0x 前缀，恢复为 ABI 解码器要求的格式
        String prefixedHex = Numeric.prependHexPrefix(amountsHex);
        // 声明期望的输出类型：两个 int256（有符号 256 位）
        List<TypeReference<Type>> outputTypes = AbiOutputs.of(new TypeReference<Int256>() {}, new TypeReference<Int256>() {});
        // 按 ABI 规则将 hex 解码为 Type 列表
        @SuppressWarnings("rawtypes")
        List<Type> decoded = FunctionReturnDecoder.decode(prefixedHex, outputTypes);
        // 解码结果不足两个字，视为无效数据
        if (decoded.size() < AMOUNT_WORDS) {
            return Optional.empty();
        }
        // 取出两个有符号金额（正=池子收到/用户付出，负=池子付出/用户收到）
        BigInteger amount0 = ((Int256) decoded.get(0)).getValue();
        BigInteger amount1 = ((Int256) decoded.get(1)).getValue();
        // 事件所在合约地址即 pool 地址，统一转小写便于后续比对
        String pool = Addresses.lower(logItem.address());
        // amount0 为正、amount1 为负：用户付出 token0、收到 token1，即 token0 -> token1
        if (amount0.signum() > 0 && amount1.signum() < 0) {
            return Optional.of(new DetectedHop(dex, pool, tokens.token0(), tokens.token1(), amount0, amount1.abs(), logItem.logIndex()));
        }
        // amount1 为正、amount0 为负：用户付出 token1、收到 token0，即 token1 -> token0
        if (amount1.signum() > 0 && amount0.signum() < 0) {
            return Optional.of(new DetectedHop(dex, pool, tokens.token1(), tokens.token0(), amount1, amount0.abs(), logItem.logIndex()));
        }
        // 同号（同收或同付）不符合 Swap 语义，忽略
        return Optional.empty();
    }
}
