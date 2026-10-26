package com.anchor.api.service;

import com.anchor.common.domain.AddressTagEnum;
import com.anchor.common.dto.WatchAddressDTO;

import java.util.List;

/**
 * 监控地址维护。写库的同时维护 Redis 地址集合。
 */
public interface WatchAddressService {
    /**
     * 按链重建 Redis 中的监控地址集合。
     */
    void reloadAll();
    /**
     * 查询一条链上的监控地址。
     */

    List<WatchAddressDTO> list(long chainId);
    /**
     * 新增地址。重复地址返回冲突。
     */

    WatchAddressDTO create(long chainId, String address, String name, AddressTagEnum tag, String note);
    /**
     * 更新地址。不存在返回未找到。
     */

    WatchAddressDTO update(long id, long chainId, String address, String name, AddressTagEnum tag, String note);
    /**
     * 删除地址。不存在返回未找到。
     */
    void delete(long id);
}
