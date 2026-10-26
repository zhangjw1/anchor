package com.anchor.parser.swap;

import java.util.Optional;

/**
 * 查询池子是由哪个工厂创建的，用来确认它属于 Uniswap V3。
 */
public interface PoolFactoryLookup {
    /**
     * 查不到或调用失败时返回空，调用方应跳过该池。
     */
    Optional<String> findFactory(long chainId, String pool);
}
