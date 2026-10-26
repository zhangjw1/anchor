package com.anchor.common.domain;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static String watchSet(long chainId) {
        return "anchor:watch:" + chainId;
    }

    public static String token(long chainId, String address) {
        return "anchor:token:" + chainId + ":" + address;
    }

    public static String poolFactory(long chainId, String pool) {
        return "anchor:pool-factory:" + chainId + ":" + pool;
    }
}
