package com.anchor.common.manager;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainReceipt;

import java.util.List;

/**
 * 单个 RPC 节点。调用前要先取得它自己的限流令牌。
 */
public interface RpcEndpoint {
    /**
     * 节点地址，只用于日志和指标。
     */
    String url();
    /**
     * 该节点的请求速率限制。
     */

    TokenBucket bucket();
    /**
     * 读取链头。异常交给网关决定是否重试。
     */
    long latestBlockNumber() throws Exception;
    /**
     * 读取最终确认高度。
     */
    long finalizedBlockNumber() throws Exception;
    /**
     * 读取区块全文。
     */

    ChainBlock getBlock(long blockNumber) throws Exception;
    /**
     * 读取这些交易的回执。
     */

    List<ChainReceipt> getReceipts(List<String> txHashes) throws Exception;
    /**
     * 对这个节点做一次 eth_call。
     */
    String ethCall(String to, String data) throws Exception;
}
