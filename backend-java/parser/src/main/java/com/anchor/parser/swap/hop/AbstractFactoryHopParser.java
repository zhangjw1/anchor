package com.anchor.parser.swap.hop;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.chain.SwapParser;
import com.anchor.parser.dto.PoolTokensDTO;
import com.anchor.parser.service.PoolFactoryService;
import com.anchor.parser.service.PoolTokenService;

import java.util.List;
import java.util.Optional;

/**
 * 协议跳解析器的公共流程：匹配 Swap topic，校验池子工厂，再交给子类解码金额。
 */
abstract class AbstractFactoryHopParser implements DexHopParser {

    private final PoolFactoryService poolFactoryService;
    private final PoolTokenService poolTokenService;

    protected AbstractFactoryHopParser(PoolFactoryService poolFactoryService, PoolTokenService poolTokenService) {
        this.poolFactoryService = poolFactoryService;
        this.poolTokenService = poolTokenService;
    }

    @Override
    public List<DetectedHop> detect(SwapParser.SwapParseContext context) {
        return FactoryVerifiedHopScanner.scan(context, factory(context.chain()), swapTopic(), poolFactoryService, poolTokenService, this::decode);
    }

    /** chains.yaml 中该协议的工厂地址；未配置返回 null，表示本链不支持。 */
    protected abstract String factory(ChainDefinition chain);

    /** 该协议池子 Swap 事件的 topic0。 */
    protected abstract String swapTopic();

    /** 把一条已通过工厂校验的 Swap 日志解码成跳。 */
    protected abstract Optional<DetectedHop> decode(ChainLog logItem, PoolTokensDTO tokens);
}
