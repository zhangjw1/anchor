package com.anchor.parser.service;

import com.anchor.common.chain.ChainDefinition;

public interface TokenMetadataService {

    void ensure(ChainDefinition chain, String address);
}
