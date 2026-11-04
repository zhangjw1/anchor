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

    /**
     * 钱包资产组合短缓存。
     */
    public static String assetPortfolio(long chainId, String address, String tokenFingerprint) {
        return "anchor:asset:" + chainId + ":" + address + ":" + tokenFingerprint;
    }

    /**
     * 报价结果短缓存。
     */
    public static String quote(long chainId, String tokenIn, String tokenOut, String amountIn, int slippageBps) {
        return "anchor:quote:" + chainId + ":" + tokenIn + ":" + tokenOut + ":" + amountIn + ":" + slippageBps;
    }

    /**
     * 按报价单号取完整报价（供构建交易）。
     */
    public static String quoteById(String quoteId) {
        return "anchor:quote:id:" + quoteId;
    }

    /**
     * 用户交易追踪记录。
     */
    public static String tradeTx(long chainId, String txHash) {
        return "anchor:trade:" + chainId + ":" + txHash;
    }

    /**
     * 仍在轮询回执的交易哈希集合（值为小写 txHash）。
     */
    public static String tradePending(long chainId) {
        return "anchor:trade:pending:" + chainId;
    }

    /**
     * 扫块选主锁；value 为持有者实例 ID。
     */
    public static String scannerLock(long chainId) {
        return "anchor:scanner:lock:" + chainId;
    }

    /**
     * 报价接口按 IP 限流计数。
     */
    public static String quoteRateLimit(String clientIp) {
        return "anchor:quote:rl:" + clientIp;
    }
}
