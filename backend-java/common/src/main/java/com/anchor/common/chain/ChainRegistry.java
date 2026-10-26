package com.anchor.common.chain;

import java.util.List;

public interface ChainRegistry {

    List<ChainDefinition> all();

    ChainDefinition requireByKey(String key);

    ChainDefinition requireById(long chainId);
}
