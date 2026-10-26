package com.anchor.common.chain;
/**
 * 根据报价组装未签名交易。当前只定义边界。
 */
public interface TxBuilder {
    /**
     * 用报价单号生成 to、data 和 value。
     */
    UnsignedTransaction buildSwap(String quoteId);
    /**
     * 交给钱包或签名器的未签名交易。
     */
    record UnsignedTransaction(long chainId, String to, String data, String value) {
    }
}
