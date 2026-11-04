package com.anchor.api.vo;

import com.anchor.common.chain.TxBuilder;

/** 未签名交易。 */
public record UnsignedTxVO(long chainId, String to, String data, String value, String gas) {

    public static UnsignedTxVO from(TxBuilder.UnsignedTransaction tx, String gas) {
        if (tx == null) {
            return null;
        }
        return new UnsignedTxVO(tx.chainId(), tx.to(), tx.data(), tx.value(), gas);
    }
}
