package com.anchor.api.vo;

public record ChainVO(String key, long chainId, String name, String type, boolean testnet, int confirmationBlocks) {
}
