package com.anchor.common.dto;

import com.anchor.common.chain.SwapParser;
import com.anchor.common.domain.ConfirmStatusEnum;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 新入库交换的事件。金额在发出前已转成十进制字符串。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SwapEventDTO(long chainId, String txHash, int logIndex, long blockNumber, String direction, String dex,
                           String aggregator, String tokenIn, String tokenOut, String amountIn, String amountOut,
                           String confirmStatus, String watchedAddress, List<Hop> hops) {

    public record Hop(int hopIndex, String dex, String pool, String tokenIn, String tokenOut,
                      String amountIn, String amountOut, int logIndex) {
    }

    /**
     * 从解析结果生成事件，初始状态为确认中。
     */
    public static SwapEventDTO from(SwapParser.ParsedSwap swap) {
        List<Hop> hops = swap.hops().stream()
                .map(h -> new Hop(h.hopIndex(), h.dex(), h.pool(), h.tokenIn(), h.tokenOut(),
                        h.amountIn().toString(), h.amountOut().toString(), h.logIndex()))
                .toList();
        return new SwapEventDTO(swap.chainId(), swap.txHash(), swap.logIndex(), swap.blockNumber(), swap.direction().name(),
                swap.dex(), swap.aggregator(), swap.tokenIn(), swap.tokenOut(), swap.amountIn().toString(),
                swap.amountOut().toString(), ConfirmStatusEnum.CONFIRMING.name(), swap.watchedAddress(), hops);
    }
}
