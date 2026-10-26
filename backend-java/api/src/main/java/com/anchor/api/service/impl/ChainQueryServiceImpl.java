package com.anchor.api.service.impl;

import com.anchor.api.service.ChainQueryService;
import com.anchor.api.vo.ChainVO;
import com.anchor.common.chain.ChainRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChainQueryServiceImpl implements ChainQueryService {

    private final ChainRegistry chainRegistry;

    public ChainQueryServiceImpl(ChainRegistry chainRegistry) {
        this.chainRegistry = chainRegistry;
    }

    @Override
    public List<ChainVO> list() {
        return chainRegistry.all().stream()
                .map(chain -> new ChainVO(chain.key(), chain.chainId(), chain.name(), chain.type().name(),
                        chain.testnet(), chain.confirmationBlocks()))
                .toList();
    }
}
