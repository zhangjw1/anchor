package com.anchor.common.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 最近区块哈希。用于发现父哈希断裂。
 */
@Mapper
public interface BlockHashMapper {
    /**
     * 读取某高度已保存的区块哈希，没有记录时返回 null。
     */
    String getHash(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber);
    /**
     * 写入或覆盖该高度的区块哈希和父哈希。
     */
    int save(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber,
             @Param("blockHash") String blockHash, @Param("parentHash") String parentHash,
             @Param("actor") String actor);

    /**
     * 只保留最近一段高度，更早的哈希删掉。
     */
    int deleteOlder(@Param("chainId") long chainId, @Param("minKeep") long minKeep);
    /**
     * 重组后删除分叉点之后的哈希。
     */
    int deleteAfter(@Param("chainId") long chainId, @Param("ancestor") long ancestor);
}
