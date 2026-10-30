package com.anchor.parser.swap.support;

import com.anchor.parser.swap.hop.DetectedHop;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwapPathResolverTest {

    private static final String A = "0xa";
    private static final String B = "0xb";
    private static final String C = "0xc";
    private static final String X = "0xd";
    private static final String Y = "0xe";

    @Test
    void keepsSerialPath() {
        List<DetectedHop> hops = List.of(hop(A, B, 1), hop(B, C, 2));
        assertEquals(hops, SwapPathResolver.onPath(hops, A, C));
    }

    @Test
    void keepsSplitRoute() {
        List<DetectedHop> hops = List.of(hop(A, B, 1), hop(A, C, 2), hop(B, C, 3));
        assertEquals(hops, SwapPathResolver.onPath(hops, A, C));
    }

    @Test
    void dropsUnrelatedSwap() {
        DetectedHop unrelated = hop(X, Y, 2);
        List<DetectedHop> result = SwapPathResolver.onPath(List.of(hop(A, B, 1), unrelated, hop(B, C, 3)), A, C);
        assertEquals(List.of(hop(A, B, 1), hop(B, C, 3)), result);
    }

    @Test
    void dropsDeadEndBranch() {
        List<DetectedHop> result = SwapPathResolver.onPath(List.of(hop(A, B, 1), hop(A, X, 2), hop(B, C, 3)), A, C);
        assertEquals(List.of(hop(A, B, 1), hop(B, C, 3)), result);
    }

    @Test
    void returnsEmptyWhenDisconnected() {
        assertTrue(SwapPathResolver.onPath(List.of(hop(A, B, 1), hop(X, C, 2)), A, C).isEmpty());
    }

    private static DetectedHop hop(String tokenIn, String tokenOut, int logIndex) {
        return new DetectedHop("Test", "0xpool" + logIndex, tokenIn, tokenOut, BigInteger.ONE, BigInteger.ONE, logIndex);
    }
}
