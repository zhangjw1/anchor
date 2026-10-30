package com.anchor.parser.swap.hop;

import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.ChainLog;
import com.anchor.common.constant.AnchorConstants;
import com.anchor.common.evm.generated.UniswapV3Pool;
import com.anchor.parser.dto.PoolTokensDTO;
import com.anchor.parser.service.PoolFactoryService;
import com.anchor.parser.service.PoolTokenService;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;

import java.util.Optional;

/**
 * 识别经 Uniswap V3 Factory 校验的 CL 池 Swap 跳。
 */
@Component
public class UniswapV3HopParser extends AbstractFactoryHopParser {

    private static final String SWAP_TOPIC = EventEncoder.encode(UniswapV3Pool.SWAP_EVENT);

    public UniswapV3HopParser(PoolFactoryService poolFactoryService, PoolTokenService poolTokenService) {
        super(poolFactoryService, poolTokenService);
    }

    @Override
    protected String factory(ChainDefinition chain) {
        return chain.contracts().uniswapV3Factory();
    }

    @Override
    protected String swapTopic() {
        return SWAP_TOPIC;
    }

    @Override
    protected Optional<DetectedHop> decode(ChainLog logItem, PoolTokensDTO tokens) {
        return ClPoolHopDecoder.decode(AnchorConstants.DEX_UNISWAP_V3, logItem, tokens);
    }
}
