package com.anchor.common.chain;

import java.math.BigInteger;

public record ChainTransaction(
        String hash,
        String from,
        String to,
        BigInteger value,
        String input) {
}
