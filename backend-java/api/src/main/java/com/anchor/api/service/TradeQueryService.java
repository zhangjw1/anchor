package com.anchor.api.service;

import com.anchor.common.chain.TxBuilder;

import java.math.BigInteger;

/** 模式 A 交易：准备待签交易、上报与查询状态。 */
public interface TradeQueryService {

    PrepareResult prepare(String quoteId, String userAddress, boolean infiniteApprove);

    TradeStatusView report(long chainId, String txHash, String kind, String quoteId, String userAddress);

    TradeStatusView status(long chainId, String txHash);

    record PrepareResult(
            String quoteId,
            long expiresAtMs,
            boolean needsApprove,
            TxBuilder.UnsignedTransaction approveTx,
            TxBuilder.UnsignedTransaction swapTx,
            BigInteger gasEstimate,
            String amountOutMinimum,
            long deadline) {
    }

    record TradeStatusView(
            long chainId,
            String txHash,
            String kind,
            String status,
            String quoteId,
            String userAddress,
            String revertReason,
            long updatedAtMs) {
    }
}
