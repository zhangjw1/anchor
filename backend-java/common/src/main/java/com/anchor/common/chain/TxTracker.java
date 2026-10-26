package com.anchor.common.chain;

public interface TxTracker {
    TxStatus track(long chainId, String txHash);

    record TxStatus(String txHash, String state) {
    }
}
