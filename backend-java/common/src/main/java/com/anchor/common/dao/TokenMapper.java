package com.anchor.common.dao;

import com.anchor.common.dataobject.TokenDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 代币符号和精度。
 */
@Mapper
public interface TokenMapper extends BaseMapper<TokenDO> {
    /**
     * 按链和合约地址查询，没有记录时返回 null。
     */

    TokenDO getByChainAndAddress(@Param("chainId") long chainId, @Param("address") String address);
    /**
     * 不存在则插入，已存在则补齐符号和精度。
     */
    int save(TokenDO entity);
}
