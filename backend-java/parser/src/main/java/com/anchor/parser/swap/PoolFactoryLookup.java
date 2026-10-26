package com.anchor.parser.swap;

import java.util.Optional;

public interface PoolFactoryLookup {
    Optional<String> findFactory(long chainId, String pool);
}
