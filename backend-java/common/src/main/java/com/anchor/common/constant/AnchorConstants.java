package com.anchor.common.constant;
/**
 * 扫描、RPC、分页和缓存的固定参数，避免业务代码里写魔数。
 */
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
    public static final int WATCH_NAME_MAX_LENGTH = 128;
    public static final int WATCH_NOTE_MAX_LENGTH = 512;
    public static final int BATCH_SIZE = 500;
    public static final int WS_REPLAY_LIMIT = 1000;
    public static final long WS_PING_INTERVAL_MS = 15_000L;
    public static final long WS_METRICS_INTERVAL_MS = 3_000L;
    public static final int KAFKA_SEND_TIMEOUT_SECONDS = 10;
    public static final String KAFKA_GROUP_API = "anchor-api";

    private AnchorConstants() {
    }
}
