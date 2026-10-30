package com.anchor.parser.swap.hop;

import com.anchor.common.chain.ChainLog;
import com.anchor.common.chain.SwapParser;
import com.anchor.common.support.Addresses;
import com.anchor.parser.dto.PoolTokensDTO;
import com.anchor.parser.service.PoolFactoryService;
import com.anchor.parser.service.PoolTokenService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * 按 topic + factory 校验扫描回执，再解码为跳。
 */
final class FactoryVerifiedHopScanner {

    private FactoryVerifiedHopScanner() {
    }

    static List<DetectedHop> scan(SwapParser.SwapParseContext context, String expectedFactory, String swapTopic,
                                  PoolFactoryService poolFactoryService, PoolTokenService poolTokenService,
                                  BiFunction<ChainLog, PoolTokensDTO, Optional<DetectedHop>> decoder) {
        if (expectedFactory == null) {
            return List.of();
        }
        long chainId = context.chain().chainId();
        List<DetectedHop> hops = new ArrayList<>();
        for (ChainLog logItem : context.receipt().logs()) {
            if (logItem.topics().isEmpty() || !swapTopic.equalsIgnoreCase(logItem.topics().get(0))) {
                continue;
            }
            String poolFactory = poolFactoryService.findFactory(chainId, logItem.address()).orElse(null);
            if (poolFactory == null || !Addresses.lower(expectedFactory).equals(Addresses.lower(poolFactory))) {
                continue;
            }
            poolTokenService.findTokens(chainId, logItem.address())
                    .flatMap(tokens -> decoder.apply(logItem, tokens))
                    .ifPresent(hops::add);
        }
        return hops;
    }
}
