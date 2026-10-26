package com.anchor.common.chain;

import java.math.BigInteger;

/**
 * 区块里的一笔交易。value 是原生币数量，单位 wei。
 */
public record ChainTransaction(
        String hash,
        String from,
        String to,
        BigInteger value,
        String input) {
}
