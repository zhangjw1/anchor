package com.anchor.common.chain;

import com.anchor.common.domain.AnchorException;

import java.util.List;

/**
 * 带故障切换的链上读取入口。
 */
public interface RpcGateway {
    /**
     * 当前链头高度。
     */
    long latestBlockNumber(long chainId);
    /**
     * 已最终确认的高度。取不到时由实现决定回退。
     */
    long finalizedBlockNumber(long chainId);
    /**
     * 读取指定高度的区块和交易。
     */

    ChainBlock getBlock(long chainId, long blockNumber);
    /**
     * 按交易哈希批量读取回执。
     */

    List<ChainReceipt> getReceipts(long chainId, List<String> txHashes);
    /**
     * 只读合约调用，返回十六进制结果。
     */
    String ethCall(long chainId, String to, String data);
    /**
     * 节点重试耗尽后抛出。
     */
    class RpcException extends AnchorException {
        public RpcException(String message, Throwable cause) {
            super("RPC_ERROR", message);
            initCause(cause);
        }
    }
}
