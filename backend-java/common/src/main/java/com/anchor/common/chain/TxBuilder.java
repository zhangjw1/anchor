package com.anchor.common.chain;

import java.math.BigInteger;

/**
 * 根据报价组装未签名交易。
 */
public interface TxBuilder {

    /**
     * ERC-20 approve(spender, amount)。
     */
    UnsignedTransaction buildApprove(long chainId, String token, String spender, BigInteger amount);

    /**
     * SwapRouter02：multicall(deadline, [exactInputSingle(...)])。
     */
    UnsignedTransaction buildSwap(StoredQuote quote, String recipient);

    /**
     * 交给钱包或签名器的未签名交易。
     *
     * @param value wei，十进制字符串
     */
    record UnsignedTransaction(long chainId, String to, String data, String value) {
    }
}
