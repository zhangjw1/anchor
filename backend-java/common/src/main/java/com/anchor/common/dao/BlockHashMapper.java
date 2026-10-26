package com.anchor.common.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BlockHashMapper {

    String getHash(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber);

    int save(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber,
               @Param("blockHash") String blockHash, @Param("parentHash") String parentHash,
               @Param("actor") String actor);

    int deleteOlder(@Param("chainId") long chainId, @Param("minKeep") long minKeep);
}
