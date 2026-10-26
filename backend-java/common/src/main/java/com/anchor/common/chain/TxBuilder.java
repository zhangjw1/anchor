package com.anchor.common.chain;

public interface TxBuilder {
    UnsignedTransaction buildSwap(String quoteId);

    record UnsignedTransaction(long chainId, String to, String data, String value) {
    }
}
