package com.anchor.parser.swap.support;

import com.anchor.parser.swap.hop.DetectedHop;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 只保留位于“用户付出币 → 用户收到币”路径上的跳。
 * <p>
 * 一跳被保留，当且仅当它的 tokenIn 能从付出币出发经若干跳到达，且它的 tokenOut 能继续到达收到币。
 * 这样串行多跳和聚合器拆单并行路由都会保留，同交易里与用户这笔兑换无关的 Swap 会被剔除。
 * 原生币在池子里以 WETH 出现，调用方需先把原生币地址换成 WETH 再传入。
 */
public final class SwapPathResolver {

    private SwapPathResolver() {
    }

    public static List<DetectedHop> onPath(List<DetectedHop> hops, String tokenIn, String tokenOut) {
        Set<String> reachable = forwardReachable(hops, tokenIn);
        Set<String> leadsToOut = backwardReachable(hops, tokenOut);
        return hops.stream()
                .filter(hop -> reachable.contains(hop.tokenIn()) && leadsToOut.contains(hop.tokenOut()))
                .toList();
    }

    private static Set<String> forwardReachable(List<DetectedHop> hops, String start) {
        Set<String> tokens = new HashSet<>();
        tokens.add(start);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DetectedHop hop : hops) {
                if (tokens.contains(hop.tokenIn()) && tokens.add(hop.tokenOut())) {
                    changed = true;
                }
            }
        }
        return tokens;
    }

    private static Set<String> backwardReachable(List<DetectedHop> hops, String end) {
        Set<String> tokens = new HashSet<>();
        tokens.add(end);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DetectedHop hop : hops) {
                if (tokens.contains(hop.tokenOut()) && tokens.add(hop.tokenIn())) {
                    changed = true;
                }
            }
        }
        return tokens;
    }
}
