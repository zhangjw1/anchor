package com.anchor.common.chain;

import com.anchor.common.support.RepoPaths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlChainRegistryTest {

    @Test
    void loadsBaseAndSepoliaFromSharedFile() {
        ChainRegistry registry = new YamlChainRegistry(RepoPaths.findUp("contracts-shared/chains.yaml"));
        ChainDefinition base = registry.requireByKey("base");
        assertEquals(8453L, base.chainId());
        assertEquals(10, base.confirmationBlocks());
        assertEquals("0x33128a8fc17869897dce68ed026d694621f6fdfd", base.contracts().uniswapV3Factory());
        assertTrue(base.quoteTokens().contains("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"));
        assertEquals(1, base.signModes().size());
        ChainDefinition sepolia = registry.requireById(84532L);
        assertTrue(sepolia.testnet());
        assertEquals(5, sepolia.confirmationBlocks());
        assertEquals(2, sepolia.signModes().size());
    }
}
