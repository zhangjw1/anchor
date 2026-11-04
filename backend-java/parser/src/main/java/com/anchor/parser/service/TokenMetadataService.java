package com.anchor.parser.service;

import com.anchor.common.chain.ChainDefinition;

import java.util.Collection;

/**
 * 补齐交换涉及的代币符号和精度。
 */
public interface TokenMetadataService {
    /**
     * 已有缓存或数据库记录时不再请求链上。
     */
    void ensure(ChainDefinition chain, String address);

    /**
     * 批量补齐；缺失元数据时尽量走 Multicall3 一次拉齐。
     */
    void ensureAll(ChainDefinition chain, Collection<String> addresses);
}
