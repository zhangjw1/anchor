package com.anchor.common.chain;

import java.util.List;

/**
 * 已加载的链定义。
 */
public interface ChainRegistry {
    /**
     * 全部链，顺序与配置文件一致。
     */

    List<ChainDefinition> all();
    /**
     * 按配置里的 key 取链，不存在则抛未知链。
     */

    ChainDefinition requireByKey(String key);
    /**
     * 按链 ID 取链，不存在则抛未知链。
     */

    ChainDefinition requireById(long chainId);
}
