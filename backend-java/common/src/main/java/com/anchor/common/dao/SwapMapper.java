package com.anchor.common.dao;

import com.anchor.common.dataobject.SwapDO;
import com.anchor.common.dto.SwapDTO;
import com.anchor.common.query.SwapQuery;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SwapMapper {

    int saveBatch(@Param("list") List<SwapDO> list);

    IPage<SwapDTO> listByQuery(IPage<SwapDTO> page, @Param("query") SwapQuery query);

    long countByChain(@Param("chainId") long chainId);
}
