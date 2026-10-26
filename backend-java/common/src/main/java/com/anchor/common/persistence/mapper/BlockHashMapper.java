package com.anchor.common.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BlockHashMapper {

    String findHash(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber);

    int upsert(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber,
               @Param("blockHash") String blockHash, @Param("parentHash") String parentHash,
               @Param("actor") String actor);

    int deleteOlder(@Param("chainId") long chainId, @Param("minKeep") long minKeep);
}
