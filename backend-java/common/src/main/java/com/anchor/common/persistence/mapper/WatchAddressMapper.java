package com.anchor.common.persistence.mapper;

import com.anchor.common.persistence.entity.WatchAddressEntity;
import com.anchor.common.persistence.model.WatchAddressRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WatchAddressMapper extends BaseMapper<WatchAddressEntity> {

    List<WatchAddressRow> listByChain(@Param("chainId") long chainId);

    List<String> listAddresses(@Param("chainId") long chainId);
}
