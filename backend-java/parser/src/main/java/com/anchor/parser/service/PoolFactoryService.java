package com.anchor.parser.service;

import java.util.Optional;

/**
 * 查询池子由哪个工厂创建，用来防止伪造池。
 */
public interface PoolFactoryService {
    /**
     * 查不到或调用失败时返回空，调用方应跳过该池。
     */
    Optional<String> findFactory(long chainId, String pool);
}
