package com.anchor.common.chain;
/**
 * 跟踪用户发出的交易状态。当前只定义边界。
 */
public interface TxTracker {
    /**
     * 查询一笔交易当前所处状态。
     */
    TxStatus track(long chainId, String txHash);
    /**
     * 交易哈希和状态文本。
     */
    record TxStatus(String txHash, String state) {
    }
}
