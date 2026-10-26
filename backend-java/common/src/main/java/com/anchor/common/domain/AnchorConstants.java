package com.anchor.common.domain;

public final class AnchorConstants {

    public static final String NATIVE_TOKEN_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    public static final String ACTOR_API = "api";
    public static final String ACTOR_INDEXER = "indexer";
    public static final String ACTOR_PARSER = "parser";
    public static final String DEX_UNISWAP_V3 = "Uniswap V3";
    public static final int RECENT_HASH_LIMIT = 64;
    public static final int RPC_TIMEOUT_SECONDS = 10;
    public static final int RPC_MAX_ATTEMPTS = 3;
    public static final long RPC_BACKOFF_MILLIS = 200L;
    public static final int CATCH_UP_CONCURRENCY = 4;
    public static final int TOKEN_CACHE_HOURS = 24;
    public static final int POOL_CACHE_DAYS = 7;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int BATCH_SIZE = 500;

    private AnchorConstants() {
    }
}
