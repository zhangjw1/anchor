package com.anchor.common.dao;

import com.anchor.common.dto.SwapHopDTO;
import com.anchor.common.entity.SwapHopDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 交换路径跳的写入与按 swap 批量查询。
 */
@Mapper
public interface SwapHopMapper {

    int saveBatch(@Param("list") List<SwapHopDO> list);

    int deleteBySwapIds(@Param("swapIds") List<Long> swapIds);

    List<SwapHopDTO> listBySwapIds(@Param("swapIds") List<Long> swapIds);
}
