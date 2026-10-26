package com.anchor.common.persistence.mapper;

import com.anchor.common.persistence.entity.TokenEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TokenMapper extends BaseMapper<TokenEntity> {

    TokenEntity findOne(@Param("chainId") long chainId, @Param("address") String address);

    int upsert(TokenEntity entity);
}
