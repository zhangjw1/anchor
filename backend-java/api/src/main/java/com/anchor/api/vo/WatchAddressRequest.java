package com.anchor.api.vo;

import com.anchor.common.domain.AddressTagEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增或更新监控地址的请求体。
 */
public record WatchAddressRequest(@NotNull Long chainId, @NotBlank String address, String name,
                                  @NotNull AddressTagEnum tag, String note) {
}
