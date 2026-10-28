package com.anchor.api.vo;

/**
 * 链列表的返回对象。
 */
public record ChainVO(
        /** 链标识，对应 chains.yaml 的 key。 */
        String key,
        /** 链 ID，对应 chains.yaml 的 chainId。 */
        long chainId,
        /** 链显示名称，对应 chains.yaml 的 name。 */
        String name,
        /** 链类型，对应 chains.yaml 的 type。 */
        String type,
        /** 是否测试网，对应 chains.yaml 的 testnet。 */
        boolean testnet,
        /** 达到已确认所需的区块数，对应 chains.yaml 的 confirmationBlocks。 */
        int confirmationBlocks) {
}
