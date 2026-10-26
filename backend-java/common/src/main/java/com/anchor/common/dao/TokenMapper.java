package com.anchor.common.dao;

import com.anchor.common.dataobject.TokenDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TokenMapper extends BaseMapper<TokenDO> {

    TokenDO getByChainAndAddress(@Param("chainId") long chainId, @Param("address") String address);

    int save(TokenDO entity);
}
