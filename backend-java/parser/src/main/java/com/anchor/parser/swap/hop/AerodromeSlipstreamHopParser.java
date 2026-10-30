package com.anchor.parser.swap.hop;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.evm.generated.AerodromeSlipstreamPool;
import com.anchor.parser.dto.PoolTokensDTO;
import com.anchor.parser.service.PoolFactoryService;
import com.anchor.parser.service.PoolTokenService;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;

import java.util.Optional;

/**
 * 识别经 Aerodrome Slipstream Factory 校验的 CL 池 Swap 跳。
 */
@Component
public class AerodromeSlipstreamHopParser extends AbstractFactoryHopParser {

    private static final String SWAP_TOPIC = EventEncoder.encode(AerodromeSlipstreamPool.SWAP_EVENT);

    public AerodromeSlipstreamHopParser(PoolFactoryService poolFactoryService, PoolTokenService poolTokenService) {
        super(poolFactoryService, poolTokenService);
    }

    @Override
    protected String factory(ChainDefinition chain) {
        return chain.contracts().aerodromeSlipstreamFactory();
    }

    @Override
    protected String swapTopic() {
        return SWAP_TOPIC;
    }

    @Override
    protected Optional<DetectedHop> decode(ChainLog logItem, PoolTokensDTO tokens) {
        return ClPoolHopDecoder.decode(AnchorConstants.DEX_AERODROME_SLIPSTREAM, logItem, tokens);
    }
}
