package com.anchor.api.vo;
/**
 * 链列表的返回对象。
 */
public record ChainVO(String key, long chainId, String name, String type, boolean testnet, int confirmationBlocks) {
}
