package com.anchor.parser.service;

import com.anchor.common.chain.ChainDefinition;

/**
 * 补齐交换涉及的代币符号和精度。
 */
public interface TokenMetadataService {
    /**
     * 已有缓存或数据库记录时不再请求链上。
     */
    void ensure(ChainDefinition chain, String address);
}
