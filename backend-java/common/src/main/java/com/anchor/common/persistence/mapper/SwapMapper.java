package com.anchor.common.persistence.mapper;

import com.anchor.common.persistence.entity.SwapEntity;
import com.anchor.common.persistence.model.SwapFilter;
import com.anchor.common.persistence.model.SwapRow;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SwapMapper {

    int upsertBatch(@Param("list") List<SwapEntity> list);

    IPage<SwapRow> selectPageByFilter(IPage<SwapRow> page, @Param("query") SwapFilter query);

    long countByChain(@Param("chainId") long chainId);
}
