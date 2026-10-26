package com.anchor.common.manager;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainReceipt;
import com.anchor.common.chain.RpcGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FailoverRpcGatewayTest {

    @Test
    void switchesNodeAfterFailure() {
        RpcEndpoint down = new StubEndpoint("down", true, 1L);
        RpcEndpoint up = new StubEndpoint("up", false, 99L);
        RpcGateway gateway = new FailoverRpcGateway(Map.of(8453L, List.of(down, up)), new SimpleMeterRegistry(), 3, 0L);
        assertEquals(99L, gateway.latestBlockNumber(8453L));
    }

    @Test
    void throwsAfterRetries() {
        RpcEndpoint down = new StubEndpoint("down", true, 1L);
        RpcGateway gateway = new FailoverRpcGateway(Map.of(8453L, List.of(down)), new SimpleMeterRegistry(), 2, 0L);
        assertThrows(RpcGateway.RpcException.class, () -> gateway.latestBlockNumber(8453L));
    }

    private static final class StubEndpoint implements RpcEndpoint {
        private final String url;
        private final boolean fail;
        private final long block;
        private final TokenBucket bucket = new TokenBucket(1000d);

        private StubEndpoint(String url, boolean fail, long block) {
            this.url = url;
            this.fail = fail;
            this.block = block;
        }

        public String url() { return url; }
        public TokenBucket bucket() { return bucket; }
        public long latestBlockNumber() { if (fail) throw new IllegalStateException("down"); return block; }
        public ChainBlock getBlock(long blockNumber) { throw new UnsupportedOperationException(); }
        public List<ChainReceipt> getReceipts(List<String> txHashes) { throw new UnsupportedOperationException(); }
        public String ethCall(String to, String data) { throw new UnsupportedOperationException(); }
    }
}
