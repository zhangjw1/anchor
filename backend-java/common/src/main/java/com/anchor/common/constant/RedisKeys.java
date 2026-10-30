package com.anchor.common.constant;
/**
 * Redis 键名。链上数据按链 ID 隔离。
 */
public final class RedisKeys {

    private RedisKeys() {
    }
    /**
     * 某条链正在监控的地址集合。
     */
    public static String watchSet(long chainId) {
        return "anchor:watch:" + chainId;
    }
    /**
     * 代币符号和精度缓存。
     */
    public static String token(long chainId, String address) {
        return "anchor:token:" + chainId + ":" + address;
    }
    /**
     * 池子对应工厂地址的缓存。
     */
    public static String poolFactory(long chainId, String pool) {
        return "anchor:pool-factory:" + chainId + ":" + pool;
    }
    /**
     * 池子 token0/token1 缓存，值为 token0,token1。
     */
    public static String poolTokens(long chainId, String pool) {
        return "anchor:pool-tokens:" + chainId + ":" + pool;
    }
    /**
     * 某条链已处理的重组次数。
     */
    public static String reorgCount(long chainId) {
        return "anchor:reorg:count:" + chainId;
    }
    /**
     * 某条链最近一次重组深度。
     */
    public static String reorgDepth(long chainId) {
        return "anchor:reorg:depth:" + chainId;
    }
}
