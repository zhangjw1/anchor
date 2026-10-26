package com.anchor.indexer.service;

import com.anchor.common.chain.ChainBlock;
import com.anchor.common.chain.ChainDefinition;
import com.anchor.common.chain.SwapParser;

import java.util.List;

public interface BlockIngestionService {

    void persist(ChainDefinition chain, ChainBlock block, List<SwapParser.ParsedSwap> swaps);
}
