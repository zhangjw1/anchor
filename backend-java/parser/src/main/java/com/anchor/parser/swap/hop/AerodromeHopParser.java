package com.anchor.parser.swap.hop;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.evm.generated.AerodromePool;
import com.anchor.parser.dto.PoolTokensDTO;
import com.anchor.parser.service.PoolFactoryService;
import com.anchor.parser.service.PoolTokenService;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;

import java.util.Optional;

/**
 * 识别经 Aerodrome 经典 Factory 校验的池子 Swap 跳。
 */
@Component
public class AerodromeHopParser extends AbstractFactoryHopParser {

    private static final String SWAP_TOPIC = EventEncoder.encode(AerodromePool.SWAP_EVENT);

    public AerodromeHopParser(PoolFactoryService poolFactoryService, PoolTokenService poolTokenService) {
        super(poolFactoryService, poolTokenService);
    }

    @Override
    protected String factory(ChainDefinition chain) {
        return chain.contracts().aerodromeFactory();
    }

    @Override
    protected String swapTopic() {
        return SWAP_TOPIC;
    }

    @Override
    protected Optional<DetectedHop> decode(ChainLog logItem, PoolTokensDTO tokens) {
        return V2PoolHopDecoder.decode(AnchorConstants.DEX_AERODROME, logItem, tokens);
    }
}
