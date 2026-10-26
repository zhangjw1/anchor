package com.anchor.common.rpc;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainReceipt;

import java.util.List;

public interface RpcEndpoint {

    String url();

    TokenBucket bucket();

    long latestBlockNumber() throws Exception;

    ChainBlock getBlock(long blockNumber) throws Exception;

    List<ChainReceipt> getReceipts(List<String> txHashes) throws Exception;

    String ethCall(String to, String data) throws Exception;
}
