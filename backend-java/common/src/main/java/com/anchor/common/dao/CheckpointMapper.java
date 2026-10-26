package com.anchor.common.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CheckpointMapper {

    Long getBlockNumber(@Param("chainId") long chainId);

    int advance(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber,
                @Param("blockHash") String blockHash, @Param("actor") String actor);
}
