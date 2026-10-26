package com.anchor.common.rpc;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.ChainRegistry;
import com.anchor.common.chain.RpcGateway;
import com.anchor.common.domain.AnchorConstants;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FailoverRpcGateway implements RpcGateway {

    private static final Logger log = LoggerFactory.getLogger(FailoverRpcGateway.class);

    private final Map<Long, List<RpcEndpoint>> endpoints;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final long backoffMillis;

    public FailoverRpcGateway(ChainRegistry registry, MeterRegistry meterRegistry) {
        this(build(registry), meterRegistry, AnchorConstants.RPC_MAX_ATTEMPTS, AnchorConstants.RPC_BACKOFF_MILLIS);
    }

    public FailoverRpcGateway(Map<Long, List<RpcEndpoint>> endpoints, MeterRegistry meterRegistry, int maxAttempts, long backoffMillis) {
        this.endpoints = endpoints;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
    }

    @Override
    public long latestBlockNumber(long chainId) {
        return execute(chainId, "eth_blockNumber", RpcEndpoint::latestBlockNumber);
    }

    @Override
    public ChainBlock getBlock(long chainId, long blockNumber) {
        return execute(chainId, "eth_getBlockByNumber", endpoint -> endpoint.getBlock(blockNumber));
    }

    @Override
    public List<ChainReceipt> getReceipts(long chainId, List<String> txHashes) {
        if (txHashes.isEmpty()) {
            return List.of();
        }
        return execute(chainId, "eth_getTransactionReceipt", endpoint -> {
            endpoint.bucket().acquire();
            for (int i = 1; i < txHashes.size(); i++) {
                endpoint.bucket().acquire();
            }
            return endpoint.getReceipts(txHashes);
        }, false);
    }

    @Override
    public String ethCall(long chainId, String to, String data) {
        return execute(chainId, "eth_call", endpoint -> endpoint.ethCall(to, data));
    }

    private <T> T execute(long chainId, String method, RpcCall<T> call) {
        return execute(chainId, method, call, true);
    }

    private <T> T execute(long chainId, String method, RpcCall<T> call, boolean acquirePermit) {
        List<RpcEndpoint> nodes = endpoints.get(chainId);
        if (nodes == null || nodes.isEmpty()) {
            throw new RpcException("no rpc endpoint for chain " + chainId, null);
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            for (RpcEndpoint node : nodes) {
                Timer.Sample sample = Timer.start(meterRegistry);
                try {
                    if (acquirePermit) {
                        node.bucket().acquire();
                    }
                    T value = call.apply(node);
                    meterRegistry.counter("rpc.requests", "chainId", String.valueOf(chainId), "method", method).increment();
                    sample.stop(meterRegistry.timer("rpc.latency", "chainId", String.valueOf(chainId), "method", method, "url", node.url()));
                    return value;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new RpcException("rpc interrupted", ex);
                } catch (Exception ex) {
                    meterRegistry.counter("rpc.errors", "chainId", String.valueOf(chainId), "method", method, "url", node.url()).increment();
                    sample.stop(meterRegistry.timer("rpc.latency", "chainId", String.valueOf(chainId), "method", method, "url", node.url()));
                    last = new RpcException(ex.getMessage(), ex);
                    log.warn("rpc failed chainId={} method={} url={} attempt={}", chainId, method, node.url(), attempt);
                }
            }
            if (attempt < maxAttempts) {
                sleep(backoffMillis * (1L << (attempt - 1)));
            }
        }
        throw last == null ? new RpcException("rpc failed", null) : last;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RpcException("rpc interrupted", ex);
        }
    }

    private static Map<Long, List<RpcEndpoint>> build(ChainRegistry registry) {
        Map<Long, List<RpcEndpoint>> map = new LinkedHashMap<>();
        registry.all().forEach(chain -> {
            List<RpcEndpoint> nodes = new ArrayList<>();
            chain.rpcNodes().forEach(node -> nodes.add(new Web3jRpcEndpoint(node.url(), node.requestsPerSecond())));
            map.put(chain.chainId(), List.copyOf(nodes));
        });
        return Map.copyOf(map);
    }

    @FunctionalInterface
    private interface RpcCall<T> {
        T apply(RpcEndpoint endpoint) throws Exception;
    }
}
