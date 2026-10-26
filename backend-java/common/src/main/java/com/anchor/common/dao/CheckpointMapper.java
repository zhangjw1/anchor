package com.anchor.common.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 每条链的扫描进度。
 */
@Mapper
public interface CheckpointMapper {
    /**
     * 已处理到的高度。还没有进度时返回 null，不能当成 0。
     */

    Long getBlockNumber(@Param("chainId") long chainId);
    /**
     * 只允许向前推进。重复处理同一高度不会把进度打回去。
     */
    int advance(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber,
                @Param("blockHash") String blockHash, @Param("actor") String actor);

    /**
     * 重组后把进度退回到仍有效的祖先块。
     */
    int rewind(@Param("chainId") long chainId, @Param("blockNumber") long blockNumber,
               @Param("blockHash") String blockHash, @Param("actor") String actor);
}
