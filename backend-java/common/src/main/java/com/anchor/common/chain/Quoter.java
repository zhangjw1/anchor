package com.anchor.common.chain;

import java.math.BigInteger;

/**
 * Uniswap V3 单跳报价。实现侧对多个费率档并发 eth_call 后择优。
 */
public interface Quoter {

    /**
     * 按输入数量估算输出；无可用池或全部档位失败时抛业务异常。
     */
    Quote quote(QuoteRequest request);

    /**
     * @param chainId      链 ID
     * @param tokenIn      付出代币（原生币可用 0xeeee…）
     * @param tokenOut     收到代币（原生币可用 0xeeee…）
     * @param amountIn     付出数量，最小单位
     * @param slippageBps  滑点基点，100 = 1%
     */
    record QuoteRequest(long chainId, String tokenIn, String tokenOut, BigInteger amountIn, int slippageBps) {
    }

    /**
     * @param quoteId            报价单号
     * @param amountOut          预估收到量
     * @param amountOutMinimum   按滑点折减后的最少收到量
     * @param fee                选中的费率档（如 500）
     * @param priceImpactBps     相对中间价的价格影响（基点）
     * @param gasEstimate        QuoterV2 返回的执行 gas
     * @param l1DataFeeWei       OP Stack L1 data fee（wei）
     * @param expiresAtEpochMs   过期时间（毫秒）
     */
    record Quote(
            String quoteId,
            BigInteger amountOut,
            BigInteger amountOutMinimum,
            int fee,
            int priceImpactBps,
            BigInteger gasEstimate,
            BigInteger l1DataFeeWei,
            long expiresAtEpochMs) {
    }
}
