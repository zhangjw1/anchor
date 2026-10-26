package com.anchor.api.vo;

import com.anchor.common.dto.WatchAddressDTO;

public record WatchAddressVO(String id, Long chainId, String address, String name, String tag, String note,
                             Long swapCount, Long lastActiveAt, Long createdAt) {

    public static WatchAddressVO from(WatchAddressDTO row) {
        return new WatchAddressVO(String.valueOf(row.getId()), row.getChainId(), row.getAddress(), row.getName(),
                row.getTag(), row.getNote(), row.getSwapCount(), row.getLastActiveAt(), row.getCreatedAtMs());
    }
}
