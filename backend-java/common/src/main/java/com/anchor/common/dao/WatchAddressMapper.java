package com.anchor.common.dao;

import com.anchor.common.dataobject.WatchAddressDO;
import com.anchor.common.dto.WatchAddressDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WatchAddressMapper extends BaseMapper<WatchAddressDO> {

    List<WatchAddressDTO> listByChain(@Param("chainId") long chainId);

    List<String> listAddresses(@Param("chainId") long chainId);
}
