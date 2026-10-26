package com.anchor.common.chain;

import com.anchor.common.domain.AnchorException;

import java.util.List;

public interface RpcGateway {

    long latestBlockNumber(long chainId);

    ChainBlock getBlock(long chainId, long blockNumber);

    List<ChainReceipt> getReceipts(long chainId, List<String> txHashes);

    String ethCall(long chainId, String to, String data);

    class RpcException extends AnchorException {
        public RpcException(String message, Throwable cause) {
            super("RPC_ERROR", message);
            initCause(cause);
        }
    }
}
