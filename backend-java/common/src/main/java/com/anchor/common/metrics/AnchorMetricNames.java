package com.anchor.common.metrics;

/**
 * Prometheus / Micrometer 指标名。
 */
public final class AnchorMetricNames {

    public static final String RPC_REQUESTS = "rpc.requests";
    public static final String RPC_ERRORS = "rpc.errors";
    public static final String RPC_LATENCY = "rpc.latency";

    public static final String INDEXER_BLOCKS = "anchor.indexer.blocks";
    public static final String INDEXER_SWAPS = "anchor.indexer.swaps";
    public static final String INDEXER_PARSE_FAILURES = "anchor.indexer.parse.failures";

    public static final String QUOTE_LATENCY = "anchor.quote.latency";
    public static final String QUOTE_FAILURES = "anchor.quote.failures";

    public static final String TRADE_REPORTS = "anchor.trade.reports";
    public static final String TRADE_CONFIRMED = "anchor.trade.confirmed";
    public static final String TRADE_FAILED = "anchor.trade.failed";
    public static final String TRADE_CONFIRM_LATENCY = "anchor.trade.confirm.latency";

    public static final String WS_CONNECTIONS = "anchor.ws.connections";

    private AnchorMetricNames() {
    }
}
