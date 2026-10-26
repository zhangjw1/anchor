package com.anchor.api.vo;

import com.anchor.common.dto.WatchAddressDTO;

/**
 * 监控地址的返回对象。id 使用字符串，避免前端精度丢失。
 */
public record WatchAddressVO(String id, Long chainId, String address, String name, String tag, String note,
                             Long swapCount, Long lastActiveAt, Long createdAt) {
    /**
     * 把查询结果转换成接口字段，创建时间用毫秒时间戳。
     */
    public static WatchAddressVO from(WatchAddressDTO row) {
        return new WatchAddressVO(String.valueOf(row.getId()), row.getChainId(), row.getAddress(), row.getName(),
                row.getTag(), row.getNote(), row.getSwapCount(), row.getLastActiveAt(), row.getCreatedAtMs());
    }
}
