package com.anchor.parser.swap.support;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.support.Addresses;

/**
 * 按 chains.yaml 的聚合器 Router 表给交易打标签。
 */
public final class AggregatorLabels {

    private AggregatorLabels() {
    }

    /** 未命中返回 null。 */
    public static String of(ChainDefinition chain, String txTo) {
        String normalized = Addresses.lower(txTo);
        if (normalized == null || chain.aggregators() == null) {
            return null;
        }
        for (ChainDefinition.Aggregator aggregator : chain.aggregators()) {
            if (normalized.equalsIgnoreCase(aggregator.address())) {
                return aggregator.name();
            }
        }
        return null;
    }
}
