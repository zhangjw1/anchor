package com.anchor.common.constant;

/**
 * 扫描、RPC、分页和缓存的固定参数，避免业务代码里写魔数。
 */
public final class AnchorConstants {

    /** 原生币占位地址，解析 WETH Withdrawal / 交易 value 时记成 ETH，统一用该地址表示。 */
    public static final String NATIVE_TOKEN_ADDRESS = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

    /** 审计字段 created_by / updated_by：API 模块写入。 */
    public static final String ACTOR_API = "api";

    /** 审计字段 created_by / updated_by：indexer 模块写入（扫块、重组、确认状态）。 */
    public static final String ACTOR_INDEXER = "indexer";

    /** 审计字段 created_by / updated_by：parser 模块写入（代币元数据）。 */
    public static final String ACTOR_PARSER = "parser";

    /** 交换记录中的 DEX 名称，Uniswap V3 解析器写入 swap.dex。 */
    public static final String DEX_UNISWAP_V3 = "Uniswap V3";

    /** Uniswap V2 池子交换。 */
    public static final String DEX_UNISWAP_V2 = "Uniswap V2";

    /** Aerodrome 经典（V2 风格）池子交换。 */
    public static final String DEX_AERODROME = "Aerodrome";

    /** Aerodrome Slipstream（CL）池子交换。 */
    public static final String DEX_AERODROME_SLIPSTREAM = "Aerodrome Slipstream";

    /** 同一笔交易跨多个协议时的顶层 dex。 */
    public static final String DEX_MULTI = "Multi";

    /**
     * 本地 block_hash 默认保留窗口；实际以 anchor.reorg.hash-window 为准。
     * Base finalized 常落后数百块，64 不够，默认 512。
     */
    public static final int RECENT_HASH_LIMIT = 512;

    /** WebSocket 单次发送超时（毫秒）。 */
    public static final int WS_SEND_TIME_LIMIT_MS = 5_000;

    /** WebSocket 发送缓冲区上限（字节）。 */
    public static final int WS_SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    /** 单次 RPC HTTP 调用的连接 / 读 / 调用超时（秒）。 */
    public static final int RPC_TIMEOUT_SECONDS = 10;

    /** FailoverRpcGateway 对同一请求的最大尝试次数（含首次）。 */
    public static final int RPC_MAX_ATTEMPTS = 3;

    /** RPC 失败后重试前的退避等待（毫秒）。 */
    public static final long RPC_BACKOFF_MILLIS = 200L;

    /** 追块时一次并发拉取的最大区块数（虚拟线程并行 getBlock）。 */
    public static final int CATCH_UP_CONCURRENCY = 4;

    /** 扫块 Redis 选主锁 TTL（秒）；持有者需在过期前续约。 */
    public static final long SCANNER_LOCK_TTL_SECONDS = 15L;

    /** 扫块锁续约间隔（毫秒）。 */
    public static final long SCANNER_LOCK_RENEW_MS = 5_000L;

    /** 代币 symbol / decimals 在 Redis 中的缓存时长（小时）。 */
    public static final int TOKEN_CACHE_HOURS = 24;

    /** Uniswap V3 池子对应 factory 地址在 Redis 中的缓存时长（天）。 */
    public static final int POOL_CACHE_DAYS = 7;

    /** 一个 ABI 字（32 字节）带 0x 前缀的十六进制长度，eth_call 返回短于它视为无效。 */
    public static final int ABI_WORD_HEX_LENGTH = 66;

    /** 一个 ABI 字（32 字节）不带前缀的十六进制长度。 */
    public static final int ABI_WORD_HEX_CHARS = 64;

    /** 交换列表查询的默认每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 交换列表查询允许的最大每页条数。 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 监控地址名称字段允许的最大长度。 */
    public static final int WATCH_NAME_MAX_LENGTH = 128;

    /** 监控地址备注字段允许的最大长度。 */
    public static final int WATCH_NOTE_MAX_LENGTH = 512;

    /** 批量写 swap 表时每批的最大条数。 */
    public static final int BATCH_SIZE = 500;

    /** WebSocket 每个连接可回放的最近事件条数上限。 */
    public static final int WS_REPLAY_LIMIT = 1000;

    /** WebSocket 心跳 ping 的调度间隔（毫秒）。 */
    public static final long WS_PING_INTERVAL_MS = 15_000L;

    /** WebSocket 推送指标统计的调度间隔（毫秒）。 */
    public static final long WS_METRICS_INTERVAL_MS = 3_000L;

    /** Kafka 单条发送等待确认的超时（秒）。 */
    public static final int KAFKA_SEND_TIMEOUT_SECONDS = 10;

    /** Outbox 每批最多拉取条数。 */
    public static final int OUTBOX_BATCH_SIZE = 200;

    /** Outbox 单条投递超时（毫秒）。 */
    public static final long OUTBOX_SEND_TIMEOUT_MS = 2_000L;

    /** Outbox 中继轮询间隔（毫秒）。 */
    public static final long OUTBOX_RELAY_DELAY_MS = 500L;

    /** Outbox 已发送记录保留天数。 */
    public static final int OUTBOX_RETENTION_DAYS = 7;

    /** Outbox 失败退避上限（秒）。 */
    public static final long OUTBOX_MAX_BACKOFF_SECONDS = 60L;

    /** Kafka lag / 链头后台刷新间隔（毫秒）。 */
    public static final long METRICS_REFRESH_INTERVAL_MS = 3_000L;

    /** Kafka lag 超过该毫秒未成功刷新则视为 stale。 */
    public static final long KAFKA_LAG_STALE_MS = 15_000L;

    /** API 模块消费 Kafka 主题时使用的消费者组 ID。 */
    public static final String KAFKA_GROUP_API = "anchor-api";

    /** Uniswap V3 报价使用的费率档（万分之一）。 */
    public static final int[] UNISWAP_V3_FEE_TIERS = {100, 500, 3000, 10_000};

    /** 资产余额与价格的 Redis 短缓存秒数。 */
    public static final int ASSET_CACHE_SECONDS = 5;

    /** 报价结果 Redis 短缓存秒数。 */
    public static final int QUOTE_CACHE_SECONDS = 5;

    /** 报价有效期（毫秒），过期后前端应重新请求。 */
    public static final long QUOTE_TTL_MS = 15_000L;

    /** 报价接口每个 IP 在窗口内允许的最大请求数。 */
    public static final int QUOTE_RATE_LIMIT_PER_WINDOW = 30;

    /** 报价限流窗口（秒）。 */
    public static final int QUOTE_RATE_LIMIT_WINDOW_SECONDS = 60;

    /** 滑点上限（基点），50% 以上拒绝。 */
    public static final int MAX_SLIPPAGE_BPS = 5_000;

    /** 认为池子流动性足够的最小 liquidity() 值；低于此值价格返回 null。 */
    public static final long MIN_POOL_LIQUIDITY = 1_000_000_000_000L;

    /** USD 展示用 6 位小数，1 美元 = 1_000_000 微美元。 */
    public static final int USD_MICRO_DECIMALS = 6;

    /** P5a 兑换 deadline：当前时间起多少秒。 */
    public static final long TRADE_DEADLINE_SECONDS = 120L;

    /** P5a 允许真实准备/上报的链 ID（Base Sepolia）。 */
    public static final long TRADE_ALLOWED_CHAIN_ID = 84532L;

    /** 交易回执轮询间隔毫秒。 */
    public static final long TRADE_POLL_INTERVAL_MS = 2_000L;

    /** 交易回执最长轮询次数。 */
    public static final int TRADE_POLL_MAX_ATTEMPTS = 90;

    /** 交易状态 Redis TTL（小时）。 */
    public static final long TRADE_STATUS_TTL_HOURS = 24L;

    private AnchorConstants() {
    }
}

