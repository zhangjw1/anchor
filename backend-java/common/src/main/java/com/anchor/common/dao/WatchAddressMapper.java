package com.anchor.common.dao;

import com.anchor.common.dataobject.WatchAddressDO;
import com.anchor.common.dto.WatchAddressDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 监控地址。简单增删改走基类，带统计的列表走 XML。
 */
@Mapper
public interface WatchAddressMapper extends BaseMapper<WatchAddressDO> {
    /**
     * 带出交换次数和最近活跃时间。
     */

    List<WatchAddressDTO> listByChain(@Param("chainId") long chainId);
    /**
     * 只返回地址，供扫描和 Redis 重载使用。
     */

    List<String> listAddresses(@Param("chainId") long chainId);
}
