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

    /** 本地 block_hash 表最多保留的最近区块数；重组回溯下限与清理旧哈希都用这个窗口。 */
    public static final int RECENT_HASH_LIMIT = 64;

    /** 单次 RPC HTTP 调用的连接 / 读 / 调用超时（秒）。 */
    public static final int RPC_TIMEOUT_SECONDS = 10;

    /** FailoverRpcGateway 对同一请求的最大尝试次数（含首次）。 */
    public static final int RPC_MAX_ATTEMPTS = 3;

    /** RPC 失败后重试前的退避等待（毫秒）。 */
    public static final long RPC_BACKOFF_MILLIS = 200L;

    /** 追块时一次并发拉取的最大区块数（虚拟线程并行 getBlock）。 */
    public static final int CATCH_UP_CONCURRENCY = 4;

    /** 代币 symbol / decimals 在 Redis 中的缓存时长（小时）。 */
    public static final int TOKEN_CACHE_HOURS = 24;

    /** Uniswap V3 池子对应 factory 地址在 Redis 中的缓存时长（天）。 */
    public static final int POOL_CACHE_DAYS = 7;

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

    /** API 模块消费 Kafka 主题时使用的消费者组 ID。 */
    public static final String KAFKA_GROUP_API = "anchor-api";

    private AnchorConstants() {
    }
}
